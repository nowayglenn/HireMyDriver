package com.example.hiremydriver.recommendation

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.util.Locale
import java.util.concurrent.Executors

/** Foreground, one-shot Android location. Works without depending on Google Play services. */
class RecommendationLocation(context: Context,
    private val resolveArea: (android.location.Address) -> String? = {
        RecommendationArea.fromAddress(it.countryCode, listOf(it.adminArea, it.subAdminArea, it.locality))
    }
) : LocationListener {
    private val context = context.applicationContext
    private val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private var generation = 0
    private var resolving = false
    private var callback: ((Location?, String?) -> Unit)? = null
    private val timeout = Runnable { finish(null, null) }

    @SuppressLint("MissingPermission")
    fun start(onResult: (Location?, String?) -> Unit) {
        stop()
        callback = onResult
        handler.postDelayed(timeout, 15_000)
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
        if (providers.isEmpty()) { finish(null, null); return }
        val cached = providers.mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .filter(::isUsable).minByOrNull { it.accuracy }
        if (cached != null) { onLocationChanged(cached); return }
        var listening = false
        providers.forEach { provider ->
            if (runCatching { manager.requestLocationUpdates(provider, 1000L, 0f, this, Looper.getMainLooper()) }.isSuccess) listening = true
        }
        if (!listening) finish(null, null)
    }

    override fun onLocationChanged(location: Location) {
        if (callback == null || resolving || !isUsable(location)) return
        if (!Geocoder.isPresent()) { finish(null, null); return }
        resolving = true
        runCatching { manager.removeUpdates(this) }
        val token = generation
        worker.execute {
            @Suppress("DEPRECATION")
            val addresses = runCatching {
                Geocoder(context, Locale.ENGLISH).getFromLocation(location.latitude, location.longitude, 3)
            }.getOrNull().orEmpty()
            val area = addresses.firstNotNullOfOrNull(resolveArea)
            handler.post {
                if (token == generation && callback != null) finish(if (area == null) null else location, area)
            }
        }
    }

    private fun isUsable(location: Location): Boolean = location.hasAccuracy() &&
        RecommendationArea.isFreshFix((SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1_000_000, location.accuracy) &&
        location.latitude.isFinite() && location.latitude in -85.0..85.0 &&
        location.longitude.isFinite() && location.longitude in -180.0..180.0

    private fun finish(location: Location?, area: String?) {
        val result = callback
        stop()
        result?.invoke(location, area)
    }

    fun stop() {
        generation++
        callback = null
        resolving = false
        handler.removeCallbacks(timeout)
        runCatching { manager.removeUpdates(this) }
    }

    fun close() { stop(); worker.shutdownNow() }
    override fun onProviderDisabled(provider: String) {}
    override fun onProviderEnabled(provider: String) {}
    @Deprecated("Legacy Android callback")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
}
