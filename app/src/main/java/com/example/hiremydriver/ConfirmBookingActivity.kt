package com.example.hiremydriver

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class ConfirmBookingActivity : BaseActivity() {
    private var submitting = false
    private var driverAcceptingBookings = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_confirm_booking)

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
        val btnConfirm = findViewById<MaterialButton>(R.id.btnConfirmAndBook)
        val confirmLabel = btnConfirm.text
        btnConfirm.isEnabled = false

        val txtDriverName = findViewById<TextView>(R.id.txtDriverName)
        val txtPriceHeader = findViewById<TextView>(R.id.txtPriceHeader)
        val txtRating = findViewById<TextView>(R.id.txtRating)
        val imgDriverSmall = findViewById<android.widget.ImageView>(R.id.imgDriverSmall)

        val txtDate = findViewById<TextView>(R.id.txtDate)
        val txtTime = findViewById<TextView>(R.id.txtTime)
        val txtDuration = findViewById<TextView>(R.id.txtDuration)
        val txtVehicle = findViewById<TextView>(R.id.txtVehicle)

        val txtCalculation = findViewById<TextView>(R.id.txtCalculation)
        val txtSubtotal = findViewById<TextView>(R.id.txtSubtotal)
        val txtTotalAmount = findViewById<TextView>(R.id.txtTotalAmount)

        // Get Data from Intent
        txtDriverName.text = intent.getStringExtra("DRIVER_NAME") ?: "Selected driver"
        txtPriceHeader.text = intent.getStringExtra("DRIVER_PRICE") ?: "Rate not set"
        txtRating.text = "No rating"
        val driverUid = intent.getStringExtra("DRIVER_UID").orEmpty()
        PeerProfilePhoto.bind(imgDriverSmall, driverUid, true,
            initialThumbnail = intent.getStringExtra("DRIVER_THUMBNAIL"))
        com.example.hiremydriver.trip.RatingDisplay.bind(txtRating, driverUid)

        txtDate.text = intent.getStringExtra("BOOKING_DATE") ?: "Not set"
        txtTime.text = intent.getStringExtra("BOOKING_TIME") ?: "Not set"
        txtDuration.text = intent.getStringExtra("BOOKING_DURATION") ?: "Not set"
        txtVehicle.text = intent.getStringExtra("VEHICLE_TYPE") ?: "Not set"
        val places = com.example.hiremydriver.trip.LocalTripStore.readPlaces(intent.getStringExtra("TRIP_DESTINATIONS"))
        val route = com.example.hiremydriver.trip.bookingRoute(
            intent.getStringExtra("PICKUP_LOCATION") ?: "Not set",
            places.takeIf { it.isNotEmpty() }?.map { it.label }
                ?: listOf(intent.getStringExtra("DESTINATION") ?: "Not set"))
        com.example.hiremydriver.trip.BookingRouteView.render(findViewById(R.id.reviewRouteTimeline), route, review = true)
        findViewById<TextView>(R.id.txtConfirmNotes).text = "Additional notes\n${intent.getStringExtra("ADDITIONAL_NOTES").orEmpty().ifBlank { "No additional notes" }}"

        val total = intent.getStringExtra("TOTAL_AMOUNT") ?: "Rate not set"
        val calculation = intent.getStringExtra("CALCULATION").orEmpty()

        txtSubtotal.text = total
        txtTotalAmount.text = total
        txtCalculation.text = calculation
        PublicDriverInfo.observe(this, intent.getStringExtra("DRIVER_UID").orEmpty()) { info ->
            driverAcceptingBookings = info.canRequestBooking
            btnConfirm.isEnabled = driverAcceptingBookings && !submitting
            btnConfirm.text = if (driverAcceptingBookings) confirmLabel else getString(R.string.driver_not_accepting_bookings)
            // Freeze the reviewed quote once submission begins; saved bookings keep their price.
            if (!submitting) {
                val hours = intent.getIntExtra("DURATION_HOURS", 4)
                val rate = BookingInput.hourlyRate(info.price)
                val amount = rate?.multiply(hours.toBigDecimal())?.let { "₱%,.2f".format(it) } ?: "Rate not set"
                val detail = rate?.let { "₱${it.stripTrailingZeros().toPlainString()} x $hours ${if (hours == 1) "hour" else "hours"}" }.orEmpty()
                txtDriverName.text = info.name
                txtPriceHeader.text = info.price
                txtSubtotal.text = amount
                txtTotalAmount.text = amount
                txtCalculation.text = detail
                intent.putExtra("DRIVER_NAME", info.name).putExtra("DRIVER_PRICE", info.price)
                    .putExtra("TOTAL_AMOUNT", amount).putExtra("CALCULATION", detail)
            }
        }

        // Listeners
        btnBack.setOnClickListener {
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }

        btnConfirm.setOnClickListener {
            if (submitting || !driverAcceptingBookings) return@setOnClickListener
            submitting = true
            val store = com.example.hiremydriver.trip.LocalTripStore(this)
            val trip = store.create(this.intent)
            btnConfirm.isEnabled = false
            com.example.hiremydriver.trip.FirebaseTripRepository(this, store).create(trip) { saved ->
                if (!isDestroyed && !isFinishing) {
                    submitting = saved
                    btnConfirm.isEnabled = driverAcceptingBookings && !submitting
                    if (saved) {
                        startActivity(Intent(this, BookingSuccessActivity::class.java)
                            .putExtra(com.example.hiremydriver.trip.LocalTripStore.ID, trip.id)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                        @Suppress("DEPRECATION")
                        overridePendingTransition(0, 0)
                        finish()
                    }
                }
            }
        }
    }
}
