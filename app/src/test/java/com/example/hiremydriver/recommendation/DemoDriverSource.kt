package com.example.hiremydriver.recommendation

import java.util.Calendar
import java.util.TimeZone

/** Fictional, local fixtures only. Never represent these as live or registered drivers. */
object DemoDriverSource {
    val zone: TimeZone get() = TimeZone.getTimeZone("Asia/Manila")
    val pickups = listOf(
        "Demo pickup · Sikatuna" to Coordinates(9.6880, 123.9740),
        "Demo pickup · Tagbilaran" to Coordinates(9.6500, 123.8550),
        "Demo pickup · Panglao" to Coordinates(9.5780, 123.7460)
    )

    fun drivers(tripStart: Long, snapshotAt: Long): List<DriverCandidate> {
        fun at(hour: Int) = Calendar.getInstance(zone).apply {
            timeInMillis = tripStart
            set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val shift = listOf(TimeWindow(at(7), at(20)))
        fun driver(id: String, point: Coordinates, vehicles: Set<String>, transmissions: Set<String>, rating: Double?, trips: Int) =
            DriverCandidate(id, "Demo Driver $id", true, true, vehicles, transmissions,
                point, snapshotAt, shift, rating = rating, completedTrips = trips, serviceAreas = setOf("Bohol"))
        val both = setOf("Manual", "Automatic")
        return listOf(
            driver("A", Coordinates(9.6920, 123.9700), setOf("Sedan", "SUV"), both, 4.8, 35),
            driver("B", Coordinates(9.6680, 123.9250), setOf("SUV", "Pickup"), both, 4.9, 60)
                .copy(bookings = listOf(TimeWindow(at(12), at(13)))),
            driver("C", Coordinates(9.6500, 123.8550), setOf("Sedan", "Van"), setOf("Automatic"), 4.7, 20),
            driver("D", Coordinates(9.5780, 123.7460), setOf("Sedan", "SUV", "Van"), both, null, 0),
            driver("E", Coordinates(9.7100, 123.9900), setOf("Sedan", "SUV", "Pickup"), setOf("Manual"), 4.9, 80),
            driver("F", Coordinates(9.6881, 123.9741), setOf("Sedan", "SUV"), both, 5.0, 10).copy(approved = false),
            driver("G", Coordinates(9.6882, 123.9742), setOf("Sedan", "SUV"), both, 4.9, 25).copy(acceptingBookings = false),
            driver("H", Coordinates(9.6883, 123.9743), setOf("Sedan", "SUV"), both, 4.8, 30)
                .copy(locationAt = snapshotAt - DriverRecommendationEngine.LOCATION_MAX_AGE_MS - 1),
            driver("I", Coordinates(10.3157, 123.8854), setOf("Sedan", "SUV"), both, 4.8, 40).copy(serviceAreas = setOf("Cebu")),
            driver("J", Coordinates(10.3320, 123.9360), setOf("SUV", "Van", "Pickup"), both, 4.9, 65).copy(serviceAreas = setOf("Cebu")),
            driver("K", Coordinates(10.3100, 123.9650), setOf("Sedan", "Van"), setOf("Automatic"), null, 0).copy(serviceAreas = setOf("Cebu"))
        )
    }
}
