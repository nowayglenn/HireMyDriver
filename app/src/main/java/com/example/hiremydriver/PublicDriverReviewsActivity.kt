package com.example.hiremydriver

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.example.hiremydriver.trip.*
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

/** Public feedback only: no reads of other customers' bookings or contact information. */
class PublicDriverReviewsActivity : BaseActivity() {
    private lateinit var cards: LinearLayout
    private lateinit var summary: TextView
    private var selected = 0
    private var records = emptyList<Pair<TripSession, BookingReview>>()
    private var listener: ListenerRegistration? = null
    private var notice: String? = "Loading reviews…"
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_customer_reviews)
        selected = savedInstanceState?.getInt("stars", 0) ?: 0
        findViewById<View>(R.id.bottomNavigation).visibility = View.GONE
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<TextView>(R.id.txtReviewsTitle).text = "Driver Reviews"
        summary = findViewById(R.id.txtReviewsIntro)
        summary.visibility = View.GONE
        val parent = findViewById<View>(R.id.review1).parent as LinearLayout
        parent.removeViews(1, parent.childCount - 1)
        val chips = ChipGroup(this).apply {
            isSingleSelection = true
            isSelectionRequired = true
        }
        listOf(0, 5, 4, 3, 2, 1).forEach { stars ->
            chips.addView(Chip(this).apply {
                id = View.generateViewId()
                text = if (stars == 0) "See All" else "$stars ${if (stars == 1) "Star" else "Stars"}"
                isCheckable = true
                isChecked = selected == stars
                val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
                setTextColor(android.content.res.ColorStateList(states,
                    intArrayOf(android.graphics.Color.WHITE, android.graphics.Color.parseColor("#061B41"))))
                chipBackgroundColor = android.content.res.ColorStateList(states,
                    intArrayOf(android.graphics.Color.parseColor("#FF7900"), android.graphics.Color.WHITE))
                chipStrokeWidth = 0f
                chipStrokeColor = android.content.res.ColorStateList.valueOf(android.graphics.Color.TRANSPARENT)
                checkedIconTint = android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE)
                setOnCheckedChangeListener { _, checked -> if (checked) { selected = stars; render() } }
            })
        }
        parent.addView(chips)
        cards = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        parent.addView(cards)
        render()
    }
    override fun onStart() {
        super.onStart()
        val uid = intent.getStringExtra("DRIVER_UID").orEmpty()
        if (uid.isBlank()) { finish(); return }
        listener = FirebaseFirestore.getInstance().collection("reviews").whereEqualTo("driverUid", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    notice = "Could not refresh reviews. Check your connection and reopen this screen."
                } else {
                    notice = null
                    records = snapshot?.documents.orEmpty().mapNotNull { doc ->
                        val data = doc.data.orEmpty()
                        val date = (data["createdAt"] as? Timestamp)?.toDate()?.time ?: 0L
                        val review = BookingReview.fromRecord(doc.id, data, date) ?: return@mapNotNull null
                        val trip = TripSession(doc.id, intent.getStringExtra("DRIVER_NAME") ?: "Driver",
                            (data["customerName"] as? String)?.takeIf { it.isNotBlank() } ?: "Customer",
                            "", "", customerUid = data["customerUid"] as? String ?: "", driverUid = uid)
                        trip to review
                    }.sortedByDescending { it.second.submittedAt }
                }
                render()
            }
    }
    private fun render() {
        if (!::cards.isInitialized) return
        val matching = records.filter { selected == 0 || it.second.rating == selected }
        cards.removeAllViews()
        if (notice != null || matching.isEmpty()) cards.addView(TextView(this).apply {
            text = notice ?: if (records.isEmpty()) "No reviews yet." else "No $selected-star reviews yet."
            textSize = 14f
            setTextColor(android.graphics.Color.parseColor("#667085"))
            setPadding(16, 24, 16, 24)
        })
        matching.forEach { (trip, review) ->
            val card = ReviewCard.create(cards, trip, review, showCustomer = true, driverSession = false)
            val privateDetails = card.findViewById<View>(R.id.txtReviewRoute).parent as View
            privateDetails.visibility = View.GONE
            val body = privateDetails.parent as LinearLayout
            body.getChildAt(body.indexOfChild(privateDetails) - 1).visibility = View.GONE
            cards.addView(card)
        }
    }
    override fun onStop() { listener?.remove(); listener = null; super.onStop() }
    override fun onSaveInstanceState(outState: Bundle) { outState.putInt("stars", selected); super.onSaveInstanceState(outState) }
}
