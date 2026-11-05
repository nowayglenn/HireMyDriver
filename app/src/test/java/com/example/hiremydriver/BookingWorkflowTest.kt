package com.example.hiremydriver

import com.example.hiremydriver.trip.*
import com.example.hiremydriver.maps.RoadRoute
import com.example.hiremydriver.maps.RouteCoordinate
import org.junit.Assert.*
import org.junit.Test

class BookingWorkflowTest {
    private fun booking() = TripSession("booking-id", "Driver", "Customer", "Pickup", "Destination",
        pickup = TripPlace("Pickup", 9.7, 123.9),
        destinations = listOf(TripPlace("Destination", 9.8, 124.0)))

    @Test fun acceptanceAndMapPreviewDoNotDepartOrStartService() {
        val accepted = booking().accept()
        val preview = accepted.copy(driverLocation = TripPlace("Current location", 9.6, 123.8))
        assertEquals("Accepted", TripUi.status(accepted))
        assertEquals("Start Going to Pickup", DriverBookingAction.label(accepted))
        assertEquals(listOf(RouteCoordinate(9.6, 123.8), RouteCoordinate(9.7, 123.9)), RoadRoute.toPickup(preview))
        assertEquals(TripPhase.ACCEPTED, preview.phase)
        assertEquals(accepted.destinations, preview.destinations)
        assertEquals(accepted, accepted.arrive())
        assertEquals(accepted, accepted.start(1000, true))
        assertNull(accepted.startedAt)
    }

    @Test fun confirmedSequenceHasDistinctLabelsAndDoesNotRestartTimer() {
        val accepted = booking().accept()
        val heading = accepted.headToPickup()
        assertEquals("Driver on the way", TripUi.status(heading))
        assertEquals("I've Arrived", DriverBookingAction.label(heading))
        assertEquals(heading, heading.headToPickup())
        val arrived = heading.arrive()
        assertEquals("Driver has arrived", TripUi.status(arrived))
        assertEquals("Start Service", DriverBookingAction.label(arrived))
        assertEquals(arrived, arrived.arrive())
        val active = arrived.start(1000, true)
        assertEquals("In Progress", TripUi.status(active))
        assertEquals(1000L, active.startedAt)
        assertEquals(active, active.start(5000, true))
        assertEquals(active, active.headToPickup())
        assertEquals(active, active.arrive())
        assertEquals(accepted.id, active.id)
        assertEquals(accepted.customerName, active.customerName)
        assertEquals(accepted.destinations, active.destinations)
    }

    @Test fun departureCannotSkipAcceptanceAndTerminalBookingsCannotReopen() {
        val pending = booking()
        assertEquals(pending, pending.headToPickup())
        assertEquals(pending, pending.arrive())
        val completed = pending.accept().headToPickup().arrive().start(1000, true).complete(2000)
        assertEquals(completed, completed.accept().headToPickup().arrive().start(3000, true))
        val declined = pending.decline("Unavailable")
        assertEquals(declined, declined.accept().headToPickup().arrive())
        assertEquals(CustomerBookingGroup.ACCEPTED, pending.accept().headToPickup().customerGroup())
        assertEquals(DriverBookingGroup.UPCOMING, pending.accept().headToPickup().driverGroup())
    }
}
