package com.example.hiremydriver

import com.example.hiremydriver.trip.*
import org.junit.Assert.assertEquals
import org.junit.Test

class BookingDetailPageTest {
    private fun booking(phase: TripPhase) = TripSession("id", "Driver", "Customer", "Pickup", "Destination", phase = phase)

    @Test fun customerAlwaysUsesOriginalCustomerDetails() {
        TripPhase.entries.forEach { assertEquals(BookingDetailPage.CUSTOMER, booking(it).detailPage(false)) }
    }

    @Test fun driverRequestsUseRequestDetailsAndAcceptedTripsUseBookingDetails() {
        assertEquals(BookingDetailPage.DRIVER_REQUEST, booking(TripPhase.REQUESTED).detailPage(true))
        assertEquals(BookingDetailPage.DRIVER_REQUEST, booking(TripPhase.DECLINED).detailPage(true))
        listOf(TripPhase.ACCEPTED, TripPhase.ARRIVED, TripPhase.IN_PROGRESS, TripPhase.COMPLETED).forEach {
            assertEquals(BookingDetailPage.DRIVER_BOOKING, booking(it).detailPage(true))
        }
    }
}
