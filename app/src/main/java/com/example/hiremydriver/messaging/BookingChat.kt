package com.example.hiremydriver.messaging

import android.content.Context
import android.content.Intent
import com.example.hiremydriver.*
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source

/** Reuse a participant pair's existing chat. Legacy booking threads remain intact and readable. */
object BookingChat {
    fun intent(context: Context, bookingId: String, driver: Boolean) =
        Intent(context, ChatConversationActivity::class.java)
            .putExtra(Messaging.CONVERSATION_ID, bookingId)
            .putExtra(Messaging.DRIVER_SESSION, driver)

    fun prepare(context: Context, id: String, driver: Boolean, done: (String?) -> Unit) {
        if (!ChatValidation.documentId(id)) { done(null); return }
        val app = if (driver) DriverAuth.firebaseApp(context) else FirebaseApp.getInstance()
        val auth = com.google.firebase.auth.FirebaseAuth.getInstance(app)
        val uid = auth.currentUser?.uid ?: run { done(null); return }
        val db = FirebaseFirestore.getInstance(app)
        val thread = db.collection("conversations").document(id)
        fun read(ref: com.google.firebase.firestore.DocumentReference, callback: (com.google.firebase.firestore.DocumentSnapshot?) -> Unit) {
            ref.get(Source.CACHE).addOnCompleteListener { cached ->
                if (auth.currentUser?.uid != uid) { done(null); return@addOnCompleteListener }
                if (cached.isSuccessful && cached.result.exists()) callback(cached.result)
                else ref.get().addOnCompleteListener { result ->
                    if (auth.currentUser?.uid != uid) done(null)
                    else callback(if (result.isSuccessful) result.result else null)
                }
            }
        }
        fun createIfNeeded() { db.runTransaction { tx ->
            val booking = tx.get(db.collection("bookings").document(id))
            val customerId = booking.getString("customerUid") ?: error("Booking unavailable")
            val driverId = booking.getString("driverUid") ?: error("Booking unavailable")
            check(uid == customerId || uid == driverId)
            val existing = tx.get(thread)
            if (!existing.exists()) tx.set(thread, mapOf(
                "customerId" to customerId, "driverId" to driverId,
                "customerName" to booking.getString("customer").orEmpty(),
                "driverName" to booking.getString("driver").orEmpty(),
                "active" to true, "lastMessageId" to "", "lastText" to "", "lastSenderId" to "",
                "readAt" to emptyMap<String, Any>(), "updatedAt" to FieldValue.serverTimestamp()
            ))
        }.addOnCompleteListener { done(if (it.isSuccessful && auth.currentUser?.uid == uid) id else null) } }
        // Query only this account's threads; choose an existing active pair before creating anything.
        fun resolvePair(customer: String, operator: String) {
            val role = if (uid == customer) "customerId" else "driverId"
            val query = db.collection("conversations").whereEqualTo(role, uid)
            fun existing(result: com.google.firebase.firestore.QuerySnapshot?) = result?.documents?.filter {
                    it.getString("customerId") == customer && it.getString("driverId") == operator && it.getBoolean("active") == true
                }?.minByOrNull { it.id }
            query.get(Source.CACHE).addOnCompleteListener { cached ->
                if (auth.currentUser?.uid != uid) { done(null); return@addOnCompleteListener }
                val local = if (cached.isSuccessful) existing(cached.result) else null
                if (local != null) done(local.id)
                else query.get().addOnCompleteListener { result ->
                    if (auth.currentUser?.uid != uid || !result.isSuccessful) done(null)
                    else existing(result.result)?.let { done(it.id) } ?: createIfNeeded()
                }
            }
        }
        // Booking details already has a confirmed, account-scoped booking. Avoid waiting for
        // a missing booking-ID chat read and another booking read before finding the pair.
        val participants = bookingChatParticipants(
            com.example.hiremydriver.trip.LocalTripStore(context).get(id), uid, driver)
        if (participants != null) {
            resolvePair(participants.first, participants.second)
            return
        }
        read(thread) { doc ->
            if (doc?.exists() == true && (doc.getString("customerId") == uid || doc.getString("driverId") == uid)) {
                // Inbox links already identify a usable thread. PairChatRepository merges
                // its other booking histories without blocking opening on another query.
                if (doc.getBoolean("active") == true) done(doc.id)
                else resolvePair(doc.getString("customerId")!!, doc.getString("driverId")!!)
            } else read(db.collection("bookings").document(id)) { booking ->
                val customer = booking?.getString("customerUid")
                val operator = booking?.getString("driverUid")
                if (auth.currentUser?.uid != uid || customer == null || operator == null || uid !in listOf(customer, operator)) done(null)
                else resolvePair(customer, operator)
            }
        }
    }
}
