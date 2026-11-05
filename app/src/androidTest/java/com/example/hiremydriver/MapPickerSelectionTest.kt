package com.example.hiremydriver

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.action.GeneralSwipeAction
import androidx.test.espresso.action.Swipe
import androidx.test.espresso.action.Press
import androidx.core.widget.NestedScrollView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.example.hiremydriver.maps.LocationPickerActivity
import com.example.hiremydriver.maps.MapScreenActivity
import com.example.hiremydriver.maps.TripMapController
import com.example.hiremydriver.trip.LocalTripStore
import com.example.hiremydriver.trip.TripPlace
import com.google.android.gms.maps.GoogleMap
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises actual keyboard/sheet resizing without creating or changing a booking. */
@RunWith(AndroidJUnit4::class)
class MapPickerSelectionTest {
    private fun controller(activity: LocationPickerActivity): TripMapController =
        MapScreenActivity::class.java.getDeclaredField("tripMap").apply { isAccessible = true }
            .get(activity) as TripMapController

    private fun googleMap(controller: TripMapController): GoogleMap? =
        TripMapController::class.java.getDeclaredField("map").apply { isAccessible = true }
            .get(controller) as? GoogleMap

    @Test fun typingAndFullscreenKeepSelectedCoordinateUnderPin() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val pin = TripPlace("Test location", 9.688, 123.974)
        val intent = Intent(context, LocationPickerActivity::class.java)
            .putExtra("PICKER_TITLE", "Destination")
            .putExtra("PLACE", LocalTripStore.placeJson(pin).toString())
            .putExtra("PLACE_LABEL", pin.label)
        ActivityScenario.launch<LocationPickerActivity>(intent).use { scenario ->
            var ready = false
            val deadline = SystemClock.elapsedRealtime() + 20_000
            while (!ready && SystemClock.elapsedRealtime() < deadline) {
                scenario.onActivity { ready = googleMap(controller(it)) != null }
                if (!ready) SystemClock.sleep(100)
            }
            assertTrue("Google map must be ready for the viewport test", ready)
            SystemClock.sleep(700)
            var expectedLatitude = pin.latitude
            var expectedLongitude = pin.longitude
            fun checkPin() {
                scenario.onActivity { activity ->
                    val picker = controller(activity)
                    val selected = picker.selectedCenter()!!
                    val visible = googleMap(picker)!!.cameraPosition.target
                    assertEquals(expectedLatitude, selected.latitude, 0.000001)
                    assertEquals(expectedLongitude, selected.longitude, 0.000001)
                    assertEquals(expectedLatitude, visible.latitude, 0.000001)
                    assertEquals(expectedLongitude, visible.longitude, 0.000001)
                    val mapPosition = IntArray(2)
                    activity.findViewById<View>(R.id.mapCanvas).getLocationOnScreen(mapPosition)
                    val pinView = activity.findViewById<View>(R.id.centerPickerPin)
                    val pinPosition = IntArray(2)
                    pinView.getLocationOnScreen(pinPosition)
                    val projected = googleMap(picker)!!.projection.toScreenLocation(selected)
                    assertEquals("The selected coordinate must stay under the pin tip",
                        (pinPosition[0] + pinView.width / 2 - mapPosition[0]).toDouble(), projected.x.toDouble(), 2.0)
                    assertEquals("The selected coordinate must stay under the pin tip",
                        (pinPosition[1] + pinView.height - mapPosition[1]).toDouble(), projected.y.toDouble(), 2.0)
                }
            }
            checkPin()
            // Reproduce a real user drag, not a programmatic camera move. Test both
            // before and after keyboard/fullscreen changes so the picker cannot stay locked.
            fun movePin(reverse: Boolean = false) {
                scenario.onActivity { activity ->
                    BottomSheetBehavior.from(activity.findViewById<NestedScrollView>(R.id.mapBottomSheet))
                        .state = BottomSheetBehavior.STATE_COLLAPSED
                }
                SystemClock.sleep(700)
                onView(withId(R.id.mapCanvas)).perform(GeneralSwipeAction(Swipe.SLOW,
                    { view -> val p = IntArray(2); view.getLocationOnScreen(p)
                        floatArrayOf(p[0] + view.width * if (reverse) 0.7f else 0.3f, p[1] + view.height * 0.4f) },
                    { view -> val p = IntArray(2); view.getLocationOnScreen(p)
                        floatArrayOf(p[0] + view.width * if (reverse) 0.3f else 0.7f, p[1] + view.height * 0.4f) }, Press.FINGER))
                SystemClock.sleep(700)
                scenario.onActivity { activity ->
                    val moved = controller(activity).selectedCenter()!!
                    assertTrue("Dragging must choose a different location",
                        kotlin.math.abs(moved.longitude - expectedLongitude) > 0.000001)
                    expectedLatitude = moved.latitude
                    expectedLongitude = moved.longitude
                }
                checkPin()
                SystemClock.sleep(1000)
                checkPin() // A later layout or GPS callback must not snap back to the old pin.
            }
            movePin()
            movePin(reverse = true)
            scenario.onActivity { activity ->
                val field = activity.findViewById<EditText>(R.id.edtPlaceLabel)
                field.requestFocus()
                field.setText("Main entrance, Sikatuna")
                (activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                    .showSoftInput(field, InputMethodManager.SHOW_IMPLICIT)
            }
            SystemClock.sleep(1000)
            checkPin()
            repeat(2) {
                scenario.onActivity { it.findViewById<View>(R.id.btnPickerExpand).performClick() }
                SystemClock.sleep(1000)
                checkPin()
            }
            movePin()
            movePin(reverse = true)
        }
    }
}
