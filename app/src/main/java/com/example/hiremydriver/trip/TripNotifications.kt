package com.example.hiremydriver.trip

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.hiremydriver.R
import com.example.hiremydriver.TripInProgressActivity
import com.example.hiremydriver.maps.DriverTripMapActivity

/** Android renders the elapsed chronometer even when the map/app process is closed. */
object TripNotifications {
    private const val CHANNEL = "ongoing_trip_timer"
    private const val ID = 401
    private fun tag(id: String) = "trip_timer:$id"

    fun needsPermission(context: Context) = Build.VERSION.SDK_INT >= 33 &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

    fun onChange(context: Context, before: TripSession, after: TripSession) {
        if (after.phase != TripPhase.IN_PROGRESS) {
            if (before.phase == TripPhase.IN_PROGRESS) cancel(context, after.id)
        } else if (before.phase != TripPhase.IN_PROGRESS) {
            show(context, after, driver = true, alert = true)
        }
    }

    fun cancel(context: Context, tripId: String) {
        context.getSystemService(NotificationManager::class.java).cancel(tag(tripId), ID)
    }

    fun show(context: Context, trip: TripSession, driver: Boolean, alert: Boolean = false) {
        val uid = com.example.hiremydriver.notifications.AppNotifications.currentUid(context, driver)
        if (uid != null && context.getSharedPreferences("terminal_trips_$uid", Context.MODE_PRIVATE).getBoolean(trip.id, false)) {
            cancel(context, trip.id); return
        }
        val state = TripNotificationState.from(trip)
        if (state == null) { cancel(context, trip.id); return }
        if (needsPermission(context)) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) return
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "Trip timer", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Trip-start alert and ongoing service timer"
                lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
            })
        }
        // GPS/timer screen refreshes must not repost, reset, or alert the notification.
        if (manager.activeNotifications.any { it.tag == tag(trip.id) && it.id == ID }) return
        val screen = if (driver) DriverTripMapActivity::class.java else TripInProgressActivity::class.java
        val intent = Intent(context, screen).apply {
            putExtra(LocalTripStore.ID, trip.id)
            data = Uri.Builder().scheme("hiremydriver").authority("trip").appendPath(trip.id).build()
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openTrip = PendingIntent.getActivity(context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val hours = if (state.durationHours == 1) "1 hour" else "${state.durationHours} hours"
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_car_all)
            .setColor(android.graphics.Color.rgb(6, 27, 65))
            .setContentTitle("Trip in progress")
            .setContentText("Elapsed time · Booked for $hours")
            .setSubText("Service timer")
            .setWhen(state.startedAt)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(false)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setSilent(!alert)
            .setDefaults(if (alert) NotificationCompat.DEFAULT_ALL else 0)
            .setPriority(if (alert) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(openTrip)
            .build()
        // Permission can be revoked while the screen is open.
        try { manager.notify(tag(trip.id), ID, notification) } catch (_: SecurityException) { }
    }

}
