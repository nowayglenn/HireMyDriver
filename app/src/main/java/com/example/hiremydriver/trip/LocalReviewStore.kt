package com.example.hiremydriver.trip

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Account-scoped offline cache of the existing server-authorized review records. */
class LocalReviewStore(private val context: Context) {
    var saveError: String = "Could not save your review. Check the booking and try again."
        private set
    private val prefs = context.getSharedPreferences("account_reviews_${com.example.hiremydriver.AccountDisplay.scope(context)}", Context.MODE_PRIVATE)
    private val trips = LocalTripStore(context)
    private val driver = com.example.hiremydriver.AccountDisplay.scope(context).startsWith("driver_")
    private val uid = if (driver) com.example.hiremydriver.DriverAuth.currentUser(context)?.uid else com.example.hiremydriver.CustomerAuth.currentUser(context)?.uid
    private val db = if (driver) com.google.firebase.firestore.FirebaseFirestore.getInstance(com.example.hiremydriver.DriverAuth.firebaseApp(context))
        else com.google.firebase.firestore.FirebaseFirestore.getInstance()
    private fun sameSession() = uid != null && uid == (if (driver) com.example.hiremydriver.DriverAuth.currentUser(context)?.uid else com.example.hiremydriver.CustomerAuth.currentUser(context)?.uid)

    fun observe(owner: androidx.lifecycle.LifecycleOwner, changed: () -> Unit) {
        trips.observe(owner, changed)
        owner.lifecycle.addObserver(object : androidx.lifecycle.DefaultLifecycleObserver {
            private var listener: com.google.firebase.firestore.ListenerRegistration? = null
            override fun onStart(owner: androidx.lifecycle.LifecycleOwner) {
                if (!sameSession()) return
                listener = db.collection("reviews").whereEqualTo(if (driver) "driverUid" else "customerUid", uid)
                    .addSnapshotListener { snapshot, error ->
                        if (!sameSession()) return@addSnapshotListener
                        if (error != null) {
                            android.widget.Toast.makeText(context, "Could not refresh reviews. Check your connection.", android.widget.Toast.LENGTH_LONG).show()
                            return@addSnapshotListener
                        }
                        snapshot?.documents?.filter { !it.metadata.hasPendingWrites() }?.forEach { doc ->
                            val data = doc.data ?: return@forEach
                            if (data[if (driver) "driverUid" else "customerUid"] != uid) return@forEach
                            val timestamp = (data["updatedAt"] as? com.google.firebase.Timestamp)
                                ?: (data["createdAt"] as? com.google.firebase.Timestamp)
                            val review = BookingReview.fromRecord(doc.id, data, timestamp?.toDate()?.time ?: 0L)
                                ?: return@forEach
                            cache(review)
                        }
                        if (snapshot != null && !snapshot.metadata.isFromCache && !snapshot.metadata.hasPendingWrites()) {
                            val valid = snapshot.documents.filter { doc ->
                                BookingReview.fromRecord(doc.id, doc.data.orEmpty(), 0L) != null
                            }.map { it.id }.toSet()
                            prefs.edit().apply { prefs.all.keys.filter { it !in valid }.forEach(::remove) }.apply()
                        }
                        changed()
                    }
            }
            override fun onStop(owner: androidx.lifecycle.LifecycleOwner) { listener?.remove(); listener = null }
        })
    }

    fun saveConfirmed(tripId: String, rating: Int, comment: String, tags: List<String>, done: (Boolean) -> Unit) {
        com.example.hiremydriver.PeerProfilePhoto.syncCustomerPhoto(context)
        saveError = "Could not save your review. Check the booking and try again."
        val trip = trips.get(tripId)
        if (driver || !sameSession() || trip == null || !trip.synced || trip.customerUid != uid) { done(false); return }
        val review = runCatching { BookingReview.forBooking(trip, rating, comment, tags, System.currentTimeMillis()) }.getOrNull()
            ?: run { done(false); return }
        val ref = db.collection("reviews").document(tripId)
        db.runTransaction { tx ->
            val booking = tx.get(db.collection("bookings").document(tripId))
            check(booking.getString("phase") == "COMPLETED" && booking.getString("customerUid") == uid)
            val previous = tx.get(ref)
            tx.set(ref, mapOf("customerUid" to uid, "driverUid" to booking.getString("driverUid"), "rating" to review.rating,
                "customerName" to booking.getString("customer").orEmpty(),
                "comment" to review.comment, "tags" to review.tags,
                "createdAt" to (previous.getTimestamp("createdAt") ?: com.google.firebase.firestore.FieldValue.serverTimestamp()),
                "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()))
        }.addOnCompleteListener {
            if (!sameSession()) { done(false); return@addOnCompleteListener }
            if (!it.isSuccessful) {
                android.util.Log.w("ReviewSave", "Server save failed", it.exception)
                saveError = if ((it.exception as? com.google.firebase.firestore.FirebaseFirestoreException)?.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED)
                    "Review access was denied. Please publish the updated Firebase rules and try again."
                    else "Could not save your review to Firebase. Check your connection and try again."
            }
            if (it.isSuccessful) {
                cache(review)
                com.example.hiremydriver.notifications.EventRelay.enqueue(context, uid!!, false, "review", tripId, "submitted")
            }
            done(it.isSuccessful)
        }
    }

    private fun cache(review: BookingReview): Boolean {
        val json = JSONObject().put("rating", review.rating).put("comment", review.comment)
            .put("tags", JSONArray(review.tags)).put("submittedAt", review.submittedAt)
        return prefs.edit().putString(review.tripId, json.toString()).commit()
    }

    fun get(tripId: String): BookingReview? = prefs.getString(tripId, null)?.let { raw ->
        runCatching {
            val json = JSONObject(raw)
            val tags = json.optJSONArray("tags")
            BookingReview.fromRecord(tripId, mapOf("rating" to json.opt("rating"), "comment" to json.opt("comment"),
                "tags" to (0 until (tags?.length() ?: 0)).map { tags?.opt(it) }), json.optLong("submittedAt"))
        }.getOrNull()
    }

    fun all(): List<Pair<TripSession, BookingReview>> = trips.all().mapNotNull { trip ->
        if (sameSession() && trip.phase == TripPhase.COMPLETED &&
            (if (driver) trip.driverUid == uid else trip.customerUid == uid)) get(trip.id)?.let { trip to it } else null
    }.sortedByDescending { it.second.submittedAt }

    // The current local driver catalog uses names; use authenticated driver IDs when adding the backend.
    fun forDriver(driverName: String) = all().filter { it.first.driverName.trim() == driverName.trim() }

    fun save(tripId: String, rating: Int, comment: String, tags: List<String>): Boolean {
        val trip = trips.get(tripId) ?: return false
        val review = runCatching { BookingReview.forBooking(trip, rating, comment, tags, System.currentTimeMillis()) }
            .getOrNull() ?: return false
        val json = JSONObject().put("rating", review.rating).put("comment", review.comment)
            .put("tags", JSONArray(review.tags)).put("submittedAt", review.submittedAt)
        // Confirm durable storage before displaying the success template.
        return prefs.edit().putString(tripId, json.toString()).commit()
    }
}
