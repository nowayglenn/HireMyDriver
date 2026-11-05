package com.example.hiremydriver

import com.example.hiremydriver.maps.NavigationMarkerStyle
import org.junit.Assert.*
import org.junit.Test

class NavigationMarkerStyleTest {
    @Test fun repeatedSensorSamplesDoNotResubmitTheIconOrStationaryRotation() {
        val style = NavigationMarkerStyle()
        assertTrue(style.update(90f, true).iconChanged)
        repeat(1000) {
            val changes = style.update(90f + (it % 3) * 0.5f, true)
            assertFalse(changes.iconChanged)
            assertNull(changes.rotation)
        }
        assertEquals(93f, style.update(93f, true).rotation!!, 0f)
    }

    @Test fun headingWrapAndLossOfCompassKeepTheCorrectMarker() {
        val style = NavigationMarkerStyle()
        style.update(359f, true)
        assertNull(style.update(0f, true).rotation)
        assertEquals(2f, style.update(2f, true).rotation!!, 0f)
        assertTrue(style.update(2f, false).iconChanged)
        assertFalse(style.update(2f, false).iconChanged)
        assertTrue(style.update(2f, true).iconChanged)
    }
}
