package com.example.hiremydriver.trip

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.example.hiremydriver.AccountDisplay
import com.example.hiremydriver.CustomerAuth
import com.example.hiremydriver.DriverAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import org.json.JSONArray
import org.json.JSONObject

/** Uses the existing Firebase project and each role's own authenticated Firebase app. */
class FirebaseTripRepository(private val context: Context, private val cache: LocalTripStore) {
    private class StaleBooking : IllegalStateException()
    private class BookingConflict : IllegalStateException()
    private class ScheduleConflict : IllegalStateException()
    private class ExtensionConflict : IllegalStateException()
    private class ExtensionUnavailable : IllegalStateException()
    private val driver = AccountDisplay.scope(context).startsWith("driver_")
    private val uid = if (driver) DriverAuth.currentUser(context)?.uid else CustomerAuth.currentUser(context)?.uid
    private val db = if (driver) FirebaseFirestore.getInstance(DriverAuth.firebaseApp(context)) else FirebaseFirestore.getInstance()
    private fun sameSession() = uid != null && uid == (if (driver) DriverAuth.currentUser(context)?.uid else CustomerAuth.currentUser(context)?.uid)
    private fun error(error: Exception?) {
        val message = if (generateSequence(error as Throwable?) { it.cause }.any { it is StaleBooking })
            "Booking status has changed. Please check the current action."
        else if (generateSequence(error as Throwable?) { it.cause }.any { it is ExtensionConflict })
            "This extension overlaps another booking. You can decline the request or ask the customer for a shorter duration."
        else if (generateSequence(error as Throwable?) { it.cause }.any { it is ExtensionUnavailable })
            "Could not verify your other bookings. Please reconnect and try accepting again."
        else if (generateSequence(error as Throwable?) { it.cause }.any { it is BookingConflict })
            "This booking overlaps another accepted booking. Please check your schedule."
        else if (generateSequence(error as Throwable?) { it.cause }.any { it is ScheduleConflict })
            "Your selected booking time exceeds the driver's available schedule. Please choose another time or duration."
        else if (error is com.google.firebase.firestore.FirebaseFirestoreException &&
            error.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED)
            "Booking access denied. Please check the published Firebase booking rules."
        else "Could not save booking. Check your connection and try again."
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    fun create(trip: TripSession, done: (Boolean) -> Unit) {
        if (driver || !sameSession() || trip.customerUid != uid || trip.driverUid.isBlank() || trip.driverUid == uid) {
            Toast.makeText(context, "Choose a registered driver and sign in as a customer before booking.", Toast.LENGTH_LONG).show()
            done(false); return
        }
        val key = "create:${trip.id}"
        if (!busy.add(key)) { done(false); return }
        val confirmed = trip.copy(synced = true, driverImage = 0)
        val ref = db.collection("bookings").document(trip.id)
        db.runTransaction { tx ->
            val existing = tx.get(ref)
            if (existing.exists()) {
                val saved = decode(existing.data!!)
                check(saved.customerUid == uid && saved.driverUid == trip.driverUid)
                saved
            } else {
                val profile = tx.get(db.collection("driverProfiles").document(trip.driverUid))
                check(profile.getString("status") == "approved" &&
                    (profile.get("matching") as? Map<*, *>)?.let { it["accepting"] == true && com.example.hiremydriver.DrivingInformation.complete(it) } == true) {
                    "This driver is not available for new bookings."
                }
                validateSchedule(profile.data.orEmpty(), confirmed, false)
                val rate = ((profile.get("matching") as? Map<*, *>)?.get("rate") as? Number)?.toInt() ?: throw ScheduleConflict()
                val priced = confirmed.copy(hourlyRate = rate, fee = "₱%,.2f".format(java.util.Locale.US, rate * confirmed.durationHours.toDouble()))
                tx.set(ref, encode(priced) + mapOf("createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                    "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp())); priced
            }
        }.addOnCompleteListener { task ->
            busy.remove(key)
            if (task.isSuccessful && sameSession()) {
                cache.save(task.result)
                CustomerBookingProfile.sync(context, listOf(task.result))
                com.example.hiremydriver.notifications.EventRelay.enqueue(context, uid!!, driver, "booking", trip.id, task.result.phase.name)
            }
            else error(task.exception)
            done(task.isSuccessful && sameSession())
        }
    }

    fun update(id: String, change: (TripSession) -> TripSession, done: (Boolean) -> Unit) {
        val cached = cache.get(id)
        if (!sameSession() || cached == null || !cached.synced) {
            Toast.makeText(context, "This booking is only saved on this phone. Create a new synced booking to continue.", Toast.LENGTH_LONG).show()
            done(false); return
        }
        val key = "$uid:$id"
        if (!busy.add(key)) { done(false); return }
        val expectedPhase = cached.phase
        val ref = db.collection("bookings").document(id)
        db.runTransaction { tx ->
            val data = tx.get(ref).data ?: throw IllegalStateException("Booking unavailable")
            val before = decode(data)
            val publicProfile = db.collection("driverProfiles").document(before.driverUid)
            val availability = tx.get(publicProfile)
            if (before.phase != expectedPhase) throw StaleBooking()
            check(if (driver) before.driverUid == uid else before.customerUid == uid)
            val after = change(before)
            if (before.phase == TripPhase.REQUESTED && after.phase == TripPhase.ACCEPTED)
                validateSchedule(availability.data.orEmpty(), after, true)
            if (before.extensionStatus == "PENDING" && after.extensionStatus == "APPROVED" &&
                after.extensionMinutes > before.extensionMinutes) {
                check(driver)
                when (extensionApprovalIssue(availability.data.orEmpty(), before, after, System.currentTimeMillis())) {
                    ExtensionApprovalIssue.INVALID_REQUEST -> throw StaleBooking()
                    ExtensionApprovalIssue.AVAILABILITY_UNAVAILABLE -> throw ExtensionUnavailable()
                    ExtensionApprovalIssue.BOOKING_CONFLICT -> throw ExtensionConflict()
                    null -> Unit
                }
            }
            if (before.phase == TripPhase.REQUESTED && after.phase == TripPhase.ACCEPTED) {
                check(availability?.getString("status") == "approved" &&
                    (availability.get("matching") as? Map<*, *>)?.let { it["accepting"] == true && com.example.hiremydriver.DrivingInformation.complete(it) } == true) {
                    "Complete verification and driving information before accepting bookings."
                }
            }
            if (after == before) throw StaleBooking()
            // Preserve any server-owned fields not represented by the Android model.
            val afterFields = encode(after)
            val next = data.toMutableMap()
            (encode(before).keys - afterFields.keys).forEach(next::remove)
            next.putAll(afterFields)
            next["updatedAt"] = com.google.firebase.firestore.FieldValue.serverTimestamp()
            if (before.extensionStatus != after.extensionStatus || before.extensionSequence != after.extensionSequence) {
                val receipts = (data["extensionEvents"] as? Map<*, *>)?.entries?.associate { it.key.toString() to it.value }?.toMutableMap()
                    ?: mutableMapOf<String, Any?>()
                receipts["EXTENSION_${after.extensionSequence}_${after.extensionStatus}"] = com.google.firebase.firestore.FieldValue.serverTimestamp()
                next["extensionEvents"] = receipts
            }
            if (before.phase != after.phase) {
                val events = (data["statusEvents"] as? Map<*, *>)?.entries?.associate { it.key.toString() to it.value }?.toMutableMap()
                    ?: mutableMapOf<String, Any?>()
                events[after.phase.name] = com.google.firebase.firestore.FieldValue.serverTimestamp()
                next["statusEvents"] = events
            }
            // Pending requests do not reserve extra time. Only the driver's approval does.
            // Firestore integers are Long, while model integers are Int: compare canonical slots.
            if (availability.contains("busySlots") &&
                com.example.hiremydriver.recommendation.BusySchedule.needsBookingUpdate(encode(before), afterFields, driver)) {
                val slots = (availability.get("busySlots") as? Map<*, *>)?.entries
                    ?.associate { it.key.toString() to it.value }?.toMutableMap() ?: error("Invalid availability")
                val slot = com.example.hiremydriver.recommendation.BusySchedule.slot(afterFields)
                if (before.phase == TripPhase.REQUESTED && after.phase == TripPhase.ACCEPTED &&
                    !com.example.hiremydriver.recommendation.RegisteredSchedule.canAccept(afterFields, slots, id, System.currentTimeMillis()))
                    throw BookingConflict()
                if (slots[id] != slot) {
                    if (slot == null) slots.remove(id) else slots[id] = slot
                    tx.update(publicProfile, mapOf("busySlots" to slots, "busyBookingId" to id))
                }
            }
            tx.set(ref, next)
            after
        }.addOnCompleteListener { task ->
            busy.remove(key)
            val success = task.isSuccessful && sameSession()
            if (success) {
                cache.save(task.result)
                if (cached.phase != task.result.phase) com.example.hiremydriver.notifications.EventRelay.enqueue(context, uid!!, driver, "booking", id, task.result.phase.name)
                if (task.result.reachedStops > cached.reachedStops) com.example.hiremydriver.notifications.EventRelay.enqueue(context, uid!!, driver, "booking", id, "DESTINATION_REACHED_${task.result.reachedStops}")
                if (task.result.extensionStatus != cached.extensionStatus || task.result.extensionSequence != cached.extensionSequence)
                    com.example.hiremydriver.notifications.EventRelay.enqueue(context, uid!!, driver, "booking", id, "EXTENSION_${task.result.extensionSequence}_${task.result.extensionStatus}")
                TripNotifications.onChange(context.applicationContext, cached, task.result)
            } else error(task.exception)
            done(success)
        }
    }

    private fun validateSchedule(profile: Map<String, Any?>, trip: TripSession, excludeSelf: Boolean) {
        val schedule = com.example.hiremydriver.recommendation.RegisteredSchedule
        val start = trip.startedAt ?: schedule.bookingStart(encode(trip)) ?: throw ScheduleConflict()
        val end = start + trip.totalMinutes() * 60000L
        if (!schedule.withinHours(profile, start, end)) throw ScheduleConflict()
        val slots = profile["busySlots"] as? Map<*, *> ?: throw ScheduleConflict()
        if (slots.entries.any { (!excludeSelf || it.key != trip.id) && schedule.conflicts(it.value as? Map<*, *>, start, end, System.currentTimeMillis()) })
            throw BookingConflict()
    }

    private fun applySnapshot(snapshot: com.google.firebase.firestore.QuerySnapshot) {
        if (!snapshot.metadata.isFromCache || !snapshot.isEmpty) cache.loaded()
        if (!snapshot.metadata.isFromCache && !snapshot.metadata.hasPendingWrites()) {
            val ids = snapshot.documents.map { it.id }.toSet()
            cache.all().filter { it.synced &&
                (if (driver) it.driverUid == uid else it.customerUid == uid) && it.id !in ids
            }.forEach { cache.remove(it.id) }
        }
        snapshot.documents.forEach { document ->
            if (!document.metadata.hasPendingWrites()) {
                runCatching { decode(document.data!!) }.getOrNull()?.let { incoming ->
                    // A listener's initial disk snapshot can predate a notification's server read.
                    // Keep our last confirmed booking until the server responds.
                    if (incoming.id == document.id) {
                        val current = cache.get(incoming.id)
                        val resolved = bookingSnapshot(current, incoming, snapshot.metadata.isFromCache)
                        if (resolved != current) cache.save(resolved)
                    }
                }
            }
        }
        if (!driver) CustomerBookingProfile.sync(context, cache.all())
    }

    fun refresh(done: () -> Unit) {
        if (!sameSession()) { cache.loadFailed(); done(); return }
        db.collection("bookings").whereEqualTo(if (driver) "driverUid" else "customerUid", uid)
            .get(com.google.firebase.firestore.Source.SERVER).addOnCompleteListener { task ->
                if (sameSession()) {
                    if (task.isSuccessful) applySnapshot(task.result) else cache.loadFailed()
                }
                done()
            }
    }

    fun observe(owner: LifecycleOwner, changed: () -> Unit) {
        owner.lifecycle.addObserver(object : DefaultLifecycleObserver {
            private var listener: ListenerRegistration? = null
            override fun onStart(owner: LifecycleOwner) {
                if (!sameSession()) return
                listener = db.collection("bookings").whereEqualTo(if (driver) "driverUid" else "customerUid", uid)
                    .addSnapshotListener(com.google.firebase.firestore.MetadataChanges.INCLUDE) { snapshot, failure ->
                        if (!sameSession()) return@addSnapshotListener
                        if (failure != null) { cache.loadFailed(); changed(); error(failure); return@addSnapshotListener }
                        snapshot?.let(::applySnapshot)
                        changed()
                    }
            }
            override fun onStop(owner: LifecycleOwner) { listener?.remove(); listener = null }
        })
    }

    companion object {
        private val busy = mutableSetOf<String>() // UI-thread duplicate guard; transactions guard other devices.
        internal fun decode(data: Map<String, Any>) = LocalTripStore.decode(JSONObject(data))
        internal fun encode(trip: TripSession): Map<String, Any> {
            fun value(v: Any): Any = when (v) {
                is JSONObject -> v.keys().asSequence().associateWith { value(v.get(it)) }
                is JSONArray -> (0 until v.length()).map { value(v.get(it)) }
                else -> v
            }
            @Suppress("UNCHECKED_CAST")
            return value(LocalTripStore.encode(trip)) as Map<String, Any>
        }
    }
}
