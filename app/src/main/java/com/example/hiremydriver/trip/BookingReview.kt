package com.example.hiremydriver.trip

data class BookingReview(
    val tripId: String,
    val rating: Int,
    val comment: String,
    val tags: List<String>,
    val submittedAt: Long
) {
    init {
        require(tripId.isNotBlank())
        require(rating in 1..5)
        require(comment.length <= 500)
        require(tags.distinct() == tags && tags.all { it in TAGS })
    }

    companion object {
        val TAGS = listOf("Punctual", "Professional", "Safe Driving", "Good Communication", "Friendly")

        /** Legacy optional fields must never make a review list crash. */
        fun fromRecord(id: String, data: Map<String, Any?>, timestamp: Long): BookingReview? {
            val number = (data["rating"] as? Number)?.toDouble() ?: return null
            if (!number.isFinite() || number !in 1.0..5.0 || number % 1.0 != 0.0 || id.isBlank()) return null
            return BookingReview(id, number.toInt(), (data["comment"] as? String).orEmpty().take(500),
                (data["tags"] as? List<*>)?.filterIsInstance<String>()?.filter { it in TAGS }?.distinct().orEmpty(),
                timestamp.coerceAtLeast(0L))
        }

        fun forBooking(trip: TripSession, rating: Int, comment: String, tags: List<String>, now: Long): BookingReview {
            require(trip.localBooking && trip.phase == TripPhase.COMPLETED) { "Complete this booking before reviewing it." }
            return BookingReview(trip.id, rating, comment.trim(), tags.distinct(), now)
        }
    }
}
