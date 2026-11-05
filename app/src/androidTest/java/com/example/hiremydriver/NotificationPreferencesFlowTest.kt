package com.example.hiremydriver

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationPreferencesFlowTest {
    @Test fun saveReopenRotateAndCancelPreserveCorrectPreferences() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = CustomerNotificationPreferences(context)
        val original = store.load()
        try {
            store.save(CustomerNotificationPreferences.Choices(true, true))
            ActivityScenario.launch(NotificationSettingsActivity::class.java).use {
                onView(withId(R.id.switchBookingUpdates)).perform(scrollTo(), click())
                onView(withId(R.id.btnSaveNotifications)).perform(scrollTo(), click())
            }
            assertEquals(CustomerNotificationPreferences.Choices(false, true), store.load())
            ActivityScenario.launch(NotificationSettingsActivity::class.java).use { scenario ->
                onView(withId(R.id.switchBookingUpdates)).perform(scrollTo()).check(matches(isNotChecked()))
                onView(withId(R.id.switchChatMessages)).perform(scrollTo(), click())
                scenario.recreate()
                onView(withId(R.id.switchChatMessages)).perform(scrollTo()).check(matches(isNotChecked()))
                onView(withId(R.id.btnBack)).perform(click())
            }
            assertEquals(CustomerNotificationPreferences.Choices(false, true), store.load())
        } finally { store.save(original) }
    }
}
