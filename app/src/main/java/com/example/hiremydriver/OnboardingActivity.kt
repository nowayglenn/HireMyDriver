package com.example.hiremydriver

import android.app.ActivityOptions
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.button.MaterialButton

data class OnboardingPage(
    val title: String,
    val description: String,
    @DrawableRes val imageRes: Int
)

class OnboardingActivity : BaseActivity() {

    override fun backgroundBehindSystemBars(): Boolean = true

    private lateinit var viewPager: ViewPager2
    private lateinit var btnNext: MaterialButton
    private lateinit var btnSkip: MaterialButton

    private lateinit var dot1: View
    private lateinit var dot2: View
    private lateinit var dot3: View
    private lateinit var dot4: View
    private var navigatingToMain = false

    private val pages = listOf(
        OnboardingPage(
            title = "Find the Right Driver",
            description = "Book a trusted driver based on your location, schedule, and travel needs.",
            imageRes = R.drawable.onboard_1
        ),
        OnboardingPage(
            title = "Verified Driver Profiles",
            description = "View driver information, ratings, experience, and verification details before hiring.",
            imageRes = R.drawable.onboard_2
        ),
        OnboardingPage(
            title = "Easy Driver Booking",
            description = "Set your pickup location, destination, date, and time, then choose the driver that fits your needs.",
            imageRes = R.drawable.onboard_3
        ),
        OnboardingPage(
            title = "Travel with Confidence",
            description = "Connect with your chosen driver and manage your booking easily through the app.",
            imageRes = R.drawable.onboard_4
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check if onboarding is already completed
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        if (prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)) {
            navigateToAppStart(animate = false)
            return
        }

        setContentView(R.layout.activity_onboarding)

        viewPager = findViewById(R.id.viewPagerOnboarding)
        btnNext = findViewById(R.id.btnNext)
        btnSkip = findViewById(R.id.btnSkip)

        dot1 = findViewById(R.id.dot1)
        dot2 = findViewById(R.id.dot2)
        dot3 = findViewById(R.id.dot3)
        dot4 = findViewById(R.id.dot4)

        // Setup ViewPager2 Adapter
        viewPager.adapter = OnboardingAdapter(pages)

        // ViewPager Page Change Listener
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateUIForPage(position)
            }
        })

        // Next Button Listener
        btnNext.setOnClickListener {
            val current = viewPager.currentItem
            if (current < pages.size - 1) {
                viewPager.currentItem = current + 1
            } else {
                completeOnboarding()
            }
        }

        // Skip Button Listener
        btnSkip.setOnClickListener {
            completeOnboarding()
        }
    }

    private fun updateUIForPage(position: Int) {
        val isLastPage = position == pages.size - 1

        // Button text
        btnNext.text = if (isLastPage) "Get Started" else "Next"

        // Update Dots Indicator
        val dots = listOf(dot1, dot2, dot3, dot4)
        dots.forEachIndexed { index, dot ->
            if (index == position) {
                dot.layoutParams.width = (24 * resources.displayMetrics.density).toInt()
                dot.setBackgroundResource(R.drawable.bg_indicator_active)
            } else {
                dot.layoutParams.width = (8 * resources.displayMetrics.density).toInt()
                dot.setBackgroundResource(R.drawable.bg_indicator_inactive)
            }
            dot.requestLayout()
        }
    }

    private fun completeOnboarding() {
        if (navigatingToMain) return
        btnNext.isEnabled = false
        btnSkip.isEnabled = false
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, true).apply()
        navigateToAppStart()
    }

    private fun navigateToAppStart(animate: Boolean = true) {
        if (navigatingToMain) return
        navigatingToMain = true
        val intent = Intent(this, MainActivity::class.java)
        val options = ActivityOptions.makeCustomAnimation(
            this,
            if (animate) R.anim.nav_fade_in else 0,
            if (animate) R.anim.nav_fade_out else 0
        )
        startActivity(intent, options.toBundle())
        // Remove onboarding from Back navigation while keeping the transition in this task.
        finish()
    }

    companion object {
        const val PREFS_NAME = "hire_my_driver_prefs"
        const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
    }

    private inner class OnboardingAdapter(private val items: List<OnboardingPage>) :
        RecyclerView.Adapter<OnboardingAdapter.OnboardingViewHolder>() {

        inner class OnboardingViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val imgVisual: ImageView = view.findViewById(R.id.imgOnboardingVisual)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OnboardingViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_onboarding_page, parent, false)
            return OnboardingViewHolder(view)
        }

        override fun onBindViewHolder(holder: OnboardingViewHolder, position: Int) {
            val item = items[position]
            holder.imgVisual.setImageResource(item.imageRes)
            // The text is baked into the artwork; expose it once to screen readers.
            holder.imgVisual.contentDescription = "${position + 1} of ${items.size}. ${item.title}. ${item.description}"
        }

        override fun getItemCount(): Int = items.size
    }
}
