package com.example.hiremydriver

import android.os.SystemClock
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.hiremydriver.trip.BookingDialogs
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the dialog without completing a real booking or sending a notification. */
@RunWith(AndroidJUnit4::class)
class ServiceCompletedDialogTest {
    @Test fun completedDialogHasFinalLayoutBeforeAnimationAndContinueRunsOnce() {
        ActivityScenario.launch(ReviewSubmittedActivity::class.java).use { scenario ->
            lateinit var dialog: AlertDialog
            var continued = 0
            scenario.onActivity { activity ->
                dialog = BookingDialogs.confirm(activity, "Booking completed", "This booking is now complete.",
                    "Continue", negative = null, smoothCompletion = true) { continued++ }
                assertEquals(View.GONE, dialog.findViewById<View>(R.id.bookingDialogCancel)!!.visibility)
                assertEquals(R.style.ServiceCompletedDialogAnimation, dialog.window!!.attributes.windowAnimations)
            }
            SystemClock.sleep(400)
            scenario.onActivity {
                assertTrue(dialog.isShowing)
                val button = dialog.findViewById<View>(R.id.bookingDialogConfirm)!!
                button.performClick()
                button.performClick()
                assertEquals(1, continued)
                assertFalse(dialog.isShowing)
            }
        }
    }
}
