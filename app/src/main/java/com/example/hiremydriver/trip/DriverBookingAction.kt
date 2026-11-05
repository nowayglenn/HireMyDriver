package com.example.hiremydriver.trip

import android.app.Activity
import com.example.hiremydriver.R

/** The details screen and map use exactly the same confirmed transitions. */
object DriverBookingAction {
    private val departureDialogs = java.util.WeakHashMap<Activity, androidx.appcompat.app.AlertDialog>()
    fun label(trip: TripSession) = when (trip.phase) {
        TripPhase.REQUESTED -> "Accept booking"
        TripPhase.ACCEPTED -> "Start Going to Pickup"
        TripPhase.ON_THE_WAY -> "I've Arrived"
        TripPhase.ARRIVED -> "Start Service"
        TripPhase.IN_PROGRESS -> "Complete service"
        TripPhase.COMPLETED, TripPhase.DECLINED, TripPhase.CANCELLED -> "Back to bookings"
    }

    fun confirm(activity: Activity, trip: TripSession, save: ((TripSession) -> TripSession) -> Unit) {
        when (trip.phase) {
            TripPhase.REQUESTED -> BookingDialogs.confirm(activity, "Accept booking?",
                "${trip.customerName}\n${trip.pickupLabel} → ${trip.destinationLabel}", "Accept booking") { save { it.accept() } }
            TripPhase.ACCEPTED -> {
                if (departureDialogs[activity]?.isShowing == true) return
                val dialog = BookingDialogs.confirm(activity, "Start heading to customer?",
                    "The customer will be notified that you are on the way.", "Yes, I'm On My Way", "Cancel",
                    icon = R.drawable.ic_car_all) { save { it.headToPickup() } }
                departureDialogs[activity] = dialog
                dialog.setOnDismissListener { departureDialogs.remove(activity) }
            }
            TripPhase.ON_THE_WAY -> BookingDialogs.confirm(activity, "Confirm arrival?",
                "Have you arrived at the customer's pickup location? This will notify the customer. Service starts only after you confirm Start Service.",
                "Yes, I've Arrived", "Cancel", icon = R.drawable.ic_location) { save { it.arrive() } }
            TripPhase.ARRIVED -> BookingDialogs.confirm(activity, "Start service?",
                "Confirm that the customer is on board and ready. The service timer will start after confirmation.",
                "Start Service", "Cancel", requiredCheck = "The customer is on board and ready") {
                save { it.start(System.currentTimeMillis(), true) }
            }
            TripPhase.IN_PROGRESS -> BookingDialogs.confirm(activity, "Complete service?",
                "Confirm that the customer has finished this booking. Remaining booked time will stop.",
                "Complete service", "Keep going", icon = R.drawable.ic_flag, smoothCompletion = true) { save { it.complete(System.currentTimeMillis()) } }
            TripPhase.COMPLETED, TripPhase.DECLINED, TripPhase.CANCELLED -> activity.finish()
        }
    }
}
