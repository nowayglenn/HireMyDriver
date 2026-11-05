package com.example.hiremydriver.trip

enum class CustomerBookingGroup { PENDING, ACCEPTED, COMPLETED, CANCELLED }
enum class DriverBookingGroup { REQUESTS, UPCOMING, ONGOING, COMPLETED, DECLINED }

fun TripSession.customerGroup() = when (phase) {
    TripPhase.REQUESTED -> CustomerBookingGroup.PENDING
    TripPhase.ACCEPTED, TripPhase.ON_THE_WAY, TripPhase.ARRIVED, TripPhase.IN_PROGRESS -> CustomerBookingGroup.ACCEPTED
    TripPhase.COMPLETED -> CustomerBookingGroup.COMPLETED
    TripPhase.DECLINED, TripPhase.CANCELLED -> CustomerBookingGroup.CANCELLED
}

fun TripSession.driverGroup() = when (phase) {
    TripPhase.REQUESTED -> DriverBookingGroup.REQUESTS
    TripPhase.ACCEPTED, TripPhase.ON_THE_WAY, TripPhase.ARRIVED -> DriverBookingGroup.UPCOMING
    TripPhase.IN_PROGRESS -> DriverBookingGroup.ONGOING
    TripPhase.COMPLETED -> DriverBookingGroup.COMPLETED
    TripPhase.DECLINED, TripPhase.CANCELLED -> DriverBookingGroup.DECLINED
}
