package com.example.hiremydriver

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DriverNavigationGateTest {
    @Test fun rapidMixedTabTapsLaunchOnlyTheFirstDestination() {
        val gate = DriverNavigationGate()
        assertTrue(gate.tryNavigate("Home", "Bookings", 1000))
        repeat(300) { offset ->
            val target = if (offset % 2 == 0) "Messages" else "Profile"
            assertFalse(gate.tryNavigate("Home", target, 1001L + offset))
        }
    }

    @Test fun newlyOpenedScreenRejectsQueuedTapsButAllowsNextDeliberateSelection() {
        val gate = DriverNavigationGate()
        assertTrue(gate.tryNavigate("Home", "Bookings", 1000))
        assertFalse(gate.tryNavigate("Bookings", "Profile", 1100))
        assertFalse(gate.tryNavigate("Bookings", "Messages", 1349))
        assertTrue(gate.tryNavigate("Bookings", "Messages", 1350))
        assertTrue(gate.tryNavigate("Messages", "Home", 1700))
    }

    @Test fun tappingCurrentTabDoesNotReloadOrDelayTheNextSelection() {
        val gate = DriverNavigationGate()
        repeat(100) { assertFalse(gate.tryNavigate("Home", "Home", it.toLong())) }
        assertTrue(gate.tryNavigate("Home", "Profile", 100))
    }

    @Test fun rejectedSpamDoesNotKeepExtendingTheTransitionInterval() {
        val gate = DriverNavigationGate()
        assertTrue(gate.tryNavigate("Profile", "Home", 1000))
        repeat(349) { offset ->
            assertFalse(gate.tryNavigate("Home", "Bookings", 1001L + offset))
        }
        assertTrue(gate.tryNavigate("Home", "Messages", 1350))
    }
}
