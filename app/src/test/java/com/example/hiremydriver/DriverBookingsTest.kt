package com.example.hiremydriver

import com.example.hiremydriver.trip.*
import com.example.hiremydriver.maps.RoadRoute
import org.junit.Assert.*
import org.junit.Test

class DriverBookingsTest {
    private fun trip(id: String) = TripSession(id, "Driver", "Customer", "Pickup", "Destination",
        pickup = TripPlace("Pickup", 9.7, 123.9), destinations = listOf(TripPlace("Destination", 9.8, 124.0)),
        driverUid = "driver", customerUid = "customer", synced = true, date = "Oct 16, 2026", time = "10:00 AM")

    @Test fun onlyCurrentDriversSyncedBookingsAppearWithoutDuplicates() {
        val own = trip("one")
        val records = listOf(own, own, trip("other").copy(driverUid = "another"), trip("demo").copy(synced = false))
        assertEquals(listOf(own), records.forDriver("driver"))
        assertTrue(records.forDriver("").isEmpty())
    }

    @Test fun scheduleOrderIsStableAndNotPreferenceMapOrder() {
        val earlier = trip("early").copy(time = "9:00 AM")
        val later = trip("late").copy(date = "Oct 17, 2026")
        assertEquals(listOf("early", "one", "late"), listOf(later, trip("one"), earlier).forDriver("driver").map { it.id })
    }

    @Test fun confirmedAcceptanceMovesRequestToUpcomingAndCompletionRemovesIt() {
        val pending = trip("one")
        val accepted = pending.accept()
        assertEquals(DriverBookingGroup.REQUESTS, pending.driverGroup())
        assertEquals(DriverBookingGroup.UPCOMING, accepted.driverGroup())
        assertEquals(DriverBookingGroup.DECLINED, pending.decline("Busy").driverGroup())
        assertEquals(DriverBookingGroup.COMPLETED, accepted.headToPickup().arrive().start(1000, true).complete(2000).driverGroup())
    }

    @Test fun newlyCompletedBookingMovesAboveOlderCompletionsRegardlessOfScheduledDate() {
        val old = trip("old").copy(date = "Oct 20, 2026", phase = TripPhase.COMPLETED, completedAt = 2000)
        val active = trip("new").copy(date = "Oct 16, 2026", phase = TripPhase.IN_PROGRESS, startedAt = 1000)
        val initial = listOf(old, active).forDriver("driver")
        assertEquals(listOf("old"), initial.completedNewestFirst().map { it.id })
        val updated = listOf(old, active.complete(3000)).forDriver("driver")
        assertEquals(listOf("new", "old"), updated.completedNewestFirst().map { it.id })
    }

    @Test fun completionOrderIsStableAndMissingTimestampsStayLast() {
        val a = trip("a").copy(phase = TripPhase.COMPLETED, completedAt = 2000)
        val b = a.copy(id = "b")
        val legacy = a.copy(id = "legacy", completedAt = null)
        assertEquals(listOf("a", "b", "legacy"),
            listOf(legacy, b, a).completedNewestFirst().map { it.id })
        assertEquals(listOf("a", "b", "legacy"),
            listOf(a, b, legacy).completedNewestFirst().map { it.id })
    }

    @Test fun pendingMapRouteIsPickupToDestinationWithoutMutatingBooking() {
        val pending = trip("one")
        val route = RoadRoute.itinerary(pending)
        assertEquals(2, route.size)
        assertEquals(pending.pickup!!.latitude, route.first().latitude, 0.0)
        assertEquals(pending.destinations.single().latitude, route.last().latitude, 0.0)
        assertEquals(TripPhase.REQUESTED, pending.phase)
        assertNull(pending.startedAt)
    }
}
