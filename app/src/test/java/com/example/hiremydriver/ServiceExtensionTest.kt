package com.example.hiremydriver

import com.example.hiremydriver.trip.*
import com.example.hiremydriver.recommendation.RegisteredSchedule
import org.junit.Assert.*
import org.junit.Test

class ServiceExtensionTest {
    private fun trip(phase: TripPhase = TripPhase.IN_PROGRESS) = TripSession("id","Driver","Customer","A","B",
        durationHours=5, phase=phase, startedAt=100000L, fee="₱2,500.00",hourlyRate=500)
    @Test fun pendingRequestDoesNotChangeTimeOrMoney() {
        val before=trip(); val pending=before.requestExtension(30)
        assertEquals("PENDING",pending.extensionStatus); assertEquals(before.scheduledEnd(),pending.scheduledEnd())
        assertEquals(before.totalAmount(),pending.totalAmount(),0.0)
        assertEquals(pending,pending.requestExtension(60))
    }
    @Test fun approvalUpdatesTimerAndAmountExactlyOnce() {
        val pending=trip().requestExtension(30); val approved=pending.resolveExtension(true)
        assertEquals(330,approved.totalMinutes()); assertEquals(2750.0,approved.totalAmount(),0.0)
        assertEquals(pending.scheduledEnd()!! + 1800000,approved.scheduledEnd())
        assertEquals(pending.remainingMillis(200000) + 1800000,approved.remainingMillis(200000))
        assertEquals(approved,approved.resolveExtension(true))
    }
    @Test fun declineAndCompletionCannotIncreaseDuration() {
        val pending=trip().requestExtension(30); val declined=pending.resolveExtension(false)
        assertEquals(300,declined.totalMinutes()); assertEquals(2500.0,declined.totalAmount(),0.0)
        val done=pending.complete(200000); assertEquals(done,done.resolveExtension(true))
    }
    @Test fun cancelOnlyBeforeServiceAndRequiresReason() {
        for (phase in TripPhase.entries) {
            val t=trip(phase); val cancelled=t.cancel("Change of plans")
            assertEquals(t.canCancel(),cancelled.phase == TripPhase.CANCELLED && phase != TripPhase.CANCELLED)
            assertEquals(t,t.cancel(" "))
        }
    }
    @Test fun invalidOrOversizedExtensionsAreRejected() {
        val t=trip(); for (minutes in listOf(-1,0,1440,Int.MAX_VALUE)) assertEquals(t,t.requestExtension(minutes))
    }
    @Test fun pendingCardCancellationCannotCancelAnAlreadyAcceptedRequest() {
        val request = trip(TripPhase.REQUESTED)
        val cancelled = request.cancelPending("  Change of plans  ")
        assertEquals(TripPhase.CANCELLED, cancelled.phase)
        assertEquals("Change of plans", cancelled.cancellationReason)
        assertEquals("Cancelled: Change of plans", cancelled.requestStatus)
        assertEquals(request, request.cancelPending("  "))
        assertEquals(request, request.cancelPending("x".repeat(1001)))
        for (phase in TripPhase.entries.filter { it != TripPhase.REQUESTED }) {
            val changed = request.copy(phase = phase)
            assertEquals(changed, changed.cancelPending("Change of plans"))
        }
    }
    @Test fun driverCanDeclinePendingRequestWithoutSelectingAReason() {
        val request = trip(TripPhase.REQUESTED)
        val declined = request.decline()
        assertEquals(TripPhase.DECLINED, declined.phase)
        assertEquals("Booking declined", declined.requestStatus)
        assertEquals(declined, declined.decline())
        val accepted = request.accept()
        assertEquals(accepted, accepted.decline())
    }
    @Test fun repeatedApprovedExtensionsAccumulateAndUseDistinctSequence() {
        val t=trip().requestExtension(30).resolveExtension(true).requestExtension(60).resolveExtension(true)
        assertEquals(90,t.extensionMinutes); assertEquals(2,t.extensionSequence); assertEquals(3250.0,t.totalAmount(),0.0)
    }
    @Test fun legacyBookingRateIsReadWithoutInventingAPrice() {
        assertEquals(500.0,trip().copy(hourlyRate=0).rate(),0.0)
    }
    @Test fun scheduleRejectsEndAfterWorkingHours() {
        val profile=mapOf("matching" to mapOf("weekly" to List(7) { mapOf("configured" to true,"enabled" to true,"start" to 480,"end" to 1020) }))
        val start=RegisteredSchedule.bookingStart(mapOf("date" to "Oct 20, 2026","time" to "3:00 PM"))!!
        assertTrue(RegisteredSchedule.withinHours(profile,start,start+2*3600000))
        assertFalse(RegisteredSchedule.withinHours(profile,start,start+4*3600000))
        assertFalse(RegisteredSchedule.withinHours(emptyMap(),start,start+3600000))
    }
    @Test fun approvedExtraMinutesBlockOtherBookings() {
        val slot=mapOf("date" to "Oct 20, 2026","time" to "10:00 AM","hours" to 1,"phase" to "IN_PROGRESS",
            "started" to RegisteredSchedule.bookingStart(mapOf("date" to "Oct 20, 2026","time" to "10:00 AM"))!!,"extensionMinutes" to 30)
        val start=RegisteredSchedule.bookingStart(mapOf("date" to "Oct 20, 2026","time" to "11:00 AM"))!!
        assertTrue(RegisteredSchedule.conflicts(slot,start,start+3600000,0))
    }
}
