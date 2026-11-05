package com.example.hiremydriver

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.hiremydriver.trip.*
import org.hamcrest.Matchers.containsString
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ReviewFlowTest {
    @Test fun driverProfileOpensAllReceivedReviewsAndReturnsToProfile() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val ids = List(2) { "profile-review-${UUID.randomUUID()}" }
        try {
            ids.forEachIndexed { index, id ->
                val trip = TripSession(id, "Local inbox driver", "Customer $id", "Pickup", "Destination")
                    .accept().arrive().start(100, true).complete(200)
                LocalTripStore(context).save(trip)
                assertTrue(LocalReviewStore(context).save(id, index + 4, "Feedback $id", emptyList()))
            }
            ActivityScenario.launch<DriverProfileActivity>(Intent(context, DriverProfileActivity::class.java)).use {
                onView(withId(R.id.rowCustomerReviews)).perform(scrollTo(), click())
                onView(withId(R.id.txtReviewsTitle)).check(matches(withText("Customer Reviews")))
                // Each saved booking gets its own original card; driver mode never exposes edit actions.
                ids.reversed().forEach { id ->
                    onView(withText("Customer $id")).perform(scrollTo()).check(matches(isDisplayed()))
                    onView(withText("Feedback $id")).check(matches(withText("Feedback $id")))
                }
                onView(withId(R.id.btnBack)).perform(click())
                onView(withId(R.id.rowCustomerReviews)).check(matches(isDisplayed()))
            }
        } finally {
            ids.forEach { id ->
                context.getSharedPreferences("local_booking_reviews_v1", Context.MODE_PRIVATE).edit().remove(id).commit()
                context.getSharedPreferences("local_map_trips_v1", Context.MODE_PRIVATE).edit().remove(id).commit()
            }
        }
    }

    @Test fun submitRestoreEditAndReadFromDriverTemplate() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val id = "review-test-${UUID.randomUUID()}"
        val trip = TripSession(id, "Review Test Driver", "Review Test Customer", "Test pickup", "Test destination")
            .accept().arrive().start(100, true).complete(200)
        LocalTripStore(context).save(trip)
        try {
            ActivityScenario.launch<RateDriverActivity>(Intent(context, RateDriverActivity::class.java)
                .putExtra(LocalTripStore.ID, id)).use { screen ->
                onView(withId(R.id.txtDriverName)).check(matches(withText("Review Test Driver")))
                onView(withId(R.id.star2)).perform(click())
                onView(withId(R.id.edtReview)).perform(scrollTo(), replaceText("Review test feedback"))
                onView(withId(R.id.tagProfessional)).perform(scrollTo(), click())
                screen.recreate()
                onView(withId(R.id.txtRatingLabel)).check(matches(withText("2.0 - Poor")))
                onView(withId(R.id.edtReview)).check(matches(withText("Review test feedback")))
                onView(withId(R.id.btnSubmitReview)).perform(scrollTo(), click())
                onView(withId(R.id.btnViewMyReviews)).check(matches(isDisplayed()))
            }
            val saved = LocalReviewStore(context).get(id)!!
            assertEquals(2, saved.rating)
            assertEquals(listOf("Professional"), saved.tags)
            assertEquals("Review test feedback", saved.comment)

            ActivityScenario.launch<RateDriverActivity>(Intent(context, RateDriverActivity::class.java)
                .putExtra(LocalTripStore.ID, id)).use {
                onView(withId(R.id.txtRatingLabel)).check(matches(withText("2.0 - Poor")))
                onView(withId(R.id.star4)).perform(click())
                onView(withId(R.id.btnSubmitReview)).perform(scrollTo(), click())
            }
            assertEquals(1, LocalReviewStore(context).all().count { it.first.id == id })
            assertEquals(4, LocalReviewStore(context).get(id)?.rating)
            assertEquals(1, LocalReviewStore(context).forDriver("Review Test Driver").count { it.first.id == id })
            assertTrue(LocalReviewStore(context).forDriver("Different Driver").none { it.first.id == id })
            ActivityScenario.launch<CustomerReviewsActivity>(Intent(context, CustomerReviewsActivity::class.java)
                .putExtra(LocalTripStore.ID, id).putExtra("DRIVER_REVIEW", true)).use {
                onView(withId(R.id.txtReviewDriverName)).check(matches(withText("Review Test Customer")))
                onView(withId(R.id.txtReviewRatingNum)).check(matches(withText("4.0")))
                onView(withId(R.id.txtReviewComment)).check(matches(withText(containsString("Professional"))))
                onView(withId(R.id.btnMoreReview)).check(matches(withEffectiveVisibility(Visibility.GONE)))
            }
            ActivityScenario.launch<DriverDetailsActivity>(Intent(context, DriverDetailsActivity::class.java)
                .putExtra("DRIVER_NAME", "Review Test Driver").putExtra("DRIVER_RATING", "★ 4.9")).use {
                onView(withId(R.id.txtRating)).check(matches(withText("★ 4.9")))
                onView(withId(R.id.txtReviewCount)).check(matches(withText("(128 reviews)")))
                onView(withText("Customer Reviews")).check(doesNotExist())
            }
            assertFalse(LocalReviewStore(context).save("missing-$id", 5, "", emptyList()))
            LocalTripStore(context).save(trip.copy(phase = TripPhase.ACCEPTED))
            assertFalse(LocalReviewStore(context).save(id, 5, "", emptyList()))
        } finally {
            // Only remove records created by this test; retain all user bookings and reviews.
            context.getSharedPreferences("local_booking_reviews_v1", Context.MODE_PRIVATE).edit().remove(id).commit()
            context.getSharedPreferences("local_map_trips_v1", Context.MODE_PRIVATE).edit().remove(id).commit()
        }
    }
}
