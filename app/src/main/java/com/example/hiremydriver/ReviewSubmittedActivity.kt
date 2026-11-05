package com.example.hiremydriver

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class ReviewSubmittedActivity : BaseActivity() {
    private var successAnimation: android.animation.AnimatorSet? = null
    override fun backgroundBehindSystemBars(): Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_review_submitted)
        findViewById<android.view.View>(R.id.bottomNavigation).visibility = android.view.View.GONE
        if (savedInstanceState == null) animateSuccess()

        // Return to bookings after a saved review.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                navigateTo(MyBookingActivity::class.java)
            }
        })

        // Find Views
        val btnBackToBookings = findViewById<MaterialButton>(R.id.btnBackToBookings)
        val btnViewMyReviews = findViewById<MaterialButton>(R.id.btnViewMyReviews)

        // Listeners
        btnBackToBookings.setOnClickListener {
            navigateTo(MyBookingActivity::class.java)
        }

        btnViewMyReviews.setOnClickListener {
            startActivity(Intent(this, CustomerReviewsActivity::class.java)
                .putExtra(CustomerReviewsActivity.EXTRA_HIDE_BOTTOM_NAVIGATION, true)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
            finish()
        }

        // Bottom Nav (Mock)
        findViewById<LinearLayout>(R.id.navHome).setOnClickListener { navigateTo(HomeActivity::class.java) }
        findViewById<LinearLayout>(R.id.navBookings).setOnClickListener { navigateTo(MyBookingActivity::class.java) }
        findViewById<LinearLayout>(R.id.navMessages).setOnClickListener { navigateTo(MessagesActivity::class.java) }
        findViewById<LinearLayout>(R.id.navProfile).setOnClickListener { navigateTo(ProfileActivity::class.java) }
    }

    private fun animateSuccess() {
        if (android.os.Build.VERSION.SDK_INT >= 26 && !android.animation.ValueAnimator.areAnimatorsEnabled()) return
        val halo = findViewById<android.view.View>(R.id.reviewSuccessHalo)
        val check = findViewById<android.view.View>(R.id.reviewSuccessCheck)
        val title = findViewById<android.view.View>(R.id.reviewSuccessTitle)
        val message = findViewById<android.view.View>(R.id.reviewSuccessMessage)
        val rise = 10 * resources.displayMetrics.density
        halo.alpha = 0f; halo.scaleX = 0.65f; halo.scaleY = 0.65f
        check.alpha = 0f; check.scaleX = 0.65f; check.scaleY = 0.65f
        listOf(title, message).forEach { it.alpha = 0f; it.translationY = rise }
        fun property(view: android.view.View, name: String, from: Float, to: Float, duration: Long, delay: Long = 0) =
            android.animation.ObjectAnimator.ofFloat(view, name, from, to).apply {
                this.duration = duration; startDelay = delay
                interpolator = android.view.animation.DecelerateInterpolator()
            }
        successAnimation = android.animation.AnimatorSet().apply {
            playTogether(
                property(halo, "scaleX", 0.65f, 1f, 600), property(halo, "scaleY", 0.65f, 1f, 600),
                property(halo, "alpha", 0f, 0.2f, 450),
                property(check, "scaleX", 0.65f, 1f, 450), property(check, "scaleY", 0.65f, 1f, 450),
                property(check, "alpha", 0f, 1f, 300),
                property(title, "alpha", 0f, 1f, 320, 100), property(title, "translationY", rise, 0f, 320, 100),
                property(message, "alpha", 0f, 1f, 320, 160), property(message, "translationY", rise, 0f, 320, 160)
            )
            start()
        }
    }

    override fun onStop() {
        successAnimation?.end()
        successAnimation = null
        super.onStop()
    }

    private fun navigateTo(activityClass: Class<*>) {
        val intent = Intent(this, activityClass)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        startActivity(intent)
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
        finish()
    }
}
