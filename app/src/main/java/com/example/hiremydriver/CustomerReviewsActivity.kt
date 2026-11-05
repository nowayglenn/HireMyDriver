package com.example.hiremydriver

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.widget.PopupMenu
import android.widget.TextView
import com.example.hiremydriver.trip.*

/** Saved reviews bound to the original review list and card templates. */
class CustomerReviewsActivity : BaseActivity() {
    companion object {
        const val EXTRA_HIDE_BOTTOM_NAVIGATION = "HIDE_REVIEW_BOTTOM_NAVIGATION"
    }
    private val driverMode by lazy { intent.getBooleanExtra("DRIVER_REVIEW", false) }
    private val tripId by lazy { intent.getStringExtra(LocalTripStore.ID) }
    private lateinit var container: LinearLayout
    private lateinit var reviews: LocalReviewStore
    private var reviewMenu: PopupMenu? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_customer_reviews)
        findViewById<View>(R.id.bottomNavigation).visibility = View.GONE
        reviews = LocalReviewStore(this)
        container = findViewById<View>(R.id.review1).parent as LinearLayout
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<TextView>(R.id.txtReviewsTitle).text = if (driverMode) {
            if (tripId == null) "Customer Reviews" else "Customer Review"
        } else if (tripId != null) "My Review" else "My Reviews"
        findViewById<TextView>(R.id.txtReviewsIntro).text = if (driverMode) {
            if (tripId == null) "Customer feedback for completed bookings."
            else "Customer feedback for this booking."
        } else if (tripId != null) "Your review for this booking." else "Here are the drivers you've reviewed."
        navigate(R.id.navHome, if (driverMode) DriverHomeActivity::class.java else HomeActivity::class.java)
        navigate(R.id.navBookings, if (driverMode) DriverBookingActivity::class.java else MyBookingActivity::class.java)
        navigate(R.id.navMessages, if (driverMode) DriverMessageActivity::class.java else MessagesActivity::class.java)
        navigate(R.id.navProfile, if (driverMode) DriverProfileActivity::class.java else ProfileActivity::class.java)
        reviews.observe(this, ::renderReviews)
    }

    private fun navigate(id: Int, target: Class<*>) {
        findViewById<View>(id).setOnClickListener {
            startActivity(Intent(this, target).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        renderReviews()
    }

    private fun renderReviews() {
        if (!::container.isInitialized) return
        reviewMenu?.dismiss()
        // Keep the original introductory label and replace preview cards only.
        if (container.childCount > 1) container.removeViews(1, container.childCount - 1)
        val records = reviews.all().filter { tripId == null || it.first.id == tripId }
        if (records.isEmpty()) {
            container.addView(TextView(this).apply {
                text = if (driverMode) {
                    if (tripId == null) "No customer reviews yet. Feedback will appear here after a completed booking is reviewed."
                    else "No customer review has been submitted for this booking yet."
                } else "No reviews yet. You can rate a driver after the service is completed."
                textSize = 14f
                setTextColor(android.graphics.Color.parseColor("#667085"))
                val padding = (16 * resources.displayMetrics.density).toInt()
                setPadding(padding, padding, padding, padding)
            })
        }
        records.forEach { (trip, review) ->
            val card = ReviewCard.create(container, trip, review, showCustomer = driverMode)
            if (!driverMode && trip.driverUid.isNotBlank()) {
                card.isFocusable = true
                card.setOnClickListener {
                    startActivity(Intent(this, DriverDetailsActivity::class.java)
                        .putExtra("DRIVER_UID", trip.driverUid)
                        .putExtra("DRIVER_NAME", trip.driverName)
                        .putExtra("DRIVER_VEHICLE", trip.vehicle)
                        .putExtra(DriverDetailsActivity.EXTRA_BOOK_AGAIN, true))
                }
            }
            card.findViewById<View>(R.id.btnMoreReview).apply {
                visibility = if (driverMode) View.GONE else View.VISIBLE
                contentDescription = "Review options"
                setOnClickListener {
                    reviewMenu?.dismiss()
                    PopupMenu(this@CustomerReviewsActivity, this, android.view.Gravity.END, 0, R.style.ReviewOptionsPopup).apply {
                        reviewMenu = this
                        menu.add("Edit Review")
                        setOnMenuItemClickListener {
                            startActivity(Intent(this@CustomerReviewsActivity, RateDriverActivity::class.java).putExtra(LocalTripStore.ID, trip.id))
                            true
                        }
                        show()
                    }
                }
            }
            container.addView(card)
        }
    }
    override fun onStop() { reviewMenu?.dismiss(); reviewMenu = null; super.onStop() }
}

