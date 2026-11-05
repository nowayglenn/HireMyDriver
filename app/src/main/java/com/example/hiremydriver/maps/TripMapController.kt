package com.example.hiremydriver.maps

import android.content.pm.PackageManager
import android.animation.ValueAnimator
import android.location.Location
import android.os.Build
import android.os.SystemClock
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.RectF
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import com.example.hiremydriver.R
import com.example.hiremydriver.trip.TripPlace
import com.example.hiremydriver.trip.TripSession
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.gms.maps.model.RoundCap
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.card.MaterialCardView

/** Native Google Maps with basic road routes; no Map ID, Places, distance or ETA display. */
class TripMapController(
    private val activity: MapScreenActivity,
    container: FrameLayout,
    saved: Bundle?,
    private val centerPicker: Boolean = false,
    private val initialPlace: TripPlace? = null,
    topOverlayDp: Int = 0,
    private val bottomOverlayDp: Int = 0
) {
    data class Pin(val place: TripPlace, val title: String, val badge: String, val color: Int)
    private var mapView: MapView? = null
    private var map: GoogleMap? = null
    private var layerSwitch: View? = null
    private var layerBottomMargin = dp(48 + bottomOverlayDp)
    private var pins = emptyList<Pin>()
    private var navigationEnabled = false
    private var driverPosition = false
    private var phoneBearing: Float? = null
    private var resumed = false
    private var markerStyle = NavigationMarkerStyle()
    private val headingHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var headingUpdatePending = false
    private val headingUpdate = Runnable {
        headingUpdatePending = false
        if (resumed && !destroyed && navigationEnabled) {
            navigationMarker?.let { applyMarkerStyle(it, phoneBearing ?: navigation.heading ?: 0f) }
        }
    }
    private val compass = PhoneHeading(activity) { bearing ->
        phoneBearing = bearing
        // Sensors can flood the main looper on some phones. Keep only the latest heading,
        // and never call the Maps renderer directly from a sensor callback.
        if (resumed && !destroyed && navigationEnabled && navigationMarker != null && !headingUpdatePending) {
            headingUpdatePending = true
            headingHandler.postDelayed(headingUpdate, 120L)
        }
    }
    private var mapTypeDialog: BottomSheetDialog? = null
    private var following = saved?.getBoolean("follow_location", true) ?: true
    private var navigation = NavigationFix()
    private var navigationMarker: Marker? = null
    private var markerAnimation: ValueAnimator? = null
    private val arrowIcon by lazy { navigationIcon(true) }
    private val dotIcon by lazy { navigationIcon(false) }
    private var routeWaypoints = emptyList<RouteCoordinate>()
    private var routeGeometry = emptyList<RouteCoordinate>()
    private var routeRequest: GoogleRoadRoutes.Subscription? = null
    private var routeFailed = false
    private var userMovedMap = saved?.getBundle(MAP_STATE) != null
    private var initialFit = saved?.getBundle(MAP_STATE) == null
    private var fitPending = false
    private var pendingCenter: TripPlace? = null
    private var pickerCamera: CameraPosition? = null
    private var pickerGesture = false
    private var pickerTouchActive = false
    private var viewportPending = false
    private var appliedViewport: List<Int>? = null
    private val viewportUpdate = Runnable { applyViewport() }
    private var topOverlayHeight = dp(topOverlayDp)
    private var sheetClearance = 0
    private var destroyed = false
    private val preferences = activity.getSharedPreferences("map_display", android.content.Context.MODE_PRIVATE)
    private var satellite = saved?.getBoolean("google_map_satellite")
        ?: preferences.getBoolean("satellite", false)
    var onCenterChanged: ((LatLng) -> Unit)? = null
    var onManualMove: (() -> Unit)? = null

    init {
        container.removeAllViews()
        // A wrapper receives all child touches, including the SDK's internal map surface.
        val gestures = object : FrameLayout(activity) {
            override fun dispatchTouchEvent(event: MotionEvent): Boolean {
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    // Take ownership before the SDK emits its asynchronous gesture callback.
                    // A GPS fix or a settling sheet must not recenter a finger already on the map.
                    if (centerPicker) pickerTouchActive = true
                    following = false
                    userMovedMap = true
                }
                parent?.requestDisallowInterceptTouchEvent(true)
                val handled = super.dispatchTouchEvent(event)
                if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                    pickerTouchActive = false
                    if (centerPicker && !pickerGesture && viewportPending) scheduleViewportUpdate()
                    parent?.requestDisallowInterceptTouchEvent(false)
                }
                return handled
            }
        }
        container.addView(gestures, FrameLayout.LayoutParams(-1, -1))
        // Map controls remain available while the renderer initializes or is unavailable.
        addLayerSwitch(container)
        // Keep the map surface clear of setup/error banners, while still checking SDK availability.
        if (hasApiKey() && GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(activity) == ConnectionResult.SUCCESS) {
            // No Map ID: standard Map and Satellite layers use the basic native Maps SDK.
            val view = MapView(activity, GoogleMapOptions().mapType(mapType()))
            mapView = view
            gestures.addView(view, FrameLayout.LayoutParams(-1, -1))
            view.onCreate(saved?.getBundle(MAP_STATE))
            view.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) updatePadding()
                tryFit()
            }
            view.getMapAsync { ready ->
                if (!destroyed) {
                    map = ready
                    ready.mapType = mapType()
                    ready.uiSettings.isZoomControlsEnabled = false
                    ready.uiSettings.isMapToolbarEnabled = false
                    ready.uiSettings.isMyLocationButtonEnabled = false
                    ready.uiSettings.isScrollGesturesEnabled = true
                    ready.uiSettings.isZoomGesturesEnabled = true
                    // Two-finger rotation and tilt are available on pickers and trip maps.
                    ready.uiSettings.isRotateGesturesEnabled = true
                    ready.uiSettings.isTiltGesturesEnabled = true
                    ready.setMaxZoomPreference(20f)
                    applyViewport()
                    if (saved?.getBundle(MAP_STATE) == null) {
                        ready.moveCamera(CameraUpdateFactory.newLatLngZoom(
                            initialPlace?.latLng() ?: LatLng(9.6879, 123.9740),
                            if (initialPlace != null) 16f else 13f
                        ))
                    }
                    if (centerPicker) {
                        pickerCamera = ready.cameraPosition
                        ready.setOnCameraMoveStartedListener { reason ->
                            if (reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE) {
                                pickerGesture = true
                                following = false
                                onManualMove?.invoke()
                            }
                        }
                        ready.setOnCameraMoveListener { capturePickerGesture() }
                        ready.setOnCameraIdleListener {
                            capturePickerGesture()
                            pickerGesture = false
                            if (viewportPending) scheduleViewportUpdate()
                        }
                        selectedCenter()?.let { onCenterChanged?.invoke(it) }
                    } else {
                        ready.setOnCameraMoveStartedListener { reason ->
                            if (reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE) {
                                userMovedMap = true
                                following = false
                            }
                        }
                    }
                    render()
                    if (initialFit && pins.isNotEmpty()) fit()
                    pendingCenter?.let { center(it) }
                    moveFollowCamera()
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun hasApiKey(): Boolean {
        val key = activity.packageManager.getApplicationInfo(activity.packageName, PackageManager.GET_META_DATA)
            .metaData?.getString("com.google.android.geo.API_KEY").orEmpty().trim()
        return key.isNotEmpty() && key != "YOUR_API_KEY"
    }

    private fun addLayerSwitch(container: FrameLayout) {
        val button = activity.layoutInflater.inflate(R.layout.view_map_layers, container, false)
        layerSwitch = button
        button.setOnClickListener { showMapTypes() }
        container.addView(button, FrameLayout.LayoutParams(dp(48), dp(48), Gravity.BOTTOM or Gravity.START).apply {
            marginStart = dp(16)
            bottomMargin = layerBottomMargin
        })
    }

    private fun showMapTypes() {
        if (mapTypeDialog?.isShowing == true) return
        val dialog = BottomSheetDialog(activity)
        val content = activity.layoutInflater.inflate(R.layout.dialog_map_type, null)
        dialog.setContentView(content)
        mapTypeDialog = dialog
        content.findViewById<View>(R.id.closeMapTypes).setOnClickListener { dialog.dismiss() }
        listOf(R.id.mapLayerNormal to false, R.id.mapLayerSatellite to true).forEach { (id, value) ->
            content.findViewById<MaterialCardView>(id).apply {
                isChecked = satellite == value
                strokeColor = if (isChecked) Color.rgb(20, 99, 217) else Color.rgb(223, 230, 239)
                setOnClickListener {
                    satellite = value
                    map?.mapType = mapType()
                    preferences.edit().putBoolean("satellite", satellite).apply()
                    dialog.dismiss()
                }
            }
        }
        dialog.setOnDismissListener { mapTypeDialog = null }
        dialog.show()
        dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            ?.setBackgroundColor(Color.TRANSPARENT)
    }

    /** Full-screen route-details controls occupy the lower-left corner too. */
    fun setLayerBottomClearance(height: Int) {
        if (sheetClearance != height) { sheetClearance = height; updatePadding() }
        layerBottomMargin = maxOf(dp(48 + bottomOverlayDp), height + dp(12))
        layerSwitch?.let { view ->
            val parent = view.parent as? View ?: return@let
            if (parent.height > 0 && view.height > 0)
                view.translationY = (parent.height - layerBottomMargin - view.bottom).toFloat()
        }
    }

    private fun mapType() = if (satellite) GoogleMap.MAP_TYPE_HYBRID else GoogleMap.MAP_TYPE_NORMAL
    fun setLayerControlVisible(visible: Boolean) {
        layerSwitch?.visibility = if (visible) View.VISIBLE else View.GONE
        if (!visible) mapTypeDialog?.dismiss()
    }
    private fun dp(n: Int) = (n * activity.resources.displayMetrics.density).toInt()
    private fun TripPlace.latLng() = LatLng(latitude, longitude)

    fun show(points: List<Pin>) {
        val visible = if (driverPosition && navigationEnabled && navigation.position != null) points.filter { it.badge != "D" } else points
        if (visible != pins) { pins = visible; render() }
        if (initialFit && visible.isNotEmpty() && !(navigationEnabled && following && navigation.position != null)) fit()
    }

    fun setNavigationEnabled(enabled: Boolean, isDriver: Boolean = true) {
        if (navigationEnabled == enabled && driverPosition == isDriver) return
        driverPosition = isDriver
        navigationEnabled = enabled
        if (!enabled) {
            compass.stop()
            navigation = NavigationFix()
            markerAnimation?.cancel()
        } else if (resumed) compass.start()
        render()
    }

    /** The existing recenter button resumes following after the user explores the map. */
    fun followLocation() {
        following = true
        userMovedMap = false
        if (routeFailed && routeWaypoints.size >= 2) loadRoute()
        moveFollowCamera()
    }

    fun pauseLocationFollow() { following = false }

    fun updateNavigationLocation(location: Location): Boolean {
        if (!navigationEnabled) return false
        val reliableBearing = location.hasBearing() && (Build.VERSION.SDK_INT < 26 ||
            !location.hasBearingAccuracy() || location.bearingAccuracyDegrees <= 45f)
        val accepted = navigation.accept(NavigationFix.Sample(location.latitude, location.longitude,
            location.elapsedRealtimeNanos / 1_000_000,
            if (location.hasAccuracy()) location.accuracy else Float.POSITIVE_INFINITY,
            if (reliableBearing) location.bearing else null,
            if (location.hasSpeed()) location.speed else null), SystemClock.elapsedRealtime())
        if (!accepted) return false
        compass.locate(location)
        updateNavigationMarker()
        moveFollowCamera()
        return true
    }

    private fun moveFollowCamera() {
        val ready = map ?: return
        val fix = navigation.position ?: return
        if (!navigationEnabled || !following) return
        // A recenter tap after a pause must not jump to an old location while GPS warms up.
        if (!NavigationFix.isUsable(fix, SystemClock.elapsedRealtime())) return
        initialFit = false
        fitPending = false
        if (centerPicker) {
            // Pick once. Later GPS fixes must not move a location the customer is naming.
            following = false
            setPickerCamera(CameraPosition.Builder(ready.cameraPosition)
                .target(LatLng(fix.latitude, fix.longitude)).zoom(17f).build())
            return
        }
        ready.animateCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder()
            .target(LatLng(fix.latitude, fix.longitude)).zoom(17f)
            .bearing(if (centerPicker) ready.cameraPosition.bearing else navigation.heading ?: ready.cameraPosition.bearing)
            .tilt(ready.cameraPosition.tilt).build()), 700, null)
    }

    private fun navigationIcon(arrow: Boolean): com.google.android.gms.maps.model.BitmapDescriptor {
        val size = dp(96)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val middle = size / 2f
        if (arrow) {
            paint.shader = RadialGradient(middle, middle, middle,
                intArrayOf(Color.argb(155, 36, 125, 255), Color.argb(0, 36, 125, 255)),
                floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
            canvas.drawArc(RectF(0f, 0f, size.toFloat(), size.toFloat()), 235f, 70f, true, paint)
            paint.shader = null
        }
        paint.color = Color.argb(28, 36, 125, 255)
        canvas.drawCircle(middle, middle, dp(16).toFloat(), paint)
        paint.color = Color.WHITE
        canvas.drawCircle(middle, middle, dp(9).toFloat(), paint)
        paint.color = Color.rgb(36, 125, 255)
        canvas.drawCircle(middle, middle, dp(6).toFloat(), paint)
        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    private fun updateNavigationMarker() {
        val ready = map ?: return
        val fix = navigation.position ?: return
        if (!navigationEnabled) return
        val target = LatLng(fix.latitude, fix.longitude)
        val heading = phoneBearing ?: navigation.heading ?: 0f
        val icon = if (phoneBearing == null && navigation.heading == null) dotIcon else arrowIcon
        markerAnimation?.cancel()
        val marker = navigationMarker
        if (marker == null) {
            markerStyle = NavigationMarkerStyle().also {
                it.update(heading, phoneBearing != null || navigation.heading != null)
            }
            navigationMarker = ready.addMarker(MarkerOptions().position(target).title("Your location")
                .icon(icon).anchor(0.5f, 0.5f).flat(true).rotation(heading).zIndex(10f))
        } else {
            val start = marker.position
            val startHeading = marker.rotation
            markerAnimation = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 700
                addUpdateListener {
                    val fraction = it.animatedValue as Float
                    val longitudeDelta = ((target.longitude - start.longitude + 540) % 360) - 180
                    marker.position = LatLng(start.latitude + (target.latitude - start.latitude) * fraction,
                        start.longitude + longitudeDelta * fraction)
                    applyMarkerStyle(marker, phoneBearing ?: NavigationFix.interpolateHeading(startHeading, heading, fraction))
                }
                start()
            }
        }
    }

    private fun applyMarkerStyle(marker: Marker, heading: Float) {
        val useArrow = phoneBearing != null || navigation.heading != null
        val changes = markerStyle.update(heading, useArrow)
        if (changes.iconChanged) marker.setIcon(if (useArrow) arrowIcon else dotIcon)
        changes.rotation?.let { marker.rotation = it }
    }

    fun showTripRoute(trip: TripSession, pickupApproach: Boolean = false) {
        val points = if (pickupApproach) RoadRoute.toPickup(trip) else RoadRoute.itinerary(trip)
        // GPS fixes move every second; reuse the approach route until the origin has moved 50 m.
        if (pickupApproach && points.size == 2 && routeWaypoints.size == 2 &&
            points.last() == routeWaypoints.last() && !routeFailed) {
            val distance = FloatArray(1)
            android.location.Location.distanceBetween(points.first().latitude, points.first().longitude,
                routeWaypoints.first().latitude, routeWaypoints.first().longitude, distance)
            if (distance[0] < 50f) return
        }
        if (centerPicker || points == routeWaypoints) return
        routeRequest?.cancel()
        routeWaypoints = points
        routeGeometry = emptyList()
        routeFailed = false
        render()
        if (points.size >= 2) loadRoute()
    }

    private fun loadRoute() {
        val requested = routeWaypoints
        routeFailed = false
        routeRequest = GoogleRoadRoutes.request(activity, requested) { result ->
            if (!destroyed && requested == routeWaypoints) {
                result.onSuccess { geometry ->
                    routeGeometry = geometry
                    render()
                    if (navigationEnabled && following && navigation.position != null) moveFollowCamera()
                    else if (!userMovedMap) fit()
                }.onFailure {
                    routeFailed = true
                    Toast.makeText(activity, "Could not load the driving route. Tap recenter to retry.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun render() {
        val ready = map ?: return
        markerAnimation?.cancel()
        navigationMarker = null
        ready.clear()
        if (routeGeometry.size >= 2) {
            val points = routeGeometry.map { LatLng(it.latitude, it.longitude) }
            // A white casing keeps the road line legible in both Map and Satellite modes.
            ready.addPolyline(PolylineOptions().addAll(points).color(Color.WHITE).width(dp(8).toFloat())
                .startCap(RoundCap()).endCap(RoundCap()).zIndex(0f))
            ready.addPolyline(PolylineOptions().addAll(points).color(Color.rgb(20, 99, 217)).width(dp(5).toFloat())
                .startCap(RoundCap()).endCap(RoundCap()).zIndex(1f))
        }
        pins.forEach { pin ->
            val size = dp(36)
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
            canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
            paint.color = pin.color
            canvas.drawCircle(size / 2f, size / 2f, size / 2f - dp(3), paint)
            paint.color = Color.WHITE
            paint.textSize = dp(15).toFloat()
            paint.textAlign = Paint.Align.CENTER
            paint.isFakeBoldText = true
            canvas.drawText(pin.badge, size / 2f, size / 2f - (paint.ascent() + paint.descent()) / 2, paint)
            ready.addMarker(MarkerOptions().position(pin.place.latLng()).title(pin.title)
                .snippet(pin.place.label).anchor(0.5f, 0.5f)
                .icon(BitmapDescriptorFactory.fromBitmap(bitmap)))
        }
        updateNavigationMarker()
    }

    /** Keep fitted pins below floating cards and Google's attribution above the overlapping sheet. */
    fun setTopOverlayHeight(height: Int) {
        if (height == topOverlayHeight) return
        topOverlayHeight = height
        updatePadding()
    }

    private fun updatePadding() {
        viewportPending = true
        scheduleViewportUpdate()
    }

    private fun scheduleViewportUpdate() {
        val view = mapView ?: return
        view.removeCallbacks(viewportUpdate)
        view.postOnAnimation(viewportUpdate)
    }

    private fun applyViewport() {
        val ready = map ?: return
        val view = mapView ?: return
        if (destroyed) return
        // Keep the SDK viewport unchanged throughout a drag, pinch and fling.
        // Coalesce header, sheet and keyboard changes into one update after it settles.
        if (centerPicker && (pickerTouchActive || pickerGesture)) {
            viewportPending = true
            return
        }
        val bottom = maxOf(dp(bottomOverlayDp), sheetClearance)
        val viewport = listOf(view.width, view.height, topOverlayHeight, bottom)
        viewportPending = false
        if (viewport == appliedViewport) return
        appliedViewport = viewport
        ready.setPadding(0, topOverlayHeight, 0, bottom)
        restorePickerCamera()
    }

    private fun capturePickerGesture() {
        if (!pickerGesture) return
        pickerCamera = map?.cameraPosition
        pickerCamera?.target?.let { onCenterChanged?.invoke(it) }
    }

    private fun restorePickerCamera() {
        // Layout updates must never interrupt an intentional drag or its settling motion.
        // Once the gesture ends, only viewport changes restore the saved selection.
        if (!centerPicker || pickerTouchActive || pickerGesture) return
        pickerCamera?.let { map?.moveCamera(CameraUpdateFactory.newCameraPosition(it)) }
    }

    private fun setPickerCamera(position: CameraPosition) {
        pickerGesture = false
        pickerCamera = position
        restorePickerCamera()
        onCenterChanged?.invoke(position.target)
    }

    fun freezePickerSelection() {
        if (!centerPicker) return
        capturePickerGesture()
        val wasMoving = pickerGesture
        pickerGesture = false
        following = false
        if (wasMoving) map?.stopAnimation()
        // Typing only names the selected point; it must not send another camera move.
        if (viewportPending) scheduleViewportUpdate()
    }

    fun fit() {
        if (routeFailed && routeWaypoints.size >= 2) loadRoute()
        fitPending = true
        tryFit()
    }
    private fun tryFit() {
        if (navigationEnabled && following && navigation.position != null) { fitPending = false; return }
        val ready = map ?: return
        val view = mapView ?: return
        if (!fitPending || view.width <= 0 || view.height <= topOverlayHeight + dp(bottomOverlayDp + 90)) return
        val points = (pins.map { it.place.latLng() } + routeGeometry.map { LatLng(it.latitude, it.longitude) }).distinct()
        if (points.isEmpty()) return
        if (points.size == 1) {
            ready.animateCamera(CameraUpdateFactory.newLatLngZoom(points.first(), 16f))
        } else {
            val bounds = LatLngBounds.Builder().apply { points.forEach { include(it) } }.build()
            ready.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, dp(45)))
        }
        fitPending = false
        initialFit = false
    }

    fun center(place: TripPlace) {
        val ready = map
        if (ready == null) { pendingCenter = place; return }
        pendingCenter = null
        if (centerPicker) {
            following = false
            setPickerCamera(CameraPosition.Builder(ready.cameraPosition).target(place.latLng()).zoom(16f).build())
            return
        }
        ready.animateCamera(CameraUpdateFactory.newLatLngZoom(place.latLng(), 16f))
    }

    /** Include the final drag position, but never treat viewport resizing as a new selection. */
    fun selectedCenter(): LatLng? {
        capturePickerGesture()
        return if (centerPicker) pickerCamera?.target else map?.cameraPosition?.target
    }
    fun onStart() { mapView?.onStart() }
    fun onResume() { resumed = true; mapView?.onResume(); if (navigationEnabled) compass.start() }
    fun onPause() {
        resumed = false
        headingHandler.removeCallbacks(headingUpdate); headingUpdatePending = false
        compass.stop(); markerAnimation?.cancel(); mapView?.onPause()
    }
    fun onStop() { mapView?.onStop() }
    fun onDestroy() {
        destroyed = true
        mapView?.removeCallbacks(viewportUpdate)
        headingHandler.removeCallbacks(headingUpdate); headingUpdatePending = false
        compass.stop()
        mapTypeDialog?.dismiss()
        markerAnimation?.cancel()
        routeRequest?.cancel()
        mapView?.onDestroy()
        map = null
    }
    fun onLowMemory() { mapView?.onLowMemory() }
    fun save(out: Bundle) {
        mapView?.let { view ->
            val state = Bundle()
            view.onSaveInstanceState(state)
            out.putBundle(MAP_STATE, state)
        }
        out.putBoolean("google_map_satellite", satellite)
        out.putBoolean("follow_location", following)
    }
    companion object {
        private const val MAP_STATE = "google_map_state"
        val NAVY = Color.rgb(6, 27, 65)
        val ORANGE = Color.rgb(255, 121, 0)
    }
}
