package com.example.hiremydriver.maps

import kotlin.math.*

/** Movement direction, not the direction a stationary phone happens to face. */
internal class NavigationFix {
    data class Sample(val latitude: Double, val longitude: Double, val elapsedMillis: Long,
                      val accuracy: Float, val bearing: Float?, val speed: Float?)
    var position: Sample? = null
        private set
    var heading: Float? = null
        private set
    private var headingOrigin: Sample? = null

    fun accept(next: Sample, now: Long): Boolean {
        if (!isUsable(next, now) ||
            (position?.elapsedMillis?.let { next.elapsedMillis <= it } == true)) return false
        val origin = headingOrigin
        if (next.bearing?.isFinite() == true && (next.speed ?: 0f) >= 0.8f) {
            heading = normalize(next.bearing)
            headingOrigin = next
        } else if (origin != null) {
            val lat1 = Math.toRadians(origin.latitude)
            val lat2 = Math.toRadians(next.latitude)
            val delta = Math.toRadians(next.longitude - origin.longitude)
            val a = sin((lat2 - lat1) / 2).pow(2) + cos(lat1) * cos(lat2) * sin(delta / 2).pow(2)
            val distance = 12_742_000 * asin(sqrt(a.coerceIn(0.0, 1.0)))
            if (distance >= maxOf(8f, origin.accuracy + next.accuracy)) {
                heading = normalize(Math.toDegrees(atan2(sin(delta) * cos(lat2),
                    cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(delta))).toFloat())
                headingOrigin = next
            }
        } else headingOrigin = next
        position = next
        return true
    }

    companion object {
        fun isUsable(sample: Sample, now: Long): Boolean =
            sample.latitude.isFinite() && sample.latitude in -90.0..90.0 &&
                sample.longitude.isFinite() && sample.longitude in -180.0..180.0 &&
                sample.accuracy.isFinite() && sample.accuracy in 0f..75f &&
                now - sample.elapsedMillis in 0..15_000

        fun newestUsable(samples: List<Sample>, now: Long): Sample? = samples.filter { isUsable(it, now) }
            .sortedWith(compareByDescending<Sample> { it.elapsedMillis }.thenBy { it.accuracy }).firstOrNull()

        fun normalize(angle: Float) = ((angle % 360f) + 360f) % 360f
        fun interpolateHeading(from: Float, to: Float, fraction: Float): Float =
            normalize(from + ((to - from + 540f) % 360f - 180f) * fraction)
    }
}
