package com.example.hiremydriver

/** One same-day availability window, in minutes after midnight. Monday is index 0. */
data class DriverDaySchedule(val enabled: Boolean, val start: Int, val end: Int, val configured: Boolean = true) {
    fun error(): String? = when {
        !enabled -> null
        start !in 0..1439 || end !in 0..1439 -> "Choose a valid start and end time."
        end <= start -> "End time must be after start time on the same day."
        else -> null
    }
}

object DriverSchedule {
    val dayNames = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    // Editing suggestions only, never published as working/off days until explicitly selected.
    fun defaults() = List(7) { DriverDaySchedule(false, 8 * 60, 18 * 60, configured = false) }

    fun decode(value: Any?): List<DriverDaySchedule> {
        val weekly = (value as? List<*>)?.takeIf { it.size == 7 } ?: return defaults()
        return weekly.map { raw ->
            val d = raw as? Map<*, *> ?: return@map DriverDaySchedule(false, 480, 1080, false)
            val start = d["start"] as? Number
            val end = d["end"] as? Number
            val valid = d["enabled"] is Boolean && start != null && end != null &&
                start.toDouble() == start.toInt().toDouble() && end.toDouble() == end.toInt().toDouble() &&
                start.toInt() in 0..1439 && end.toInt() in 0..1439
            DriverDaySchedule(valid && d["configured"] != false && d["enabled"] == true,
                if (valid) start!!.toInt() else 480, if (valid) end!!.toInt() else 1080,
                valid && d["configured"] != false)
        }
    }
}
