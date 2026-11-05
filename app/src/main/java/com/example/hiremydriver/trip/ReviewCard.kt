package com.example.hiremydriver.trip

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.example.hiremydriver.R
import java.text.DateFormat
import java.util.Date

/** Reuses the original review card without changing its dimensions or styling. */
object ReviewCard {
    fun create(parent: ViewGroup, trip: TripSession, review: BookingReview, showCustomer: Boolean,
        driverSession: Boolean = showCustomer): View {
        val card = LayoutInflater.from(parent.context).inflate(R.layout.item_customer_review, parent, false)
        card.findViewById<TextView>(R.id.txtReviewDriverName).text = if (showCustomer) trip.customerName else trip.driverName
        card.findViewById<TextView>(R.id.txtReviewDate).apply {
            text = if (review.submittedAt > 0) DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(review.submittedAt)) else ""
            visibility = if (text.isBlank()) View.GONE else View.VISIBLE
        }
        card.findViewById<TextView>(R.id.txtReviewRatingNum).text = "${review.rating}.0"
        com.example.hiremydriver.PeerProfilePhoto.bind(card.findViewById(R.id.imgDriverThumb),
            if (showCustomer) trip.customerUid else trip.driverUid, !showCustomer, driverSession,
            card.findViewById(R.id.txtReviewDriverName))
        val starRow = card.findViewById<View>(R.id.imgStar5).parent as LinearLayout
        var index = 0
        for (i in 0 until starRow.childCount) {
            val star = starRow.getChildAt(i) as? ImageView ?: continue
            star.setImageResource(if (index++ < review.rating) R.drawable.ic_star else R.drawable.ic_star_border)
        }
        card.findViewById<TextView>(R.id.txtReviewComment).text = listOf(
            review.comment.ifBlank { "No written comment." }, review.tags.joinToString(" · ")
        ).filter { it.isNotBlank() }.joinToString("\n\n")
        card.findViewById<TextView>(R.id.txtReviewRoute).text = trip.bookingRoute().joinToString(" → ") { it.place }
        card.findViewById<TextView>(R.id.txtReviewType).text = "By the hour · ${trip.durationHours} ${if (trip.durationHours == 1) "hour" else "hours"}"
        card.findViewById<View>(R.id.btnMoreReview).visibility = View.GONE
        return card
    }
}
