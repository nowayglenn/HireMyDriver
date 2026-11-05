package com.example.hiremydriver

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.materialswitch.MaterialSwitch

class DriverHomeActivity : DriverNavigationActivity() {
    private lateinit var bookingSections: DriverHomeBookings
    private var approvalListener: com.google.firebase.firestore.ListenerRegistration? = null

    override fun onStart() {
        super.onStart()
        approvalListener = DriverAuth.observeApproval(this) {
            renderApproval()
            refreshAvailability()
            bookingSections.render()
        }
    }

    override fun onStop() {
        approvalListener?.remove()
        approvalListener = null
        super.onStop()
    }

    // =====================================================
    // HEADER & AVAILABILITY
    // =====================================================

    private lateinit var btnDriverNotification: ImageButton
    private lateinit var switchAvailability: MaterialSwitch
    private var refreshingAvailability = false
    private var savingAvailability = false
    private lateinit var txtAvailabilityStatus: TextView
    private lateinit var viewOnlineStatus: View


    // =====================================================
    // UPCOMING BOOKING
    // =====================================================

    private lateinit var txtViewAllUpcoming: TextView

    private lateinit var txtUpcomingDay: TextView
    private lateinit var txtUpcomingTime: TextView
    private lateinit var txtUpcomingCustomer: TextView
    private lateinit var txtUpcomingRoute: TextView
    private lateinit var txtBookingType: TextView
    private lateinit var txtUpcomingStatus: TextView


    // =====================================================
    // STATISTICS
    // =====================================================

    private lateinit var txtPendingCount: TextView
    private lateinit var txtCompletedCount: TextView
    private lateinit var txtDriverRating: TextView


    // =====================================================
    // BOOKING REQUESTS
    // =====================================================

    private lateinit var txtViewAllRequests: TextView

    private lateinit var requestContainer1: View
    private lateinit var requestContainer2: View
    private lateinit var requestContainer3: View
    private lateinit var requestContainer4: View
    private lateinit var requestContainer5: View

    private lateinit var btnDecline1: View
    private lateinit var btnAccept1: View

    private lateinit var btnDecline2: View
    private lateinit var btnAccept2: View

    private lateinit var btnDecline3: View
    private lateinit var btnAccept3: View

    private lateinit var btnDecline4: View
    private lateinit var btnAccept4: View

    private lateinit var btnDecline5: View
    private lateinit var btnAccept5: View


    // =====================================================
    // ON CREATE
    // =====================================================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_driver_home)

        // Animate active nav background
        animateNavActiveBackground(findViewById(R.id.iconHome))

        initializeViews()
        bookingSections = DriverHomeBookings(this)
        setupDriverNavigation()
        setupClickListeners()
        loadDriverData()
        com.example.hiremydriver.trip.LocalTripStore(this).observe(this) { bookingSections.render() }
        BookingScreenRefresh(this, R.id.driverHomeRefresh, R.id.scrollDriverHome, com.example.hiremydriver.trip.LocalTripStore(this)) {
            loadDriverData()
            refreshAvailability()
        }
        if (savedInstanceState == null && intent.getBooleanExtra(SHOW_PENDING_APPROVAL, false)) {
            PendingApprovalDialog().show(supportFragmentManager, PendingApprovalDialog.TAG)
        }
        // FragmentManager restores an open dialog; a dismissed one must not reappear on rotation.
        intent.removeExtra(SHOW_PENDING_APPROVAL)
    }


    // =====================================================
    // INITIALIZE VIEWS
    // =====================================================

    private fun initializeViews() {

        // -------------------------------------------------
        // HEADER & AVAILABILITY
        // -------------------------------------------------

        btnDriverNotification = findViewById(R.id.btnDriverNotification)
        switchAvailability = findViewById(R.id.switchAvailability)
        txtAvailabilityStatus = findViewById(R.id.txtAvailabilityStatus)
        viewOnlineStatus = findViewById(R.id.viewOnlineStatus)


        // -------------------------------------------------
        // UPCOMING BOOKING
        // -------------------------------------------------

        txtViewAllUpcoming = findViewById(R.id.txtViewAllUpcoming)
        txtUpcomingDay = findViewById(R.id.txtUpcomingDay)
        txtUpcomingTime = findViewById(R.id.txtUpcomingTime)
        txtUpcomingCustomer = findViewById(R.id.txtUpcomingCustomer)
        txtUpcomingRoute = findViewById(R.id.txtUpcomingRoute)
        txtBookingType = findViewById(R.id.txtBookingType)
        txtUpcomingStatus = findViewById(R.id.txtUpcomingStatus)


        // -------------------------------------------------
        // STATISTICS
        // -------------------------------------------------

        txtPendingCount = findViewById(R.id.txtPendingCount)
        txtCompletedCount = findViewById(R.id.txtCompletedCount)
        txtDriverRating = findViewById(R.id.txtDriverRating)


        // -------------------------------------------------
        // BOOKING REQUESTS
        // -------------------------------------------------

        txtViewAllRequests = findViewById(R.id.txtViewAllRequests)

        requestContainer1 = findViewById(R.id.requestContainer1)
        requestContainer2 = findViewById(R.id.requestContainer2)
        requestContainer3 = findViewById(R.id.requestContainer3)
        requestContainer4 = findViewById(R.id.requestContainer4)
        requestContainer5 = findViewById(R.id.requestContainer5)

        btnDecline1 = findViewById(R.id.btnDecline1)
        btnAccept1 = findViewById(R.id.btnAccept1)

        btnDecline2 = findViewById(R.id.btnDecline2)
        btnAccept2 = findViewById(R.id.btnAccept2)

        btnDecline3 = findViewById(R.id.btnDecline3)
        btnAccept3 = findViewById(R.id.btnAccept3)

        btnDecline4 = findViewById(R.id.btnDecline4)
        btnAccept4 = findViewById(R.id.btnAccept4)

        btnDecline5 = findViewById(R.id.btnDecline5)
        btnAccept5 = findViewById(R.id.btnAccept5)
    }


    // =====================================================
    // CLICK LISTENERS
    // =====================================================

    private fun setupClickListeners() {
        switchAvailability.setOnCheckedChangeListener { _, checked ->
            if (!refreshingAvailability && !savingAvailability) {
                if (checked && DriverAvailabilityGate.blocked(this)) {
                    refreshAvailability()
                    return@setOnCheckedChangeListener
                }
                savingAvailability = true
                switchAvailability.isEnabled = false
                DriverScheduleStore(this).setAccepting(checked) { saved ->
                    savingAvailability = false
                    if (!isFinishing && !isDestroyed) {
                        if (!saved) Toast.makeText(this, "Could not update availability. Complete Driving Information and check your connection.", Toast.LENGTH_LONG).show()
                        refreshAvailability()
                    }
                }
                refreshAvailability()
            }
        }
        btnDriverNotification.setOnClickListener {
            startActivity(Intent(this, DriverNotificationsActivity::class.java))
        }
        txtViewAllUpcoming.setOnClickListener {
            startActivity(Intent(this, DriverUpcomingBookingsActivity::class.java))
        }
        txtViewAllRequests.setOnClickListener {
            startActivity(Intent(this, DriverBookingRequestsActivity::class.java))
        }
    }
    private fun loadDriverData() {
        val personal = getSharedPreferences(DriverAuth.preferenceName(this, DriverPersonalSettingsActivity.PREFERENCES), MODE_PRIVATE)
        val fullName = personal.getString("name", null)
            ?: DriverAuth.currentUser(this)?.displayName ?: "Driver"
        findViewById<TextView>(R.id.txtDriverName).text = fullName.trim().split(Regex("\\s+")).firstOrNull { it.isNotBlank() } ?: "Driver"
        DriverProfilePhoto.show(findViewById(R.id.imgDriverProfile))
        renderApproval()
        com.example.hiremydriver.trip.RatingDisplay.bind(txtDriverRating, DriverAuth.currentUser(this)?.uid.orEmpty(), true, 26f)
        bookingSections.render()
    }
    // =====================================================
    // ACCEPT BOOKING
    // =====================================================

    override fun onResume() {
        super.onResume()
        com.example.hiremydriver.recommendation.DriverMatchingSettings.sync(this) {
            if (!isFinishing && !isDestroyed) refreshAvailability()
        }
        com.example.hiremydriver.recommendation.DriverLocationSync.refresh(this)
        refreshAvailability()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    private fun refreshAvailability() {
        val available = DriverScheduleStore(this).acceptingBookings
        val complete = DriverScheduleStore(this).drivingComplete
        refreshingAvailability = true
        switchAvailability.isChecked = available
        switchAvailability.isEnabled = DriverAuth.approved(this) && !savingAvailability
        refreshingAvailability = false
        txtAvailabilityStatus.text = when {
            !DriverAuth.approved(this) -> getString(R.string.driver_pending_message)
            !complete -> getString(R.string.driver_incomplete_message)
            available -> getString(R.string.driver_available_message)
            else -> getString(R.string.driver_offline_message)
        }
        txtAvailabilityStatus.setOnClickListener {
            if (!DriverAuth.approved(this) || !complete) DriverAvailabilityGate.blocked(this)
        }
        val statusColor = ColorStateList.valueOf(Color.parseColor(if (available) "#00B85A" else "#9CA3AF"))
        viewOnlineStatus.backgroundTintList = statusColor
    }

    private fun renderApproval() {
        val badge = findViewById<android.widget.LinearLayout>(R.id.layoutVerified)
        badge.visibility = View.VISIBLE
        val approved = DriverAuth.approved(this)
        (badge.getChildAt(0) as android.widget.ImageView).apply {
            if (approved) clearColorFilter()
            else colorFilter = android.graphics.ColorMatrixColorFilter(android.graphics.ColorMatrix().apply { setSaturation(0f) })
        }
        (badge.getChildAt(1) as TextView).text = getString(if (approved) R.string.driver_verified_label else R.string.tab_pending)
    }

    companion object {
        const val SHOW_PENDING_APPROVAL = "show_pending_approval"
    }
}
