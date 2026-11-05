package com.example.hiremydriver

import com.example.hiremydriver.trip.*
import org.junit.Assert.*
import org.junit.Test

class EmergencyMessageTest {
    private val trip = TripSession("trip", "Driver", "Customer", "Pickup", "Destination",
        destinations = listOf(TripPlace("Stop one", 9.0, 123.0), TripPlace("Stop two", 10.0, 124.0)),
        driverLocation = TripPlace("Driver GPS", 45.0, 46.0))
    private val now = 0L
    @Test fun includesCustomerLocationAllStopsAndPhilippineTime() {
        val body = EmergencyMessage.body(trip, true, EmergencyMessage.Position(9.5, 123.5, now), now)
        assertTrue(body.contains("Customer: Customer"))
        assertTrue(body.contains("https://maps.google.com/?q=9.5,123.5"))
        assertTrue(body.contains("Pickup Location: Pickup"))
        assertTrue(body.contains("Stop one -> Stop two"))
        assertTrue(body.contains("January 1, 1970"))
        assertTrue(body.contains("08:00:00 AM (Philippine Standard Time)"))
        assertFalse(body.contains("45.0"))
    }
    @Test fun unavailableOrOldCustomerLocationNeverUsesDriverGps() {
        listOf(null, EmergencyMessage.Position(9.5, 123.5, -120001)).forEach {
            val body = EmergencyMessage.body(trip, true, it, now)
            assertTrue(body.contains("Could not be determined"))
            assertFalse(body.contains("https://maps.google.com"))
            assertFalse(body.contains("45.0"))
        }
    }
    @Test fun respectsExistingTripDetailsPreference() {
        val body = EmergencyMessage.body(trip, false, null, now)
        assertFalse(body.contains("Pickup Location:"))
        assertFalse(body.contains("Destination:"))
        assertTrue(body.contains("Customer: Customer"))
    }
}
