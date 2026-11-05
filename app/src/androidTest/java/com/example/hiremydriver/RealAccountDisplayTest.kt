package com.example.hiremydriver

import android.view.View
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import org.junit.Assert.*
import org.junit.Test

class RealAccountDisplayTest {
    @Test fun driverHomeDoesNotExposeTemplateCustomerRequests() {
        ActivityScenario.launch(DriverHomeActivity::class.java).use { scenario ->
            scenario.onActivity { screen ->
                assertEquals(View.GONE, screen.findViewById<View>(R.id.requestContainer1).visibility)
                assertEquals("No upcoming bookings.", screen.findViewById<TextView>(R.id.txtUpcomingCustomer).text.toString())
                assertNotEquals("Laur", screen.findViewById<TextView>(R.id.txtDriverName).text.toString())
                assertEquals(View.VISIBLE, screen.findViewById<View>(R.id.calendarContainer).visibility)
                listOf(R.id.navHome, R.id.navBooking, R.id.navMessage, R.id.navProfile).forEach {
                    assertTrue("Driver Home must wire every navigation tab", screen.findViewById<View>(it).hasOnClickListeners())
                }
                val requests = screen.findViewById<View>(R.id.requestContainer1).parent as android.widget.LinearLayout
                for (index in 0 until requests.childCount) {
                    val child = requests.getChildAt(index)
                    if (child.javaClass == View::class.java) assertEquals(View.GONE, child.visibility)
                }
            }
        }
    }

    @Test fun notificationTemplatesAreNotPresentedAsRealEvents() {
        ActivityScenario.launch(NotificationsActivity::class.java).use { scenario ->
            scenario.onActivity { assertNull(it.findViewById<View>(R.id.notif1)) }
        }
        ActivityScenario.launch(DriverNotificationsActivity::class.java).use { scenario ->
            scenario.onActivity { assertNull(it.findViewById<View>(R.id.notif1)) }
        }
    }
}
