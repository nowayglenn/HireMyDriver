package com.example.hiremydriver

import com.example.hiremydriver.recommendation.RegisteredSchedule
import com.example.hiremydriver.trip.*
import org.junit.Assert.*
import org.junit.Test

class ExtensionApprovalTest {
    private fun start(date: String, time: String) =
        RegisteredSchedule.bookingStart(mapOf("date" to date, "time" to time))!!
    private val pending = TripSession("current", "Driver", "Customer", "A", "B",
        durationHours = 3, phase = TripPhase.IN_PROGRESS,
        startedAt = start("Oct 20, 2026", "9:15 PM"), hourlyRate = 6000)
        .requestExtension(30)
    private val regularHours = mapOf<String, Any?>(
        "matching" to mapOf("weekly" to List(7) {
            mapOf("configured" to true, "enabled" to true, "start" to 360, "end" to 1080)
        }), "busySlots" to emptyMap<String, Any>())

    @Test fun driverCanApproveOngoingServiceAcrossMidnightOutsideRegularHours() {
        val after = pending.resolveExtension(true)
        assertFalse(RegisteredSchedule.withinHours(regularHours, pending.startedAt!!, after.scheduledEnd()!!))
        assertNull(extensionApprovalIssue(regularHours, pending, after, pending.startedAt!!))
        assertEquals(start("Oct 21, 2026", "12:45 AM"), after.scheduledEnd())
        assertEquals(210, after.totalMinutes())
        assertEquals(21000.0, after.totalAmount(), 0.0)
    }

    private fun reserved(time: String) = mapOf<String, Any>(
        "date" to "Oct 21, 2026", "time" to time, "hours" to 1,
        "phase" to "ACCEPTED", "started" to 0L)

    @Test fun addedTimeCannotOverlapAnotherConfirmedBooking() {
        val profile = regularHours + ("busySlots" to mapOf("other" to reserved("12:30 AM")))
        assertEquals(ExtensionApprovalIssue.BOOKING_CONFLICT,
            extensionApprovalIssue(profile, pending, pending.resolveExtension(true), pending.startedAt!!))
    }

    @Test fun touchingNextBookingBoundaryIsAllowedAndOwnReservationIsExcluded() {
        val profile = regularHours + ("busySlots" to mapOf(
            "other" to reserved("12:45 AM"), pending.id to reserved("12:15 AM")))
        assertNull(extensionApprovalIssue(profile, pending, pending.resolveExtension(true), pending.startedAt!!))
    }

    @Test fun missingAvailabilityCannotBypassConflictChecks() {
        assertEquals(ExtensionApprovalIssue.AVAILABILITY_UNAVAILABLE,
            extensionApprovalIssue(emptyMap(), pending, pending.resolveExtension(true), 0))
    }

    @Test fun staleCompletedAndOversizedRequestsAreRejected() {
        val completed = pending.copy(phase = TripPhase.COMPLETED)
        val oversized = pending.copy(durationHours = 24)
        for (before in listOf(completed, oversized, pending.copy(startedAt = null))) {
            assertEquals(ExtensionApprovalIssue.INVALID_REQUEST,
                extensionApprovalIssue(regularHours, before, before.resolveExtension(true), 0))
        }
        assertEquals(ExtensionApprovalIssue.INVALID_REQUEST, extensionApprovalIssue(regularHours, pending,
            pending.resolveExtension(true).copy(extensionMinutes = 60), 0))
    }
}
