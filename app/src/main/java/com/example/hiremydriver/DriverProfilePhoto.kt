package com.example.hiremydriver

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.AtomicFile
import android.widget.ImageView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.Executors

/** Driver account photo, isolated from the customer's Firebase session and other drivers. */
object DriverProfilePhoto {
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var cached: Bitmap? = null
    private var loaded = false
    private var revision = 0L
    private var cachedUid: String? = null
    private var checkedAt = 0L
    private val published = mutableMapOf<String, String>()
    private val publishing = mutableSetOf<String>()
    private fun publish(context: Context, uid: String?, photo: Bitmap?) {
        if (uid == null || photo == null || DriverAuth.currentUser(context)?.uid != uid) return
        val thumbnail = RegisteredDriverDirectory.thumbnail(photo)
        if (published[uid] == thumbnail || !publishing.add(uid)) return
        com.google.firebase.firestore.FirebaseFirestore.getInstance(DriverAuth.firebaseApp(context))
            .collection(RegisteredDriverDirectory.COLLECTION).document(uid).update("thumbnail", thumbnail)
            .addOnCompleteListener { publishing.remove(uid); if (it.isSuccessful) published[uid] = thumbnail }
    }

    fun show(view: ImageView) {
        val uid = DriverAuth.currentUser(view.context)?.uid
        if (cachedUid != uid) { cachedUid = uid; cached = null; loaded = false; checkedAt = 0L; revision++; view.setImageResource(R.drawable.no_profile) }
        val request = Any()
        val previous = view.getTag(R.id.imgDriverProfilePhoto)
        view.setTag(R.id.imgDriverProfilePhoto, request)
        if (loaded) {
            publish(view.context, uid, cached)
            cached?.let(view::setImageBitmap) ?: view.setImageResource(R.drawable.no_profile)
            if (System.currentTimeMillis() - checkedAt < 5 * 60 * 1000L) return
        }
        if (previous == null && !loaded) view.setImageResource(R.drawable.no_profile)
        val snapshot = revision
        val context = view.context.applicationContext
        worker.execute {
            val result = runCatching {
                check(DriverAuth.currentUser(context)?.uid == uid)
                val disk = readStored(context)
                if (disk != null) main.post {
                    if (snapshot == revision && DriverAuth.currentUser(context)?.uid == uid && view.getTag(R.id.imgDriverProfilePhoto) === request) view.setImageBitmap(disk)
                }
                if (disk != null && System.currentTimeMillis() - photoFile(context, uid).lastModified() < 5 * 60 * 1000L) disk
                else if (uid != null) (runCatching { DriverCloudStorage.profile(context, uid) }.getOrElse { disk ?: throw it } ?: disk)?.also { photo ->
                    val file = AtomicFile(photoFile(context, uid))
                    val stream = file.startWrite()
                    try {
                        check(photo.compress(Bitmap.CompressFormat.JPEG, 92, stream))
                        file.finishWrite(stream)
                    } catch (error: Exception) { file.failWrite(stream); throw error }
                } else null
            }
            main.post {
                if (snapshot == revision && DriverAuth.currentUser(context)?.uid == uid && result.isSuccess) {
                    cached = result.getOrNull()
                    loaded = true
                    checkedAt = System.currentTimeMillis()
                    publish(context, uid, cached)
                }
                // A newer request or completed save must not be replaced by an older read.
                if (view.getTag(R.id.imgDriverProfilePhoto) === request && loaded && DriverAuth.currentUser(context)?.uid == uid) {
                    cached?.let(view::setImageBitmap) ?: view.setImageResource(R.drawable.no_profile)
                }
            }
        }
    }

    internal fun save(context: Context, uri: Uri, onComplete: (Boolean) -> Unit) {
        val application = context.applicationContext
        val uid = DriverAuth.currentUser(application)?.uid
        worker.execute {
            val result = runCatching {
                if (uid != null) DriverCloudStorage.upload(application, uid, "$uid/profile.jpg",
                    DriverCloudStorage.encode(application, uri), profile = true)
                if (DriverAuth.currentUser(application)?.uid != uid) throw IOException("Driver session changed")
                saveToFile(application, uri, AtomicFile(photoFile(application, uid)))
            }
            main.post {
                if (DriverAuth.currentUser(application)?.uid != uid) { onComplete(false); return@post }
                result.getOrNull()?.let {
                    cachedUid = uid
                    cached = it
                    loaded = true
                    checkedAt = System.currentTimeMillis()
                    revision++
                    if (uid != null) com.google.firebase.firestore.FirebaseFirestore.getInstance(DriverAuth.firebaseApp(application))
                        .collection(RegisteredDriverDirectory.COLLECTION).document(uid)
                        .update("thumbnail", RegisteredDriverDirectory.thumbnail(it))
                }
                onComplete(result.isSuccess)
            }
        }
    }

    private fun photoFile(context: Context, uid: String? = DriverAuth.currentUser(context)?.uid) = File(context.filesDir,
        uid?.let { "driver-profile-$it.jpg" } ?: "driver-profile-photo.jpg")

    internal fun readStored(context: Context): Bitmap? {
        val input = try {
            AtomicFile(photoFile(context)).openRead()
        } catch (_: FileNotFoundException) {
            return null
        }
        return input.use { BitmapFactory.decodeStream(it) ?: throw IOException("Unreadable driver photo") }
    }

    /** Decode before opening the destination, then atomically replace the last confirmed photo. */
    internal fun saveToFile(
        context: Context,
        uri: Uri,
        destination: AtomicFile = AtomicFile(photoFile(context))
    ): Bitmap {
        val bitmap = CustomerProfilePhoto.decode(context, uri) ?: throw IOException("Unreadable image")
        try {
            val bytes = ByteArrayOutputStream().use {
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it)) throw IOException("Unable to encode photo")
                it.toByteArray()
            }
            var output: FileOutputStream? = null
            try {
                output = destination.startWrite()
                output.write(bytes)
                output.fd.sync()
                destination.finishWrite(output)
                output = null
                // AtomicFile can report a failed rename only in its log. Verify before showing success.
                if (!destination.readFully().contentEquals(bytes)) throw IOException("Unable to save photo")
            } catch (error: Exception) {
                if (output != null) destination.failWrite(output)
                throw error
            }
            return bitmap
        } catch (error: Exception) {
            bitmap.recycle()
            throw error
        }
    }
}

data class DriverPhotoState(
    val busy: Boolean = false,
    val saved: Boolean = false,
    val error: Boolean = false
)

/** A confirmed crop continues saving when the profile activity is recreated. */
class DriverProfilePhotoModel(application: Application) : AndroidViewModel(application) {
    private val mutableState = MutableLiveData(DriverPhotoState())
    val state: LiveData<DriverPhotoState> = mutableState

    fun save(uri: Uri) {
        if (mutableState.value?.busy == true) return
        mutableState.value = DriverPhotoState(busy = true)
        DriverProfilePhoto.save(getApplication(), uri) { saved ->
            mutableState.value = DriverPhotoState(saved = saved, error = !saved)
        }
    }

    fun clearResult() {
        mutableState.value = mutableState.value?.copy(saved = false, error = false)
    }
}
