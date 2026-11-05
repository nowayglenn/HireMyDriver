package com.example.hiremydriver

import com.example.hiremydriver.recommendation.RegisteredSchedule
import org.junit.Assert.*
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class RegisteredScheduleTest {
    private val start = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("Asia/Manila")
    }.parse("2026-10-19 09:00")!!.time
    private val settings = mapOf("languages" to listOf("English"), "areas" to listOf("Bohol"), "vehicles" to listOf("SUV"),
        "transmissions" to listOf("Automatic"), "accepting" to true, "experience" to "3", "rate" to 500,
        "weekly" to List(7) { mapOf("enabled" to true, "start" to 480, "end" to 1080) })
    private val profile: Map<String, Any?> = mapOf("status" to "approved", "availabilityReady" to true,
        "matching" to settings, "busySlots" to emptyMap<String, Any>())
    private val slot = mapOf("date" to "Oct 19, 2026", "time" to "09:00 AM", "hours" to 4, "phase" to "ACCEPTED", "started" to 0)
    private fun matches(data: Map<String, Any?> = profile, area: String = "Bohol", vehicle: String = "SUV", transmission: String = "Automatic") =
        RegisteredSchedule.matches(data, area, vehicle, transmission, start, start + 3600000, start - 1)

    @Test fun homeDiscoveryDoesNotInventATripTimeOrRequireScheduleMigration() {
        val booked = profile + ("busySlots" to mapOf("booking" to slot))
        assertTrue(RegisteredSchedule.discoverable(booked, "Bohol"))
        assertFalse(matches(booked))
        val noWorkingDay = profile + ("matching" to (settings + ("weekly" to emptyList<Any>())))
        assertTrue(RegisteredSchedule.discoverable(noWorkingDay - "availabilityReady", "Bohol"))
        assertFalse(matches(noWorkingDay))
    }

    @Test fun homeDiscoveryRequiresApprovalOnlineCompleteSetupAndSavedServiceArea() {
        val local = profile + ("matching" to (settings + ("areas" to listOf("Sikatuna (Bohol)"))))
        assertTrue(RegisteredSchedule.discoverable(local, "Sikatuna (Bohol)"))
        assertFalse(RegisteredSchedule.discoverable(local, "Balilihan (Bohol)"))
        assertFalse(RegisteredSchedule.discoverable(local, "Sikatuna (Cebu)"))
        assertFalse(RegisteredSchedule.discoverable(profile + ("status" to "pending"), "Bohol"))
        assertFalse(RegisteredSchedule.discoverable(profile + ("matching" to (settings + ("accepting" to false))), "Bohol"))
        for (field in listOf("vehicles", "transmissions", "experience", "areas", "rate", "languages"))
            assertFalse(RegisteredSchedule.discoverable(profile + ("matching" to (settings - field)), "Bohol"))
    }

    @Test fun incompleteDriverCannotMatchEvenIfLegacyAvailabilityIsOn() {
        for (field in listOf("vehicles", "transmissions", "experience", "areas", "rate", "languages"))
            assertFalse(matches(profile + ("matching" to (settings - field))))
    }

    @Test fun unconfiguredDayIsNeverUsedForMatching() {
        assertFalse(matches(profile + ("matching" to (settings + ("weekly" to List(7) {
            mapOf("enabled" to true, "start" to 480, "end" to 1080, "configured" to false)
        })))))
    }

    @Test fun realAreaPreferencesAndConfirmedScheduleRequired() {
        assertTrue(matches())
        assertFalse(matches(area = "Cebu")); assertFalse(matches(vehicle = "Van")); assertFalse(matches(transmission = "Manual"))
        assertFalse(matches(profile + ("availabilityReady" to false)))
        assertFalse(matches(profile + ("matching" to (settings + ("weekly" to emptyList<Any>())))))
        assertFalse(matches(profile + ("matching" to (settings + ("accepting" to false)))))
        assertFalse(matches(profile + ("status" to "rejected")))
        assertFalse(matches(profile + ("status" to "pending")))
    }
    @Test fun activeSlotsBlockButAdjacentBookingsDoNotOverlap() {
        listOf("ACCEPTED", "ON_THE_WAY", "ARRIVED", "IN_PROGRESS").forEach { phase ->
            assertFalse(matches(profile + ("busySlots" to mapOf("booking" to (slot + mapOf("phase" to phase, "started" to start))))))
        }
        assertFalse(RegisteredSchedule.conflicts(slot, start + 4 * 3600000, start + 5 * 3600000, start))
        assertTrue(RegisteredSchedule.conflicts(slot + ("date" to "invalid"), start, start + 3600000, start))
        assertTrue(RegisteredSchedule.conflicts(slot + mapOf("phase" to "IN_PROGRESS", "started" to start), start + 9 * 3600000, start + 10 * 3600000, start + 8 * 3600000))
    }
    @Test fun incompleteAndOvernightWindowsAreNeverAvailable() {
        assertFalse(matches(profile - "busySlots"))
        assertFalse(matches(profile + ("busySlots" to mapOf("broken" to null))))
        assertFalse(RegisteredSchedule.matches(profile, "Bohol", "SUV", "Automatic", start, start + 24 * 3600000, start - 1))
    }
    @Test fun acceptingRechecksOtherSlotsAndAllowsBackToBackBookings() {
        assertFalse(RegisteredSchedule.canAccept(slot, mapOf("other" to slot), "new", start))
        assertTrue(RegisteredSchedule.canAccept(slot, mapOf("same" to slot), "same", start))
        assertTrue(RegisteredSchedule.canAccept(slot + ("time" to "01:00 PM"), mapOf("other" to slot), "new", start))
        assertFalse(RegisteredSchedule.canAccept(slot + ("date" to "invalid"), emptyMap<String, Any>(), "new", start))
    }
}
