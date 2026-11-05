package com.example.hiremydriver.trip

import android.app.Activity
import android.content.Context
import android.widget.TextView
import com.example.hiremydriver.*
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue

/** Contact details are shared only with the assigned driver, inside the booking. */
object CustomerBookingProfile {
    private val syncing = mutableSetOf<String>()
    private val shared = mutableMapOf<String, Map<String, String>>()
    private val profiles = java.util.WeakHashMap<Activity, MutableMap<String, Map<String, String>>>()
    private val preloads = java.util.WeakHashMap<Activity, MutableSet<String>>()

    /** Render private contact details directly in the assigned driver's booking card. */
    fun bind(activity: Activity, trip: TripSession) {
        val owner = activity as? androidx.lifecycle.LifecycleOwner ?: return
        val uid = DriverAuth.currentUser(activity)?.uid ?: return
        if (trip.driverUid != uid) return
        val key = "$uid:${trip.id}"
        fun render(data: Map<String, String>?) {
            val fields = mapOf("name" to R.id.txtCustomerName, "email" to R.id.txtCustomerEmail,
                "phone" to R.id.txtCustomerPhone, "address" to R.id.txtCustomerAddress)
            fields.forEach { (field, id) ->
                val value = data?.get(field)?.takeIf { it.isNotBlank() }
                    ?: if (field == "name") trip.customerName else if (data == null) "—" else "Not provided"
                activity.findViewById<TextView>(id)?.let { view ->
                    if (view.text.toString() != value) view.text = value
                }
            }
        }
        render(profiles[activity]?.get(key))
        if (!preloads.getOrPut(activity) { mutableSetOf() }.add(key)) return
        owner.lifecycle.addObserver(object : androidx.lifecycle.DefaultLifecycleObserver {
            private var listener: com.google.firebase.firestore.ListenerRegistration? = null
            override fun onStart(owner: androidx.lifecycle.LifecycleOwner) {
                listener = observe(activity, trip) { data, error ->
                    if (data != null) render(data)
                    else if ((error as? com.google.firebase.firestore.FirebaseFirestoreException)?.code ==
                        com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED) render(null)
                }
            }
            override fun onStop(owner: androidx.lifecycle.LifecycleOwner) {
                listener?.remove(); listener = null
            }
            override fun onDestroy(owner: androidx.lifecycle.LifecycleOwner) {
                profiles.remove(activity); preloads.remove(activity)
            }
        })
    }

    private fun observe(activity: Activity, trip: TripSession,
        changed: (Map<String, String>?, Exception?) -> Unit): com.google.firebase.firestore.ListenerRegistration {
        val uid = trip.driverUid
        val key = "$uid:${trip.id}"
        return FirebaseFirestore.getInstance(DriverAuth.firebaseApp(activity))
            .collection("bookings").document(trip.id).collection("customer").document("profile")
            .addSnapshotListener(com.google.firebase.firestore.MetadataChanges.INCLUDE) { doc, error ->
                if (activity.isDestroyed || DriverAuth.currentUser(activity)?.uid != uid) return@addSnapshotListener
                if (error != null) {
                    if ((error as? com.google.firebase.firestore.FirebaseFirestoreException)?.code ==
                        com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                        profiles[activity]?.remove(key)
                    }
                    changed(null, error); return@addSnapshotListener
                }
                // A missing disk entry is not evidence that the server has no profile.
                if (doc == null || (!doc.exists() && doc.metadata.isFromCache)) return@addSnapshotListener
                val data = listOf("name", "email", "phone", "address").associateWith { doc.getString(it).orEmpty() }
                    .takeIf { doc.exists() } ?: emptyMap()
                profiles.getOrPut(activity) { mutableMapOf() }[key] = data
                changed(data, null)
            }
    }
    fun sync(context: Context, trips: List<TripSession>) {
        if (!AccountDisplay.scope(context).startsWith("customer_")) return
        val user = CustomerAuth.currentUser(context) ?: return
        val eligible = trips.filter { it.synced && it.customerUid == user.uid }
        if (eligible.isEmpty() || !syncing.add(user.uid)) return
        val db = FirebaseFirestore.getInstance()
        val owner = db.collection("users").document(user.uid)
        com.google.android.gms.tasks.Tasks.whenAllSuccess<com.google.firebase.firestore.DocumentSnapshot>(
            owner.get(), owner.collection("settings").document("profile").get()
        ).addOnCompleteListener { result ->
            if (!result.isSuccessful || CustomerAuth.currentUser(context)?.uid != user.uid) {
                syncing.remove(user.uid); return@addOnCompleteListener
            }
            val registered = result.result[0]
            val saved = result.result[1]
            val prefs = context.getSharedPreferences("customer_profile_${user.uid}", Context.MODE_PRIVATE)
            fun field(local: String, original: String) = if (prefs.contains(local)) prefs.getString(local, "").orEmpty()
                else saved.getString(local) ?: registered.getString(original).orEmpty()
            val data = mapOf("name" to field("name","fullName"), "email" to user.email.orEmpty(),
                "phone" to field("phone","phoneNumber"), "address" to field("address","address"))
            val pending = eligible.filter { shared["${user.uid}:${it.id}"] != data }
            if (pending.isEmpty()) { syncing.remove(user.uid); return@addOnCompleteListener }
            val writes = pending.map { trip ->
                db.collection("bookings").document(trip.id).collection("customer").document("profile")
                    .set(data + ("updatedAt" to FieldValue.serverTimestamp())).addOnSuccessListener {
                        if (CustomerAuth.currentUser(context)?.uid == user.uid) shared["${user.uid}:${trip.id}"] = data
                    }
            }
            com.google.android.gms.tasks.Tasks.whenAllComplete(writes).addOnCompleteListener { syncing.remove(user.uid) }
        }
    }

}
