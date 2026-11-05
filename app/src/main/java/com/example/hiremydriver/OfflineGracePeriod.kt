package com.example.hiremydriver

/** Connectivity callbacks must not restart the grace period for the same outage. */
internal class OfflineGracePeriod(private val durationMillis: Long = 8_000L) {
    private var offlineSince: Long? = null

    fun remaining(connected: Boolean, now: Long): Long {
        if (connected) {
            offlineSince = null
            return 0L
        }
        val start = offlineSince ?: now.also { offlineSince = it }
        return (durationMillis - (now - start)).coerceAtLeast(0L)
    }
}
