package com.example.hiremydriver

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest

/** Customer authentication in the shared project. Administrative access is enforced by the server. */
object CustomerAuth {
    internal class AccountAlreadyExistsException : Exception()
    internal fun isExistingAccount(error: Exception?): Boolean =
        generateSequence(error as Throwable?) { it.cause }.take(10).any {
            it is AccountAlreadyExistsException ||
                (it is FirebaseAuthException && it.errorCode == "ERROR_EMAIL_ALREADY_IN_USE")
        }
    private class SetupException : Exception()
    internal class PasswordSessionException : Exception()
    internal class PasswordProviderException : Exception()

    fun changePassword(context: Context, currentPassword: String, newPassword: String): Task<Void> = try {
        changePasswordForSession(auth(context), currentPassword, newPassword)
    } catch (error: Exception) { Tasks.forException(error) }

    internal fun changePasswordForSession(firebaseAuth: FirebaseAuth, currentPassword: String, newPassword: String): Task<Void> = try {
        val user = firebaseAuth.currentUser ?: throw PasswordSessionException()
        val email = user.email ?: throw PasswordProviderException()
        if (user.providerData.none { it.providerId == EmailAuthProvider.PROVIDER_ID }) {
            throw PasswordProviderException()
        }
        val credential = EmailAuthProvider.getCredential(email, currentPassword)
        user.reauthenticate(credential).continueWithTask { verification ->
            if (!verification.isSuccessful) throw verification.exception ?: IllegalStateException()
            // Never update a previous user's account after a local session change.
            if (firebaseAuth.currentUser?.uid != user.uid) throw PasswordSessionException()
            user.updatePassword(newPassword)
        }
    } catch (error: Exception) { Tasks.forException(error) }

    internal fun passwordChangeFailure(error: Exception?): PasswordChangeFailure {
        val causes = generateSequence<Throwable>(error) { it.cause }.take(8).toList()
        val code = causes.filterIsInstance<FirebaseAuthException>().firstOrNull()?.errorCode
        return when {
            causes.any { it is PasswordSessionException } || code in setOf("ERROR_USER_TOKEN_EXPIRED", "ERROR_INVALID_USER_TOKEN", "ERROR_REQUIRES_RECENT_LOGIN") ->
                PasswordChangeFailure(PasswordChangeField.FORM, "Please sign in again before changing your password.")
            causes.any { it is PasswordProviderException } ->
                PasswordChangeFailure(PasswordChangeField.FORM, "This account does not use a password. Use your sign-in provider to manage it.")
            code in setOf("ERROR_INVALID_CREDENTIAL", "ERROR_WRONG_PASSWORD", "ERROR_INVALID_LOGIN_CREDENTIALS") ->
                PasswordChangeFailure(PasswordChangeField.CURRENT, "Current password is incorrect.")
            else -> {
                val failure = failure(error)
                PasswordChangeFailure(if (failure.field == AuthErrorField.PASSWORD) PasswordChangeField.NEW else PasswordChangeField.FORM, failure.message)
            }
        }
    }

    private fun auth(context: Context): FirebaseAuth {
        val app = FirebaseApp.initializeApp(context.applicationContext) ?: throw SetupException()
        if (!CustomerFirebaseProject.accepts(app.options.projectId)) throw SetupException()
        return FirebaseAuth.getInstance(app)
    }

    @JvmStatic fun currentUser(context: Context): FirebaseUser? = try { auth(context).currentUser } catch (_: SetupException) { null }
    @JvmStatic fun signOut(context: Context) { try { auth(context).signOut() } catch (_: SetupException) { } }

    fun signInGoogle(context: Context, token: String): Task<FirebaseUser> = try {
        auth(context).signInWithCredential(com.google.firebase.auth.GoogleAuthProvider.getCredential(token, null)).continueWith { task ->
            if (!task.isSuccessful) throw task.exception ?: IllegalStateException()
            task.result.user ?: throw IllegalStateException()
        }
    } catch (error: Exception) { Tasks.forException(error) }

    @JvmStatic fun syncAccount(context: Context): Task<Void> {
        val user = currentUser(context) ?: return Tasks.forException(PasswordSessionException())
        val preferences = context.getSharedPreferences("customer_profile_${user.uid}", Context.MODE_PRIVATE)
        return completeGoogleProfile(context, user, displayName(context), preferences.getString("phone", "").orEmpty())
    }

    fun completeGoogleProfile(context: Context, user: FirebaseUser, name: String, phone: String): Task<Void> {
        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        return db.runTransaction { tx ->
            check(currentUser(context)?.uid == user.uid)
            val ref = db.collection("users").document(user.uid)
            val old = tx.get(ref)
            check(!old.exists() || old.getString("role") == "customer") { "Use the driver login for this account." }
            if (!old.exists()) tx.set(ref, mapOf("fullName" to name, "phoneNumber" to phone,
                "email" to user.email.orEmpty(), "role" to "customer",
                "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()))
            null
        }.continueWith { task ->
            if (!task.isSuccessful) throw task.exception ?: IllegalStateException()
            check(currentUser(context)?.uid == user.uid)
            context.getSharedPreferences("customer_profile_${user.uid}", Context.MODE_PRIVATE).edit()
                .putString("name", name).putString("phone", phone).apply()
            LaunchSession.remember(context, LaunchRole.CUSTOMER, user.uid)
            null
        }
    }

    fun signIn(context: Context, email: String, password: String): Task<FirebaseUser> = try {
        auth(context).signInWithEmailAndPassword(email.trim(), password).continueWith { task ->
            if (!task.isSuccessful) throw task.exception ?: IllegalStateException()
            val user = task.result.user ?: throw IllegalStateException()
            LaunchSession.remember(context, LaunchRole.CUSTOMER, user.uid)
            user
        }
    } catch (error: Exception) { Tasks.forException(error) }

    fun signUp(context: Context, name: String, phone: String, email: String, password: String): Task<FirebaseUser> = try {
        auth(context).createUserWithEmailAndPassword(email.trim(), password).continueWithTask { task ->
            if (!task.isSuccessful) throw task.exception ?: IllegalStateException()
            val user = task.result.user ?: throw IllegalStateException()
            // The phone is a profile field, not a verified Firebase phone-login credential.
            context.getSharedPreferences("customer_profile_${user.uid}", Context.MODE_PRIVATE)
                .edit().putString("name", name).putString("phone", phone).apply()
            user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(name).build())
                .continueWith {
                    LaunchSession.remember(context, LaunchRole.CUSTOMER, user.uid)
                    user // Account creation succeeded even if profile sync must be retried later.
                }
        }
    } catch (error: Exception) { Tasks.forException(error) }

    @JvmStatic fun resetPassword(context: Context, email: String): Task<Void> = try {
        auth(context).sendPasswordResetEmail(email.trim())
    } catch (error: Exception) { Tasks.forException(error) }

    @JvmStatic fun displayName(context: Context): String {
        val user = currentUser(context) ?: return "Customer"
        return user.displayName?.takeIf { it.isNotBlank() }
            ?: context.getSharedPreferences("customer_profile_${user.uid}", Context.MODE_PRIVATE)
                .getString("name", null)?.takeIf { it.isNotBlank() }
            ?: "Customer"
    }

    // Shorten only greetings; retain the registered full name for profile screens.
    @JvmStatic fun firstName(context: Context): String =
        displayName(context).trim().takeWhile { !it.isWhitespace() }

    internal fun failure(error: Exception?): CustomerAuthFailure {
        val causes = generateSequence<Throwable>(error) { it.cause }.take(8).toList()
        val code = when {
            causes.any { it is SetupException } -> "SETUP_MISSING"
            causes.any { it is FirebaseNetworkException } -> "NETWORK"
            causes.any { it is FirebaseTooManyRequestsException } -> "TOO_MANY_REQUESTS"
            else -> causes.filterIsInstance<FirebaseAuthException>().firstOrNull()?.errorCode
        }
        return CustomerAuthFailureMessages.describe(code, causes.joinToString("\n") { it.message.orEmpty() })
    }

    @JvmStatic fun message(error: Exception?): String = failure(error).message
}
