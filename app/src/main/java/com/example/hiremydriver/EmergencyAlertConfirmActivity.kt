package com.example.hiremydriver

import android.os.Bundle
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class EmergencyAlertConfirmActivity : com.example.hiremydriver.maps.MapScreenActivity() {
    private var customerLocation: android.location.Location? = null
    private var busy = false
    private val activePhases = setOf(com.example.hiremydriver.trip.TripPhase.IN_PROGRESS)
    override fun onLocationChanged(location: android.location.Location) { customerLocation = location }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_emergency_alert_confirm)

        // Back Behavior
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
                @Suppress("DEPRECATION")
                overridePendingTransition(0, 0)
            }
        })

        // Find Views
        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        val btnSendAlert = findViewById<MaterialButton>(R.id.btnSendAlert)
        val btnCancel = findViewById<MaterialButton>(R.id.btnCancel)
        val trip = com.example.hiremydriver.trip.LocalTripStore(this).get(intent.getStringExtra(com.example.hiremydriver.trip.LocalTripStore.ID).orEmpty())
        if (trip == null || !trip.synced || trip.customerUid != CustomerAuth.currentUser(this)?.uid || trip.phase !in activePhases) {
            Toast.makeText(this, "Open your active booking to send an emergency alert.", Toast.LENGTH_LONG).show()
            finish(); return
        }
        findViewById<android.widget.TextView>(R.id.emergencyDestination).text = "Destination: " +
            (trip.destinations.takeIf { it.isNotEmpty() }?.joinToString(" → ") { it.label } ?: trip.destinationLabel)
        findViewById<android.widget.TextView>(R.id.emergencyDriver).text = "Driver: ${trip.driverName}"
        findViewById<android.widget.TextView>(R.id.emergencyTime).text = "Alert time: current Philippine Standard Time"
        useMyLocation()

        // Listeners
        btnBack.setOnClickListener {
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }

        btnCancel.setOnClickListener {
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }

        btnSendAlert.setOnClickListener {
            if (busy) return@setOnClickListener
            busy = true; btnSendAlert.isEnabled = false
            EmergencyContactStore.load(this, preferCached = true) { result ->
                if (isFinishing || isDestroyed) return@load
                val contact = result.getOrNull()
                if (result.isFailure || contact == null) {
                    busy = false; btnSendAlert.isEnabled = true
                    androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle(if (result.isFailure) "Could not load emergency contact" else "No emergency contact")
                        .setMessage(if (result.isFailure) "Check your connection and try again. No SMS was sent." else "Save a trusted person's mobile number first.")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Open Settings") { _, _ -> startActivity(android.content.Intent(this, EmergencyContactActivity::class.java)) }.show()
                    return@load
                }
                val location = customerLocation?.takeIf { System.currentTimeMillis() - it.time in 0..120_000 }
                val confirm = androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Open emergency SMS?")
                    .setMessage("Open Messages for ${contact.name} (${contact.phone}), then tap Send there. " +
                        (if (location == null) "Current location could not be determined; the SMS will say location unavailable. " else "Your current GPS location will be included. ") +
                        if (contact.tripDetails) "Your trip details will also be included." else "Trip details are disabled in your contact settings.")
                    .setNegativeButton("Cancel") { _, _ -> busy = false; btnSendAlert.isEnabled = true }
                    .setPositiveButton("Open Messages") { _, _ ->
                        busy = true; btnSendAlert.isEnabled = false
                        try {
                            check(CustomerAuth.currentUser(this)?.uid == trip.customerUid) { "Please sign in again." }
                            val latest = com.example.hiremydriver.trip.LocalTripStore(this).get(trip.id) ?: trip
                            check(latest.phase in activePhases) { "Open an active booking to prepare an emergency message." }
                            val position = customerLocation?.let { EmergencyMessage.Position(it.latitude, it.longitude, it.time) }
                            val body = EmergencyMessage.body(latest, contact.tripDetails, position, System.currentTimeMillis())
                            startActivity(EmergencySms.composeIntent(contact.phone, body))
                            finish()
                        } catch (_: android.content.ActivityNotFoundException) {
                            Toast.makeText(this, "No SMS app is installed on this phone.", Toast.LENGTH_LONG).show()
                        } catch (error: Exception) {
                            Toast.makeText(this, error.message ?: "Could not open Messages. Please try again.", Toast.LENGTH_LONG).show()
                        } finally {
                            busy = false; btnSendAlert.isEnabled = true
                        }
                    }.create()
                confirm.setOnCancelListener { busy = false; btnSendAlert.isEnabled = true }
                confirm.setOnDismissListener { if (!busy) btnSendAlert.isEnabled = true }
                confirm.show()
            }
        }
    }
}
