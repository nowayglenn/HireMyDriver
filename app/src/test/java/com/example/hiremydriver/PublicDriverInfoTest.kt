package com.example.hiremydriver

import org.junit.Assert.*
import org.junit.Test

class PublicDriverInfoTest {
    @Test fun availabilitySwitchGatesBookingWithoutHidingPublicProfile() {
        val matching = mapOf("accepting" to true, "vehicles" to listOf("SUV"),
            "transmissions" to listOf("Automatic"), "experience" to "7", "areas" to listOf("Bohol"),
            "languages" to listOf("English"), "rate" to 500L)
        val profile = mapOf("fullName" to "Public driver", "status" to "approved", "matching" to matching)
        assertTrue(PublicDriverInfo.from(profile).canRequestBooking)
        val offline = PublicDriverInfo.from(profile + ("matching" to (matching + ("accepting" to false))))
        assertFalse(offline.canRequestBooking)
        assertEquals("Public driver", offline.name)
        assertEquals("approved", offline.status)
        assertFalse(PublicDriverInfo.from(profile + ("status" to "pending")).canRequestBooking)
        assertFalse(PublicDriverInfo.from(profile + ("matching" to (matching - "vehicles"))).canRequestBooking)
        assertFalse(PublicDriverInfo.from(emptyMap()).canRequestBooking)
    }
    @Test fun languagesComeOnlyFromSavedSelections() {
        assertTrue(PublicDriverInfo.from(emptyMap()).languages.isEmpty())
        val info = PublicDriverInfo.from(mapOf("matching" to mapOf("languages" to listOf("Filipino", "Cebuano/Bisaya"))))
        assertEquals(listOf("Filipino", "Cebuano/Bisaya"), info.languages)
    }
    @Test fun savedDrivingInformationOverridesRegistrationSnapshot() {
        val info = PublicDriverInfo.from(mapOf("fullName" to "Driver", "experience" to "2",
            "matching" to mapOf("rate" to 1250L, "experience" to "7", "vehicles" to listOf("SUV"),
                "transmissions" to listOf("Manual"), "areas" to listOf("Bohol"))))
        assertEquals("7", info.experience)
        assertEquals(1250L, info.rate)
        assertEquals(listOf("SUV"), info.vehicles)
        assertEquals(listOf("Manual"), info.transmissions)
        assertEquals(listOf("Bohol"), info.areas)
        assertEquals(java.math.BigDecimal("1250"), BookingInput.hourlyRate(info.price))
    }
    @Test fun legacyProfileDoesNotInventRateOrDrivingPreferences() {
        val info = PublicDriverInfo.from(mapOf("experience" to "3", "matching" to "invalid"))
        assertEquals("3", info.experience)
        assertNull(info.rate)
        assertEquals("Rate not set", info.price)
        assertTrue(info.vehicles.isEmpty())
    }
}
