package com.example.hiremydriver

import org.junit.Assert.*
import org.junit.Test

class ScheduleDisplayTest {
    @Test fun displaysOnlySavedWorkingHoursInWeekOrder() {
        val days = List(7) { mapOf("enabled" to (it == 0), "start" to 360L, "end" to 1080L) }
        val result = ScheduleDisplay.days(days)
        assertEquals("Monday" to "6:00 AM – 6:00 PM", result.first())
        assertEquals(1, result.size)
        assertEquals(0, result.count { it.second == "Off" })
    }
    @Test fun missingAndInvalidTimesNeverImplyAvailability() {
        assertTrue(ScheduleDisplay.days(null).isEmpty())
        assertTrue(ScheduleDisplay.days(emptyList<Any>()).isEmpty())
        val result = ScheduleDisplay.days(List(7) { mapOf("enabled" to true, "start" to 1000, "end" to 500) })
        assertTrue(result.isEmpty())
    }
    @Test fun noonAndMidnightUseCorrectClockLabels() {
        val result = ScheduleDisplay.days(List(7) { mapOf("enabled" to true, "start" to 0, "end" to 720) })
        assertEquals("12:00 AM – 12:00 PM", result.first().second)
    }
    @Test fun untouchedDaysAreNotInventedDaysOff() {
        val unset = mapOf("enabled" to false, "start" to 480, "end" to 1080, "configured" to false)
        assertTrue(ScheduleDisplay.days(List(7) { unset }).isEmpty())
        val days = MutableList(7) { unset.toMap() }
        days[2] = mapOf("enabled" to true, "start" to 555, "end" to 1005, "configured" to true)
        days[4] = mapOf("enabled" to false, "start" to 480, "end" to 1080, "configured" to true)
        val shown = ScheduleDisplay.days(days)
        assertEquals(listOf("Wednesday" to "9:15 AM – 4:45 PM"), shown)
    }
}
