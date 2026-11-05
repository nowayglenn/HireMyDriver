package com.example.hiremydriver

import com.example.hiremydriver.trip.TripNotificationState
import com.example.hiremydriver.trip.TripPhase
import com.example.hiremydriver.trip.TripSession
import org.junit.Assert.*
import org.junit.Test

class TripNotificationStateTest {
    private val booking = TripSession("test", "Driver", "Customer", "Pickup", "Destination", durationHours = 2)

    @Test fun timerOnlyAppearsAfterCustomerIsOnBoard() {
        assertNull(TripNotificationState.from(booking))
        val arrived = booking.accept().headToPickup().arrive()
        assertNull(TripNotificationState.from(arrived.start(1000, false)))
        val active = arrived.start(1000, true)
        assertEquals(TripNotificationState(1000, 2), TripNotificationState.from(active))
    }

    @Test fun reopeningOrRefreshingKeepsOriginalChronometerBase() {
        val active = booking.accept().headToPickup().arrive().start(1000, true)
        assertEquals(TripNotificationState.from(active), TripNotificationState.from(active.start(5000, true)))
        assertEquals(1000L, TripNotificationState.from(active.copy(requestStatus = "Updated"))!!.startedAt)
    }

    @Test fun completionRemovesTimerAndMissingStartDoesNotInventOne() {
        val active = booking.accept().headToPickup().arrive().start(1000, true)
        assertNull(TripNotificationState.from(active.complete(2000)))
        assertNull(TripNotificationState.from(booking.copy(phase = TripPhase.IN_PROGRESS)))
    }

    @Test fun bookedTimeEndingDoesNotCompleteServiceAutomatically() {
        val active = booking.accept().headToPickup().arrive().start(1000, true)
        assertEquals(0L, active.remainingMillis(10_000_000))
        assertNotNull(TripNotificationState.from(active))
    }
}
