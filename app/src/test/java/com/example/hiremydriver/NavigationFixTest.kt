package com.example.hiremydriver

import com.example.hiremydriver.maps.NavigationFix
import org.junit.Assert.*
import org.junit.Test

class NavigationFixTest {
    private fun fix(time: Long, latitude: Double = 9.6879, longitude: Double = 123.974,
                    accuracy: Float = 4f, bearing: Float? = null, speed: Float? = null) =
        NavigationFix.Sample(latitude, longitude, time, accuracy, bearing, speed)

    @Test fun rejectsStaleInaccurateAndOutOfOrderPositions() {
        val state = NavigationFix()
        assertFalse(state.accept(fix(1), 20_000))
        assertFalse(state.accept(fix(20_000, accuracy = 100f), 20_000))
        assertTrue(state.accept(fix(20_000), 20_000))
        assertFalse(state.accept(fix(19_000), 20_000))
        assertFalse(state.accept(fix(21_000), 20_000))
        assertFalse(state.accept(fix(21_000, latitude = Double.NaN), 21_000))
    }

    @Test fun stationaryNoiseDoesNotInventOrChangeDirection() {
        val state = NavigationFix()
        state.accept(fix(1000, bearing = 120f, speed = 0f), 1000)
        assertNull(state.heading)
        state.accept(fix(2000, bearing = 90f, speed = 5f), 2000)
        assertEquals(90f, state.heading!!, 0.01f)
        state.accept(fix(3000, latitude = 9.687901, bearing = 240f, speed = 0f), 3000)
        assertEquals(90f, state.heading!!, 0.01f)
    }

    @Test fun recenterPrefersNewestValidProviderInsteadOfWaitingForGps() {
        val gps = fix(90_000, accuracy = 5f)
        val network = fix(99_000, accuracy = 30f)
        val invalid = fix(100_000, accuracy = 900f)
        assertEquals(network, NavigationFix.newestUsable(listOf(gps, invalid, network), 100_000))
        assertEquals(gps, NavigationFix.newestUsable(listOf(gps, fix(90_000, accuracy = 40f)), 100_000))
    }

    @Test fun recenterNeverUsesAnExpiredOrInvalidCachedLocation() {
        assertNull(NavigationFix.newestUsable(listOf(fix(1), fix(100_001),
            fix(100_000, latitude = Double.NaN), fix(100_000, accuracy = 200f)), 100_000))
        val live = fix(100_000)
        assertTrue(NavigationFix.isUsable(live, 100_000))
        assertFalse(NavigationFix.isUsable(live, 116_000))
    }

    @Test fun derivesHeadingAfterReliableDisplacementWhenGpsHasNoBearing() {
        val state = NavigationFix()
        state.accept(fix(1000), 1000)
        state.accept(fix(2000, longitude = 123.975), 2000)
        assertEquals(90f, state.heading!!, 0.1f)
    }

    @Test fun turnsAcrossNorthByShortestDirection() {
        assertEquals(0f, NavigationFix.interpolateHeading(350f, 10f, 0.5f), 0.01f)
        assertEquals(0f, NavigationFix.interpolateHeading(10f, 350f, 0.5f), 0.01f)
        assertEquals(270f, NavigationFix.normalize(-90f), 0.01f)
    }
}
