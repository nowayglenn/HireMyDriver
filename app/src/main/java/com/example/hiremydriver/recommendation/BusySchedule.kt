package com.example.hiremydriver.recommendation

import android.content.Context
import com.example.hiremydriver.DriverAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

/** Anonymous time slots in the existing public driver profile; private booking documents stay private. */
object BusySchedule {
    private val syncing = mutableSetOf<String>()

    fun slot(data: Map<String, Any>): Map<String, Any>? {
        if (data["phase"] !in setOf("ACCEPTED", "ON_THE_WAY", "ARRIVED", "IN_PROGRESS")) return null
        return mapOf(
            "date" to (data["date"] ?: ""),
            "time" to (data["time"] ?: ""),
            "hours" to ((data["hours"] as? Number)?.toLong() ?: 0L),
            "phase" to data.getValue("phase"),
            "started" to ((data["started"] as? Number)?.toLong() ?: 0L),
            "extensionMinutes" to ((data["extensionMinutes"] as? Number)?.toLong() ?: 0L)
        )
    }

    /** A customer request must not repair or rewrite the driver's availability. */
    fun needsBookingUpdate(before: Map<String, Any>, after: Map<String, Any>, driver: Boolean): Boolean =
        driver || slot(before) != slot(after)

    fun refresh(context: Context, done: ((Boolean) -> Unit)? = null) {
        val uid = DriverAuth.currentUser(context)?.uid ?: run { done?.invoke(false); return }
        if (!syncing.add(uid)) { done?.invoke(false); return }

        val db = FirebaseFirestore.getInstance(DriverAuth.firebaseApp(context))
        val profile = db.collection("driverProfiles").document(uid)

        fun finish(ok: Boolean) {
            syncing.remove(uid)
            done?.invoke(ok)
        }

        fun same() = DriverAuth.currentUser(context)?.uid == uid

        db.collection("bookings").whereEqualTo("driverUid", uid).get(com.google.firebase.firestore.Source.SERVER).addOnCompleteListener { bookings ->
            if (!bookings.isSuccessful || !same()) {
                android.util.Log.w("BusySchedule", "Booking read failed: ${bookings.exception?.javaClass?.simpleName}")
                finish(false); return@addOnCompleteListener
            }

            profile.get(com.google.firebase.firestore.Source.SERVER).addOnCompleteListener profileRead@ { read ->
                if (!read.isSuccessful || !same()) {
                    finish(false)
                    return@profileRead
                }

                val doc = read.result
                val oldSlots = (doc?.get("busySlots") as? Map<*, *>)?.keys?.filterIsInstance<String>().orEmpty()
                val activeBookings = if (bookings.isSuccessful && bookings.result != null) {
                    bookings.result.documents.filter { it.data?.let(::slot) != null }
                } else {
                    emptyList()
                }

                val ids = (oldSlots + activeBookings.map { it.id }).distinct()

                if (ids.isEmpty()) {
                    profile.set(mapOf("busySlots" to emptyMap<String, Any>(), "availabilityReady" to true), SetOptions.merge())
                        .addOnCompleteListener { finish(it.isSuccessful && same()) }
                    return@profileRead
                }

                fun next(index: Int) {
                    if (!same()) { finish(false); return }
                    if (index == ids.size) {
                        profile.set(mapOf("availabilityReady" to true), SetOptions.merge())
                            .addOnCompleteListener { finish(it.isSuccessful && same()) }
                        return
                    }

                    val id = ids[index]
                    db.runTransaction { tx ->
                        val booking = tx.get(db.collection("bookings").document(id))
                        val current = tx.get(profile)
                        val slots = (current.get("busySlots") as? Map<*, *>)?.entries
                            ?.associate { it.key.toString() to it.value }?.toMutableMap() ?: mutableMapOf()
                        val data = booking.data
                        check(data == null || data["driverUid"] == uid)
                        val desired = data?.let(::slot)
                        if (slots[id] != desired) {
                            if (desired == null) slots.remove(id) else slots[id] = desired
                            tx.update(profile, mapOf("busySlots" to slots, "busyBookingId" to id))
                        }
                        true
                    }.addOnCompleteListener { if (it.isSuccessful) next(index + 1) else finish(false) }
                }

                next(0)
            }
        }
    }
}
