package com.example.hiremydriver.recommendation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.example.hiremydriver.CustomerAuth
import java.util.Locale
import java.util.concurrent.Executors

/** GPS first; an actual geocoded saved address is the fallback, never a hardcoded province centre. */
class CustomerServiceArea(private val context: Context) {
    private val reader = RecommendationLocation(context)
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var generation = 0
    private var deadline: Runnable? = null
    data class CachedArea(val area: String, val coordinates: Coordinates, val capturedAt: Long)

    fun cachedArea(): CachedArea? {
        val uid = CustomerAuth.currentUser(context)?.uid ?: return null
        val prefs = context.getSharedPreferences("customer_profile_$uid", Context.MODE_PRIVATE)
        val at = prefs.getLong("matching_recent_at", 0)
        val permitted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!permitted || System.currentTimeMillis() - at !in 0..300_000) return null
        val area = prefs.getString("matching_recent_area", null) ?: return null
        val lat = prefs.getString("matching_recent_lat", null)?.toDoubleOrNull() ?: return null
        val lng = prefs.getString("matching_recent_lng", null)?.toDoubleOrNull() ?: return null
        return runCatching { CachedArea(area, Coordinates(lat, lng), at) }.getOrNull()
    }
    fun resolve(done: (area: String?, coords: Coordinates?) -> Unit) {
        stop()
        val token = generation
        val uid = CustomerAuth.currentUser(context)?.uid ?: run { done(null, null); return }
        val prefs = context.getSharedPreferences("customer_profile_$uid", Context.MODE_PRIVATE)
        var delivered = false
        fun deliver(area: String?, coords: Coordinates?) {
            if (token != generation || delivered) return
            delivered = true
            deadline?.let(main::removeCallbacks)
            deadline = null
            reader.stop()
            if (CustomerAuth.currentUser(context)?.uid == uid) done(area, coords)
        }
        // Bound GPS plus the saved-address fallback; some geocoder services never return.
        deadline = Runnable { deliver(null, null) }.also { main.postDelayed(it, 25_000) }
        fun fallback() {
            val saved = prefs.getString("address", "").orEmpty().trim()
            if (saved.isEmpty()) { deliver(null, null); return }
            if (saved == prefs.getString("matching_address", null)) {
                val lat = prefs.getString("matching_saved_lat", null)?.toDoubleOrNull()
                val lng = prefs.getString("matching_saved_lng", null)?.toDoubleOrNull()
                val area = prefs.getString("matching_saved_area", null)
                if (lat != null && lng != null && area != null) { deliver(area, Coordinates(lat, lng)); return }
            }
            worker.execute {
                @Suppress("DEPRECATION")
                val address = runCatching { Geocoder(context, Locale.ENGLISH).getFromLocationName(saved, 1)?.firstOrNull() }.getOrNull()
                val area = address?.let { RecommendationArea.fromAddress(it.countryCode, listOf(it.adminArea, it.subAdminArea, it.locality)) }
                val coords = address?.takeIf { it.hasLatitude() && it.hasLongitude() }?.let { Coordinates(it.latitude, it.longitude) }
                main.post {
                    if (token != generation || delivered) return@post
                    if (area != null && coords != null) prefs.edit().putString("matching_address", saved)
                        .putString("matching_saved_area", area).putString("matching_saved_lat", coords.latitude.toString())
                        .putString("matching_saved_lng", coords.longitude.toString()).apply()
                    deliver(area, coords)
                }
            }
        }
        val permission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!permission) { fallback(); return }
        reader.start { location, area ->
            if (token != generation) return@start
            if (location != null && area != null) {
                if (CustomerAuth.currentUser(context)?.uid == uid) prefs.edit()
                    .putString("matching_recent_area", area)
                    .putString("matching_recent_lat", location.latitude.toString())
                    .putString("matching_recent_lng", location.longitude.toString())
                    .putLong("matching_recent_at", System.currentTimeMillis()).apply()
                deliver(area, Coordinates(location.latitude, location.longitude))
            } else fallback()
        }
    }
    fun stop() { generation++; deadline?.let(main::removeCallbacks); deadline = null; reader.stop() }
    fun close() { stop(); reader.close(); worker.shutdownNow() }
}
