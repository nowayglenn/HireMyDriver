package com.example.hiremydriver

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import com.google.android.material.button.MaterialButton

class DriverDetailsActivity : BaseActivity() {
    companion object {
        const val EXTRA_BOOK_AGAIN = "BOOK_AGAIN_FROM_REVIEW"
    }
    private var serviceAreasExpanded = false
    private var displayedServiceAreas = emptyList<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_driver_details)
        serviceAreasExpanded = savedInstanceState?.getBoolean("service_areas_expanded") ?: false
        findViewById<TextView>(R.id.btnViewServiceAreas).setOnClickListener {
            serviceAreasExpanded = !serviceAreasExpanded
            renderServiceAreas()
        }

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
        val btnBookNow = findViewById<MaterialButton>(R.id.btnBookNow)
        val bookingLabel = if (intent.getBooleanExtra(EXTRA_BOOK_AGAIN, false))
            R.string.btn_book_again else R.string.btn_book_now
        btnBookNow.setText(bookingLabel)
        btnBookNow.isEnabled = false

        val txtDriverName = findViewById<TextView>(R.id.txtDriverName)
        val txtRating = findViewById<TextView>(R.id.txtRating)
        val txtReviewCount = findViewById<TextView>(R.id.txtReviewCount)
        val txtExperience = findViewById<TextView>(R.id.txtExperienceLarge)
        val txtVehicleType = findViewById<TextView>(R.id.txtVehicleType)
        val txtAboutMe = findViewById<TextView>(R.id.txtAboutMe)
        val txtPriceDetail = findViewById<TextView>(R.id.txtPriceDetail)
        val txtAvailability = findViewById<TextView>(R.id.txtAvailabilityTime)
        val txtLanguages = findViewById<TextView>(R.id.txtLanguages)
        val imgDriverLarge = findViewById<android.widget.ImageView>(R.id.imgDriverLarge)

        // Get data from intent
        val name = intent.getStringExtra("DRIVER_NAME") ?: "Driver"
        val rating = intent.getStringExtra("DRIVER_RATING") ?: "No ratings yet"
        val experience = intent.getStringExtra("DRIVER_EXP") ?: "Not provided"
        val vehicle = intent.getStringExtra("DRIVER_VEHICLE") ?: "Not provided"
        val price = intent.getStringExtra("DRIVER_PRICE") ?: "Rate not set"
        val imageResId = intent.getIntExtra("DRIVER_IMAGE", R.drawable.no_profile)
        val demoRecommendation = intent.getBooleanExtra("DEMO_RECOMMENDATION", false)

        // Set data
        txtDriverName.text = name
        txtRating.text = rating
        txtExperience.text = experience
        txtVehicleType.text = vehicle
        txtPriceDetail.text = price
        imgDriverLarge.setImageResource(imageResId)
        RegisteredDriverDirectory.image(imgDriverLarge, intent.getStringExtra("DRIVER_THUMBNAIL").orEmpty())

        // Static data for now (to match design)
        txtReviewCount.text = "(0 reviews)"
        com.example.hiremydriver.trip.RatingDisplay.bind(txtRating, intent.getStringExtra("DRIVER_UID").orEmpty(), countView = txtReviewCount)
        PeerProfilePhoto.bind(imgDriverLarge, intent.getStringExtra("DRIVER_UID").orEmpty(), true, initialThumbnail = intent.getStringExtra("DRIVER_THUMBNAIL"))
        showServiceAreas(intent.getStringExtra("DRIVER_LOCATION").orEmpty().split(","))
        txtAboutMe.text = intent.getStringExtra("DRIVER_BIO")?.takeIf { it.isNotBlank() } ?: "Not provided"
        txtAvailability.text = "Loading schedule…"
        txtLanguages.text = "Not provided"
        val driverUid = intent.getStringExtra("DRIVER_UID").orEmpty()
        txtReviewCount.setOnClickListener {
            if (driverUid.isNotBlank()) startActivity(Intent(this, PublicDriverReviewsActivity::class.java)
                .putExtra("DRIVER_UID", driverUid).putExtra("DRIVER_NAME", txtDriverName.text.toString()))
        }
        PublicDriverInfo.observe(this, driverUid, unavailable = {
            if (!isFinishing && !isDestroyed) {
                Toast.makeText(this, "This driver is no longer available.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }) { info ->
            btnBookNow.isEnabled = info.canRequestBooking
            btnBookNow.setText(if (info.canRequestBooking) bookingLabel else R.string.driver_not_accepting_bookings)
            txtLanguages.text = info.languages.joinToString(" · ").ifBlank { "Not provided" }
            txtDriverName.text = info.name
            txtExperience.text = info.experience.ifBlank { "Not provided" }.let { if (info.experience.isBlank()) it else "$it years experience" }
            txtVehicleType.text = info.vehicleLabel
            txtPriceDetail.text = info.price
            showServiceAreas(info.areas)
            findViewById<TextView>(R.id.txtRegisteredAddress).text = info.address.ifBlank { "Address not available yet" }
            txtAboutMe.text = info.bio.ifBlank { "Not provided" }
            showSchedule(info)
        }

        if (demoRecommendation) {
            txtReviewCount.text = "(Demo rating)"
            txtAboutMe.text = "Demo driver profile. Sample information only; bookings and messages are not available for this driver."
            txtAvailability.text = "Check your selected trip filters"
            txtLanguages.text = "Not provided"
        }

        // Listeners
        btnBack.setOnClickListener {
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }

        btnBookNow.setOnClickListener {
            if (!intent.hasExtra("DRIVER_UID")) {
                Toast.makeText(this, "Please select a registered driver first.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            if (demoRecommendation) {
                Toast.makeText(this, "Demo drivers cannot receive bookings yet.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val intent = Intent(this, BookDriverActivity::class.java)
            intent.putExtra("DRIVER_UID", this.intent.getStringExtra("DRIVER_UID"))
            intent.putExtra("DRIVER_THUMBNAIL", this.intent.getStringExtra("DRIVER_THUMBNAIL"))
            intent.putExtra("DRIVER_NAME", txtDriverName.text.toString())
            intent.putExtra("DRIVER_RATING", txtRating.text.toString())
            intent.putExtra("DRIVER_PRICE", txtPriceDetail.text.toString())
            intent.putExtra("DRIVER_IMAGE", imageResId)
            startActivity(intent)
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    private var displayedSchedule: Pair<String, List<Pair<String, String>>>? = null

    private fun showServiceAreas(areas: List<String>) {
        displayedServiceAreas = areas.map(String::trim).filter(String::isNotEmpty).distinct()
        renderServiceAreas()
    }

    private fun renderServiceAreas() {
        val text = findViewById<TextView>(R.id.txtLocationArea)
        val toggle = findViewById<TextView>(R.id.btnViewServiceAreas)
        val expandable = displayedServiceAreas.size > 3
        text.maxLines = if (expandable && !serviceAreasExpanded) 3 else Int.MAX_VALUE
        text.ellipsize = if (expandable && !serviceAreasExpanded) android.text.TextUtils.TruncateAt.END else null
        text.text = when {
            displayedServiceAreas.isEmpty() -> getString(R.string.profile_service_area_unset)
            !expandable -> displayedServiceAreas.joinToString(", ")
            serviceAreasExpanded -> displayedServiceAreas.joinToString(", ")
            else -> displayedServiceAreas.take(3).joinToString(", ")
        }
        toggle.visibility = if (expandable) android.view.View.VISIBLE else android.view.View.GONE
        toggle.text = if (serviceAreasExpanded) getString(R.string.profile_service_areas_less)
            else getString(R.string.profile_service_areas_all, displayedServiceAreas.size)
        androidx.core.view.ViewCompat.setStateDescription(toggle,
            getString(if (serviceAreasExpanded) R.string.profile_service_areas_expanded else R.string.profile_service_areas_collapsed))
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("service_areas_expanded", serviceAreasExpanded)
        super.onSaveInstanceState(outState)
    }

    private fun showSchedule(info: PublicDriverInfo) {
        val state = info.scheduleLabel to info.scheduleDays
        if (displayedSchedule == state) return
        displayedSchedule = state
        val rows = findViewById<android.widget.LinearLayout>(R.id.driverScheduleRows)
        val hasSchedule = info.scheduleDays.isNotEmpty()
        findViewById<TextView>(R.id.txtAvailabilityTime).text =
            if (hasSchedule) info.scheduleLabel else "Schedule not set"
        rows.removeAllViews()
        info.scheduleDays.forEach { (day, hours) ->
            val row = layoutInflater.inflate(R.layout.item_driver_schedule_day, rows, false)
            row.findViewById<TextView>(R.id.scheduleDay).text = day
            row.findViewById<TextView>(R.id.scheduleHours).text = hours
            rows.addView(row)
        }
        rows.visibility = if (hasSchedule) android.view.View.VISIBLE else android.view.View.GONE
    }

}
