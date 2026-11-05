package com.example.hiremydriver

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.json.JSONObject

/** Private Storage requests use Firebase ID tokens; the publishable key grants no photo access. */
internal object ProfilePhotoStorage {
    private const val PROJECT_URL = "https://gemtzjexvdgblpwbvdvh.supabase.co"
    private const val PUBLISHABLE_KEY = "sb_publishable_tXfVSYDHUZ7vKq0eowyg2A_Vr_TnZES"
    private const val MAX_BYTES = 2 * 1024 * 1024
    private val worker = Executors.newSingleThreadExecutor()

    fun reference(uid: String): Uri = Uri.Builder().scheme("profile-photo").authority("account").appendPath(uid).build()
    fun isReference(uri: Uri) = uri.scheme == "profile-photo" && uri.authority == "account"

    fun save(context: Context, uid: String, uri: Uri?): Task<Unit> = Tasks.call(worker) {
        requireSession(context, uid)
        if (uri == null) return@call Unit // Editing text fields does not delete the saved photo.
        if (isReference(uri)) {
            if (uri != reference(uid)) throw PhotoStorageFailure("Please sign in again before saving your photo.")
            return@call Unit
        }
        require(uri.scheme == "content" || uri.scheme == "file")
        val bitmap = CustomerProfilePhoto.decode(context, uri)
            ?: throw PhotoStorageFailure("Could not open this photo. Please choose another image.")
        val bytes = try {
            ByteArrayOutputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output))
                output.toByteArray()
            }
        } finally { bitmap.recycle() }
        if (bytes.size > MAX_BYTES) throw PhotoStorageFailure("Please choose a photo smaller than 2 MB.")
        request(context, uid, bytes)
        requireSession(context, uid)
        CustomerProfilePhoto.rememberUpload(uid, bytes)
        // A failed cache write must not turn an already completed cloud upload into a false failure.
        runCatching { writeCache(context, uid, bytes) }
        Unit
    }

    /** Null means this account has not uploaded a photo yet. Network failures use its own cache. */
    fun cached(context: Context, uid: String): Bitmap? {
        requireSession(context, uid)
        val file = cacheFile(context, uid)
        return if (file.isFile) runCatching { CustomerProfilePhoto.decode(context, Uri.fromFile(file)) }.getOrNull() else null
    }

    fun load(context: Context, uid: String): Bitmap? {
        requireSession(context, uid)
        val disk = cacheFile(context, uid)
        if (disk.isFile && System.currentTimeMillis() - disk.lastModified() < 5 * 60 * 1000L) {
            runCatching { CustomerProfilePhoto.decode(context, Uri.fromFile(disk)) }.getOrNull()?.let { return it }
        }
        val bytes = try {
            request(context, uid, null)?.also { data -> runCatching { writeCache(context, uid, data) } }
        } catch (error: Exception) {
            requireSession(context, uid)
            val cached = cacheFile(context, uid)
            if (cached.isFile) cached.readBytes() else throw error
        }
        requireSession(context, uid)
        if (bytes == null) {
            cacheFile(context, uid).delete()
            return null
        }
        // Reuse bounded decoding and EXIF support even for photos uploaded outside this app.
        val file = File.createTempFile("profile-decode-", ".jpg", context.cacheDir)
        return try {
            file.writeBytes(bytes)
            CustomerProfilePhoto.decode(context, Uri.fromFile(file)) ?: throw IOException("Unreadable profile photo")
        } finally { file.delete() }
    }

    private fun request(context: Context, uid: String, upload: ByteArray?): ByteArray? {
        val user = requireSession(context, uid)
        val path = ProfilePhotoContract.objectPath(uid)
        val route = if (upload == null) "object/authenticated" else "object"
        repeat(2) { attempt ->
            val token = Tasks.await(user.getIdToken(attempt > 0), 20, TimeUnit.SECONDS).token
                ?: throw PhotoStorageFailure("Please sign in again before saving your photo.")
            requireSession(context, uid)
            val url = java.net.URL("$PROJECT_URL/storage/v1/$route/profile-photos/$path")
            val connection = (url.openConnection() as javax.net.ssl.HttpsURLConnection).apply {
                requestMethod = if (upload == null) "GET" else "POST"
                connectTimeout = 15000
                readTimeout = 20000
                instanceFollowRedirects = false
                useCaches = false
                setRequestProperty("apikey", PUBLISHABLE_KEY)
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Cache-Control", "no-cache")
                if (upload != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "image/jpeg")
                    setRequestProperty("x-upsert", "true")
                    setRequestProperty("Cache-Control", "max-age=0")
                    setFixedLengthStreamingMode(upload.size)
                }
            }
            try {
                if (upload != null) connection.outputStream.use { it.write(upload) }
                val status = connection.responseCode
                if (status in 200..299) {
                    requireSession(context, uid)
                    return if (upload == null) connection.inputStream.use { input ->
                        val result = ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            if (result.size() + read > MAX_BYTES) throw IOException("Photo exceeds size limit")
                            result.write(buffer, 0, read)
                        }
                        result.toByteArray()
                    } else ByteArray(0)
                }
                val body = connection.errorStream?.use { input ->
                    val buffer = ByteArray(8192)
                    val count = input.read(buffer)
                    if (count > 0) String(buffer, 0, count, Charsets.UTF_8) else ""
                }.orEmpty()
                val json = runCatching { JSONObject(body) }.getOrNull()
                val code = json?.optString("code").orEmpty()
                val message = json?.optString("message").orEmpty()
                if (upload == null && ProfilePhotoContract.isMissingPhoto(status, code, message)) return null
                if (status == 401 && attempt == 0) return@repeat
                throw PhotoStorageFailure(when {
                    status == 401 -> "Photo access could not be verified. Please sign in again."
                    status == 403 || message.contains("row-level security", true) ->
                        "Photo storage access is not ready. Please finish the Supabase access rules."
                    code == "NoSuchBucket" || message.contains("bucket not found", true) ->
                        "The profile-photos storage bucket is not ready yet."
                    status == 413 -> "Please choose a photo smaller than 2 MB."
                    else -> "Could not save or load your photo. Please check your connection and try again."
                })
            } finally { connection.disconnect() }
        }
        throw IOException("Photo request failed")
    }

    private fun requireSession(context: Context, uid: String) = CustomerAuth.currentUser(context)
        ?.takeIf { it.uid == uid && !it.isAnonymous }
        ?: throw PhotoStorageFailure("Your account changed. Please sign in again.")

    private fun cacheFile(context: Context, uid: String): File {
        ProfilePhotoContract.objectPath(uid)
        return File(File(context.filesDir, "customer-photo-cache"), "$uid.jpg")
    }

    private fun writeCache(context: Context, uid: String, bytes: ByteArray) {
        val file = cacheFile(context, uid)
        check(file.parentFile!!.isDirectory || file.parentFile!!.mkdirs())
        val atomic = android.util.AtomicFile(file)
        val stream = atomic.startWrite()
        try { stream.write(bytes); atomic.finishWrite(stream) }
        catch (error: Exception) { atomic.failWrite(stream); throw error }
    }
}

internal class PhotoStorageFailure(message: String) : IOException(message)
