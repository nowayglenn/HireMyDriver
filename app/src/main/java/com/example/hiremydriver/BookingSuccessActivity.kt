package com.example.hiremydriver

import android.content.Intent
import android.os.Bundle
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import com.google.android.material.button.MaterialButton

class BookingSuccessActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_booking_success)

        // Animation
        val iconContainer = findViewById<FrameLayout>(R.id.iconContainer)
        iconContainer.scaleX = 0f
        iconContainer.scaleY = 0f
        iconContainer.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(800)
            .setInterpolator(OvershootInterpolator())
            .start()

        // Back Behavior - Go to Home
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                navigateToHome()
            }
        })

        // Find Views
        val btnViewBookings = findViewById<MaterialButton>(R.id.btnViewBookings)
        val btnBackHome = findViewById<MaterialButton>(R.id.btnBackHome)

        // Listeners
        btnViewBookings.setOnClickListener {
            val intent = Intent(this, MyBookingActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
            finish()
        }

        btnBackHome.setOnClickListener {
            navigateToHome()
        }
    }

    private fun navigateToHome() {
        val intent = Intent(this, HomeActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
        finish()
    }
}
