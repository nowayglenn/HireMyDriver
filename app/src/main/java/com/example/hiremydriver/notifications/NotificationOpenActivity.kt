package com.example.hiremydriver.notifications

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.example.hiremydriver.*
import com.example.hiremydriver.messaging.Messaging
import com.example.hiremydriver.trip.LocalTripStore
import com.example.hiremydriver.trip.FirebaseTripRepository

/** Revalidates account and loads the authoritative booking before following a notification. */
class NotificationOpenActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val driver = intent.getStringExtra("role") == "driver"
        val uid = intent.getStringExtra("uid").orEmpty()
        val id = intent.getStringExtra("bookingId").orEmpty()
        if (uid.isBlank() || AppNotifications.currentUid(this, driver) != uid || LaunchSession.role(this) != if (driver) LaunchRole.DRIVER else LaunchRole.CUSTOMER) {
            startActivity(Intent(this, SplashActivity::class.java)); finish(); return
        }
        intent.putExtra("DRIVER_MAP", driver)
        val db = AppNotifications.db(this, driver)
        val event = intent.getStringExtra("eventId").orEmpty()
        if (event.isNotBlank()) db.collection("users").document(uid).collection("notifications").document(event).update("read", true)
        if (intent.getStringExtra("kind") == "message") {
            startActivity(Intent(this, ChatConversationActivity::class.java).putExtra(Messaging.CONVERSATION_ID, id).putExtra(Messaging.DRIVER_SESSION, driver)); finish(); return
        }
        if (driver && intent.getStringExtra("kind") == "approval") {
            startActivity(Intent(this, DriverProfileActivity::class.java)); finish(); return
        }
        if (id.isBlank()) { finish(); return }
        db.collection("bookings").document(id).get(com.google.firebase.firestore.Source.SERVER).addOnCompleteListener {
            if (isFinishing || isDestroyed) return@addOnCompleteListener
            if (AppNotifications.currentUid(this, driver) != uid ||
                LaunchSession.role(this) != if (driver) LaunchRole.DRIVER else LaunchRole.CUSTOMER) {
                finish(); return@addOnCompleteListener
            }
            val trip = if (it.isSuccessful) runCatching { FirebaseTripRepository.decode(it.result.data!!) }.getOrNull() else null
            if (trip != null && trip.id == id && (if (driver) trip.driverUid else trip.customerUid) == uid) {
                LocalTripStore(this).save(trip)
                if (driver && intent.getStringExtra("kind") == "review") {
                    startActivity(Intent(this, CustomerReviewsActivity::class.java)
                        .putExtra(LocalTripStore.ID, id).putExtra("DRIVER_REVIEW", true))
                } else if (intent.getStringExtra("kind") == "booking" &&
                    intent.getStringExtra("phase")?.matches(Regex("EXTENSION_[1-9][0-9]*_(PENDING|APPROVED|DECLINED)")) == true &&
                    trip.phase == com.example.hiremydriver.trip.TripPhase.IN_PROGRESS) {
                    val screen = if (driver) com.example.hiremydriver.maps.DriverTripMapActivity::class.java
                        else TripInProgressActivity::class.java
                    startActivity(Intent(this, screen).putExtra(LocalTripStore.ID, id))
                } else com.example.hiremydriver.trip.BookingNavigation.details(this, id, driver)
            } else Toast.makeText(this, "Could not open this booking. Please check your connection.", Toast.LENGTH_LONG).show()
            finish()
        }
    }
}
