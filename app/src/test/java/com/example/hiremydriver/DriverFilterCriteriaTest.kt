package com.example.hiremydriver

import org.junit.Assert.*
import org.junit.Test

class DriverFilterCriteriaTest {
    private val settings = mapOf("languages" to listOf("English"), "vehicles" to listOf("SUV", "Truck"),
        "transmissions" to listOf("Manual"), "experience" to "6", "rate" to 500, "accepting" to true,
        "areas" to listOf("Sikatuna (Bohol)", "Balilihan (Bohol)"),
        "weekly" to List(7) { index -> mapOf("configured" to true, "enabled" to (index == 0), "start" to 480, "end" to 1020) })
    private val driver: Map<String, Any?> = mapOf("status" to "approved", "matching" to settings)

    @Test fun combinedFiltersUseSavedFieldsAndReviews() {
        val filter = DriverFilterCriteria(3, "Truck", "Sikatuna (Bohol)", 1, 4)
        assertTrue(filter.matches(driver, 4.5))
        assertFalse(filter.copy(vehicle = "Sedan").matches(driver, 4.5))
        assertFalse(filter.copy(area = "Panglao (Bohol)").matches(driver, 4.5))
        assertFalse(filter.copy(day = 2).matches(driver, 4.5))
        assertFalse(filter.copy(experience = 1).matches(driver, 4.5))
        assertFalse(filter.matches(driver, 3.9))
        assertFalse(filter.matches(driver, null))
    }
    @Test fun defaultsDoNotFilterUnratedDriversButNeverBypassApproval() {
        val filter = DriverFilterCriteria()
        assertFalse(filter.active)
        assertTrue(filter.matches(driver, null))
        assertFalse(filter.matches(driver + ("status" to "pending"), 5.0))
        assertFalse(filter.matches(driver + ("matching" to (settings + ("accepting" to false))), 5.0))
    }
    @Test fun selectedDayMustBeExplicitlyConfiguredAndHaveValidHours() {
        val weekly = List(7) { mapOf("enabled" to true, "configured" to false, "start" to 480, "end" to 1020) }
        assertFalse(DriverFilterCriteria(day = 1).matches(driver + ("matching" to settings + ("weekly" to weekly)), null))
    }
    @Test fun onlyTheFiveSupportedVehicleTypesAreOffered() {
        assertEquals(listOf("Sedan", "SUV", "Van", "Pickup", "Truck"), DrivingInformation.vehicles)
    }
}
