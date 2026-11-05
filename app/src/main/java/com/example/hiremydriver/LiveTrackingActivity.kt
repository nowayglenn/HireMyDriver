package com.example.hiremydriver

import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView
import android.widget.Toast
import com.example.hiremydriver.maps.MapScreenActivity
import com.example.hiremydriver.maps.TripMapController
import com.example.hiremydriver.trip.*

/** Full-screen map shared by both roles. Only driver mode can record this phone's GPS. */
class LiveTrackingActivity : MapScreenActivity() {
    private lateinit var store: LocalTripStore
    private lateinit var trip: TripSession
    private val driverMode by lazy { intent.getBooleanExtra("DRIVER_MAP", false) }
    private fun isHistory() = ::trip.isInitialized && trip.phase == TripPhase.COMPLETED
    private fun isCustomerHistory() = isHistory() && !driverMode
    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() { refresh(); handler.postDelayed(this, 15_000) }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_live_tracking)
        store = LocalTripStore(this)
        if (intent.hasExtra(LocalTripStore.ID) && store.get(intent.getStringExtra(LocalTripStore.ID).orEmpty()) == null) {
            Toast.makeText(this, "Booking no longer available", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        trip = store.fromIntent(intent)
        tripMap = TripMapController(this, findViewById(R.id.mapPlaceholder), savedInstanceState, topOverlayDp = 90)
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<View>(R.id.mapFloatingHeader).addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ -> tripMap?.setTopOverlayHeight(view.height + (32 * resources.displayMetrics.density).toInt()) }
        findViewById<View>(R.id.fabRecenter).setOnClickListener {
            if (isHistory()) tripMap?.fit()
            else if (canShare()) resumeDriverFollow() else if (!driverMode) showOwnLocation(follow = true) else tripMap?.fit()
        }
        val panel = findViewById<View>(R.id.mapDetailsPanel)
        val details = findViewById<TextView>(R.id.btnMapDetails)
        fun showDetails(show: Boolean) {
            panel.visibility = if (show && !isCustomerHistory()) View.VISIBLE else View.GONE
            details.text = if (show) "Hide details" else "Route details"
        }
        showDetails(savedInstanceState?.getBoolean("details_visible") ?: isHistory())
        details.setOnClickListener { showDetails(panel.visibility != View.VISIBLE) }
        findViewById<View>(R.id.fullScreenMapRoot).viewTreeObserver.addOnPreDrawListener {
            layoutMapControls()
            true
        }
        findViewById<View>(R.id.btnMapMyLocation).setOnClickListener { resumeDriverFollow() }
        refresh()
        store.observe(this, ::refresh)
        if (!driverMode && !isHistory()) showOwnLocation()
    }
    private fun canShare() = driverMode && trip.phase in listOf(TripPhase.ON_THE_WAY, TripPhase.ARRIVED, TripPhase.IN_PROGRESS)
    private fun layoutMapControls() {
        val root = findViewById<View>(R.id.fullScreenMapRoot)
        if (root.height <= 0) return
        fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
        val panel = findViewById<View>(R.id.mapDetailsPanel)
        val header = findViewById<View>(R.id.mapFloatingHeader)
        val safe = androidx.core.view.ViewCompat.getRootWindowInsets(root)
            ?.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        val panelHeight = minOf((root.height * 0.52f).toInt(), root.height - header.bottom - dp(124)).coerceAtLeast(0)
        if (panel.layoutParams.height != panelHeight)
            panel.layoutParams = panel.layoutParams.apply { height = panelHeight }
        panel.setPadding(safe?.left ?: 0, 0, safe?.right ?: 0, safe?.bottom ?: 0)
        val open = panel.visibility == View.VISIBLE
        val details = findViewById<View>(R.id.btnMapDetails)
        listOf(details, findViewById<View>(R.id.fabRecenter)).forEach { control ->
            control.translationY = if (open) (root.height - panelHeight - dp(16) - control.bottom).toFloat() else 0f
        }
        // Keep the layer selector above Route details, never behind the rounded panel.
        val clearance = if (details.visibility == View.VISIBLE)
            (root.height - details.y).toInt() else (safe?.bottom ?: 0) + dp(28)
        tripMap?.setLayerBottomClearance(clearance)
    }
    private fun refresh() {
        if (!::trip.isInitialized) return
        trip = store.get(trip.id) ?: trip
        setDriverFollowEnabled(canShare())
        if (isHistory()) {
            stopOwnLocation()
            tripMap?.setNavigationEnabled(false)
        }
        val routeOnly = isCustomerHistory()
        findViewById<View>(R.id.fabRecenter).visibility = View.VISIBLE
        findViewById<View>(R.id.btnMapDetails).visibility = if (routeOnly) View.GONE else View.VISIBLE
        tripMap?.setLayerControlVisible(true)
        if (routeOnly) {
            findViewById<View>(R.id.mapDetailsPanel).visibility = View.GONE
        }
        findViewById<TextView>(R.id.txtMapTitle).text = if (isHistory()) "Map history" else "Trip map"
        findViewById<View>(R.id.fabRecenter).contentDescription = if (isHistory()) "Show saved route" else if (canShare() || !driverMode) "Follow my location" else "Show all pins"
        // History contains the saved itinerary, not the phone's current position or a GPS replay.
        tripMap?.show(TripUi.pins(if (isHistory()) trip.copy(driverLocation = null) else trip))
        tripMap?.showTripRoute(trip)
        val timeline = findViewById<android.widget.LinearLayout>(R.id.mapRouteTimeline)
        val route = trip.bookingRoute()
        if (timeline.tag != route) {
            BookingRouteView.render(timeline, route)
            for (index in 0 until timeline.childCount) {
                timeline.getChildAt(index).findViewById<TextView>(R.id.routePointName).textSize = 18f
                timeline.getChildAt(index).findViewById<TextView>(R.id.routePointCaption).textSize = 14f
            }
            timeline.tag = route
        }
        findViewById<TextView>(R.id.txtMapLocationDetail).text = if (isHistory()) buildString {
            append("Service completed")
            trip.completedAt?.let {
                append(" · ").append(java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM,
                    java.text.DateFormat.SHORT).format(java.util.Date(it)))
            }
            append(if (trip.pickup == null || trip.destinations.isEmpty())
                "\nMap pins were not saved for this booking."
            else "\nSaved pickup and destinations · Road route shown for reference, not a recorded GPS trail.")
        } else TripUi.locationStatus(trip)
        findViewById<View>(R.id.btnMapMyLocation).visibility = if (canShare()) View.VISIBLE else View.GONE
    }
    override fun onLocationChanged(location: Location) {
        if (isHistory()) return
        if (!driverMode) { tripMap?.updateNavigationLocation(location); return }
        if (!::trip.isInitialized || !canShare()) return
        if (tripMap?.updateNavigationLocation(location) != true) return
        store.update(trip.id) { current ->
            if (current.phase in listOf(TripPhase.ON_THE_WAY, TripPhase.ARRIVED, TripPhase.IN_PROGRESS))
                current.copy(driverLocation = TripPlace("Driver position", location.latitude, location.longitude), locationAt = location.time)
            else current
        }
        refresh()
    }
    override fun onResume() { super.onResume(); handler.post(tick)
        if (::trip.isInitialized && !isHistory()) TripNotifications.show(this, store.get(trip.id) ?: trip, driver = driverMode)
    }
    override fun onPause() { handler.removeCallbacks(tick); super.onPause() }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("details_visible", findViewById<View>(R.id.mapDetailsPanel).visibility == View.VISIBLE)
    }
}
