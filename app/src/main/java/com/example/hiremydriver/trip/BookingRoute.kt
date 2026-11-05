package com.example.hiremydriver.trip

data class BookingRouteEntry(val place: String, val caption: String)

fun bookingRoute(pickup: String, destinations: List<String>): List<BookingRouteEntry> =
    listOf(BookingRouteEntry(pickup, "Pickup")) + destinations.mapIndexed { index, place ->
        BookingRouteEntry(place, "Destination ${index + 1}")
    }

/** Keep every confirmed destination in history, including already visited stops. */
fun TripSession.bookingRoute() = bookingRoute(pickup?.label ?: pickupLabel,
    destinations.takeIf { it.isNotEmpty() }?.map { it.label } ?: listOf(destinationLabel))
