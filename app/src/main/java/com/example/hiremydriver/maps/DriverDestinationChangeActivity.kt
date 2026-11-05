package com.example.hiremydriver.maps

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView
import android.widget.Toast
import com.example.hiremydriver.R
import com.example.hiremydriver.trip.*
import java.util.Locale

/** Reviews a customer-selected stop; resolving it uses the existing trip state rules. */
class DriverDestinationChangeActivity : MapScreenActivity() {
    private lateinit var store: LocalTripStore
    private lateinit var tripId: String
    private lateinit var requested: TripPlace
    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() { refresh(); handler.postDelayed(this, 15_000) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_driver_destination_change)
        store = LocalTripStore(this)
        tripId = intent.getStringExtra(LocalTripStore.ID).orEmpty()
        val trip = store.get(tripId)
        val pending = LocalTripStore.readPlace(savedInstanceState?.getString("requested_stop")) ?: trip?.pendingDestination
        if (trip == null || pending == null) { finish(); return }
        requested = pending
        tripMap = TripMapController(this, findViewById(R.id.changeMapCanvas), savedInstanceState, topOverlayDp = 90, bottomOverlayDp = 20)
        tripMap?.show(listOf(TripMapController.Pin(requested, "Requested destination", "+", TripMapController.ORANGE)))
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<View>(R.id.btnChangeRecenter).setOnClickListener { tripMap?.fit() }
        findViewById<View>(R.id.mapOverlayContent).addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
            // Leave map space below the floating cards when system text is enlarged.
            val density = resources.displayMetrics.density
            val mapArea = findViewById<View>(R.id.mapArea)
            val height = maxOf((360 * density).toInt(), view.height + (200 * density).toInt())
            if (mapArea.layoutParams.height != height) {
                mapArea.layoutParams = mapArea.layoutParams.apply { this.height = height }
            }
            tripMap?.setTopOverlayHeight(view.height)
        }
        findViewById<View>(R.id.btnAcceptDestination).setOnClickListener { resolve(true) }
        findViewById<View>(R.id.btnDeclineDestination).setOnClickListener { resolve(false) }
        refresh()
        store.observe(this, ::refresh)
    }

    private fun refresh() {
        if (!::requested.isInitialized) return
        val trip = store.get(tripId)
        val actionable = trip?.phase == TripPhase.IN_PROGRESS && trip.pendingDestination == requested
        findViewById<View>(R.id.btnAcceptDestination).isEnabled = actionable
        findViewById<View>(R.id.btnDeclineDestination).isEnabled = actionable
        findViewById<TextView>(R.id.txtChangeStatus).text = if (actionable) "Customer requested a new destination" else "This request is no longer pending"
        findViewById<TextView>(R.id.txtRequestedPlace).text = requested.label
        findViewById<TextView>(R.id.txtAfterStop).text = trip?.let {
            if (it.reachedStops < it.destinations.size) "After ${it.destinations.last().label}" else "Next destination · All previous stops visited"
        } ?: "Booking no longer available"
        val minutes = (trip?.remainingMillis(System.currentTimeMillis()) ?: 0) / 60_000
        findViewById<TextView>(R.id.txtChangeRemaining).text = String.format(Locale.getDefault(), "%dh %02dm", minutes / 60, minutes % 60)
        findViewById<TextView>(R.id.txtChangeDuration).text = trip?.let { "${it.durationHours} hours" } ?: "—"
    }

    private fun resolve(accept: Boolean) {
        findViewById<View>(R.id.btnAcceptDestination).isEnabled = false
        findViewById<View>(R.id.btnDeclineDestination).isEnabled = false
        store.updateConfirmed(tripId, { current ->
            // Do not accidentally approve a newer request after this screen was opened.
            if (current.phase == TripPhase.IN_PROGRESS && current.pendingDestination == requested) {
                current.resolveStop(accept)
            } else current
        }) { resolved ->
            if (!isDestroyed && !isFinishing) {
                if (resolved) {
                    Toast.makeText(this, if (accept) "Destination accepted" else "Destination declined", Toast.LENGTH_SHORT).show()
                    finish()
                } else refresh()
            }
        }
    }

    override fun onResume() { super.onResume(); if (::requested.isInitialized) handler.post(tick) }
    override fun onPause() { handler.removeCallbacks(tick); super.onPause() }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::requested.isInitialized) outState.putString("requested_stop", LocalTripStore.placeJson(requested).toString())
    }
}
