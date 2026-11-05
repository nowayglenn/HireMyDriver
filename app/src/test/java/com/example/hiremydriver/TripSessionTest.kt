package com.example.hiremydriver

import com.example.hiremydriver.trip.*
import org.junit.Assert.*
import org.junit.Test

class TripSessionTest {
    private val pickup=TripPlace("Sikatuna",9.688,123.974)
    private val first=TripPlace("First destination",9.65,123.86)
    private val second=TripPlace("Another destination",9.66,123.87)
    private fun booking()=TripSession("booking-a","Driver","Customer",pickup.label,first.label,pickup,listOf(first),4)
    @Test fun timerRequiresAcceptanceArrivalAndCustomerOnBoard(){
        val pending=booking()
        assertNull(pending.start(1000,true).startedAt)
        val accepted=pending.accept()
        assertNull(accepted.start(1000,true).startedAt)
        val arrived=accepted.headToPickup().arrive()
        assertNull(arrived.start(1000,false).startedAt)
        val started=arrived.start(1000,true)
        assertEquals(1000L,started.startedAt)
        assertEquals(started,started.start(5000,true))
    }
    @Test fun requestedStopDoesNotChangeRouteUntilAcceptedOrRestartTimer(){
        val active=booking().accept().headToPickup().arrive().start(1000,true)
        val pending=active.requestStop(second)
        assertEquals(listOf(first),pending.destinations)
        assertEquals(pending,pending.requestStop(pickup))
        val accepted=pending.resolveStop(true)
        assertEquals(listOf(first,second),accepted.destinations)
        assertNull(accepted.pendingDestination)
        assertEquals(1000L,accepted.startedAt)
        assertEquals(3*3_600_000L,accepted.remainingMillis(3_601_000))
        assertEquals(listOf(first),pending.resolveStop(false).destinations)
    }
    @Test fun reachedStopIsSavedBeforeCompletingService(){
        val active=booking().accept().headToPickup().arrive().start(1000,true).reachStop()
        assertNull(active.nextDestination())
        assertEquals(TripPhase.IN_PROGRESS,active.phase)
        assertEquals(0,active.remainingMillis(20_000_000).toInt())
        assertEquals(TripPhase.IN_PROGRESS,active.phase)
        val completed=active.complete(3_601_000)
        assertEquals(TripPhase.COMPLETED,completed.phase)
        assertEquals(completed,completed.requestStop(second))
        assertEquals(completed.remainingMillis(4_000_000),completed.remainingMillis(8_000_000))
    }
    @Test fun onlyFinalDestinationCompletesBookingEvenWithTimeRemaining(){
        val active=booking().copy(destinations=listOf(first,second,pickup))
            .accept().headToPickup().arrive().start(1000,true)
        assertEquals(active,active.completeAtLastDestination(2000))
        val atFirst=active.reachStop().completeAtLastDestination(2000)
        assertEquals(TripPhase.IN_PROGRESS,atFirst.phase)
        assertEquals(second,atFirst.nextDestination())
        val atSecond=atFirst.reachStop().completeAtLastDestination(3000)
        assertEquals(TripPhase.IN_PROGRESS,atSecond.phase)
        assertEquals(pickup,atSecond.nextDestination())
        val completed=atSecond.reachStop().completeAtLastDestination(4000)
        assertEquals(TripPhase.COMPLETED,completed.phase)
        assertEquals(3,completed.reachedStops)
        assertTrue(completed.remainingMillis(4000)>0)
        assertEquals(completed,completed.completeAtLastDestination(5000))
    }
    @Test fun newlyAcceptedDestinationMustBeReachedBeforeCompletion(){
        val atFirst=booking().accept().headToPickup().arrive().start(1000,true).reachStop()
        val withExtra=atFirst.requestStop(second).resolveStop(true)
        assertFalse(withExtra.hasReachedLastDestination())
        assertEquals(withExtra,withExtra.completeAtLastDestination(2000))
        assertEquals(TripPhase.COMPLETED,withExtra.reachStop().completeAtLastDestination(3000).phase)
    }
    @Test fun emptyRouteCannotCompleteThroughReachedDestination(){
        val active=booking().copy(destinations=emptyList()).accept().headToPickup().arrive().start(1000,true)
        assertEquals(active,active.completeAtLastDestination(2000))
    }
    @Test fun coordinatesAndDurationAreValidated(){
        assertThrows(IllegalArgumentException::class.java){TripPlace("Invalid",Double.NaN,123.0)}
        assertThrows(IllegalArgumentException::class.java){TripPlace("Invalid",9.0,181.0)}
        assertThrows(IllegalArgumentException::class.java){booking().copy(durationHours=0)}
    }
    @Test fun completedBookingRejectsLateApproval(){
        val completed=booking().accept().headToPickup().arrive().start(1000,true).requestStop(second).complete(2000)
        assertNull(completed.pendingDestination)
        assertEquals(completed,completed.resolveStop(true))
    }
}
