package com.example.hiremydriver.maps

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.hiremydriver.BaseActivity
import com.example.hiremydriver.trip.LocalTripStore
import com.example.hiremydriver.trip.TripSession
import com.example.hiremydriver.trip.TripNotifications

/** Foreground location only; customer screens never publish their phone as the driver's position. */
open class MapScreenActivity : BaseActivity(), LocationListener {
    override fun onContentChanged() {
        super.onContentChanged()
        val content = findViewById<android.view.ViewGroup>(android.R.id.content) ?: return
        val root = content.getChildAt(0) ?: return
        val header: android.view.View? = root.findViewById<android.view.View>(com.example.hiremydriver.R.id.mapOverlayContent)
            ?: root.findViewById<android.view.View>(com.example.hiremydriver.R.id.mapFloatingHeader)
        val headerPadding = header?.let { intArrayOf(it.paddingLeft, it.paddingTop, it.paddingRight, it.paddingBottom) }
        // The standalone route map has no bottom sheet to reserve space for system navigation.
        val standaloneControls = listOf(com.example.hiremydriver.R.id.fabRecenter, com.example.hiremydriver.R.id.btnMapDetails)
            .mapNotNull { id -> root.findViewById<android.view.View>(id) }
            .mapNotNull { view -> (view.layoutParams as? android.view.ViewGroup.MarginLayoutParams)?.let {
                Triple(view, it.bottomMargin, Pair(it.marginStart, it.marginEnd))
            } }
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(content) { _, insets ->
            val safe = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars() or androidx.core.view.WindowInsetsCompat.Type.displayCutout())
            val ime = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime()).bottom
            content.setPadding(0, 0, 0, ime)
            if (header != null && headerPadding != null)
                header.setPadding(headerPadding[0] + safe.left, headerPadding[1] + safe.top,
                    headerPadding[2] + safe.right, headerPadding[3])
            standaloneControls.forEach { (view, bottom, sides) ->
                val params = view.layoutParams as android.view.ViewGroup.MarginLayoutParams
                params.bottomMargin = bottom + safe.bottom
                val rtl = view.layoutDirection == android.view.View.LAYOUT_DIRECTION_RTL
                params.marginStart = sides.first + if (rtl) safe.right else safe.left
                params.marginEnd = sides.second + if (rtl) safe.left else safe.right
                view.layoutParams = params
            }
            root.findViewById<android.view.View>(com.example.hiremydriver.R.id.mapBottomSheet)?.setPadding(safe.left, 0, safe.right, safe.bottom)
            androidx.core.view.WindowInsetsCompat.CONSUMED
        }
        androidx.core.view.ViewCompat.requestApplyInsets(content)
    }
    fun setMapSheetClearance(height: Int) {
        val headerHeight = findViewById<android.view.View>(com.example.hiremydriver.R.id.mapOverlayContent)?.height ?: 0
        tripMap?.setTopOverlayHeight(headerHeight)
        tripMap?.setLayerBottomClearance(height)
        findViewById<android.view.View>(com.example.hiremydriver.R.id.centerPickerPin)?.let { pin ->
            pin.translationY = (headerHeight - height) / 2f - pin.height / 2f
        }
        listOf(com.example.hiremydriver.R.id.btnMyLocation, com.example.hiremydriver.R.id.btnMapRecenter)
            .mapNotNull { findViewById<android.view.View>(it) }.forEach { control ->
            val parent = control.parent as? android.view.View ?: return@forEach
            val gap = (16 * resources.displayMetrics.density).toInt()
            if (parent.height > 0 && control.height > 0)
                control.translationY = (parent.height - height - gap - control.bottom).toFloat()
        }
    }
    protected var tripMap: TripMapController? = null
    fun preservePickerSelection() { tripMap?.freezePickerSelection() }
    private var wantsLocation=false
    private val listeningProviders = mutableSetOf<String>()
    private var currentLocationRequested = false
    private var locationRequestGeneration = 0
    private val currentLocationSignals = mutableListOf<android.os.CancellationSignal>()
    private val locationHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val currentLocationTimeout = Runnable { cancelCurrentLocationRequest() }
    private var driverFollowEnabled = false
    private var notificationTripId: String? = null
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationTripId?.let { id ->
            if (granted) LocalTripStore(this).get(id)?.let { TripNotifications.show(this, it, driver = true, alert = true) }
            else Toast.makeText(this, "Trip started. Allow notifications in Settings to see the timer outside the app.", Toast.LENGTH_LONG).show()
        }
        notificationTripId = null
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        notificationTripId = savedInstanceState?.getString("notification_trip_id")
    }
    protected fun offerTripNotification(trip: TripSession) {
        if (TripNotifications.needsPermission(this)) {
            notificationTripId = trip.id
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else TripNotifications.show(this, trip, driver = true, alert = true)
    }
    private val manager by lazy { getSystemService(LOCATION_SERVICE) as LocationManager }
    private val permissions=registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if(result.values.any{it}) startLocation() else Toast.makeText(this,"Location permission denied. You can still use the map and pins.",Toast.LENGTH_LONG).show()
    }
    protected fun useMyLocation(){wantsLocation=true
        if(hasPermission())startLocation() else permissions.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION))
    }
    private fun stopLocationUpdates() {
        cancelCurrentLocationRequest()
        if (listeningProviders.isEmpty()) return
        manager.removeUpdates(this)
        listeningProviders.clear()
    }
    protected fun stopOwnLocation(){wantsLocation=false;stopLocationUpdates()}
    protected fun setDriverFollowEnabled(enabled: Boolean) {
        if (driverFollowEnabled == enabled) return
        driverFollowEnabled = enabled
        tripMap?.setNavigationEnabled(enabled)
        if (enabled) useMyLocation() else stopOwnLocation()
    }
    protected fun resumeDriverFollow() {
        tripMap?.followLocation()
        currentLocationRequested = true
        useMyLocation()
    }
    protected fun showOwnLocation(follow: Boolean = false) {
        tripMap?.setNavigationEnabled(true, isDriver = false)
        if (follow) {
            tripMap?.followLocation()
            currentLocationRequested = true
        } else tripMap?.pauseLocationFollow()
        useMyLocation()
    }
    private fun hasPermission()=ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED
    @Suppress("MissingPermission")
    private fun startLocation(){
        if (!wantsLocation || !hasPermission() ||
            !lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) return
        val providers=listOf(LocationManager.NETWORK_PROVIDER,LocationManager.GPS_PROVIDER)
            .filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
        if(providers.isEmpty()){Toast.makeText(this,"Turn on your phone's Location setting to use GPS.",Toast.LENGTH_LONG).show();return}
        // Check both providers before registering, including when already subscribed.
        // The newest valid cached fix can satisfy a button tap immediately.
        if (currentLocationRequested || listeningProviders.isEmpty()) {
            val cached = providers.mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            val latest = NavigationFix.newestUsable(cached.map { it.navigationSample() }, android.os.SystemClock.elapsedRealtime())
            cached.firstOrNull { it.navigationSample() == latest }?.let(::onLocationChanged)
        }
        providers.forEach { provider ->
            if (!wantsLocation) return
            if (provider in listeningProviders) return@forEach
            runCatching { manager.requestLocationUpdates(provider,1000L,1f,this)
                listeningProviders.add(provider)
            }
        }
        if (currentLocationRequested) {
            currentLocationRequested = false
            requestCurrentLocation(providers)
        }
    }

    private fun Location.navigationSample() = NavigationFix.Sample(latitude, longitude,
        elapsedRealtimeNanos / 1_000_000, if (hasAccuracy()) accuracy else Float.POSITIVE_INFINITY, null, null)

    private fun cancelCurrentLocationRequest() {
        locationRequestGeneration++
        locationHandler.removeCallbacks(currentLocationTimeout)
        currentLocationSignals.forEach { it.cancel() }
        currentLocationSignals.clear()
    }

    @Suppress("MissingPermission")
    private fun requestCurrentLocation(providers: List<String>) {
        cancelCurrentLocationRequest()
        val generation = locationRequestGeneration
        locationHandler.postDelayed(currentLocationTimeout, 10_000L)
        providers.forEach { provider ->
            val signal = android.os.CancellationSignal()
            currentLocationSignals.add(signal)
            runCatching {
                androidx.core.location.LocationManagerCompat.getCurrentLocation(manager, provider, signal,
                    ContextCompat.getMainExecutor(this)) { location ->
                    if (generation != locationRequestGeneration || !wantsLocation || isFinishing || isDestroyed) return@getCurrentLocation
                    if (location != null && NavigationFix.isUsable(location.navigationSample(), android.os.SystemClock.elapsedRealtime())) {
                        onLocationChanged(location)
                        cancelCurrentLocationRequest()
                    }
                }
            }
        }
    }
    override fun onLocationChanged(location:Location){}
    override fun onProviderDisabled(provider:String){Toast.makeText(this,"Location is unavailable. The last recorded position may be out of date.",Toast.LENGTH_LONG).show()}
    override fun onProviderEnabled(provider:String){}
    @Deprecated("Legacy Android callback") override fun onStatusChanged(provider:String?,status:Int,extras:Bundle?){}
    override fun onStart(){super.onStart();tripMap?.onStart()}
    override fun onResume(){super.onResume();tripMap?.onResume();if(wantsLocation)startLocation()}
    // Permission dialogs and brief overlays pause an activity without hiding its map.
    // Keep one subscription while visible, then release GPS when the screen actually stops.
    override fun onPause(){tripMap?.onPause();super.onPause()}
    override fun onStop(){stopLocationUpdates();tripMap?.onStop();super.onStop()}
    override fun onDestroy(){stopLocationUpdates();tripMap?.onDestroy();super.onDestroy()}
    override fun onLowMemory(){super.onLowMemory();tripMap?.onLowMemory()}
    override fun onSaveInstanceState(outState:Bundle){
        outState.putString("notification_trip_id", notificationTripId)
        tripMap?.save(outState);super.onSaveInstanceState(outState)
    }
}
