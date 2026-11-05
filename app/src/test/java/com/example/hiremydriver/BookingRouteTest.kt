package com.example.hiremydriver

import com.example.hiremydriver.trip.*
import org.junit.Assert.*
import org.junit.Test

class BookingRouteTest {
    @Test fun eachLocationHasItsOwnCaptionWithoutNumberingThePlace() {
        val entries = bookingRoute("Home", listOf("Mall", "School", "Balilihan"))
        assertEquals(listOf("Pickup", "Destination 1", "Destination 2", "Destination 3"), entries.map { it.caption })
        assertEquals(listOf("Home", "Mall", "School", "Balilihan"), entries.map { it.place })
    }
    @Test fun completedHistoryRetainsAllConfirmedStopsButNotPendingRequests() {
        val stops = listOf(TripPlace("Mall", 9.6, 123.9), TripPlace("School", 9.7, 123.8))
        val trip = TripSession("id", "Driver", "Customer", "Home", "Mall", destinations = stops)
            .accept().headToPickup().arrive().start(1000, true).reachStop().reachStop()
        val requested = trip.requestStop(TripPlace("Unconfirmed", 9.8, 123.7))
        assertEquals(trip.bookingRoute(), requested.bookingRoute())
        assertEquals(trip.bookingRoute(), requested.complete(2000).bookingRoute())
        assertEquals(3, requested.complete(2000).bookingRoute().size)
    }
    @Test fun legacySingleDestinationAndRepeatedPlacesKeepTheirRows() {
        val trip = TripSession("id", "Driver", "Customer", "Home", "Mall")
        assertEquals(listOf("Pickup", "Destination 1"), trip.bookingRoute().map { it.caption })
        assertEquals(3, bookingRoute("Home", listOf("Home", "Home")).size)
    }
}
