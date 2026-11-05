package com.example.hiremydriver

import org.junit.Assert.*
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class ServiceAreaSearchTest {
    private val now = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("Asia/Manila")
    }.parse("2026-10-19 09:00")!!.time
    private val settings = mapOf("languages" to listOf("English"), "areas" to listOf("Sikatuna (Bohol)", "Balilihan (Bohol)"),
        "vehicles" to listOf("SUV"), "transmissions" to listOf("Manual"), "experience" to "3",
        "rate" to 500, "accepting" to true, "weekly" to List(7) {
            mapOf("configured" to true, "enabled" to true, "start" to 480, "end" to 1080)
        })
    private val profile: Map<String, Any?> = mapOf("fullName" to "Test Driver", "status" to "approved",
        "matching" to settings, "availabilityReady" to true, "busySlots" to emptyMap<String, Any>())

    @Test fun eitherSelectedMunicipalityMatchesButGpsAndUnselectedPlacesDoNot() {
        assertTrue(ServiceAreaSearch.matches(profile, "sikatuna", now))
        assertTrue(ServiceAreaSearch.matches(profile, "Sikatuna, Bohol", now))
        assertTrue(ServiceAreaSearch.matches(profile, "BALILIHAN", now))
        assertTrue(ServiceAreaSearch.matches(profile, "Test Driver", now))
        assertFalse(ServiceAreaSearch.matches(profile + ("savedLocation" to mapOf("area" to "Cebu")), "Cebu", now))
        assertFalse(ServiceAreaSearch.matches(profile, "Tagbilaran", now))
        assertFalse(ServiceAreaSearch.matches(profile, "", now))
    }

    @Test fun searchIncludesUnavailablePopularDriversButNotUnapprovedProfiles() {
        val unavailable = profile + ("matching" to settings + ("accepting" to false))
        assertTrue(ServiceAreaSearch.matches(unavailable, "Sikatuna", now))
        assertTrue(ServiceAreaSearch.matches(unavailable, "Test Driver", now))
        assertTrue(ServiceAreaSearch.matches(unavailable, "Bohol", now))
        assertFalse(ServiceAreaSearch.matches(profile + ("status" to "rejected"), "Sikatuna", now))
        assertFalse(ServiceAreaSearch.matches(profile + ("status" to "pending"), "Sikatuna", now))
        assertTrue(ServiceAreaSearch.matches(profile, "Sikatuna", now + 12 * 3600000))
        val slot = mapOf("date" to "Oct 19, 2026", "time" to "09:00 AM", "hours" to 2, "phase" to "ACCEPTED", "started" to 0)
        assertTrue(ServiceAreaSearch.matches(profile + ("busySlots" to mapOf("booking" to slot)), "Sikatuna", now))
    }

    @Test fun nameSearchDoesNotRequireCompletedDrivingSettingsOrServiceAreas() {
        val incomplete = profile + ("matching" to mapOf("accepting" to false))
        assertTrue(ServiceAreaSearch.matches(incomplete, "Test Driver", now))
        assertTrue(ServiceAreaSearch.matches(profile - "matching", "Test Driver", now))
        assertFalse(ServiceAreaSearch.matches(incomplete, "Sikatuna", now))
        assertFalse(ServiceAreaSearch.matches(incomplete, "Another Driver", now))
    }
}
