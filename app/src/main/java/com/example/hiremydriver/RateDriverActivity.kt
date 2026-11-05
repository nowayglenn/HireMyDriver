package com.example.hiremydriver

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import android.text.InputFilter
import com.example.hiremydriver.trip.*
import androidx.activity.OnBackPressedCallback
import com.google.android.material.button.MaterialButton

class RateDriverActivity : BaseActivity() {

    private lateinit var stars: List<ImageView>
    private lateinit var txtRatingLabel: TextView
    private lateinit var edtReview: EditText
    private lateinit var txtCharCount: TextView
    private lateinit var tags: List<TextView>

    private var currentRating = 5
    private val tripId by lazy { intent.getStringExtra(LocalTripStore.ID).orEmpty() }
    private lateinit var reviews: LocalReviewStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rate_driver)
        val trip = LocalTripStore(this).get(tripId)
        if (trip == null || !trip.localBooking || trip.phase != TripPhase.COMPLETED) {
            Toast.makeText(this, "Open a completed booking to rate its driver.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        reviews = LocalReviewStore(this)
        findViewById<TextView>(R.id.txtDriverName).text = trip.driverName
        PeerProfilePhoto.bind(findViewById(R.id.imgRateDriver), trip.driverUid, true,
            nameView = findViewById(R.id.txtDriverName))
        findViewById<TextView>(R.id.txtVehicle).text = trip.vehicle
        findViewById<TextView>(R.id.txtExperience).text = listOf(trip.date, trip.time).filter { it.isNotBlank() }.joinToString(" · ")

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
        val btnSubmit = findViewById<MaterialButton>(R.id.btnSubmitReview)
        txtRatingLabel = findViewById(R.id.txtRatingLabel)
        edtReview = findViewById(R.id.edtReview)
        edtReview.filters = arrayOf(InputFilter.LengthFilter(500))
        txtCharCount = findViewById(R.id.txtCharCount)

        stars = listOf(
            findViewById(R.id.star1),
            findViewById(R.id.star2),
            findViewById(R.id.star3),
            findViewById(R.id.star4),
            findViewById(R.id.star5)
        )

        tags = listOf(
            findViewById(R.id.tagPunctual),
            findViewById(R.id.tagProfessional),
            findViewById(R.id.tagSafeDriving),
            findViewById(R.id.tagCommunication),
            findViewById(R.id.tagFriendly)
        )

        // Setup Star Selection
        stars.forEachIndexed { index, imageView ->
            imageView.setOnClickListener {
                updateStars(index + 1)
            }
        }

        // Setup Tag Selection
        tags.forEach { tag ->
            tag.setOnClickListener {
                toggleTag(tag)
            }
        }

        // Character Counter
        edtReview.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                txtCharCount.text = "${s?.length ?: 0}/500"
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        val existing = reviews.get(tripId)
        updateStars(savedInstanceState?.getInt("review_rating") ?: existing?.rating ?: 5)
        val selectedTags = savedInstanceState?.getStringArrayList("review_tags") ?: existing?.tags.orEmpty()
        tags.filter { it.text.toString() in selectedTags }.forEach { toggleTag(it) }
        edtReview.setText(savedInstanceState?.getString("review_comment") ?: existing?.comment.orEmpty())
        if (existing != null) btnSubmit.text = "Update Review"

        // Listeners
        btnBack.setOnClickListener {
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }

        btnSubmit.setOnClickListener {
            btnSubmit.isEnabled = false
            reviews.saveConfirmed(tripId, currentRating, edtReview.text.toString(),
                tags.filter { it.tag == "selected" }.map { it.text.toString() }) { saved ->
            if (isFinishing || isDestroyed) return@saveConfirmed
            if (!saved) {
                btnSubmit.isEnabled = true
                Toast.makeText(this, reviews.saveError, Toast.LENGTH_LONG).show()
                return@saveConfirmed
            }
            val intent = Intent(this, ReviewSubmittedActivity::class.java)
                .putExtra(LocalTripStore.ID, tripId)
            startActivity(intent)
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
            finish()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (!::edtReview.isInitialized) return
        outState.putInt("review_rating", currentRating)
        outState.putString("review_comment", edtReview.text.toString())
        outState.putStringArrayList("review_tags", ArrayList(tags.filter { it.tag == "selected" }.map { it.text.toString() }))
    }

    private fun updateStars(rating: Int) {
        currentRating = rating
        stars.forEachIndexed { index, imageView ->
            if (index < rating) {
                imageView.setImageResource(R.drawable.ic_star)
                imageView.imageTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#FF7900"))
            } else {
                imageView.setImageResource(R.drawable.ic_star_border)
                imageView.imageTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#667085"))
            }
        }

        val labels = arrayOf("Terrible", "Poor", "Average", "Good", "Excellent")
        txtRatingLabel.text = "$rating.0 - ${labels[rating - 1]}"
    }

    private fun toggleTag(tag: TextView) {
        val isSelected = tag.tag == "selected"
        if (isSelected) {
            tag.tag = "unselected"
            tag.setBackgroundResource(R.drawable.bg_tag_unselected)
            tag.setTextColor(android.graphics.Color.parseColor("#667085"))
        } else {
            tag.tag = "selected"
            tag.setBackgroundResource(R.drawable.bg_tag_selected)
            tag.setTextColor(android.graphics.Color.parseColor("#1463D9"))
        }
    }
}
