package com.example.hiremydriver.recommendation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.example.hiremydriver.R

/** Shared lifecycle for Home and See all; the original screens remain the presentation layer. */
class NearbyRecommendations(
    private val activity: AppCompatActivity,
    private val onState: (State) -> Unit
) : DefaultLifecycleObserver {
    data class State(
        val message: String,
        val area: String? = null,
        val matches: List<DriverMatch> = emptyList(),
        val loading: Boolean = false
    )

    private val reader = CustomerServiceArea(activity)
    private var generation = 0
    private var watchGeneration = 0
    private var lastState: State? = null
    private var matchingWatch: com.google.firebase.firestore.ListenerRegistration? = null
    private var refreshing = false
    private var askedPermission = false
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var matchingDeadline: Runnable? = null
    private var previousArea: String? = null
    private var previousCoordinates: Coordinates? = null
    private var locationAt = 0L
    private val uid = com.example.hiremydriver.CustomerAuth.currentUser(activity)?.uid.orEmpty()
    companion object {
        private data class Snapshot(val uid: String, val state: State, val coords: Coordinates?, val at: Long)
        private var snapshot: Snapshot? = null
        fun clearCache() { snapshot = null }
    }
    private val recheck = object : Runnable {
        override fun run() { refresh(); handler.postDelayed(this, 60000) }
    }
    private val permission = activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (hasPermission()) refresh() else unavailable()
    }

    init {
        reader.cachedArea()?.let {
            previousArea = it.area; previousCoordinates = it.coordinates; locationAt = it.capturedAt
        }
        snapshot?.takeIf { it.uid == uid && System.currentTimeMillis() - it.at in 0..300_000 }?.let {
            previousArea = it.state.area; previousCoordinates = it.coords; locationAt = it.at
            lastState = it.state; onState(it.state)
        }
        activity.lifecycle.addObserver(this)
    }

    private fun hasPermission() =
        ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun retry() {
        if (!hasPermission()) {
            if (askedPermission && !activity.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION)) {
                openSettings(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", activity.packageName, null)))
            } else {
                askedPermission = true
                permission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
            }
        } else if (!LocationManagerCompat.isLocationEnabled(activity.getSystemService(LocationManager::class.java))) {
            openSettings(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        } else refresh()
    }

    private fun openSettings(intent: Intent) {
        try { activity.startActivity(intent) }
        catch (_: android.content.ActivityNotFoundException) {
            Toast.makeText(activity, R.string.permission_settings_unavailable, Toast.LENGTH_LONG).show()
        }
    }

    private fun unavailable() {
        refreshing = false
        if (lastState?.matches?.isNotEmpty() == true) onState(lastState!!)
        else emit(State(activity.getString(R.string.nearby_location_required)))
    }

    private fun emit(state: State) { lastState = state; onState(state) }

    fun refresh() {
        if (refreshing) return
        if (!activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return
        refreshing = true
        val token = ++generation
        if (matchingWatch == null && lastState?.matches.isNullOrEmpty()) emit(State(activity.getString(R.string.recommendation_locating), loading = true))
        val resolved: (String?, Coordinates?) -> Unit = resolved@{ area, coords ->
            if (token != generation) return@resolved
            if (area == null) {
                matchingWatch?.remove(); matchingWatch = null; watchGeneration++
                unavailable()
            } else {
                val previous = previousCoordinates
                if (matchingWatch != null && area == previousArea && coords != null && previous != null &&
                    DriverRecommendationEngine.distanceKm(coords, previous) < 0.25) { refreshing = false; lastState?.let(onState); return@resolved }
                previousArea = area; previousCoordinates = coords
                matchingWatch?.remove()
                val watchToken = ++watchGeneration
                if (lastState?.matches.isNullOrEmpty()) emit(State(activity.getString(R.string.recommendation_matches_loading), area, loading = true))
                matchingDeadline?.let(handler::removeCallbacks)
                matchingDeadline = Runnable {
                    if (watchToken == watchGeneration) {
                        refreshing = false
                        watchGeneration++
                        matchingWatch?.remove(); matchingWatch = null
                        if (lastState?.matches.isNullOrEmpty()) emit(State(activity.getString(R.string.nearby_connection_retry), area))
                        else lastState?.let(onState)
                    }
                }.also { handler.postDelayed(it, 15_000) }
                matchingWatch = FirebaseMatching.search(activity, area, originCoords = coords, watch = true, browsing = true) { matches, error ->
                    if (watchToken != watchGeneration) return@search
                    matchingDeadline?.let(handler::removeCallbacks); matchingDeadline = null
                    refreshing = false
                    if (error != null && lastState?.matches?.isNotEmpty() == true) { onState(lastState!!); return@search }
                    val message = error ?: activity.getString(if (matches.isEmpty()) R.string.nearby_empty else R.string.nearby_summary, area)
                    val state = State(message, area, matches)
                    if (error == null) snapshot = Snapshot(uid, state, coords, locationAt)
                    if (lastState != state) emit(state)
                }
            }
        }
        val age = System.currentTimeMillis() - locationAt
        if (previousArea != null && age in 0..300_000) {
            // Start the directory read immediately instead of waiting for GPS/geocoding again.
            resolved(previousArea, previousCoordinates)
            if (age <= 60_000) return
        }
        reader.resolve { area, coords ->
            if (token != generation) return@resolve
            // A failed background location refresh must not discard the usable current list.
            if (area == null && previousArea != null && age in 0..300_000) return@resolve
            if (area != null) locationAt = System.currentTimeMillis()
            resolved(area, coords)
        }
    }

    override fun onResume(owner: LifecycleOwner) { refresh(); handler.postDelayed(recheck, 60000) }
    override fun onPause(owner: LifecycleOwner) { handler.removeCallbacks(recheck); matchingDeadline?.let(handler::removeCallbacks); matchingDeadline = null; watchGeneration++; matchingWatch?.remove(); matchingWatch = null; generation++; reader.stop(); refreshing = false }
    override fun onDestroy(owner: LifecycleOwner) { reader.close() }
}
