package com.example.hiremydriver.trip

import java.text.SimpleDateFormat
import java.util.Locale

/** Stable schedule order, restricted to real bookings for the signed-in driver. */
fun List<TripSession>.forDriver(uid: String): List<TripSession> {
    if (uid.isBlank()) return emptyList()
    val format = SimpleDateFormat("MMM d, yyyy h:mm a", Locale.ENGLISH).apply { isLenient = false }
    return filter { it.synced && it.driverUid == uid }.distinctBy { it.id }
        .sortedWith(compareBy<TripSession> {
            runCatching { format.parse("${it.date} ${it.time}")?.time }.getOrNull() ?: Long.MAX_VALUE
        }.thenBy { it.id })
}

/** Actual completion order; legacy bookings without a completion time stay at the end. */
fun List<TripSession>.completedNewestFirst(): List<TripSession> =
    filter { it.phase == TripPhase.COMPLETED }
        .sortedWith(compareByDescending<TripSession> { it.completedAt ?: 0L }.thenBy { it.id })
