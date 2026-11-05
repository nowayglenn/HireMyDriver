package com.example.hiremydriver.recommendation

import kotlin.math.*

data class Coordinates(val latitude: Double, val longitude: Double) {
    init {
        require(latitude.isFinite() && latitude in -90.0..90.0)
        require(longitude.isFinite() && longitude in -180.0..180.0)
    }
}

/** Half-open interval: a booking ending at 10:00 permits another starting at 10:00. */
data class TimeWindow(val start: Long, val end: Long) {
    init { require(end > start) }
    fun covers(other: TimeWindow) = start <= other.start && end >= other.end
    fun overlaps(other: TimeWindow) = start < other.end && other.start < end
}

data class DriverCandidate(
    val id: String,
    val name: String,
    val approved: Boolean,
    val acceptingBookings: Boolean,
    val vehicles: Set<String>,
    val transmissions: Set<String>,
    val location: Coordinates?,
    val locationAt: Long?,
    val availability: List<TimeWindow>,
    val bookings: List<TimeWindow> = emptyList(),
    val rating: Double? = null,
    val completedTrips: Int = 0,
    val hourlyRate: Int = 500,
    val serviceAreas: Set<String> = emptySet(),
    val profileStatus: String = "pending",
    val thumbnail: String = "",
    val experience: String = "",
    val bio: String = "",
    val rateProvided: Boolean = true
) {
    init {
        require(id.isNotBlank() && name.isNotBlank())
        require(rating == null || (rating.isFinite() && rating in 0.0..5.0))
        require(completedTrips >= 0 && hourlyRate >= 0)
    }
}

data class RecommendationRequest(
    val pickup: Coordinates,
    val trip: TimeWindow,
    val vehicle: String,
    val transmission: String,
    val radiusKm: Double = 30.0,
    val serviceArea: String? = null
) {
    init {
        require(vehicle.isNotBlank() && transmission.isNotBlank())
        require(radiusKm.isFinite() && radiusKm > 0)
    }
}

data class DriverMatch(val driver: DriverCandidate, val distanceKm: Double)

/** No network or Android dependency. Replace the candidate source when a backend is ready. */
object DriverRecommendationEngine {
    const val LOCATION_MAX_AGE_MS = 15 * 60 * 1000L

    fun rank(request: RecommendationRequest, drivers: List<DriverCandidate>, now: Long): List<DriverMatch> {
        if (request.trip.start <= now) return emptyList()
        val eligible = drivers.asSequence().filter { driver ->
            driver.approved && driver.acceptingBookings &&
                (request.serviceArea == null || driver.serviceAreas.any { it.equals(request.serviceArea, ignoreCase = true) }) &&
                matches(request.vehicle, driver.vehicles) &&
                matches(request.transmission, driver.transmissions) &&
                driver.availability.any { it.covers(request.trip) } &&
                driver.bookings.none { it.overlaps(request.trip) }
        }
        return rankByDistance(request.pickup, eligible, now, request.radiusKm)
    }

    /** Home browsing has no trip date/vehicle selected; do not invent trip filters. */
    fun rankNearby(origin: Coordinates, serviceArea: String, drivers: List<DriverCandidate>, now: Long,
        radiusKm: Double = 30.0): List<DriverMatch> {
        require(serviceArea.isNotBlank() && radiusKm.isFinite() && radiusKm > 0)
        val eligible = drivers.asSequence().filter { driver ->
            driver.approved && driver.acceptingBookings &&
                driver.serviceAreas.any { it.equals(serviceArea, ignoreCase = true) }
        }
        return rankByDistance(origin, eligible, now, radiusKm)
    }

    private fun rankByDistance(origin: Coordinates, drivers: Sequence<DriverCandidate>, now: Long,
        radiusKm: Double): List<DriverMatch> = drivers.mapNotNull { driver ->
            val location = driver.location ?: return@mapNotNull null
            val timestamp = driver.locationAt ?: return@mapNotNull null
            if (timestamp > now || timestamp < now - LOCATION_MAX_AGE_MS) return@mapNotNull null
            val distance = distanceKm(origin, location)
            if (distance > radiusKm) null else DriverMatch(driver, distance)
        }.sortedWith(compareBy<DriverMatch> { it.distanceKm }
            .thenByDescending { it.driver.rating ?: -1.0 }
            .thenByDescending { it.driver.completedTrips }
            .thenBy { it.driver.id }).toList()

    private fun matches(requested: String, supported: Set<String>) =
        supported.isNotEmpty() && (requested.equals("Any", ignoreCase = true) || supported.any { it.equals(requested, ignoreCase = true) })

    /** Haversine straight-line distance; this is not road distance or an ETA. */
    fun distanceKm(a: Coordinates, b: Coordinates): Double {
        val latDelta = Math.toRadians(b.latitude - a.latitude)
        val lonDelta = Math.toRadians(b.longitude - a.longitude)
        val h = sin(latDelta / 2).pow(2) + cos(Math.toRadians(a.latitude)) *
            cos(Math.toRadians(b.latitude)) * sin(lonDelta / 2).pow(2)
        return 6371.0088 * 2 * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }
}
