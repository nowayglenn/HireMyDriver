package com.example.hiremydriver

import android.graphics.Bitmap
import android.widget.ImageView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.util.WeakHashMap

/** Small display photos only; never grants access to private originals or driver documents. */
object PeerProfilePhoto {
    private val names = mutableMapOf<String, String>()
    private val publishedNames = mutableSetOf<String>()
    private val photoSync = java.util.concurrent.Executors.newSingleThreadExecutor()
    private val syncing = mutableSetOf<String>()

    @JvmStatic fun syncCustomerPhoto(context: android.content.Context) {
        val uid = CustomerAuth.currentUser(context)?.uid ?: return
        if (!syncing.add(uid)) return
        val app = context.applicationContext
        photoSync.execute {
            val bitmap = runCatching { ProfilePhotoStorage.cached(app, uid) ?: ProfilePhotoStorage.load(app, uid) }.getOrNull()
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                syncing.remove(uid)
                if (CustomerAuth.currentUser(app)?.uid == uid && bitmap != null) publishCustomer(uid, bitmap)
            }
        }
    }

    @JvmStatic fun publishCustomerName(context: android.content.Context) {
        val uid = CustomerAuth.currentUser(context)?.uid ?: return
        if (uid in publishedNames) return
        val db = FirebaseFirestore.getInstance()
        db.collection("users").document(uid).get().addOnSuccessListener { profile ->
            if (CustomerAuth.currentUser(context)?.uid != uid) return@addOnSuccessListener
            val name = profile.getString("fullName")?.takeIf { it.isNotBlank() } ?: return@addOnSuccessListener
            db.collection("users").document(uid).collection("public").document("profile")
                .set(mapOf("name" to name), com.google.firebase.firestore.SetOptions.merge())
                .addOnSuccessListener { publishedNames.add(uid) }
        }
    }

    private val thumbnails = mutableMapOf<String, String>()
    private val bindings = WeakHashMap<ImageView, String>()
    private val cleanups = WeakHashMap<ImageView, () -> Unit>()
    private val generations = WeakHashMap<ImageView, Any>()
    private val published = mutableMapOf<String, String>()
    private val publishing = mutableSetOf<String>()
    private val queued = mutableMapOf<String, String>()

    fun publishCustomer(uid: String, bitmap: Bitmap) {
        publishThumbnail(uid, RegisteredDriverDirectory.thumbnail(bitmap))
    }

    private fun publishThumbnail(uid: String, thumbnail: String) {
        if (uid != com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid) return
        if (uid in publishing) { queued[uid] = thumbnail; return }
        if (published[uid] == thumbnail) return
        publishing.add(uid)
        FirebaseFirestore.getInstance().collection("users").document(uid).collection("public").document("profile")
            .set(mapOf("thumbnail" to thumbnail), com.google.firebase.firestore.SetOptions.merge()).addOnCompleteListener {
                publishing.remove(uid)
                if (it.isSuccessful) published[uid] = thumbnail
                queued.remove(uid)?.let { latest ->
                    if (latest != thumbnail) publishThumbnail(uid, latest)
                }
            }
    }

    fun bind(view: ImageView, uid: String, peerIsDriver: Boolean, driverSession: Boolean = false,
        nameView: android.widget.TextView? = null, initialThumbnail: String? = null) {
        val key = "${if (peerIsDriver) "driver" else "customer"}:$uid"
        names[key]?.let { nameView?.text = it }
        if (bindings[view] == key) return
        cleanups.remove(view)?.invoke()
        bindings.remove(view)
        if (!thumbnails.containsKey(key) && !initialThumbnail.isNullOrBlank()) thumbnails[key] = initialThumbnail
        val disk = view.context.getSharedPreferences("public_avatar_cache", android.content.Context.MODE_PRIVATE)
        if (!thumbnails.containsKey(key)) disk.getString(key, null)?.let { thumbnails[key] = it }

        RegisteredDriverDirectory.image(view, thumbnails[key].orEmpty())
        if (uid.isBlank()) return
        val owner = ScreenOwner.from(view.context) ?: return
        bindings[view] = key
        val generation = Any()
        generations[view] = generation
        val db = if (driverSession) FirebaseFirestore.getInstance(DriverAuth.firebaseApp(view.context)) else FirebaseFirestore.getInstance()
        val ref = if (peerIsDriver) db.collection("driverProfiles").document(uid)
            else db.collection("users").document(uid).collection("public").document("profile")

        val observer = object : DefaultLifecycleObserver {
            var listener: ListenerRegistration? = null
            var displayed = thumbnails[key]
            override fun onStart(owner: LifecycleOwner) {
                if (listener != null) return
                listener = ref.addSnapshotListener(com.google.firebase.firestore.MetadataChanges.INCLUDE) { data, error ->
                    if (error != null || data == null || bindings[view] != key || generations[view] !== generation) return@addSnapshotListener
                    if (!data.exists() && data.metadata.isFromCache) return@addSnapshotListener
                    var thumbnail = data.getString("thumbnail").orEmpty()
                    var name = data.getString(if (peerIsDriver) "fullName" else "name")?.takeIf { it.isNotBlank() }

                    if (!peerIsDriver && (thumbnail.isBlank() || name == null)) {
                        db.collection("users").document(uid).get().addOnSuccessListener { userDoc ->
                            if (bindings[view] != key || generations[view] !== generation) return@addOnSuccessListener
                            if (thumbnail.isBlank()) thumbnail = userDoc.getString("thumbnail").orEmpty()
                            if (name == null) name = userDoc.getString("fullName")?.takeIf { it.isNotBlank() }

                            name?.let {
                                names[key] = it
                                nameView?.text = it
                            }
                            if (thumbnail.isNotBlank()) {
                                thumbnails[key] = thumbnail
                                disk.edit().putString(key, thumbnail).apply()
                                if (displayed != thumbnail) {
                                    displayed = thumbnail
                                    RegisteredDriverDirectory.image(view, thumbnail)
                                }
                            }
                        }
                    }

                    name?.let {
                        names[key] = it
                        nameView?.text = it
                    }
                    if (thumbnail.isNotBlank()) {
                        thumbnails[key] = thumbnail
                        disk.edit().putString(key, thumbnail).apply()
                        if (displayed != thumbnail) {
                            displayed = thumbnail
                            RegisteredDriverDirectory.image(view, thumbnail)
                        }
                    }
                }
            }
            override fun onStop(owner: LifecycleOwner) { listener?.remove(); listener = null }
            override fun onDestroy(owner: LifecycleOwner) { cleanups.remove(view)?.invoke() }
        }
        val attachment = object : android.view.View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: android.view.View) = Unit
            override fun onViewDetachedFromWindow(v: android.view.View) {
                cleanups.remove(view)?.invoke()
            }
        }
        cleanups[view] = {
            observer.onStop(owner); owner.lifecycle.removeObserver(observer)
            bindings.remove(view); generations.remove(view); view.removeOnAttachStateChangeListener(attachment)
        }
        view.addOnAttachStateChangeListener(attachment)
        owner.lifecycle.addObserver(observer)
    }
}
