package com.example.hiremydriver

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.io.ByteArrayOutputStream

/** Public display fields only. Registration documents, email and phone stay private; the registered address is displayed on the profile. */
data class RegisteredDriver(val uid: String, val name: String, val experience: String,
    val bio: String, val status: String, val thumbnail: String,
    val price: String = "Rate not set", val vehicle: String = "", val area: String = "",
    val searchData: Map<String, Any?> = emptyMap()) {
    fun matchesSearch(query: String, now: Long): Boolean = ServiceAreaSearch.matches(searchData, query, now)
    val statusLabel: String get() = if (status == "approved") "Verified" else "Pending verification"
    fun intent(context: Context) = Intent(context, DriverDetailsActivity::class.java).apply {
        putExtra("DRIVER_UID", uid)
        putExtra("DRIVER_NAME", name)
        putExtra("DRIVER_EXP", "$experience years experience")
        putExtra("DRIVER_BIO", bio)
        putExtra("DRIVER_STATUS", status)
        putExtra("DRIVER_THUMBNAIL", thumbnail)
        putExtra("DRIVER_PRICE", price)
        putExtra("DRIVER_VEHICLE", vehicle)
        putExtra("DRIVER_LOCATION", area)
    }
}

object RegisteredDriverDirectory {
    private var cachedUid: String? = null
    private var cachedRows: List<RegisteredDriver>? = null
    fun clearCache() { cachedUid = null; cachedRows = null }
    private val decodedPhotos = object : android.util.LruCache<String, Bitmap>(4 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    const val COLLECTION = "driverProfiles"
    private val addressSyncing = mutableSetOf<String>()
    /** Repair older public profiles from the authenticated owner's saved registration. */
    fun syncRegisteredAddress(context: Context, uid: String) {
        if (DriverAuth.currentUser(context)?.uid != uid || !addressSyncing.add(uid)) return
        val db = FirebaseFirestore.getInstance(DriverAuth.firebaseApp(context))
        val profile = db.collection(COLLECTION).document(uid)
        db.runTransaction { tx ->
            val registration = tx.get(db.collection("driverApplications").document(uid))
            val current = tx.get(profile)
            check(DriverAuth.currentUser(context)?.uid == uid)
            val address = registration.getString("address")
            if (current.exists() && !address.isNullOrBlank() && current.getString("address") != address)
                tx.update(profile, "address", address)
        }.addOnCompleteListener { addressSyncing.remove(uid) }
    }
    fun displayFields(application: Map<String, Any>, thumbnail: String? = null): Map<String, Any> {
        val data = mutableMapOf<String, Any>(
            "fullName" to (application["fullName"] as? String).orEmpty(),
            "experience" to (application["experience"] as? String).orEmpty(),
            "bio" to (application["bio"] as? String).orEmpty(),
            "address" to (application["address"] as? String).orEmpty(),
            "status" to (application["status"] as? String ?: "pending"))
        thumbnail?.takeIf { it.length <= 60000 }?.let { data["thumbnail"] = it }
        return data
    }
    fun publish(context: Context, uid: String, application: Map<String, Any>, thumbnail: String? = null) {
        val db = FirebaseFirestore.getInstance(DriverAuth.firebaseApp(context))
        // This secondary publication can be retried at login if console rules are not published yet.
        db.collection(COLLECTION).document(uid).set(displayFields(application, thumbnail), com.google.firebase.firestore.SetOptions.merge())
    }
    fun thumbnail(bitmap: Bitmap): String {
        val scale = minOf(1f, 160f / maxOf(bitmap.width, bitmap.height))
        val small = Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1), true)
        return try { ByteArrayOutputStream().use {
            small.compress(Bitmap.CompressFormat.JPEG, 65, it)
            Base64.encodeToString(it.toByteArray(), Base64.NO_WRAP)
        } } finally { if (small !== bitmap) small.recycle() }
    }
    fun image(view: ImageView, thumbnail: String) {
        decodedPhotos.get(thumbnail)?.let { view.setImageBitmap(it); return }
        val bitmap = runCatching {
            if (thumbnail.length !in 1..60000) return@runCatching null
            val bytes = Base64.decode(thumbnail, Base64.DEFAULT)
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true; inSampleSize = 1 }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            options.inJustDecodeBounds = false
            while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize > 320) options.inSampleSize *= 2
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        }.getOrNull()
        if (bitmap == null) view.setImageResource(R.drawable.no_profile) else {
            decodedPhotos.put(thumbnail, bitmap); view.setImageBitmap(bitmap)
        }
    }
    fun bind(card: View, driver: RegisteredDriver) {
        card.findViewById<TextView>(R.id.txtDriverName).text = driver.name
        card.findViewById<TextView>(R.id.txtExperience).text = "${driver.experience} years experience"
        card.findViewById<TextView>(R.id.txtVehicle).text = driver.vehicle.ifBlank { driver.statusLabel }
        card.findViewById<TextView>(R.id.txtRating).text = "No ratings yet"
        com.example.hiremydriver.trip.RatingDisplay.bind(card.findViewById(R.id.txtRating), driver.uid)
        card.findViewById<TextView>(R.id.txtPrice).text = driver.price
        image(card.findViewById(R.id.imgDriver), driver.thumbnail)
        card.setOnClickListener { card.context.startActivity(driver.intent(card.context)) }
    }
    @JvmStatic fun observe(activity: AppCompatActivity, callback: Listener) {
        val uid = CustomerAuth.currentUser(activity)?.uid
        val initial = cachedRows.takeIf { uid != null && cachedUid == uid }
        initial?.let { callback.changed(it, if (it.isEmpty()) "No registered drivers yet." else "") }
        activity.lifecycle.addObserver(object : DefaultLifecycleObserver {
            private var registration: ListenerRegistration? = null
            private var loaded = initial != null
            override fun onStart(owner: LifecycleOwner) {
                if (!loaded) callback.changed(emptyList(), "Loading registered drivers…")
                registration = FirebaseFirestore.getInstance().collection(COLLECTION)
                    .whereEqualTo("status", "approved")
                    .addSnapshotListener(com.google.firebase.firestore.MetadataChanges.INCLUDE) { snapshot, error ->
                        if (CustomerAuth.currentUser(activity)?.uid != uid) return@addSnapshotListener
                        if (snapshot?.isEmpty == true && snapshot.metadata.isFromCache) return@addSnapshotListener
                        if (error != null) {
                            if (!loaded) callback.changed(emptyList(), "Could not load drivers. Check your connection and try again.")
                        } else {
                            loaded = true
                            val rows = readRows(snapshot)
                            cachedUid = uid; cachedRows = rows
                            callback.changed(rows, if (rows.isEmpty()) "No registered drivers yet." else "")
                        }
                    }
            }
            override fun onStop(owner: LifecycleOwner) { registration?.remove(); registration = null }
        })
    }
    private fun readRows(snapshot: com.google.firebase.firestore.QuerySnapshot?): List<RegisteredDriver> =
        snapshot?.documents.orEmpty().mapNotNull { doc ->
            val name = doc.getString("fullName")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val info = PublicDriverInfo.from(doc.data.orEmpty())
            RegisteredDriver(doc.id, name, info.experience, info.bio, info.status,
                info.thumbnail, info.price, info.vehicleLabel, info.areas.joinToString(", "), doc.data.orEmpty())
        }.sortedBy { it.name.lowercase() }

    fun refresh(activity: AppCompatActivity, done: (List<RegisteredDriver>?, String?) -> Unit) {
        val uid = CustomerAuth.currentUser(activity)?.uid ?: run { done(null, "Please sign in again."); return }
        FirebaseFirestore.getInstance().collection(COLLECTION).whereEqualTo("status", "approved")
            .get(com.google.firebase.firestore.Source.SERVER).addOnCompleteListener { task ->
                if (CustomerAuth.currentUser(activity)?.uid != uid) { done(null, "Please sign in again."); return@addOnCompleteListener }
                if (task.isSuccessful) {
                    val rows = readRows(task.result)
                    cachedUid = uid; cachedRows = rows
                    done(rows, if (rows.isEmpty()) "No registered drivers yet." else "")
                } else done(null, "Could not refresh drivers. Check your connection and try again.")
            }
    }
    fun interface Listener { fun changed(drivers: List<RegisteredDriver>, message: String) }
    @JvmStatic fun render(activity: AppCompatActivity, parent: LinearLayout, drivers: List<RegisteredDriver>, message: String) {
        val content = drivers.toList() to message
        if (parent.tag == content) return
        parent.tag = content
        if (drivers.isEmpty()) {
            parent.removeAllViews()
            if (message.startsWith("Loading")) {
                CardSkeleton.show(parent, R.layout.item_driver)
                return
            }
            parent.addView(TextView(activity).apply {
                text = message; textSize = 14f; setTextColor(android.graphics.Color.parseColor("#687386"))
                setPadding(16, 24, 16, 24)
            })
        } else {
            val existing = (0 until parent.childCount).map { parent.getChildAt(it) }
                .mapNotNull { view -> (view.tag as? RegisteredDriver)?.let { it.uid to view } }.toMap()
            val ids = drivers.map { it.uid }.toSet()
            for (index in parent.childCount - 1 downTo 0) {
                if ((parent.getChildAt(index).tag as? RegisteredDriver)?.uid !in ids) parent.removeViewAt(index)
            }
            drivers.forEachIndexed { index, driver ->
                val card = existing[driver.uid] ?: activity.layoutInflater.inflate(R.layout.item_driver, parent, false)
                val moved = card.parent != null && parent.indexOfChild(card) != index
                if (card.parent == null) parent.addView(card, index)
                else if (moved) { parent.removeView(card); parent.addView(card, index) }
                if (card.tag != driver || moved) { bind(card, driver); card.tag = driver }
            }
        }
    }
}
