package com.example.hiremydriver

import com.example.hiremydriver.maps.RoadRoute
import com.example.hiremydriver.maps.RouteCoordinate
import com.example.hiremydriver.trip.TripPhase
import com.example.hiremydriver.trip.TripPlace
import com.example.hiremydriver.trip.TripSession
import org.junit.Assert.*
import org.junit.Test

class RoadRouteTest {
    private val pickup = TripPlace("Sikatuna", 9.6879, 123.9740)
    private val first = TripPlace("Tagbilaran", 9.6500, 123.8550)
    private val extra = TripPlace("Panglao", 9.58, 123.75)
    private fun trip() = TripSession("route-test", "Driver", "Customer", pickup.label, first.label,
        pickup = pickup, destinations = listOf(first))

    @Test fun decodesKnownRoadGeometryIncludingNegativeCoordinates() {
        val points = RoadRoute.decode("_p~iF~ps|U_ulLnnqC_mqNvxq`@")
        assertEquals(listOf(RouteCoordinate(38.5, -120.2), RouteCoordinate(40.7, -120.95),
            RouteCoordinate(43.252, -126.453)), points)
    }

    @Test fun rejectsTruncatedOrInvalidGeometry() {
        listOf("_", "?", "!", "~~~~~~~~~~~~~").forEach { encoded ->
            try { RoadRoute.decode(encoded); fail("Accepted malformed geometry") }
            catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun pendingStopDoesNotChangeConfirmedRouteButAcceptedStopDoes() {
        val active = trip().copy(phase = TripPhase.IN_PROGRESS, startedAt = 1L)
        val requested = active.requestStop(extra)
        assertEquals(RoadRoute.itinerary(active), RoadRoute.itinerary(requested))
        assertEquals(3, RoadRoute.itinerary(requested.resolveStop(true)).size)
        assertEquals(RoadRoute.itinerary(active), RoadRoute.itinerary(requested.resolveStop(false)))
    }

    @Test fun gpsTicksAndTripProgressDoNotTriggerNewPlannedRouteRequests() {
        val original = trip()
        val moved = original.copy(driverLocation = extra, locationAt = 100L,
            phase = TripPhase.IN_PROGRESS, reachedStops = 1)
        assertEquals(RoadRoute.itinerary(original), RoadRoute.itinerary(moved))
        assertEquals(RoadRoute.itinerary(original), RoadRoute.itinerary(moved.complete(200L)))
    }

    @Test fun splitsLargeItinerariesWithoutReorderingAndWithinEssentialsLimit() {
        val points = (0..25).map { RouteCoordinate(9.0 + it / 100.0, 123.0) }
        val batches = RoadRoute.batches(points)
        assertEquals(3, batches.size)
        assertTrue(batches.all { it.size in 2..12 })
        assertEquals(batches[0].last(), batches[1].first())
        assertEquals(batches[1].last(), batches[2].first())
        assertEquals(points, batches.first() + batches.drop(1).flatMap { it.drop(1) })
    }

    @Test fun removesConsecutiveDuplicatePinsButKeepsReturnJourneys() {
        val a = RouteCoordinate(9.0, 123.0)
        val b = RouteCoordinate(9.1, 123.1)
        assertEquals(listOf(listOf(a, b, a)), RoadRoute.batches(listOf(a, a, b, b, a)))
        assertTrue(RoadRoute.batches(listOf(a, a)).isEmpty())
    }

    @Test fun unpinnedOrDeclinedBookingsDoNotInventRoutes() {
        assertTrue(RoadRoute.itinerary(trip().copy(pickup = null)).isEmpty())
        assertTrue(RoadRoute.itinerary(trip().copy(destinations = emptyList())).isEmpty())
        assertTrue(RoadRoute.itinerary(trip().decline("Unavailable")).isEmpty())
    }
}
