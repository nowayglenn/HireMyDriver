package com.example.hiremydriver

import android.content.Context
import android.net.Uri
import android.os.Bundle
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

internal class DriverSetupFailure(message: String) : Exception(message)
internal class DriverRegistrationPasswordRequired : Exception()

/** Same Firebase project, separate auth session so customer sign-in remains unchanged. */
internal object DriverAuth {
    private val worker = Executors.newSingleThreadExecutor()
    private const val APP = "HireMyDriverDriver"
    @Volatile private var approvedUid: String? = null
    @Synchronized private fun app(context: Context): FirebaseApp {
        FirebaseApp.getApps(context).firstOrNull { it.name == APP }?.let { return it }
        val default = FirebaseApp.initializeApp(context) ?: throw DriverSetupFailure("Firebase setup is missing.")
        check(default.options.projectId == CustomerFirebaseProject.PROJECT_ID)
        return FirebaseApp.initializeApp(context.applicationContext, default.options, APP)
    }
    private fun auth(context: Context) = FirebaseAuth.getInstance(app(context))
    fun firebaseApp(context: Context): FirebaseApp = app(context)
    private fun database(context: Context) = FirebaseFirestore.getInstance(app(context))
    fun currentUser(context: Context): FirebaseUser? = runCatching { auth(context).currentUser }.getOrNull()
    fun approved(context: Context) = currentUser(context)?.uid?.let { it == approvedUid } == true
    fun observeApproval(context: Context, changed: () -> Unit): com.google.firebase.firestore.ListenerRegistration? {
        val uid = currentUser(context)?.uid ?: return null
        return database(context).collection("driverApplications").document(uid)
            .addSnapshotListener { snapshot, error ->
                if (currentUser(context)?.uid != uid) return@addSnapshotListener
                if (error != null || snapshot == null) { approvedUid = null; changed(); return@addSnapshotListener }
                approvedUid = uid.takeIf { snapshot.getString("status") == "approved" }
                if (snapshot.exists() && !snapshot.metadata.isFromCache)
                    RegisteredDriverDirectory.syncRegisteredAddress(context, uid)
                changed()
            }
    }
    fun signOut(context: Context) { approvedUid = null; auth(context).signOut() }
    fun preferenceName(context: Context, base: String) = currentUser(context)?.uid?.let { "${base}_$it" } ?: base
    fun resetPassword(context: Context, email: String): Task<Void> = auth(context).sendPasswordResetEmail(email.trim())
    fun changePassword(context: Context, currentPassword: String, newPassword: String): Task<Void> = try {
        CustomerAuth.changePasswordForSession(auth(context), currentPassword, newPassword)
    } catch (error: Exception) { Tasks.forException(error) }

    fun signIn(context: Context, email: String, password: String): Task<String> = Tasks.call(worker) {
        approvedUid = null
        val user = await(auth(context).signInWithEmailAndPassword(email.trim(), password)).user
            ?: throw DriverSetupFailure("Could not sign in. Please try again.")
        val db = database(context)
        val profile = await(db.collection("users").document(user.uid).get(Source.SERVER))
        if (profile.getString("role") != "driver") {
            auth(context).signOut()
            throw DriverSetupFailure("No submitted driver registration was found for this account. Please finish registration or use Customer login.")
        }
        val application = await(db.collection("driverApplications").document(user.uid).get(Source.SERVER))
        if (!application.exists()) throw DriverSetupFailure("Please complete your driver registration first.")
        requireSession(context, user.uid)
        val data = application.data ?: throw DriverSetupFailure("The driver application could not be loaded.")
        cacheApplication(context, user.uid, data)
        RegisteredDriverDirectory.publish(context, user.uid, data)
        worker.execute {
            runCatching {
                if (currentUser(context)?.uid != user.uid) return@runCatching
                val photo = DriverCloudStorage.profile(context, user.uid)
                val thumbnail = photo?.let { try { RegisteredDriverDirectory.thumbnail(it) } finally { it.recycle() } }
                if (currentUser(context)?.uid == user.uid && thumbnail != null)
                    RegisteredDriverDirectory.publish(context, user.uid, data, thumbnail)
            }
        }
        val status = data["status"] as? String ?: "pending"
        approvedUid = user.uid.takeIf { status == "approved" }
        if (status == "pending" || status == "approved") LaunchSession.remember(context, LaunchRole.DRIVER, user.uid)
        status
    }

    fun submit(context: Context, draft: Bundle): Task<Unit> = Tasks.call(worker) {
        val values = DriverRegistrationContract.fields.keys.associateWith { draft.getString(it, "").trim() }
        DriverRegistrationContract.validate(values, draft.getBoolean("terms"))
        val password = draft.getString("password", "")
        if (password.length < 8 || password != draft.getString("confirmPassword"))
            throw DriverSetupFailure("Please check your registration password and confirmation.")
        val photos = DriverRegistrationContract.documents.mapValues { (key, _) ->
            val source = draft.getString(key).orEmpty()
            if (source.isBlank()) throw DriverSetupFailure("Please add the license front, back and police clearance photos.")
            val uri = Uri.parse(source)
            require(uri.scheme in listOf("file", "content")) { "Please select your document photos again." }
            DriverCloudStorage.encode(context, uri)
        }
        val firebaseAuth = auth(context)
        approvedUid = null
        val attempts = context.getSharedPreferences("driver_registration_attempts", Context.MODE_PRIVATE)
        val email = values.getValue("email")
        val user = try {
            val creation = firebaseAuth.createUserWithEmailAndPassword(email, password)
            // Keep the retry marker even if a slow successful response arrives after our wait times out.
            creation.addOnSuccessListener { result -> result.user?.let {
                attempts.edit().putString(email.lowercase(), it.uid).apply()
            } }
            await(creation).user!!.also {
                // Retry marker contains only the account ID; never persist its password.
                attempts.edit().putString(email.lowercase(), it.uid).commit()
            }
        } catch (error: Exception) {
            if (causes(error).none { it is FirebaseAuthUserCollisionException }) throw error
            // Only resume an account created by this driver's unfinished registration.
            // Missing profiles can belong to legacy customers, so never convert them.
            val retryUid = attempts.getString(email.lowercase(), null)
                ?: throw CustomerAuth.AccountAlreadyExistsException()
            // Prove ownership before reading or resuming an existing account.
            val resumed = try {
                await(firebaseAuth.signInWithEmailAndPassword(email, password)).user
                    ?: throw DriverSetupFailure("Please sign in again to finish registration.")
            } catch (signInError: Exception) {
                if (causes(signInError).filterIsInstance<FirebaseAuthException>().any {
                    it.errorCode in setOf("ERROR_INVALID_CREDENTIAL", "ERROR_WRONG_PASSWORD",
                        "ERROR_INVALID_LOGIN_CREDENTIALS", "ERROR_USER_NOT_FOUND")
                }) throw DriverRegistrationPasswordRequired()
                throw signInError
            }
            if (resumed.uid != retryUid) throw CustomerAuth.AccountAlreadyExistsException()
            resumed
        }
        val db = database(context)
        val userRef = db.collection("users").document(user.uid)
        val applicationRef = db.collection("driverApplications").document(user.uid)
        val previousProfile = await(userRef.get(Source.SERVER))
        if (previousProfile.exists() && previousProfile.getString("role") != "driver") {
            firebaseAuth.signOut()
            throw CustomerAuth.AccountAlreadyExistsException()
        }
        val existing = await(applicationRef.get(Source.SERVER))
        if (existing.exists()) {
            attempts.edit().remove(email.lowercase()).commit()
            throw CustomerAuth.AccountAlreadyExistsException()
        }
        // Identical retries use identical immutable paths, avoiding duplicate private documents.
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(user.uid.toByteArray())
        photos.values.forEach { digest.update(MessageDigest.getInstance("SHA-256").digest(it)) }
        val submission = UUID.nameUUIDFromBytes(digest.digest()).toString()
        val accountValues = values + ("email" to (user.email ?: email))
        val data = DriverRegistrationContract.application(accountValues, user.uid, submission).toMutableMap()
        photos.forEach { (key, bytes) ->
            requireSession(context, user.uid)
            DriverCloudStorage.upload(context, user.uid,
                DriverRegistrationContract.objectPath(user.uid, submission, DriverRegistrationContract.documents.getValue(key)), bytes)
        }
        draft.getByteArray("selfie")?.takeIf { it.isNotEmpty() }?.let { bytes ->
            val temporary = File.createTempFile("driver-selfie-", ".jpg", context.cacheDir)
            try {
                temporary.writeBytes(bytes)
                DriverCloudStorage.upload(context, user.uid, "${user.uid}/profile.jpg",
                    DriverCloudStorage.encode(context, Uri.fromFile(temporary)), profile = true)
            } finally { temporary.delete() }
        }
        data["createdAt"] = FieldValue.serverTimestamp()
        requireSession(context, user.uid)
        val batch = db.batch()
        if (!previousProfile.exists()) batch.set(userRef, mapOf(
            "fullName" to values.getValue("name"), "email" to accountValues.getValue("email"),
            "phoneNumber" to values.getValue("mobile"), "role" to "driver",
            "createdAt" to FieldValue.serverTimestamp()
        ))
        batch.set(applicationRef, data)
        await(batch.commit())
        attempts.edit().remove(email.lowercase()).commit()
        requireSession(context, user.uid)
        cacheApplication(context, user.uid, data)
        val portrait = draft.getByteArray("selfie")?.let { android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size) }
        val thumbnail = portrait?.let { try { RegisteredDriverDirectory.thumbnail(it) } finally { it.recycle() } }
        RegisteredDriverDirectory.publish(context, user.uid, data, thumbnail)
        runCatching { await(user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(values.getValue("name")).build())) }
        LaunchSession.remember(context, LaunchRole.DRIVER, user.uid)
        Unit
    }

    private fun cacheApplication(context: Context, uid: String, data: Map<String, Any>) {
        requireSession(context, uid)
        val docs = context.getSharedPreferences("${DriverDocumentStore.PREFERENCES}_$uid", Context.MODE_PRIVATE).edit().clear()
        mapOf("licenseNumber" to "licenseNumber", "licenseExpiry" to "licenseExpiration",
            "clearanceIssue" to "issueDate", "clearanceExpiry" to "validityDate").forEach { (local, cloud) ->
            docs.putString(local, data[cloud] as? String ?: "")
        }
        mapOf("front" to "licenseFrontPath", "back" to "licenseBackPath", "police" to "policeImagePath")
            .forEach { (local, cloud) ->
                (data[cloud] as? String)?.let { path ->
                    val parts = path.split('/')
                    require(parts.size == 3 && parts[0] == uid)
                    DriverRegistrationContract.objectPath(uid, parts[1], parts[2])
                    docs.putString(local, DriverCloudStorage.reference(path))
                }
            }
        docs.apply()
        context.getSharedPreferences("${DriverPersonalSettingsActivity.PREFERENCES}_$uid", Context.MODE_PRIVATE).edit().apply {
            mapOf("name" to "fullName", "email" to "email", "mobile" to "phoneNumber",
                "birthDate" to "birthDate", "address" to "address", "bio" to "bio").forEach { (local, cloud) ->
                putString(local, data[cloud] as? String ?: "")
            }
            putString("experience", data["experience"] as? String ?: "")
            apply()
        }
    }
    private fun requireSession(context: Context, uid: String) {
        if (currentUser(context)?.uid != uid) throw DriverSetupFailure("Your driver session changed. Please sign in again.")
    }
    private fun <T> await(task: Task<T>): T = Tasks.await(task, 60, TimeUnit.SECONDS)
    private fun causes(error: Throwable?) = generateSequence(error) { it.cause }.take(10).toList()
    fun message(error: Exception?): String {
        val chain = causes(error)
        chain.filterIsInstance<DriverSetupFailure>().firstOrNull()?.let { return it.message.orEmpty() }
        chain.filterIsInstance<IllegalArgumentException>().firstOrNull()?.let { return it.message ?: "Please check your registration details." }
        val firestore = chain.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
        if (firestore?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED)
            return "Driver registration access was denied. Check the published Firestore driver rules."
        if (firestore != null || chain.any { it is java.io.IOException || it is java.util.concurrent.TimeoutException })
            return "Could not connect. Your application may still be processing; retry with the same email and password."
        return CustomerAuth.message(error)
    }
}
