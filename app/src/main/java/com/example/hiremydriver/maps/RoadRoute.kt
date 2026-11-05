package com.example.hiremydriver.maps

import com.example.hiremydriver.trip.TripPhase
import com.example.hiremydriver.trip.TripSession

data class RouteCoordinate(val latitude: Double, val longitude: Double) {
    init {
        require(latitude.isFinite() && latitude in -90.0..90.0)
        require(longitude.isFinite() && longitude in -180.0..180.0)
    }
}

object RoadRoute {
    /** Preview and pickup navigation route to the customer; destination pins remain visible. */
    fun toPickup(trip: TripSession): List<RouteCoordinate> {
        val driver = trip.driverLocation ?: return emptyList()
        val pickup = trip.pickup ?: return emptyList()
        return compact(listOf(RouteCoordinate(driver.latitude, driver.longitude), RouteCoordinate(pickup.latitude, pickup.longitude)))
    }

    /** The same confirmed itinerary is used by both roles; a moving GPS marker is not a new itinerary. */
    fun itinerary(trip: TripSession): List<RouteCoordinate> {
        val pickup = trip.pickup ?: return emptyList()
        if (trip.phase == TripPhase.DECLINED || trip.destinations.isEmpty()) return emptyList()
        return compact((listOf(pickup) + trip.destinations).map { RouteCoordinate(it.latitude, it.longitude) })
    }

    private fun compact(points: List<RouteCoordinate>) = points.fold(mutableListOf<RouteCoordinate>()) { result, point ->
        result.apply { if (lastOrNull() != point) add(point) }
    }

    /** At most 10 intermediate stops per basic Essentials request, preserving visit order. */
    fun batches(points: List<RouteCoordinate>): List<List<RouteCoordinate>> {
        val route = compact(points)
        if (route.size < 2) return emptyList()
        return buildList {
            var start = 0
            while (start < route.lastIndex) {
                val end = minOf(start + 11, route.lastIndex)
                add(route.subList(start, end + 1).toList())
                start = end
            }
        }
    }

    fun decode(encoded: String): List<RouteCoordinate> {
        var index = 0
        var latitude = 0L
        var longitude = 0L
        fun delta(): Long {
            var value = 0L
            var shift = 0
            while (true) {
                require(index < encoded.length && shift <= 30) { "Invalid route geometry" }
                val part = encoded[index++].code - 63
                require(part in 0..63) { "Invalid route geometry" }
                value = value or ((part and 31).toLong() shl shift)
                if (part < 32) break
                shift += 5
            }
            return if ((value and 1L) != 0L) (value shr 1).inv() else value shr 1
        }
        return buildList {
            while (index < encoded.length) {
                latitude += delta()
                longitude += delta()
                add(RouteCoordinate(latitude / 100_000.0, longitude / 100_000.0))
            }
        }
    }
}
