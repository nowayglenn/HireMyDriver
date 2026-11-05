package com.example.hiremydriver

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.LruCache
import android.widget.ImageView
import java.util.concurrent.Executors

/** Cloud photo per Firebase account, with local draft previews and offline fallback. */
object CustomerProfilePhoto {
    private const val PREFERENCES = "customer_profile_photo"
    private val decoder = Executors.newSingleThreadExecutor()
    private data class PhotoKey(val uid: String?, val uri: String)
    private data class PhotoRequest(val key: PhotoKey)
    // Retain decoded photos across tab activity recreation. Keys keep accounts and unsaved drafts separate.
    private val photos = object : LruCache<PhotoKey, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: PhotoKey, value: Bitmap) = value.byteCount
    }
    private val revisions = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val checkedAt = java.util.concurrent.ConcurrentHashMap<PhotoKey, Long>()
    private val missing = java.util.concurrent.ConcurrentHashMap.newKeySet<PhotoKey>()
    private const val REFRESH_MS = 5 * 60 * 1000L

    internal fun rememberUpload(uid: String, bytes: ByteArray) {
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return
        synchronized(photos) {
            photos.put(PhotoKey(uid, ProfilePhotoStorage.reference(uid).toString()), bitmap)
            revisions[uid] = (revisions[uid] ?: 0L) + 1L
            checkedAt[PhotoKey(uid, ProfilePhotoStorage.reference(uid).toString())] = System.currentTimeMillis()
            missing.remove(PhotoKey(uid, ProfilePhotoStorage.reference(uid).toString()))
        }
        android.os.Handler(android.os.Looper.getMainLooper()).post { PeerProfilePhoto.publishCustomer(uid, bitmap) }
    }

    private fun preferenceName(context: Context) = CustomerAuth.currentUser(context)?.uid?.let { "${PREFERENCES}_$it" } ?: PREFERENCES

    private fun legacyUri(context: Context): Uri? = context.getSharedPreferences(preferenceName(context), Context.MODE_PRIVATE)
        .getString("uri", null)?.let(Uri::parse)

    fun savedUri(context: Context): Uri? = CustomerAuth.currentUser(context)?.uid?.let(ProfilePhotoStorage::reference)

    fun save(context: Context, uid: String, uri: Uri?) = ProfilePhotoStorage.save(context.applicationContext, uid, uri)

    fun show(view: ImageView, uri: Uri?, onReady: (Boolean) -> Unit = {}) {
        val context = view.context.applicationContext
        val uid = CustomerAuth.currentUser(context)?.uid
        val key = PhotoKey(uid, uri?.toString().orEmpty())
        val previous = view.getTag(R.id.imgProfilePhoto) as? PhotoRequest
        val request = PhotoRequest(key)
        view.setTag(R.id.imgProfilePhoto, request)
        if (uri == null) {
            view.setImageResource(R.drawable.no_profile)
            onReady(true)
            return
        }
        val cached = photos.get(key)
        if (cached != null) view.setImageBitmap(cached)
        else if (previous?.key != key) view.setImageResource(R.drawable.no_profile)
        // A cached preview is already usable. Do not disable Save while refreshing it.
        if (cached != null) {
            if (uid != null && ProfilePhotoStorage.isReference(uri)) PeerProfilePhoto.publishCustomer(uid, cached)
            onReady(true)
            if (!ProfilePhotoStorage.isReference(uri) || System.currentTimeMillis() - (checkedAt[key] ?: 0L) < REFRESH_MS) return
        }
        if (cached == null && key in missing && System.currentTimeMillis() - (checkedAt[key] ?: 0L) < REFRESH_MS) { onReady(true); return }
        val revision = uid?.let { revisions[it] } ?: 0L
        val legacy = legacyUri(context)
        decoder.execute {
            if (cached == null && uid != null && ProfilePhotoStorage.isReference(uri)) {
                runCatching { ProfilePhotoStorage.cached(context, uid) }.getOrNull()?.let { disk ->
                    view.post {
                        if (CustomerAuth.currentUser(context)?.uid == uid && (revisions[uid] ?: 0L) == revision) {
                            photos.put(key, disk)
                            if (view.getTag(R.id.imgProfilePhoto) === request) { view.setImageBitmap(disk); onReady(true) }
                        }
                    }
                }
            }
            val result = runCatching {
                if ((photos.get(key) != null || key in missing) && System.currentTimeMillis() - (checkedAt[key] ?: 0L) < REFRESH_MS) photos.get(key)
                else if (ProfilePhotoStorage.isReference(uri)) {
                    check(uid != null && uri == ProfilePhotoStorage.reference(uid))
                    ProfilePhotoStorage.load(context, uid)
                        ?: legacy?.let { runCatching { decode(context, it) }.getOrNull() }
                } else decode(context, uri) ?: error("Unreadable image")
            }
            view.post {
                val sameAccount = CustomerAuth.currentUser(context)?.uid == uid
                val newerUpload = ProfilePhotoStorage.isReference(uri) &&
                    (uid?.let { revisions[it] } ?: 0L) != revision
                var bitmap = result.getOrNull()
                if (sameAccount) synchronized(photos) {
                    if (newerUpload) bitmap = photos.get(key)
                    else if (result.isSuccess) {
                        checkedAt[key] = System.currentTimeMillis()
                        if (bitmap != null) photos.put(key, bitmap!!)
                        else bitmap = photos.get(key)
                        if (bitmap == null) missing.add(key) else missing.remove(key)
                    }
                }
                if (view.getTag(R.id.imgProfilePhoto) === request) {
                    if (sameAccount) {
                        if (bitmap != null) {
                            view.setImageBitmap(bitmap)
                            if (uid != null && ProfilePhotoStorage.isReference(uri)) PeerProfilePhoto.publishCustomer(uid, bitmap!!)
                        }
                        else if (result.isSuccess && !newerUpload) view.setImageResource(R.drawable.no_profile)
                        // On refresh failure, leave the existing photo visible.
                        if (cached == null) onReady(result.isSuccess || newerUpload)
                    } else {
                        view.setImageResource(R.drawable.no_profile)
                        if (cached == null) onReady(false)
                    }
                }
            }
        }
    }

    internal fun decode(context: Context, uri: Uri): Bitmap? {
        val resolver = context.contentResolver
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        if (options.outWidth <= 0 || options.outHeight <= 0) return null
        options.inJustDecodeBounds = false
        options.inSampleSize = 1
        while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize > 1024) options.inSampleSize *= 2
        val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: return null
        val orientation = runCatching {
            resolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }
        }.getOrNull()
        val transform = Matrix().apply {
            when (orientation) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(-90f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(-90f)
            }
        }
        return if (transform.isIdentity) bitmap else Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, transform, true)
    }
}
