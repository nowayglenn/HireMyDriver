package com.example.hiremydriver

import com.example.hiremydriver.trip.*
import org.junit.Assert.*
import org.junit.Test

class BookingReviewTest {
    @Test fun storedReviewsTolerateMissingOptionalFields() {
        val review = BookingReview.fromRecord("booking", mapOf("rating" to 5L), 0)!!
        assertEquals(5, review.rating)
        assertEquals("", review.comment)
        assertTrue(review.tags.isEmpty())
        assertEquals(0L, review.submittedAt)
        val mixed = BookingReview.fromRecord("booking", mapOf("rating" to 4.0,
            "comment" to 123, "tags" to listOf("Friendly", 42, "unknown", "Friendly")), -1)!!
        assertEquals(listOf("Friendly"), mixed.tags)
        assertEquals(0L, mixed.submittedAt)
    }

    @Test fun malformedStoredRatingsAreSkipped() {
        listOf(null, "5", 0, 6, 4.5, Double.NaN, Double.POSITIVE_INFINITY).forEach {
            assertNull(BookingReview.fromRecord("booking", mapOf("rating" to it), 1))
        }
    }
    private val completed = TripSession("booking-1", "Driver", "Customer", "Pickup", "Destination")
        .accept().headToPickup().arrive().start(100, true).complete(200)

    @Test fun savesCorrectBookingAndNormalizesOptionalFeedback() {
        val review = BookingReview.forBooking(completed, 2, "  Needs improvement  ", listOf("Friendly", "Friendly"), 300)
        assertEquals(completed.id, review.tripId)
        assertEquals(2, review.rating)
        assertEquals("Needs improvement", review.comment)
        assertEquals(listOf("Friendly"), review.tags)
        assertEquals(300L, review.submittedAt)
        assertEquals("", BookingReview.forBooking(completed, 5, "  ", emptyList(), 300).comment)
    }

    @Test fun onlyRealCompletedBookingsCanBeReviewed() {
        TripPhase.entries.filter { it != TripPhase.COMPLETED }.forEach { phase ->
            rejected { BookingReview.forBooking(completed.copy(phase = phase), 5, "", emptyList(), 300) }
        }
        rejected { BookingReview.forBooking(completed.copy(localBooking = false), 5, "", emptyList(), 300) }
    }

    @Test fun rejectsInvalidRatingsLongCommentsAndUnknownTags() {
        listOf(0, 6).forEach { rating -> rejected { BookingReview.forBooking(completed, rating, "", emptyList(), 300) } }
        rejected { BookingReview.forBooking(completed, 5, "a".repeat(501), emptyList(), 300) }
        rejected { BookingReview.forBooking(completed, 5, "", listOf("unknown"), 300) }
        assertEquals(500, BookingReview.forBooking(completed, 1, "a".repeat(500), emptyList(), 300).comment.length)
    }

    private fun rejected(block: () -> Unit) {
        try { block(); fail("Invalid review was accepted") } catch (_: IllegalArgumentException) { }
    }
}
