package com.example.hiremydriver

import com.example.hiremydriver.recommendation.RegisteredSchedule
import org.junit.Assert.*
import org.junit.Test

class DriverBookingScheduleTest {
    private fun schedule(enabled: Set<Int>) = mapOf("matching" to mapOf("weekly" to List(7) { index ->
        mapOf("configured" to (index in enabled), "enabled" to (index in enabled), "start" to 360, "end" to 1080)
    }))
    private fun at(day: Int, time: String = "6:00 AM") = RegisteredSchedule.bookingStart(
        mapOf("date" to "Oct $day, 2026", "time" to time))!!

    @Test fun mondayAndTuesdayOnlyRejectWednesdayAndOtherDays() {
        val data = schedule(setOf(0,1))
        for (day in 19..25) {
            val start = at(day)
            assertEquals(day <= 20, RegisteredSchedule.workingWindow(data,start) != null)
            assertEquals(day <= 20, RegisteredSchedule.withinHours(data,start,start + 3600000))
        }
    }
    @Test fun everyDriverCanChooseAnyOfTheSevenDays() {
        for (enabled in 0..6) {
            val data = schedule(setOf(enabled))
            for (day in 0..6) assertEquals("Saved weekday $enabled, selected weekday $day", enabled == day,
                RegisteredSchedule.workingWindow(data,at(19 + day)) != null)
        }
    }
    @Test fun selectedHoursMustFitTheEntireWorkingWindow() {
        val data = schedule(setOf(0))
        assertTrue(RegisteredSchedule.withinHours(data,at(19),at(19,"6:00 PM")))
        assertFalse(RegisteredSchedule.withinHours(data,at(19,"5:00 AM"),at(19,"7:00 AM")))
        assertFalse(RegisteredSchedule.withinHours(data,at(19,"5:00 PM"),at(19,"7:00 PM")))
        assertFalse(RegisteredSchedule.withinHours(data,at(19,"5:00 PM"),at(20)))
    }
    @Test fun newDriverScheduleReplacesPreviouslyAllowedDays() {
        assertNotNull(RegisteredSchedule.workingWindow(schedule(setOf(0)),at(19)))
        assertNull(RegisteredSchedule.workingWindow(schedule(setOf(2,6)),at(19)))
        assertNotNull(RegisteredSchedule.workingWindow(schedule(setOf(2,6)),at(21)))
    }
    @Test fun missingOrDisabledScheduleNeverAllowsBooking() {
        assertNull(RegisteredSchedule.workingWindow(emptyMap(),at(19)))
        assertNull(RegisteredSchedule.workingWindow(schedule(emptySet()),at(19)))
    }
}
