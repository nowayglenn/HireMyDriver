package com.example.hiremydriver.maps

/** Avoid expensive Maps renderer updates for unchanged icons and tiny compass jitter. */
internal class NavigationMarkerStyle {
    data class Changes(val iconChanged: Boolean, val rotation: Float?)
    private var arrow: Boolean? = null
    private var rotation: Float? = null

    fun update(heading: Float, useArrow: Boolean): Changes {
        val normalized = NavigationFix.normalize(heading)
        val previous = rotation
        val difference = previous?.let { kotlin.math.abs(((normalized - it + 540f) % 360f) - 180f) }
        val nextRotation = normalized.takeIf { difference == null || difference >= 2f }
        val iconChanged = arrow != useArrow
        arrow = useArrow
        if (nextRotation != null) rotation = nextRotation
        return Changes(iconChanged, nextRotation)
    }
}
