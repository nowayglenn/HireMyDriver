package com.example.hiremydriver

import com.example.hiremydriver.recommendation.BusySchedule
import org.junit.Assert.*
import org.junit.Test

class BookingAvailabilityTest {
    private val booking = mapOf<String, Any>("date" to "Oct 20, 2026", "time" to "9:15 PM",
        "hours" to 3, "phase" to "IN_PROGRESS", "started" to 100000L)

    @Test fun firestoreLongsAndModelIntsHaveTheSameSlot() {
        val stored = booking + mapOf("hours" to 3L, "extensionMinutes" to 0L)
        assertEquals(BusySchedule.slot(stored), BusySchedule.slot(booking))
    }

    @Test fun customerExtensionDoesNotWriteDriverProfileEvenIfSlotNeedsRepair() {
        val request = booking + mapOf("requestedMinutes" to 30, "extensionStatus" to "PENDING", "extensionSequence" to 1)
        assertFalse(BusySchedule.needsBookingUpdate(booking, request, false))
        assertEquals(BusySchedule.slot(booking), BusySchedule.slot(request))
    }

    @Test fun approvalReservesExtraTimeAndCancellationReleasesIt() {
        val approved = booking + mapOf("extensionMinutes" to 30, "extensionStatus" to "APPROVED")
        assertTrue(BusySchedule.needsBookingUpdate(booking, approved, true))
        assertNotEquals(BusySchedule.slot(booking), BusySchedule.slot(approved))
        val accepted = booking + ("phase" to "ACCEPTED")
        val cancelled = accepted + ("phase" to "CANCELLED")
        assertTrue(BusySchedule.needsBookingUpdate(accepted, cancelled, false))
        assertNull(BusySchedule.slot(cancelled))
    }

    @Test fun customerDestinationRequestDoesNotRewriteAvailability() {
        assertFalse(BusySchedule.needsBookingUpdate(booking,
            booking + ("request" to "Waiting for driver confirmation"), false))
    }
}
