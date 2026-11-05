package com.example.hiremydriver

import android.content.Context

/** Account-scoped pending cache of the server schedule. */
class DriverScheduleStore(private val context: Context) {
    private val prefs = context.getSharedPreferences(DriverAuth.preferenceName(context, PREFERENCES), Context.MODE_PRIVATE)

    val drivingComplete: Boolean get() = com.example.hiremydriver.recommendation.DriverMatchingSettings.drivingComplete(context)
    val acceptingBookings: Boolean
        get() = DriverAuth.approved(context) && drivingComplete && !prefs.contains("accepting_pending") && prefs.getBoolean("accepting_bookings", false)

    fun setAccepting(value: Boolean, done: (Boolean) -> Unit) {
            if (value && (!DriverAuth.approved(context) || !drivingComplete)) { done(false); return }
            prefs.edit().putBoolean("accepting_bookings", value).apply()
            com.example.hiremydriver.recommendation.DriverMatchingSettings.markAccepting(context)
            com.example.hiremydriver.recommendation.DriverMatchingSettings.sync(context) { saved ->
                if (!saved) {
                    // Never silently turn on later after a failed toggle. Explicit retry is required.
                    prefs.edit().putBoolean("accepting_bookings", false).apply()
                    com.example.hiremydriver.recommendation.DriverMatchingSettings.markAccepting(context)
                }
                done(saved)
            }
        }

    fun load(): List<DriverDaySchedule> = DriverSchedule.defaults().mapIndexed { index, default ->
        DriverDaySchedule(
            prefs.getBoolean("day_${index}_enabled", default.enabled),
            prefs.getInt("day_${index}_start", default.start),
            prefs.getInt("day_${index}_end", default.end),
            prefs.getBoolean("day_${index}_configured", prefs.contains("day_${index}_enabled"))
        )
    }

    fun save(days: List<DriverDaySchedule>, done: ((Boolean) -> Unit)? = null) {
        require(days.size == 7 && days.all { it.error() == null })
        prefs.edit().apply {
            days.forEachIndexed { index, day ->
                putBoolean("day_${index}_enabled", day.enabled)
                putInt("day_${index}_start", day.start)
                putInt("day_${index}_end", day.end)
                putBoolean("day_${index}_configured", day.configured)
            }
            apply()
        }
        com.example.hiremydriver.recommendation.DriverMatchingSettings.markSchedule(context)
        com.example.hiremydriver.recommendation.DriverMatchingSettings.sync(context, done)
    }

    companion object { const val PREFERENCES = "driver_schedule_local" }
}
