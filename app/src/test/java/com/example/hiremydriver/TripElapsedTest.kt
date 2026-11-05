package com.example.hiremydriver

import com.example.hiremydriver.trip.*
import org.junit.Assert.*
import org.junit.Test

class TripElapsedTest {
    private val trip = TripSession("id", "Driver", "Customer", "Pickup", "Destination")
    @Test fun timerRunsOnlyAfterServiceStarts() {
        for (phase in listOf(TripPhase.REQUESTED,TripPhase.ACCEPTED,TripPhase.ON_THE_WAY,TripPhase.ARRIVED,TripPhase.DECLINED))
            assertNull(trip.copy(phase=phase,startedAt=1000).elapsedMillis(4000))
        assertEquals(3000L,trip.copy(phase=TripPhase.IN_PROGRESS,startedAt=1000).elapsedMillis(4000))
    }
    @Test fun completedDurationStaysFixedAcrossReopeningAndClockChanges() {
        val done = trip.copy(phase=TripPhase.COMPLETED,startedAt=1000,completedAt=65000)
        assertEquals(64000L,done.elapsedMillis(70000))
        assertEquals(done.elapsedMillis(70000),done.copy().elapsedMillis(9999999))
        assertNull(done.copy(completedAt=null).elapsedMillis(9999999))
    }
}
