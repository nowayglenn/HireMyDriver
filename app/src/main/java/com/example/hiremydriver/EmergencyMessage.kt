package com.example.hiremydriver

import com.example.hiremydriver.trip.TripSession
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object EmergencyMessage {
    data class Position(val latitude: Double, val longitude: Double, val recordedAt: Long)
    fun body(trip: TripSession, includeTripDetails: Boolean, position: Position?, now: Long): String {
        val location = position?.takeIf {
            now - it.recordedAt in 0..120_000 && it.latitude.isFinite() && it.longitude.isFinite()
                && it.latitude in -90.0..90.0 && it.longitude in -180.0..180.0
        }
        fun format(pattern: String) = SimpleDateFormat(pattern, Locale.ENGLISH).apply {
            timeZone = TimeZone.getTimeZone("Asia/Manila")
        }.format(Date(now))
        return buildList {
            add("EMERGENCY ALERT - Hire My Driver")
            add("I need immediate assistance during my trip.")
            add("Customer: ${trip.customerName}")
            if (location != null) {
                add("Current Location: ${location.latitude}, ${location.longitude}")
                add("View My Location: https://maps.google.com/?q=${location.latitude},${location.longitude}")
            } else add("Current Location: Could not be determined. Map link unavailable.")
            if (includeTripDetails) {
                add("Pickup Location: ${trip.pickup?.label ?: trip.pickupLabel}")
                add("Destination: " + (trip.destinations.takeIf { it.isNotEmpty() }?.joinToString(" -> ") { it.label } ?: trip.destinationLabel))
                add("Driver: ${trip.driverName}")
            }
            add("Date: ${format("MMMM d, yyyy")}")
            add("Time: ${format("hh:mm:ss a")} (Philippine Standard Time)")
            add("Please contact me or seek assistance immediately.")
            add("Generated through the Hire My Driver application.")
        }.joinToString("\n\n")
    }
}
