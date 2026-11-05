package com.example.hiremydriver

import android.app.Activity
import android.app.Instrumentation
import android.os.SystemClock
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CustomerNavigationFlowTest {
    @Test fun repeatedClicksOnRealTabViewsDispatchOnlyOneScreen() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        // These screens do not require creating or signing in to a test account.
        val sources = listOf(MessagesActivity::class.java, MyBookingActivity::class.java, ProfileActivity::class.java)
        val destinations = listOf(HomeActivity::class.java, MyBookingActivity::class.java,
            MessagesActivity::class.java, ProfileActivity::class.java)
        val tabIds = listOf(R.id.navHome, R.id.navBookings, R.id.navMessages, R.id.navProfile)
        for (source in sources) {
            ActivityScenario.launch(source).use { scenario ->
                SystemClock.sleep(400) // Allow the shared transition interval from the previous scenario to end.
                val monitors = destinations.map {
                    instrumentation.addMonitor(it.name, Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null), true)
                }
                try {
                    scenario.onActivity { screen ->
                        val currentTab = tabIds[destinations.indexOf(source)]
                        repeat(100) { screen.findViewById<View>(currentTab).performClick() }
                        assertEquals("Current tab must not reopen itself", 0, monitors.sumOf { it.hits })
                        repeat(300) { tap ->
                            // performClick also exercises queued callbacks even after views are disabled.
                            screen.findViewById<View>(tabIds[tap % tabIds.size]).performClick()
                        }
                        assertEquals("Rapid taps from ${source.simpleName} must launch once", 1, monitors.sumOf { it.hits })
                    }
                } finally {
                    monitors.forEach { instrumentation.removeMonitor(it) }
                }
            }
        }
    }
}
