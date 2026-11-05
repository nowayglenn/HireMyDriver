package com.example.hiremydriver.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.hiremydriver.*
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import java.util.UUID

object AppNotifications {
    const val CHANNEL = "trip_updates_v2"
    private var registered: String? = null
    fun currentUid(c: Context, driver: Boolean) = if (driver) DriverAuth.currentUser(c)?.uid else CustomerAuth.currentUser(c)?.uid
    fun db(c: Context, driver: Boolean) = if (driver) FirebaseFirestore.getInstance(DriverAuth.firebaseApp(c)) else FirebaseFirestore.getInstance()
    private fun device(c: Context): String {
        val p = c.getSharedPreferences("notification_device", Context.MODE_PRIVATE)
        return p.getString("installation", null) ?: UUID.randomUUID().toString().also { p.edit().putString("installation", it).commit() }
    }
    fun register(c: Context, force: Boolean = false) {
        val role = LaunchSession.role(c)
        if (role == LaunchRole.NONE) return
        val driver = role == LaunchRole.DRIVER
        val uid = currentUid(c, driver) ?: return
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            if (currentUid(c, driver) != uid || LaunchSession.role(c) != role) return@addOnSuccessListener
            val signature = "$uid:$token"
            if (!force && registered == signature) return@addOnSuccessListener
            db(c, driver).collection("users").document(uid).collection("devices").document(device(c))
                .set(mapOf("token" to token, "role" to if (driver) "driver" else "customer", "updatedAt" to FieldValue.serverTimestamp()))
                .addOnSuccessListener { registered = signature }
        }
        EventRelay.schedule(c)
        EventRelay.flush(c.applicationContext) {}
    }
    fun unregister(c: Context) {
        val driver = LaunchSession.role(c) == LaunchRole.DRIVER
        currentUid(c, driver)?.let { uid -> db(c, driver).collection("users").document(uid).collection("devices").document(device(c)).delete() }
        registered = null
        c.getSystemService(NotificationManager::class.java).cancelAll()
    }
    fun intent(c: Context, data: Map<String, String>) = Intent(c, NotificationOpenActivity::class.java).apply {
        data.forEach { (k, v) -> putExtra(k, v) }
        this.data = android.net.Uri.parse("hiremydriver://notification/" + android.net.Uri.encode(data["eventId"]))
        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
    @Synchronized fun show(c: Context, data: Map<String, String>) {
        val uid = data["uid"] ?: return
        val driver = data["role"] == "driver"
        if (currentUid(c, driver) != uid || LaunchSession.role(c) != if (driver) LaunchRole.DRIVER else LaunchRole.CUSTOMER) return
        // Completion must stop Android's ongoing chronometer even while the app is closed.
        if (data["kind"] == "booking" && (data["phase"] in setOf("COMPLETED", "CANCELLED", "DECLINED") ||
                data["eventId"]?.endsWith("_COMPLETED") == true || data["eventId"]?.endsWith("_CANCELLED") == true)) {
            data["bookingId"]?.let { id ->
                c.getSharedPreferences("terminal_trips_$uid", Context.MODE_PRIVATE).edit().putBoolean(id, true).apply()
                com.example.hiremydriver.trip.TripNotifications.cancel(c, id)
                db(c, driver).collection("bookings").document(id).get(com.google.firebase.firestore.Source.SERVER).addOnSuccessListener { document ->
                    if (currentUid(c, driver) == uid && LaunchSession.role(c) == if (driver) LaunchRole.DRIVER else LaunchRole.CUSTOMER) {
                        runCatching { com.example.hiremydriver.trip.FirebaseTripRepository.decode(document.data!!) }.getOrNull()?.let { trip ->
                            if (trip.customerUid == uid || trip.driverUid == uid) com.example.hiremydriver.trip.LocalTripStore(c).save(trip)
                        }
                    }
                }
            }
        }
        val choices = CustomerNotificationPreferences(c).load()
        if (!driver && ((data["kind"] == "message" && !choices.chatMessages) || (data["kind"] == "booking" && !choices.bookingUpdates))) return
        if (com.example.hiremydriver.trip.TripNotifications.needsPermission(c)) return
        val event = data["eventId"] ?: return
        val seen = c.getSharedPreferences("notification_seen_$uid", Context.MODE_PRIVATE)
        if (seen.contains(event)) return
        val manager = c.getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) return
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel(CHANNEL, "Trip updates and messages", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Booking, arrival, service and destination alerts"
            enableVibration(true)
            setSound(android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION),
                android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION).build())
        })
        val pending = PendingIntent.getActivity(c, 0, intent(c, data), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(c, CHANNEL).setSmallIcon(R.drawable.ic_car_all)
            .setColor(android.graphics.Color.rgb(6, 27, 65)).setContentTitle(data["title"] ?: "Hire My Driver")
            .setContentText(data["body"] ?: "Open the app for details").setAutoCancel(true)
            .setContentIntent(pending).setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setStyle(NotificationCompat.BigTextStyle().bigText(data["body"] ?: "Open the app for details"))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).build()
        try { manager.notify(event, 7483, notification); seen.edit().putLong(event, System.currentTimeMillis()).apply() } catch (_: SecurityException) { }
    }
}
class AppMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) { AppNotifications.register(this, true) }
    override fun onMessageReceived(message: RemoteMessage) { AppNotifications.show(this, message.data) }
}
