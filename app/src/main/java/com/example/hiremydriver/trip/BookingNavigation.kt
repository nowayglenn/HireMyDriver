package com.example.hiremydriver.trip

import android.app.Activity
import android.content.Intent
import com.example.hiremydriver.BookingDetailsActivity
import com.example.hiremydriver.DriverRequestDetailsActivity
import com.example.hiremydriver.DriverAcceptedBookingDetailsActivity

object BookingNavigation {
    const val READ_ONLY = "TRIP_DETAILS_READ_ONLY"
    fun details(activity: Activity, id: String, driver: Boolean, readOnly: Boolean = false) {
        val trip = LocalTripStore(activity).get(id) ?: return
        val target = when (trip.detailPage(driver)) {
            BookingDetailPage.CUSTOMER -> BookingDetailsActivity::class.java
            BookingDetailPage.DRIVER_REQUEST -> DriverRequestDetailsActivity::class.java
            BookingDetailPage.DRIVER_BOOKING -> DriverAcceptedBookingDetailsActivity::class.java
        }
        activity.startActivity(Intent(activity, target).putExtra(LocalTripStore.ID, id).putExtra(READ_ONLY, readOnly))
    }
}
