package com.example.hiremydriver

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import com.google.android.material.button.MaterialButton

class BookingDetailsActivity : BaseActivity() {
    private var savedDetails: com.example.hiremydriver.trip.SavedBookingDetails? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_booking_details)
        if (intent.hasExtra(com.example.hiremydriver.trip.LocalTripStore.ID)) {
            savedDetails = com.example.hiremydriver.trip.SavedBookingDetails(this)
            savedDetails?.customer()
            return
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
        val bannerStatus = findViewById<LinearLayout>(R.id.bannerStatus)
        val imgStatusIcon = findViewById<ImageView>(R.id.imgStatusIcon)
        val txtStatusTitle = findViewById<TextView>(R.id.txtStatusTitle)
        val txtStatusSubtitle = findViewById<TextView>(R.id.txtStatusSubtitle)

        val btnLeft = findViewById<MaterialButton>(R.id.btnLeft)
        val btnRight = findViewById<MaterialButton>(R.id.btnRight)

        val status = intent.getStringExtra("EXTRA_STATUS") ?: "PENDING"

        // Setup UI based on Status
        setupStatusUI(status, bannerStatus, imgStatusIcon, txtStatusTitle, txtStatusSubtitle, btnLeft, btnRight)

        // Listeners
        btnBack.setOnClickListener {
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    private fun setupStatusUI(
        status: String,
        banner: LinearLayout,
        icon: ImageView,
        title: TextView,
        subtitle: TextView,
        leftBtn: MaterialButton,
        rightBtn: MaterialButton
    ) {
        // Default action for Left Button is just to go back
        leftBtn.setOnClickListener {
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }

        when (status) {
            "PENDING" -> {
                banner.setBackgroundResource(R.drawable.bg_important_box)
                icon.setImageResource(R.drawable.ic_schedule)
                icon.imageTintList = ColorStateList.valueOf(Color.parseColor("#1463D9"))
                icon.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#E0E9F5"))

                title.text = "Booking Pending"
                title.setTextColor(Color.parseColor("#1463D9"))
                subtitle.text = "Waiting for the driver to accept your request."
                subtitle.setTextColor(Color.parseColor("#1463D9"))

                leftBtn.text = "Cancel Request"
                leftBtn.setTextColor(Color.parseColor("#F04438"))
                leftBtn.strokeColor = ColorStateList.valueOf(Color.parseColor("#F04438"))

                rightBtn.text = "Contact Support"
                rightBtn.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#061B41"))
            }
            "ACCEPTED" -> {
                banner.setBackgroundResource(R.drawable.bg_info_light_blue)
                icon.setImageResource(R.drawable.ic_car_all)
                icon.imageTintList = ColorStateList.valueOf(Color.parseColor("#1463D9"))
                icon.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#E0E9F5"))

                title.text = "Booking Accepted"
                title.setTextColor(Color.parseColor("#1463D9"))
                subtitle.text = "The driver is on the way to your location."
                subtitle.setTextColor(Color.parseColor("#1463D9"))

                val driverName = intent.getStringExtra("DRIVER_NAME") ?: "Selected driver"
                findViewById<TextView>(R.id.txtDriverName).text = driverName
                findViewById<TextView>(R.id.txtRating).text = "★ 4.9"
                findViewById<TextView>(R.id.txtExperience).text = "10 years experience"
                findViewById<TextView>(R.id.txtVehicle).text = "Sedan, SUV"
                findViewById<ImageView>(R.id.imgDriverThumb).setImageResource(R.drawable.no_profile)

                leftBtn.text = "Track Trip"
                leftBtn.setOnClickListener {
                    val intent = Intent(this, TripInProgressActivity::class.java)
                    this.intent.extras?.let { intent.putExtras(it) }
                    intent.putExtra("DRIVER_NAME", driverName)
                    intent.putExtra("PICKUP_LOCATION", findViewById<TextView>(R.id.txtTripPickup).text.toString())
                    intent.putExtra("DESTINATION", findViewById<TextView>(R.id.txtTripDestination).text.toString())
                    startActivity(intent)
                    @Suppress("DEPRECATION")
                    overridePendingTransition(0, 0)
                }

                rightBtn.text = "Message Driver"
                rightBtn.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FF7900"))
                rightBtn.setOnClickListener {
                    val intent = Intent(this, ChatConversationActivity::class.java)
                    intent.putExtra("CHAT_NAME", driverName)
                    intent.putExtra("DRIVER_NAME", driverName)
                    intent.putExtra("DRIVER_IMAGE", R.drawable.no_profile)
                    startActivity(intent)
                    @Suppress("DEPRECATION")
                    overridePendingTransition(0, 0)
                }
            }
            "CANCELLED" -> {
                banner.setBackgroundResource(R.drawable.bg_status_cancelled)
                icon.setImageResource(R.drawable.ic_help)
                icon.imageTintList = ColorStateList.valueOf(Color.parseColor("#F04438"))
                icon.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FEE4E2"))

                title.text = "Booking Cancelled"
                title.setTextColor(Color.parseColor("#F04438"))
                subtitle.text = "This booking was cancelled on May 5, 2025."
                subtitle.setTextColor(Color.parseColor("#F04438"))

                leftBtn.text = "Back to Home"
                leftBtn.setOnClickListener {
                    startActivity(Intent(this, HomeActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    })
                    finish()
                }
                rightBtn.text = "Rebook Driver"
                rightBtn.setOnClickListener { rebookDriver() }
                rightBtn.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#061B41"))
            }
            "COMPLETED" -> {
                banner.setBackgroundResource(R.drawable.bg_status_banner)
                icon.setImageResource(R.drawable.ic_check_circle)
                icon.imageTintList = ColorStateList.valueOf(Color.parseColor("#159447"))
                icon.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#E7F6EC"))

                title.text = "Booking Completed"
                title.setTextColor(Color.parseColor("#159447"))
                subtitle.text = "This trip was completed on May 13, 2025."
                subtitle.setTextColor(Color.parseColor("#159447"))

                leftBtn.text = "Book Again"
                leftBtn.setOnClickListener { rebookDriver() }
                rightBtn.text = "Rate Driver"
                rightBtn.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FF7900"))
                rightBtn.setOnClickListener {
                    val intent = Intent(this, RateDriverActivity::class.java)
                    startActivity(intent)
                    @Suppress("DEPRECATION")
                    overridePendingTransition(0, 0)
                }
            }
        }
    }

    private fun rebookDriver() {
        startActivity(Intent(this, BookDriverActivity::class.java).apply {
            this@BookingDetailsActivity.intent.extras?.let { putExtras(it) }
            putExtra("DRIVER_NAME", findViewById<TextView>(R.id.txtDriverName).text.toString())
        })
    }
    override fun onResume() {
        super.onResume()
        savedDetails?.customer()
    }
}
