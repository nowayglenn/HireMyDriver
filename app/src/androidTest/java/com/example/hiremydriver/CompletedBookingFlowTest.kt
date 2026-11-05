package com.example.hiremydriver

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.hiremydriver.trip.*
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.containsString
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class CompletedBookingFlowTest {
    @Test fun driverCompletionAppearsInCustomerBookingsAndMapHistory() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = LocalTripStore(context)
        val pickup = TripPlace("Test Sikatuna", 9.688, 123.971)
        val stops = listOf(TripPlace("Test Tagbilaran", 9.647, 123.855), TripPlace("Test Panglao", 9.578, 123.746))
        val trip = store.create(Intent().putExtra("DRIVER_NAME", "Completion Test ${UUID.randomUUID()}")
            .putExtra("TRIP_PICKUP", LocalTripStore.placeJson(pickup).toString())
            .putExtra("TRIP_DESTINATIONS", LocalTripStore.placesJson(stops)))
        try {
            ActivityScenario.launch<DriverRequestDetailsActivity>(Intent(context, DriverRequestDetailsActivity::class.java)
                .putExtra(LocalTripStore.ID, trip.id)).use {
                onView(withId(R.id.btnAcceptBooking)).perform(click())
                onView(withId(R.id.bookingDialogConfirm)).perform(click())
                assertEquals(TripPhase.ACCEPTED, store.get(trip.id)?.phase)
            }
            // Simulate pickup confirmation; completion itself uses the driver's actual UI.
            store.update(trip.id) { it.arrive().start(System.currentTimeMillis(), true) }
            ActivityScenario.launch<DriverAcceptedBookingDetailsActivity>(Intent(context, DriverAcceptedBookingDetailsActivity::class.java)
                .putExtra(LocalTripStore.ID, trip.id)).use {
                onView(withId(R.id.btnStartService)).perform(click())
                onView(withId(R.id.bookingDialogConfirm)).perform(click())
                onView(withId(R.id.txtBookingStatus)).check(matches(withText("Service completed")))
            }
            assertEquals(TripPhase.COMPLETED, store.get(trip.id)?.phase)
            assertEquals(stops, store.get(trip.id)?.destinations)
            ActivityScenario.launch<MyBookingActivity>(Intent(context, MyBookingActivity::class.java)).use {
                onView(withId(R.id.tabCompleted)).perform(scrollTo(), click())
                onView(allOf(withId(R.id.bookingCardDetails), isDescendantOfA(allOf(withId(R.id.bookingCard), hasDescendant(withText(trip.driverName))))))
                    .perform(scrollTo(), click())
                onView(withId(R.id.txtStatusTitle)).check(matches(withText("Service completed")))
                onView(withId(R.id.btnViewMapHistory)).perform(scrollTo(), click())
                onView(withId(R.id.txtMapTitle)).check(matches(withText("Map history")))
                onView(withId(R.id.mapDetailsPanel)).check(matches(withEffectiveVisibility(Visibility.GONE)))
                onView(withId(R.id.fabRecenter)).check(matches(isDisplayed()))
                onView(withId(R.id.btnMapDetails)).check(matches(withEffectiveVisibility(Visibility.GONE)))
                onView(withId(R.id.btnBack)).perform(click())
                onView(withId(R.id.btnRight)).check(matches(withText("Rate Driver")))
            }
        } finally {
            context.getSharedPreferences("local_map_trips_v1", Context.MODE_PRIVATE).edit().remove(trip.id).commit()
        }
    }
}
