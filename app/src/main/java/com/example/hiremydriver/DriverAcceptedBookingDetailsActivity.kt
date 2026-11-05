package com.example.hiremydriver

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import com.google.android.material.button.MaterialButton

class DriverAcceptedBookingDetailsActivity : BaseActivity() {
    private var savedDetails: com.example.hiremydriver.trip.SavedBookingDetails? = null


    private enum class TripState { ACCEPTED, IN_PROGRESS, COMPLETED }
    private var currentState = TripState.ACCEPTED
    private lateinit var mapTripId: String

    private lateinit var txtBookingStatus: TextView
    private lateinit var cardTripInProgress: View
    private lateinit var cardTripCompleted: View
    private lateinit var btnStartService: MaterialButton
    private lateinit var btnViewReview: MaterialButton
    private lateinit var btnMessageCustomer: MaterialButton
    private lateinit var rootContainer: ViewGroup

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_driver_accepted_booking_details)
        if (intent.hasExtra(com.example.hiremydriver.trip.LocalTripStore.ID)) {
            savedDetails = com.example.hiremydriver.trip.SavedBookingDetails(this)
            savedDetails?.driverBooking()
            return
        }
        mapTripId = com.example.hiremydriver.trip.LocalTripStore(this).fromIntent(intent).id

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
                @Suppress("DEPRECATION")
                overridePendingTransition(0, 0)
            }
        })

        rootContainer = findViewById<ViewGroup>(android.R.id.content).getChildAt(0) as ViewGroup

        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        val btnViewMap = findViewById<View>(R.id.btnViewMap)

        txtBookingStatus = findViewById(R.id.txtBookingStatus)
        cardTripInProgress = findViewById(R.id.cardTripInProgress)
        cardTripCompleted = findViewById(R.id.cardTripCompleted)
        btnStartService = findViewById(R.id.btnStartService)
        btnViewReview = findViewById(R.id.btnViewReview)
        btnMessageCustomer = findViewById(R.id.btnMessageCustomer)

        val txtCustomerName = findViewById<TextView>(R.id.txtCustomerName)
        val txtCustomerPhone = findViewById<TextView>(R.id.txtCustomerPhone)
        val txtCustomerEmail = findViewById<TextView>(R.id.txtCustomerEmail)
        val txtPickupLocation = findViewById<TextView>(R.id.txtPickupLocation)
        val txtDropoffLocation = findViewById<TextView>(R.id.txtDropoffLocation)
        val txtBookingType = findViewById<TextView>(R.id.txtBookingType)
        val txtTotalAmount = findViewById<TextView>(R.id.txtTotalAmount)
        val imgCustomerProfile = findViewById<ImageView>(R.id.imgCustomerProfile)

        // Read intent data
        val name = intent.getStringExtra("CUSTOMER_NAME") ?: "Customer"
        val email = intent.getStringExtra("CUSTOMER_EMAIL") ?: "john.doe@email.com"
        val phone = intent.getStringExtra("CUSTOMER_PHONE") ?: "0917 123 4567"
        val pickup = intent.getStringExtra("PICKUP_LOCATION") ?: "Cebu City"
        val dropoff = intent.getStringExtra("DROPOFF_LOCATION") ?: "Lapu-Lapu City"
        val pkg = intent.getStringExtra("BOOKING_PACKAGE") ?: "By the Hour (4 hours)"
        val total = intent.getStringExtra("TOTAL_AMOUNT") ?: "₱2,000.00"
        val imgRes = intent.getIntExtra("CUSTOMER_IMAGE", R.drawable.mic)

        txtCustomerName.text = name
        txtCustomerPhone.text = phone
        txtCustomerEmail.text = email
        txtPickupLocation.text = pickup
        txtDropoffLocation.text = dropoff
        txtBookingType.text = pkg
        txtTotalAmount.text = total
        imgCustomerProfile.setImageResource(imgRes)

        btnBack.setOnClickListener {
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }

        btnViewMap.setOnClickListener { openDriverMap() }

        btnMessageCustomer.setOnClickListener {
            val intent = Intent(this, ChatConversationActivity::class.java)
            intent.putExtra("CHAT_NAME", name)
            intent.putExtra("CHAT_IMAGE", imgRes)
            startActivity(intent)
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }

        btnStartService.setOnClickListener { openDriverMap() }

        btnViewReview.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Customer review")
                .setMessage("No customer review is available for this sample booking yet.")
                .setPositiveButton("OK", null)
                .show()
        }

        currentState = TripState.entries.firstOrNull {
            it.name == (savedInstanceState?.getString("trip_state") ?: intent.getStringExtra("TRIP_STATE"))
        } ?: TripState.ACCEPTED

        renderTripState(animate = false)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("trip_state", currentState.name)
        super.onSaveInstanceState(outState)
    }

    private fun renderTripState(animate: Boolean = true) {
        if (animate) {
            TransitionManager.beginDelayedTransition(
                rootContainer,
                AutoTransition().apply {
                    duration = 350
                }
            )
        }

        val completed = currentState == TripState.COMPLETED
        val started = currentState == TripState.IN_PROGRESS

        cardTripInProgress.visibility = if (started) View.VISIBLE else View.GONE
        cardTripCompleted.visibility = if (completed) View.VISIBLE else View.GONE
        btnStartService.visibility = if (completed) View.GONE else View.VISIBLE
        btnViewReview.visibility = if (completed) View.VISIBLE else View.GONE
        btnStartService.text = if (started) "View ongoing trip" else "Open pickup map"

        txtBookingStatus.text = when (currentState) {
            TripState.ACCEPTED -> "Accepted"
            TripState.IN_PROGRESS -> "In Progress"
            TripState.COMPLETED -> "Completed"
        }

        txtBookingStatus.setBackgroundResource(
            if (started) R.drawable.bg_info_light_blue else R.drawable.bg_status_accepted_green
        )

        txtBookingStatus.setTextColor(
            Color.parseColor(if (started) "#1463D9" else "#00B85A")
        )
    }

    private fun openDriverMap() {
        startActivity(Intent(this, com.example.hiremydriver.maps.DriverTripMapActivity::class.java).apply {
            this@DriverAcceptedBookingDetailsActivity.intent.extras?.let { putExtras(it) }
            putExtra(com.example.hiremydriver.trip.LocalTripStore.ID, mapTripId)
        })
    }

    override fun onResume() {
        super.onResume()
        savedDetails?.let { it.driverBooking(); return }
        if (!::mapTripId.isInitialized) return
        val trip = com.example.hiremydriver.trip.LocalTripStore(this).get(mapTripId) ?: return
        currentState = when (trip.phase) {
            com.example.hiremydriver.trip.TripPhase.IN_PROGRESS -> TripState.IN_PROGRESS
            com.example.hiremydriver.trip.TripPhase.COMPLETED -> TripState.COMPLETED
            else -> TripState.ACCEPTED
        }
        renderTripState(animate = false)
        txtBookingStatus.text = com.example.hiremydriver.trip.TripUi.status(trip)
    }
}
