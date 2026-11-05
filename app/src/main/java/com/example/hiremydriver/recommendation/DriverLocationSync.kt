package com.example.hiremydriver.recommendation

import android.content.Context
import android.location.Geocoder
import android.location.LocationManager
import android.os.SystemClock
import com.example.hiremydriver.DriverAuth
import com.example.hiremydriver.DriverPersonalSettingsActivity
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Locale
import java.util.concurrent.Executors

/** Saved measured/geocoded position; does not claim continuous live tracking. */
object DriverLocationSync {
    private val worker = Executors.newSingleThreadExecutor()
    private val resolving = mutableSetOf<String>()
    fun refresh(context: Context) {
        val c = context.applicationContext
        val uid = DriverAuth.currentUser(c)?.uid ?: return
        val prefs = c.getSharedPreferences("driver_position_$uid", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong("savedAt", 0) < 60000) return
        val manager = c.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val location = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).mapNotNull {
            @Suppress("MissingPermission")
            runCatching { manager.getLastKnownLocation(it) }.getOrNull()
        }.filter { it.hasAccuracy() && RecommendationArea.isFreshFix((SystemClock.elapsedRealtimeNanos() - it.elapsedRealtimeNanos) / 1000000, it.accuracy) }
            .minByOrNull { it.accuracy }
        fun save(coords: Coordinates, source: String, capturedAt: Long, address: String = "") {
            if (DriverAuth.currentUser(c)?.uid != uid) return
            val lat = prefs.getString("lat", null)?.toDoubleOrNull()
            val lng = prefs.getString("lng", null)?.toDoubleOrNull()
            if (lat != null && lng != null && now - prefs.getLong("savedAt", 0) < 300000 &&
                DriverRecommendationEngine.distanceKm(Coordinates(lat, lng), coords) < 0.25) return
            FirebaseFirestore.getInstance(DriverAuth.firebaseApp(c)).collection("driverProfiles").document(uid)
                .update("savedLocation", mapOf("latitude" to coords.latitude, "longitude" to coords.longitude,
                    "capturedAt" to capturedAt, "source" to source))
                .addOnSuccessListener { prefs.edit().putString("lat", coords.latitude.toString()).putString("lng", coords.longitude.toString())
                    .putString("source", source).putString("address", address).putLong("savedAt", now).apply() }
        }
        if (location != null) { save(Coordinates(location.latitude, location.longitude), "gps", location.time); return }
        // Keep a measured saved position if GPS is temporarily unavailable.
        if (prefs.getString("source", null) == "gps") return
        val address = c.getSharedPreferences(DriverAuth.preferenceName(c, DriverPersonalSettingsActivity.PREFERENCES), Context.MODE_PRIVATE)
            .getString("address", "").orEmpty().trim()
        if (address.isBlank() || address == prefs.getString("address", null) || !resolving.add(uid)) return
        worker.execute {
            @Suppress("DEPRECATION")
            val result = runCatching { Geocoder(c, Locale.ENGLISH).getFromLocationName(address, 1)?.firstOrNull() }.getOrNull()
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                resolving.remove(uid)
                if (result != null && result.hasLatitude() && result.hasLongitude())
                    save(Coordinates(result.latitude, result.longitude), "address", now, address)
            }
        }
    }
}
