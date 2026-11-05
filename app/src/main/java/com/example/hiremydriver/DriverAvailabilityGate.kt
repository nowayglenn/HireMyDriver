package com.example.hiremydriver

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog

/** Approval and saved driving information are separate prerequisites. */
object DriverAvailabilityGate {
    const val PENDING = "Pending approval. You can turn on availability after admin verification."
    fun blocked(activity: AppCompatActivity): Boolean {
        if (!DriverAuth.approved(activity)) {
            AlertDialog.Builder(activity).setTitle("Pending approval").setMessage(PENDING)
                .setPositiveButton("OK", null).show()
            return true
        }
        if (!DriverScheduleStore(activity).drivingComplete) {
            AlertDialog.Builder(activity).setTitle("Driving information incomplete")
                .setMessage("Please complete your driving information before turning on your availability and accepting bookings.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Complete information") { _, _ ->
                    activity.startActivity(Intent(activity, DriverDrivingInfoActivity::class.java))
                }.show()
            return true
        }
        return false
    }
}
