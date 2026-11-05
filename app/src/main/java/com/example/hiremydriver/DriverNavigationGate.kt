package com.example.hiremydriver

/** Shared across driver screens so queued taps cannot immediately open another tab. */
internal class DriverNavigationGate(private val intervalMillis: Long = 350L) {
    private var lastNavigationAt: Long? = null

    fun tryNavigate(current: String, target: String, now: Long): Boolean {
        if (current == target) return false
        val previous = lastNavigationAt
        if (previous != null && now - previous < intervalMillis) return false
        lastNavigationAt = now
        return true
    }
}
