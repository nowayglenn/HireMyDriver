package com.example.hiremydriver.messaging

import com.example.hiremydriver.trip.TripSession

/** Cached booking identity is a routing hint only; Firestore still verifies chat access. */
internal fun bookingChatParticipants(booking: TripSession?, uid: String, driver: Boolean): Pair<String, String>? {
    if (booking == null || !booking.synced || uid.isBlank() ||
        booking.customerUid.isBlank() || booking.driverUid.isBlank() ||
        booking.customerUid == booking.driverUid) return null
    if (uid != if (driver) booking.driverUid else booking.customerUid) return null
    return booking.customerUid to booking.driverUid
}
