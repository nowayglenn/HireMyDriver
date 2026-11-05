package com.example.hiremydriver

import org.junit.Assert.*
import org.junit.Test

class DriverScheduleTest {
    @Test fun acceptsCustomMinutesAndMidnightStart() {
        assertNull(DriverDaySchedule(true, 9 * 60 + 15, 17 * 60 + 45).error())
        assertNull(DriverDaySchedule(true, 0, 1439).error())
    }

    @Test fun rejectsEmptyAndReversedWindows() {
        assertNotNull(DriverDaySchedule(true, 480, 480).error())
        assertNotNull(DriverDaySchedule(true, 1080, 480).error())
    }

    @Test fun rejectsOutOfRangeTimes() {
        assertNotNull(DriverDaySchedule(true, -1, 600).error())
        assertNotNull(DriverDaySchedule(true, 0, 1440).error())
    }

    @Test fun dayOffCanRetainAnUnfinishedTimeEdit() {
        val unfinished = DriverDaySchedule(true, 1000, 500)
        assertNotNull(unfinished.error())
        assertNull(unfinished.copy(enabled = false).error())
    }

    @Test fun defaultsIncludeEditableSundayAndAllowAllDaysOff() {
        val days = DriverSchedule.defaults()
        assertEquals(7, days.size)
        assertFalse(days.last().enabled)
        assertTrue(days.none { it.configured || it.enabled })
        assertNull(days.last().copy(enabled = true).error())
        assertTrue(days.map { it.copy(enabled = false) }.all { it.error() == null })
    }

    @Test fun decodePreservesExplicitOffAndCustomDaysWithoutInventingDefaults() {
        assertTrue(DriverSchedule.decode(null).none { it.configured || it.enabled })
        val saved = List(7) { i -> mapOf("enabled" to (i == 2 || i == 4), "start" to 555L, "end" to 1005L) }
        val days = DriverSchedule.decode(saved)
        assertTrue(days[2].enabled && days[4].enabled)
        assertEquals(555, days[2].start)
        assertTrue(days.all { it.configured })
        assertFalse(days[0].enabled)
    }
}
