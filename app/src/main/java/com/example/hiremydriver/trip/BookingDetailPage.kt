package com.example.hiremydriver.trip

enum class BookingDetailPage { CUSTOMER, DRIVER_REQUEST, DRIVER_BOOKING }

fun TripSession.detailPage(driver: Boolean) = when {
    !driver -> BookingDetailPage.CUSTOMER
    phase == TripPhase.REQUESTED || phase == TripPhase.DECLINED || phase == TripPhase.CANCELLED -> BookingDetailPage.DRIVER_REQUEST
    else -> BookingDetailPage.DRIVER_BOOKING
}
