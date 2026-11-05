package com.example.hiremydriver

import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.SystemClock
import android.view.View
import androidx.core.widget.NestedScrollView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.hiremydriver.trip.*
import com.google.android.material.bottomsheet.BottomSheetBehavior
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** Local-only display fixtures; no booking transitions or server writes. */
@RunWith(AndroidJUnit4::class)
class MapControlsLayoutTest {
    private fun fixture(run: (Context, TripSession) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val pickup = TripPlace("Sikatuna pickup", 9.688, 123.974)
        val destination = TripPlace("Tagbilaran destination", 9.647, 123.855)
        val trip = TripSession(UUID.randomUUID().toString(), "Layout test", "Layout test",
            pickup.label, destination.label, pickup, listOf(destination),
            phase = TripPhase.ACCEPTED, localBooking = false)
        val scope = AccountDisplay.scope(context)
        LocalTripStore(context).save(trip)
        try { run(context, trip) } finally {
            context.getSharedPreferences("account_map_trips_$scope", Context.MODE_PRIVATE)
                .edit().remove(trip.id).commit()
        }
    }

    private fun bounds(view: View): Rect {
        assertEquals(View.VISIBLE, view.visibility)
        val rect = Rect()
        assertTrue("Control must be visible and within its parent", view.getGlobalVisibleRect(rect))
        assertEquals("Control must not be vertically clipped", view.height, rect.height())
        return rect
    }

    @Test fun trackTripControlsStayAboveSheetAfterExpandCollapseAndRecreate() = fixture { context, trip ->
        ActivityScenario.launch<TripInProgressActivity>(Intent(context, TripInProgressActivity::class.java)
            .putExtra(LocalTripStore.ID, trip.id)).use { scenario ->
            repeat(2) {
                for (state in listOf(BottomSheetBehavior.STATE_COLLAPSED,
                    BottomSheetBehavior.STATE_HALF_EXPANDED, BottomSheetBehavior.STATE_EXPANDED)) {
                    scenario.onActivity { activity ->
                        BottomSheetBehavior.from(activity.findViewById<NestedScrollView>(R.id.mapBottomSheet)).state = state
                    }
                    SystemClock.sleep(900)
                    scenario.onActivity { activity ->
                        val sheetPosition = IntArray(2)
                        activity.findViewById<View>(R.id.mapBottomSheet).getLocationOnScreen(sheetPosition)
                        val sheetTop = sheetPosition[1]
                        val headerBottom = bounds(activity.findViewById(R.id.mapOverlayContent)).bottom
                        for (id in listOf(R.id.btnMapType, R.id.btnMapRecenter)) {
                            val control = bounds(activity.findViewById(id))
                            assertTrue("Control must stay above the card", control.bottom < sheetTop)
                            assertTrue("Control must stay below the header", control.top >= headerBottom)
                        }
                    }
                }
                scenario.recreate()
            }
        }
    }

    @Test fun fullscreenRoutePanelKeepsMapFullHeightAndControlsVisible() = fixture { context, trip ->
        ActivityScenario.launch<LiveTrackingActivity>(Intent(context, LiveTrackingActivity::class.java)
            .putExtra(LocalTripStore.ID, trip.id)).use { scenario ->
            repeat(3) {
                scenario.onActivity { it.findViewById<View>(R.id.btnMapDetails).performClick() }
                SystemClock.sleep(700)
                scenario.onActivity { activity ->
                    val root = activity.findViewById<View>(R.id.fullScreenMapRoot)
                    assertEquals(root.height, activity.findViewById<View>(R.id.mapPlaceholder).height)
                    val panel = activity.findViewById<View>(R.id.mapDetailsPanel)
                    val headerBottom = bounds(activity.findViewById(R.id.mapFloatingHeader)).bottom
                    for (id in listOf(R.id.btnMapType, R.id.fabRecenter, R.id.btnMapDetails)) {
                        val control = bounds(activity.findViewById(id))
                        assertTrue("Control must stay below the header", control.top >= headerBottom)
                        if (panel.visibility == View.VISIBLE)
                            assertTrue("Control must stay above route details", control.bottom < bounds(panel).top)
                    }
                }
            }
        }
    }
}
