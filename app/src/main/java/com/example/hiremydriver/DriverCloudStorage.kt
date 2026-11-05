package com.example.hiremydriver

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.google.android.gms.tasks.Tasks
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.URL
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection
import org.json.JSONObject

/** Private objects, authenticated with the separate driver's Firebase session. */
internal object DriverCloudStorage {
    private const val URL_ROOT = "https://gemtzjexvdgblpwbvdvh.supabase.co/storage/v1"
    private const val KEY = "sb_publishable_tXfVSYDHUZ7vKq0eowyg2A_Vr_TnZES"
    private const val MAX_BYTES = 5 * 1024 * 1024
    fun reference(path: String): String = Uri.Builder().scheme("driver-document").authority("account")
        .apply { path.split('/').forEach(::appendPath) }.build().toString()
    fun isReference(value: Uri) = value.scheme == "driver-document" && value.authority == "account"

    fun encode(context: Context, uri: Uri): ByteArray {
        val bitmap = DocumentPhotoDecoder.decode(context, uri, 2400)
            ?: throw DriverSetupFailure("Could not read a document photo. Please select it again.")
        return try {
            ByteArrayOutputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output))
                output.toByteArray().also {
                    if (it.size > MAX_BYTES) throw DriverSetupFailure("Please choose a document photo smaller than 5 MB.")
                }
            }
        } finally { bitmap.recycle() }
    }

    fun upload(context: Context, uid: String, path: String, bytes: ByteArray, profile: Boolean = false) {
        request(context, uid, path, bytes, profile)
    }
    fun load(context: Context, reference: Uri): Bitmap? {
        require(isReference(reference))
        val parts = reference.pathSegments
        require(parts.size == 3)
        val path = DriverRegistrationContract.objectPath(parts[0], parts[1], parts[2])
        val bytes = request(context, parts[0], path, null, false) ?: return null
        return decodeBytes(context, bytes)
    }
    fun profile(context: Context, uid: String): Bitmap? =
        request(context, uid, "$uid/profile.jpg", null, true)?.let { decodeBytes(context, it) }

    private fun decodeBytes(context: Context, bytes: ByteArray): Bitmap? {
        val temporary = File.createTempFile("driver-document-", ".jpg", context.cacheDir)
        return try { temporary.writeBytes(bytes); DocumentPhotoDecoder.decode(context, Uri.fromFile(temporary), 2400) }
        finally { temporary.delete() }
    }
    private fun requireUser(context: Context, uid: String) = DriverAuth.currentUser(context)
        ?.takeIf { it.uid == uid && !it.isAnonymous }
        ?: throw DriverSetupFailure("Your driver session changed. Please sign in again.")

    private fun request(context: Context, uid: String, path: String, upload: ByteArray?, profile: Boolean): ByteArray? {
        if (profile) require(path == "$uid/profile.jpg" && Regex("[A-Za-z0-9_-]{1,128}").matches(uid))
        else {
            val parts = path.split('/')
            require(parts.size == 3 && parts[0] == uid)
            DriverRegistrationContract.objectPath(uid, parts[1], parts[2])
        }
        val limit = if (profile) 2 * 1024 * 1024 else MAX_BYTES
        if (upload != null && upload.size > limit) throw DriverSetupFailure("The photo is too large to upload.")
        repeat(2) { attempt ->
            val user = requireUser(context, uid)
            val token = Tasks.await(user.getIdToken(attempt > 0), 30, TimeUnit.SECONDS).token
                ?: throw DriverSetupFailure("Please sign in again.")
            requireUser(context, uid)
            val bucket = if (profile) "profile-photos" else "driver-documents"
            val route = if (upload == null) "object/authenticated" else "object"
            val connection = (URL("$URL_ROOT/$route/$bucket/$path").openConnection() as HttpsURLConnection).apply {
                requestMethod = if (upload == null) "GET" else "POST"
                connectTimeout = 15000; readTimeout = 30000; instanceFollowRedirects = false; useCaches = false
                setRequestProperty("apikey", KEY)
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Cache-Control", "no-cache")
                if (upload != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "image/jpeg")
                    setRequestProperty("x-upsert", if (profile) "true" else "false")
                    setFixedLengthStreamingMode(upload.size)
                }
            }
            try {
                if (upload != null) connection.outputStream.use { it.write(upload) }
                val status = connection.responseCode
                if (status in 200..299) {
                    val bytes = if (upload == null) connection.inputStream.use { input ->
                        val output = ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            if (output.size() + count > limit) throw IOException("Photo exceeds size limit")
                            output.write(buffer, 0, count)
                        }
                        output.toByteArray()
                    } else ByteArray(0)
                    requireUser(context, uid)
                    return bytes
                }
                val body = connection.errorStream?.use { input ->
                    val buffer = ByteArray(8192)
                    val count = input.read(buffer)
                    if (count > 0) String(buffer, 0, count, Charsets.UTF_8) else ""
                }.orEmpty()
                val json = runCatching { JSONObject(body) }.getOrNull()
                val code = json?.optString("code").orEmpty()
                val message = json?.optString("message").orEmpty()
                if (status == 401 && attempt == 0) return@repeat
                if (upload == null && ProfilePhotoContract.isMissingPhoto(status, code, message)) return null
                // A timed-out successful upload can be safely retried without replacing any object.
                if (upload != null && !profile && (status == 409 || code == "Duplicate")) {
                    val existing = request(context, uid, path, null, false)
                    if (existing != null && existing.contentEquals(upload)) return ByteArray(0)
                }
                throw DriverSetupFailure(when {
                    status == 403 || message.contains("row-level security", true) ->
                        "Document access was denied. Check the Supabase Firebase provider and driver-document rules."
                    status == 401 -> "Document access could not be verified. Please sign in again."
                    code == "NoSuchBucket" -> "The $bucket bucket is not ready yet."
                    else -> "Could not upload or load the document. Check your connection and retry."
                })
            } finally { connection.disconnect() }
        }
        throw IOException("Document request failed")
    }
}
