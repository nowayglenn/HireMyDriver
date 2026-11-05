package com.example.hiremydriver

import android.os.SystemClock
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Opens only the success screen; never creates or submits a real review. */
@RunWith(AndroidJUnit4::class)
class ReviewSuccessAnimationTest {
    @Test fun successSettlesAndRecreationDoesNotHideContentOrActions() {
        ActivityScenario.launch(ReviewSubmittedActivity::class.java).use { scenario ->
            repeat(2) {
                SystemClock.sleep(900)
                scenario.onActivity { activity ->
                    for (id in listOf(R.id.reviewSuccessCheck, R.id.reviewSuccessTitle, R.id.reviewSuccessMessage)) {
                        val view = activity.findViewById<View>(id)
                        assertEquals(1f, view.alpha, 0.001f)
                        assertEquals(1f, view.scaleX, 0.001f)
                        assertEquals(0f, view.translationY, 0.001f)
                    }
                    assertEquals(0.2f, activity.findViewById<View>(R.id.reviewSuccessHalo).alpha, 0.001f)
                    assertTrue(activity.findViewById<View>(R.id.btnBackToBookings).isEnabled)
                    assertTrue(activity.findViewById<View>(R.id.btnViewMyReviews).isEnabled)
                    assertEquals(View.GONE, activity.findViewById<View>(R.id.bottomNavigation).visibility)
                }
                scenario.recreate()
            }
        }
    }
}
