package com.example.hiremydriver

import android.content.Context
import android.content.Intent
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import com.example.hiremydriver.trip.LocalTripStore
import org.junit.Assert.*
import org.junit.Test

/** Read-only UI checks: never creates a booking, review, or chat message. */
class DriverInformationUiTest {
    @Test fun recommendationChoicesUpdateFieldsAndSurviveRecreation() {
        ActivityScenario.launch(AiRecommendationActivity::class.java).use { screen ->
            screen.onActivity { it.findViewById<View>(R.id.containerDuration).performClick() }
            onView(withText("5 hour(s)")).perform(click())
            onView(withId(R.id.txtDuration)).check(matches(withText("5 hours")))
            screen.onActivity { it.findViewById<View>(R.id.containerVehicle).performClick() }
            onView(withText("Van")).perform(click())
            screen.onActivity { it.findViewById<View>(R.id.containerTransmission).performClick() }
            onView(withText("Manual")).perform(click())
            screen.recreate()
            onView(withId(R.id.txtDuration)).check(matches(withText("5 hours")))
            onView(withId(R.id.txtVehicle)).check(matches(withText("Van")))
            onView(withId(R.id.txtTransmission)).check(matches(withText("Manual")))
        }
    }
    @Test fun individualReviewAndBookingDetailsHaveNoBottomTabs() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        ActivityScenario.launch<CustomerReviewsActivity>(Intent(context, CustomerReviewsActivity::class.java)
            .putExtra(LocalTripStore.ID, "ui-check-only")).use { screen ->
            screen.onActivity { assertEquals(View.GONE, it.findViewById<View>(R.id.bottomNavigation).visibility) }
        }
        ActivityScenario.launch<BookingDetailsActivity>(Intent(context, BookingDetailsActivity::class.java)
            .putExtra("EXTRA_STATUS", "COMPLETED")).use { screen ->
            screen.onActivity { assertNull(it.findViewById<View>(R.id.bottomNavigation)) }
        }
    }
    @Test fun publicReviewFiltersRemainSelectedAcrossRecreation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        ActivityScenario.launch<PublicDriverReviewsActivity>(Intent(context, PublicDriverReviewsActivity::class.java)
            .putExtra("DRIVER_UID", "ui-read-only-driver")).use { screen ->
            onView(withText("All Reviews")).check(matches(isChecked()))
            onView(withText("4 Stars")).perform(click()).check(matches(isChecked()))
            screen.recreate()
            onView(withText("4 Stars")).check(matches(isChecked()))
            screen.onActivity { assertEquals(View.GONE, it.findViewById<View>(R.id.bottomNavigation).visibility) }
        }
    }
}
