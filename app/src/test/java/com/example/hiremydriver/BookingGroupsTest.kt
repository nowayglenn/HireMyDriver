package com.example.hiremydriver

import com.example.hiremydriver.trip.*
import org.junit.Assert.*
import org.junit.Test

class BookingGroupsTest {
    private fun booking(id: String = "one") = TripSession(id, "Driver", "Customer", "Pickup", "Destination")

    @Test fun bookingMovesThroughOriginalTemplateCategoriesWithoutStartingTimerEarly() {
        val request = booking()
        assertEquals(CustomerBookingGroup.PENDING, request.customerGroup())
        assertEquals(DriverBookingGroup.REQUESTS, request.driverGroup())
        val accepted = request.accept()
        assertEquals(CustomerBookingGroup.ACCEPTED, accepted.customerGroup())
        assertEquals(DriverBookingGroup.UPCOMING, accepted.driverGroup())
        assertNull(accepted.startedAt)
        val arrived = accepted.headToPickup().arrive()
        assertEquals(DriverBookingGroup.UPCOMING, arrived.driverGroup())
        val started = arrived.start(1000, true)
        assertEquals(DriverBookingGroup.ONGOING, started.driverGroup())
        assertEquals(CustomerBookingGroup.ACCEPTED, started.customerGroup())
        val completed = started.complete(2000)
        assertEquals(CustomerBookingGroup.COMPLETED, completed.customerGroup())
        assertEquals(DriverBookingGroup.COMPLETED, completed.driverGroup())
    }

    @Test fun declineIsTerminalAndPreservesBookingDetails() {
        val original = booking().copy(fee = "1000", notes = "Blue gate", vehicle = "SUV")
        val declined = original.decline("Schedule conflict")
        assertEquals(CustomerBookingGroup.CANCELLED, declined.customerGroup())
        assertEquals(DriverBookingGroup.DECLINED, declined.driverGroup())
        assertTrue(declined.requestStatus.contains("Schedule conflict"))
        assertEquals(original.fee, declined.fee)
        assertEquals(original.notes, declined.notes)
        assertEquals(original.vehicle, declined.vehicle)
        assertEquals(declined, declined.accept().headToPickup().arrive().start(1000, true).complete(2000))
        assertNull(declined.startedAt)
    }

    @Test fun staleDeclineCannotCancelAcceptedOrStartedBooking() {
        val accepted = booking().accept()
        assertEquals(accepted, accepted.decline("Late response"))
        val started = accepted.headToPickup().arrive().start(1000, true)
        assertEquals(started, started.decline("Late response"))
    }

    @Test fun multipleBookingsHaveOneCategoryEachAndRemainIndependent() {
        val records = listOf(booking("one"), booking("two").accept(), booking("three").accept().headToPickup().arrive().start(1000, true))
        val groups = records.groupBy { it.driverGroup() }
        assertEquals(listOf("one"), groups[DriverBookingGroup.REQUESTS]?.map { it.id })
        assertEquals(listOf("two"), groups[DriverBookingGroup.UPCOMING]?.map { it.id })
        assertEquals(listOf("three"), groups[DriverBookingGroup.ONGOING]?.map { it.id })
        assertEquals(records.size, groups.values.sumOf { it.size })
    }
}
