package com.example.hiremydriver

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import com.google.android.material.materialswitch.MaterialSwitch

class DriverAvailabilityScheduleActivity : BaseActivity() {
    private var rendering = false
    private var saving = false
    private var approvalListener: com.google.firebase.firestore.ListenerRegistration? = null
    override fun onStart() {
        super.onStart()
        approvalListener = DriverAuth.observeApproval(this) { renderSchedule() }
    }
    override fun onStop() { approvalListener?.remove(); approvalListener = null; super.onStop() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_driver_availability_schedule)

        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        val switchAcceptBookings = findViewById<MaterialSwitch>(R.id.switchAcceptBookings)
        val rowWeeklySchedule = findViewById<View>(R.id.rowWeeklySchedule)
        val rowUpcomingSchedule = findViewById<View>(R.id.rowUpcomingSchedule)

        btnBack.setOnClickListener {
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }

        switchAcceptBookings.setOnCheckedChangeListener { _, isChecked ->
            if (rendering || saving) return@setOnCheckedChangeListener
            if (isChecked && DriverAvailabilityGate.blocked(this)) {
                renderSchedule()
                return@setOnCheckedChangeListener
            }
            saving = true
            switchAcceptBookings.isEnabled = false
            DriverScheduleStore(this).setAccepting(isChecked) { saved ->
                saving = false
                if (!isFinishing && !isDestroyed) {
                    if (!saved) android.widget.Toast.makeText(this, "Could not update availability. Complete Driving Information and check your connection.", android.widget.Toast.LENGTH_LONG).show()
                    renderSchedule()
                }
            }
            renderSchedule()
        }

        rowWeeklySchedule.setOnClickListener {
            val intent = Intent(this, DriverEditWeeklyScheduleActivity::class.java)
            startActivity(intent)
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }

        rowUpcomingSchedule.setOnClickListener {
            val intent = Intent(this, DriverUpcomingScheduleActivity::class.java)
            startActivity(intent)
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    override fun onResume() {
        super.onResume()
        renderSchedule()
        com.example.hiremydriver.recommendation.DriverMatchingSettings.hydrate(this) {
            if (!isFinishing && !isDestroyed) renderSchedule()
        }
    }

    private fun renderSchedule() {
        val store = DriverScheduleStore(this)
        rendering = true
        findViewById<MaterialSwitch>(R.id.switchAcceptBookings).isChecked = store.acceptingBookings
        findViewById<MaterialSwitch>(R.id.switchAcceptBookings).isEnabled = DriverAuth.approved(this) && !saving
        rendering = false
        updateAvailabilityInfo(store.acceptingBookings)
        val count = store.load().count { it.enabled }
        findViewById<TextView>(R.id.txtWeeklyScheduleSummary).text = if (store.load().none { it.configured }) "Schedule not set · Tap to edit" else when (count) {
            0 -> if (store.load().all { it.configured }) "All days off · Tap to edit" else "No working days set · Tap to edit"
            1 -> "1 working day · Tap to edit hours"
            else -> "$count working days · Tap to edit hours"
        }
    }

    private fun updateAvailabilityInfo(available: Boolean) {
        val complete = DriverScheduleStore(this).drivingComplete
        val label = findViewById<TextView>(R.id.txtAvailabilityInfo)
        label.setOnClickListener { if (!DriverAuth.approved(this) || !complete) DriverAvailabilityGate.blocked(this) }
        label.text = if (!DriverAuth.approved(this)) DriverAvailabilityGate.PENDING else if (!complete) DrivingInformation.REQUIRED_MESSAGE else if (available)
            "Your availability is on. Set your preferred working days and hours below."
        else "Your availability is off. Your weekly hours are kept for when you return."
    }
}
