package com.example.hiremydriver.maps

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Executors
import javax.net.ssl.HttpsURLConnection

/** Reuses a route in memory across customer, driver and full-screen views of the same itinerary. */
object GoogleRoadRoutes {
    private data class Cached(val expiresAt: Long, val geometry: List<RouteCoordinate>)
    private val cache = linkedMapOf<List<RouteCoordinate>, Cached>()
    private val pending = mutableMapOf<List<RouteCoordinate>, MutableList<Subscription>>()
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    class Subscription(private var callback: ((Result<List<RouteCoordinate>>) -> Unit)?) {
        fun cancel() { callback = null }
        internal fun deliver(result: Result<List<RouteCoordinate>>) { callback?.invoke(result); callback = null }
    }

    fun request(context: Context, points: List<RouteCoordinate>, callback: (Result<List<RouteCoordinate>>) -> Unit): Subscription {
        val key = points.toList()
        val subscription = Subscription(callback)
        val app = context.applicationContext
        synchronized(this) {
            val stored = cache[key]?.takeIf { it.expiresAt > System.currentTimeMillis() }
            if (stored != null) {
                main.post { subscription.deliver(Result.success(stored.geometry)) }
                return subscription
            }
            pending[key]?.let { it.add(subscription); return subscription }
            pending[key] = mutableListOf(subscription)
        }
        executor.execute {
            val result = runCatching { compute(app, key) }
            val listeners = synchronized(this) {
                result.getOrNull()?.let {
                    // Ephemeral display reuse only: never write Google's route geometry to disk.
                    cache[key] = Cached(System.currentTimeMillis() + 30 * 60_000L, it)
                    while (cache.size > 20) cache.remove(cache.keys.first())
                }
                pending.remove(key).orEmpty().toList()
            }
            main.post { listeners.forEach { it.deliver(result) } }
        }
        return subscription
    }

    @Suppress("DEPRECATION")
    private fun compute(context: Context, points: List<RouteCoordinate>): List<RouteCoordinate> {
        val apiKey = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
            .metaData?.getString("com.example.hiremydriver.ROUTES_API_KEY").orEmpty().trim()
        check(apiKey.isNotEmpty()) { "Route setup is not finished yet." }
        val signatures = if (Build.VERSION.SDK_INT >= 28) {
            context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                .signingInfo?.apkContentsSigners
        } else {
            context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures
        }
        val certificate = checkNotNull(signatures?.firstOrNull()) { "Unable to verify this app for routing." }
        val fingerprint = MessageDigest.getInstance("SHA-1").digest(certificate.toByteArray())
            .joinToString("") { "%02X".format(it.toInt() and 255) }
        val batches = RoadRoute.batches(points)
        check(batches.isNotEmpty()) { "Choose different pickup and destination pins." }
        return buildList {
            batches.forEach { batch ->
                val segment = fetch(apiKey, context.packageName, fingerprint, batch)
                addAll(if (lastOrNull() == segment.firstOrNull()) segment.drop(1) else segment)
            }
        }
    }

    private fun fetch(key: String, packageName: String, fingerprint: String, points: List<RouteCoordinate>): List<RouteCoordinate> {
        fun waypoint(point: RouteCoordinate) = JSONObject().put("location", JSONObject().put("latLng",
            JSONObject().put("latitude", point.latitude).put("longitude", point.longitude)))
        val body = JSONObject()
            .put("origin", waypoint(points.first()))
            .put("destination", waypoint(points.last()))
            .put("intermediates", JSONArray().apply { points.drop(1).dropLast(1).forEach { put(waypoint(it)) } })
            .put("travelMode", "DRIVE")
            .put("routingPreference", "TRAFFIC_UNAWARE")
            .put("computeAlternativeRoutes", false)
            .put("polylineQuality", "HIGH_QUALITY")
            .put("polylineEncoding", "ENCODED_POLYLINE")
        val connection = URL("https://routes.googleapis.com/directions/v2:computeRoutes").openConnection() as HttpsURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 10_000
            connection.readTimeout = 20_000
            connection.instanceFollowRedirects = false
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            connection.setRequestProperty("X-Goog-Api-Key", key)
            connection.setRequestProperty("X-Android-Package", packageName)
            connection.setRequestProperty("X-Android-Cert", fingerprint)
            connection.setRequestProperty("X-Goog-FieldMask", "routes.polyline.encodedPolyline")
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            check(connection.responseCode == 200) { "Route unavailable. Please try again later." }
            val response = connection.inputStream.use { stream ->
                val bytes = stream.readBytes()
                check(bytes.size <= 4_000_000) { "Route response is too large." }
                JSONObject(bytes.toString(Charsets.UTF_8))
            }
            val encoded = response.optJSONArray("routes")?.optJSONObject(0)
                ?.optJSONObject("polyline")?.optString("encodedPolyline").orEmpty()
            check(encoded.isNotEmpty()) { "No driving route was found between these pins." }
            return RoadRoute.decode(encoded).also { check(it.size >= 2) { "No driving route was found between these pins." } }
        } finally {
            connection.disconnect()
        }
    }
}
