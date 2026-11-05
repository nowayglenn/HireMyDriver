package com.example.hiremydriver

import java.util.Locale

/** Display only saved, valid working hours; missing hours must not imply availability. */
object ScheduleDisplay {
    fun days(value: Any?): List<Pair<String, String>> {
        val weekly = (value as? List<*>)?.takeIf { it.size == 7 } ?: return emptyList()
        val names = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
        if (weekly.none { (it as? Map<*, *>)?.let { day -> day["configured"] != false && day["enabled"] is Boolean } == true }) return emptyList()
        return weekly.mapIndexedNotNull { index, raw ->
            val day = raw as? Map<*, *>
            val start = (day?.get("start") as? Number)?.toInt()
            val end = (day?.get("end") as? Number)?.toInt()
            if (day?.get("configured") == false || day?.get("enabled") != true || start == null || end == null ||
                start !in 0..1439 || end !in 1..1439 || end <= start) return@mapIndexedNotNull null
            val hours = "${time(start)} – ${time(end)}"
            names[index] to hours
        }
    }

    private fun time(minutes: Int): String {
        val hour = (minutes / 60) % 24
        return String.format(Locale.US, "%d:%02d %s", if (hour % 12 == 0) 12 else hour % 12,
            minutes % 60, if (hour < 12) "AM" else "PM")
    }
}
