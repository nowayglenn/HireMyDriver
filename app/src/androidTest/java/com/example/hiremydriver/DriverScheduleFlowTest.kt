package com.example.hiremydriver

import android.content.Context
import android.view.View
import android.widget.TimePicker
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.Matcher
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DriverScheduleFlowTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    private fun withRestoredSchedule(test: (DriverScheduleStore) -> Unit) {
        val store = DriverScheduleStore(context)
        val originalDays = store.load()
        val accepting = store.acceptingBookings
        try { test(store) }
        finally { store.save(originalDays); store.setAccepting(accepting) {} }
    }

    private fun setTime(id: Int, hour: Int, minute: Int) {
        onView(withId(id)).perform(scrollTo(), click())
        onView(isAssignableFrom(TimePicker::class.java)).perform(object : ViewAction {
            override fun getConstraints(): Matcher<View> = isAssignableFrom(TimePicker::class.java)
            override fun getDescription() = "Select a schedule time"
            override fun perform(uiController: UiController, view: View) {
                (view as TimePicker).apply { this.hour = hour; this.minute = minute }
                uiController.loopMainThreadUntilIdle()
            }
        })
        onView(withId(android.R.id.button1)).perform(click())
    }

    @Test fun savesCustomSundayHoursAndRestoresThemOnReopen() = withRestoredSchedule { store ->
        store.save(DriverSchedule.defaults())
        ActivityScenario.launch(DriverEditWeeklyScheduleActivity::class.java).use {
            onView(withId(R.id.scheduleDay7Part1)).perform(scrollTo(), click())
            setTime(R.id.scheduleDay7Part2, 9, 15)
            setTime(R.id.scheduleDay7Part4, 15, 45)
            onView(withId(R.id.btnSaveSchedule)).perform(click())
        }
        assertEquals(DriverDaySchedule(true, 555, 945), store.load()[6])
        ActivityScenario.launch(DriverEditWeeklyScheduleActivity::class.java).use {
            onView(withId(R.id.scheduleDay7Part1)).perform(scrollTo()).check(matches(isChecked()))
            setTime(R.id.scheduleDay7Part2, 10, 30)
            onView(withId(R.id.btnBack)).perform(click())
        }
        assertEquals(555, store.load()[6].start)
    }

    @Test fun invalidWindowCannotOverwriteSavedScheduleAndDraftSurvivesRotation() = withRestoredSchedule { store ->
        store.save(DriverSchedule.defaults())
        ActivityScenario.launch(DriverEditWeeklyScheduleActivity::class.java).use { scenario ->
            onView(withId(R.id.scheduleDay1Part1)).perform(scrollTo(), click())
            setTime(R.id.scheduleDay1Part2, 20, 0)
            scenario.recreate()
            onView(withId(R.id.btnSaveSchedule)).perform(click())
            onView(withId(R.id.scheduleError1)).check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
            assertEquals(480, store.load()[0].start)
            onView(withId(R.id.btnBack)).perform(click())
        }
    }

    @Test fun incompleteInformationBlocksBothAvailabilitySwitches() = withRestoredSchedule { store ->
        val prefs = context.getSharedPreferences(DriverAuth.preferenceName(context, "driver_driving_info"), Context.MODE_PRIVATE)
        val confirmed = prefs.getBoolean("cloud_confirmed", false)
        prefs.edit().putBoolean("cloud_confirmed", false).commit()
        try {
            store.setAccepting(true) { assertFalse(it) }
            assertFalse(store.acceptingBookings)
            ActivityScenario.launch(DriverHomeActivity::class.java).use {
                onView(withId(R.id.switchAvailability)).check(matches(isNotChecked())).check(matches(org.hamcrest.Matchers.not(isEnabled())))
            }
            ActivityScenario.launch(DriverAvailabilityScheduleActivity::class.java).use {
                onView(withId(R.id.switchAcceptBookings)).check(matches(isNotChecked())).check(matches(org.hamcrest.Matchers.not(isEnabled())))
            }
        } finally { prefs.edit().putBoolean("cloud_confirmed", confirmed).commit() }
    }
}
