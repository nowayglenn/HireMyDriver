package com.example.hiremydriver

import android.app.Activity
import android.app.Instrumentation
import android.os.SystemClock
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DriverNavigationFlowTest {
    private val destinations = listOf(DriverHomeActivity::class.java, DriverBookingActivity::class.java,
        DriverMessageActivity::class.java, DriverProfileActivity::class.java)
    private val tabIds = listOf(R.id.navHome, R.id.navBooking, R.id.navMessage, R.id.navProfile)

    @Test fun repeatedClicksOnEveryDriverTabDispatchOnlyOneScreen() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        for (source in destinations) {
            ActivityScenario.launch(source).use { scenario ->
                SystemClock.sleep(400) // Let the previous screen's shared transition interval end.
                val monitors = destinations.map {
                    instrumentation.addMonitor(it.name, Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null), true)
                }
                try {
                    scenario.onActivity { screen ->
                        val currentTab = tabIds[destinations.indexOf(source)]
                        repeat(100) { screen.findViewById<View>(currentTab).performClick() }
                        assertEquals("Current tab must not reopen itself", 0, monitors.sumOf { it.hits })
                        assertFalse("Current tab must not finish its screen", screen.isFinishing)

                        repeat(300) { tap ->
                            // performClick also exercises callbacks queued before the views were disabled.
                            screen.findViewById<View>(tabIds[tap % tabIds.size]).performClick()
                        }
                        assertEquals("Rapid taps from ${source.simpleName} must launch once", 1, monitors.sumOf { it.hits })
                        assertTrue("Tab switches must remove the outgoing screen", screen.isFinishing)
                        tabIds.forEach { assertFalse(screen.findViewById<View>(it).isEnabled) }
                    }
                } finally {
                    monitors.forEach { instrumentation.removeMonitor(it) }
                }
            }
        }
    }

    @Test fun backgroundScreenCannotNavigateAndResumedScreenCan() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        ActivityScenario.launch(DriverMessageActivity::class.java).use { scenario ->
            SystemClock.sleep(400)
            val monitor = instrumentation.addMonitor(DriverBookingActivity::class.java.name,
                Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null), true)
            try {
                scenario.moveToState(Lifecycle.State.STARTED)
                scenario.onActivity { screen ->
                    repeat(100) { screen.findViewById<View>(R.id.navBooking).performClick() }
                    assertEquals("Paused screens must ignore queued clicks", 0, monitor.hits)
                }
                scenario.moveToState(Lifecycle.State.RESUMED)
                scenario.onActivity { screen ->
                    tabIds.forEach { assertTrue(screen.findViewById<View>(it).isEnabled) }
                    screen.findViewById<View>(R.id.navBooking).performClick()
                    assertEquals("Normal navigation must work after resume", 1, monitor.hits)
                }
            } finally {
                instrumentation.removeMonitor(monitor)
            }
        }
    }
}
