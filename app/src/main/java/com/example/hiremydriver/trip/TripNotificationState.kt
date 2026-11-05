package com.example.hiremydriver.trip

/** The system chronometer uses this persisted start time; no running app timer is needed. */
internal data class TripNotificationState(val startedAt: Long, val durationHours: Int) {
    companion object {
        fun from(trip: TripSession): TripNotificationState? =
            trip.startedAt?.takeIf { trip.phase == TripPhase.IN_PROGRESS }
                ?.let { TripNotificationState(it, trip.durationHours) }
    }
}
