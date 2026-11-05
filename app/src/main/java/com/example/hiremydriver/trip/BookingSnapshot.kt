package com.example.hiremydriver.trip

/** A Firestore disk snapshot must not roll back our last confirmed server result. */
internal fun bookingSnapshot(current: TripSession?, incoming: TripSession, fromCache: Boolean): TripSession =
    if (fromCache && current != null) current else incoming
