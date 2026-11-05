package com.example.hiremydriver

import android.content.Context
import android.view.ContextThemeWrapper
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.example.hiremydriver.trip.BookingReview
import com.example.hiremydriver.trip.ReviewCard
import com.example.hiremydriver.trip.TripSession
import org.junit.Assert.*
import org.junit.Test

class ReviewCardRegressionTest {
    @Test fun existingReviewScreensOpenWithoutWritingReviewData() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        listOf(false, true).forEach { driver ->
            androidx.test.core.app.ActivityScenario.launch<CustomerReviewsActivity>(
                android.content.Intent(context, CustomerReviewsActivity::class.java).putExtra("DRIVER_REVIEW", driver)
            ).use { scenario ->
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                scenario.onActivity { activity ->
                    assertEquals(if (driver) "Customer Reviews" else "My Reviews",
                        activity.findViewById<TextView>(R.id.txtReviewsTitle).text.toString())
                }
            }
        }
    }

    @Test fun submittedReviewHidesOnlyItsNavigation() {
        androidx.test.core.app.ActivityScenario.launch(ReviewSubmittedActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals(android.view.View.GONE, activity.findViewById<android.view.View>(R.id.bottomNavigation).visibility)
                assertTrue(activity.findViewById<android.view.View>(R.id.btnViewMyReviews).isEnabled)
                assertTrue(activity.findViewById<android.view.View>(R.id.btnBackToBookings).isEnabled)
            }
        }
    }

    @Test fun bothReviewModesInflateWithAnImageAndSafeOptionalFields() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val context = ContextThemeWrapper(ApplicationProvider.getApplicationContext<Context>(), R.style.Theme_HireMyDriver)
            val parent = LinearLayout(context)
            val trip = TripSession("review-card-test", "Driver name", "Customer name", "Pickup", "Destination")
            val review = BookingReview.fromRecord(trip.id, mapOf("rating" to 5L), 0)!!
            listOf(false, true).forEach { driverMode ->
                val card = ReviewCard.create(parent, trip, review, driverMode)
                assertTrue(card.findViewById<android.view.View>(R.id.imgDriverThumb) is ImageView)
                assertEquals(if (driverMode) "Customer name" else "Driver name",
                    card.findViewById<TextView>(R.id.txtReviewDriverName).text.toString())
                assertEquals(android.view.View.GONE, card.findViewById<android.view.View>(R.id.txtReviewDate).visibility)
            }
        }
    }
}
