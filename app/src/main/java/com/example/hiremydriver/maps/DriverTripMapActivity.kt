package com.example.hiremydriver.maps

import android.content.Intent
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView
import com.example.hiremydriver.ChatConversationActivity
import com.example.hiremydriver.R
import com.example.hiremydriver.trip.*
import com.google.android.material.button.MaterialButton

class DriverTripMapActivity : MapScreenActivity() {
    private lateinit var detailSheet: MapDetailSheet
    private lateinit var store:LocalTripStore
    private lateinit var trip:TripSession
    private var busy = false
    private var previewLocation: TripPlace? = null
    private var lastLocationSave = 0L
    private val handler=Handler(Looper.getMainLooper())
    private val tick=object:Runnable{override fun run(){refresh();handler.postDelayed(this,15_000)}}
    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);setContentView(R.layout.activity_driver_trip_map)
        store=LocalTripStore(this);trip=store.fromIntent(intent)
        tripMap=TripMapController(this,findViewById(R.id.mapCanvas),savedInstanceState,topOverlayDp=160,bottomOverlayDp=20)
        findViewById<View>(R.id.btnBack).setOnClickListener{finish()}
        detailSheet = MapDetailSheet(this, savedInstanceState)
        findViewById<View>(R.id.mapOverlayContent).addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
            tripMap?.setTopOverlayHeight(view.height)
        }
        findViewById<View>(R.id.btnMyLocation).apply {
            contentDescription = "Follow my location"
            setOnClickListener { resumeDriverFollow() }
        }
        findViewById<View>(R.id.btnDriverExpandMap).setOnClickListener {
            startActivity(Intent(this, com.example.hiremydriver.LiveTrackingActivity::class.java)
                .putExtra(LocalTripStore.ID, trip.id).putExtra("DRIVER_MAP", true))
        }
        findViewById<View>(R.id.btnTripMessage).setOnClickListener{startActivity(com.example.hiremydriver.messaging.BookingChat.intent(this, trip.id, true))}
        findViewById<View>(R.id.btnDeclineExtension).setOnClickListener { reviewExtension(false) }
        findViewById<View>(R.id.btnAcceptExtension).setOnClickListener { reviewExtension(true) }
        findViewById<View>(R.id.btnTripAction).setOnClickListener {
            refresh()
            if (trip.phase == TripPhase.IN_PROGRESS || trip.phase == TripPhase.COMPLETED) showTripDetails() else action()
        }
        findViewById<View>(R.id.btnReturnToBookings).setOnClickListener {
            startActivity(Intent(this, com.example.hiremydriver.DriverBookingActivity::class.java)
                .putExtra("OPEN_TAB", "COMPLETED").addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
            finish()
        }
        findViewById<View>(R.id.btnReviewStop).setOnClickListener{
            refresh()
            if (trip.pendingDestination != null) startActivity(Intent(this, DriverDestinationChangeActivity::class.java)
                .putExtra(LocalTripStore.ID, trip.id))
        }
        findViewById<View>(R.id.btnReachedStop).setOnClickListener{
            if (busy) return@setOnClickListener
            refresh()
            val target=trip.nextDestination()
            if (target == null) {
                if (trip.phase == TripPhase.IN_PROGRESS && trip.hasReachedLastDestination()) completeLastDestination()
                return@setOnClickListener
            }
            val expectedStop = trip.reachedStops
            val last = expectedStop == trip.destinations.lastIndex
            BookingDialogs.confirm(this, "Reached ${target.label}?",
                if (last) "This is the last destination. Confirming will complete the booking."
                else "Mark this destination as reached and continue to the next stop.", "Confirm", "Not yet", icon = R.drawable.ic_location) {
                change(afterSaved = { if (it.hasReachedLastDestination()) completeLastDestination() }) { current ->
                    if (current.reachedStops == expectedStop && current.nextDestination() == target) current.reachStop() else current
                }
            }
        };refresh()
        store.observe(this, ::refresh)
    }
    private fun showTripDetails() {
        BookingNavigation.details(this, trip.id, true, readOnly = true)
    }
    private fun completeLastDestination() {
        change(afterSaved = { completed ->
            if (completed.phase == TripPhase.COMPLETED) {
                BookingDialogs.confirm(this, "Booking completed",
                    "You have reached the last destination. This booking is now complete.", "Continue",
                    negative = null, smoothCompletion = true) { refresh() }
            }
        }) { it.completeAtLastDestination(System.currentTimeMillis()) }
    }
    private fun reviewExtension(approve: Boolean) {
        if (busy) return
        refresh()
        if (trip.phase != TripPhase.IN_PROGRESS || trip.extensionStatus != "PENDING") return
        val requested = trip
        val message = if (approve)
            "Add ${BookingExtras.duration(requested.requestedMinutes)} for ${BookingExtras.money(requested.rate() * requested.requestedMinutes / 60)}.\n" +
                "New end: ${BookingExtras.time(requested.scheduledEnd()?.plus(requested.requestedMinutes * 60000L))}\n\n" +
                "By accepting, you agree to continue until this time."
        else "Keep the current end time and amount. The customer will see that the request was declined."
        BookingDialogs.confirm(this, if (approve) "Accept extension?" else "Decline extension?",
            message, if (approve) "Accept extension" else "Decline request", "Go back") {
            if (com.example.hiremydriver.NetworkStatus.requireConnection(this) { refresh() })
                change { current ->
                    if (current.extensionSequence == requested.extensionSequence)
                        current.resolveExtension(approve) else current
                }
        }
    }
    private fun renderExtension() {
        val show = trip.extensionStatus.isNotBlank()
        findViewById<View>(R.id.driverExtensionCard).visibility = if (show) View.VISIBLE else View.GONE
        if (!show) return
        val pending = trip.phase == TripPhase.IN_PROGRESS && trip.extensionStatus == "PENDING"
        findViewById<TextView>(R.id.txtDriverExtensionTitle).text = when {
            pending -> "Extension request"
            trip.extensionStatus == "APPROVED" -> "Extension accepted"
            trip.extensionStatus == "DECLINED" -> "Extension declined"
            else -> "Previous extension request"
        }
        findViewById<TextView>(R.id.txtDriverExtensionAmount).text =
            "+${BookingExtras.duration(trip.requestedMinutes)}  ·  ${BookingExtras.money(trip.rate() * trip.requestedMinutes / 60)}"
        findViewById<TextView>(R.id.txtDriverExtensionEnd).text = if (pending)
            "New end: ${BookingExtras.time(trip.scheduledEnd()?.plus(trip.requestedMinutes * 60000L))}"
        else "Scheduled end: ${BookingExtras.time(trip.scheduledEnd())}"
        findViewById<View>(R.id.driverExtensionActions).visibility = if (pending) View.VISIBLE else View.GONE
        findViewById<View>(R.id.btnAcceptExtension).isEnabled = !busy
        findViewById<View>(R.id.btnDeclineExtension).isEnabled = !busy
    }
    private fun change(afterSaved: (TripSession) -> Unit = {}, update:(TripSession)->TripSession) {
        if (busy) return
        busy = true
        val previousPhase = trip.phase
        refresh()
        store.updateConfirmed(trip.id, update) { saved ->
            busy = false
            if (!isFinishing && !isDestroyed) {
                refresh()
                if (saved && previousPhase != TripPhase.IN_PROGRESS && trip.phase == TripPhase.IN_PROGRESS) offerTripNotification(trip)
                if (saved) afterSaved(trip)
            }
        }
    }
    private fun action() {
        if (busy) return
        refresh()
        DriverBookingAction.confirm(this, trip) { update -> change(update = update) }
    }
    private fun refresh(){if(!::trip.isInitialized)return;trip=store.get(trip.id)?:trip
        setDriverFollowEnabled(trip.phase in listOf(TripPhase.ACCEPTED, TripPhase.ON_THE_WAY, TripPhase.ARRIVED, TripPhase.IN_PROGRESS))
        val onTrip = trip.phase == TripPhase.IN_PROGRESS
        findViewById<TextView>(R.id.txtMapTitle).text = when (trip.phase) {
            TripPhase.REQUESTED -> "Pending"
            TripPhase.ACCEPTED -> "Accepted · Map preview"
            TripPhase.ON_THE_WAY -> "Driver on the way"
            TripPhase.ARRIVED -> "Driver has arrived"
            TripPhase.IN_PROGRESS -> "In Progress"
            TripPhase.COMPLETED -> "Service completed"
            TripPhase.CANCELLED -> "Booking cancelled"; TripPhase.DECLINED -> "Booking declined"
        }
        findViewById<TextView>(R.id.txtMapCustomer).text = trip.customerName
        com.example.hiremydriver.PeerProfilePhoto.bind(findViewById(R.id.imgMapCustomer), trip.customerUid, false, true)
        findViewById<TextView>(R.id.txtMapTargetCaption).text = if (onTrip) "NEXT DESTINATION" else "PICKUP LOCATION"
        findViewById<TextView>(R.id.txtMapTarget).text = if (onTrip) trip.nextDestination()?.label
            ?: "All destinations visited" else trip.pickup?.label ?: trip.pickupLabel
        findViewById<TextView>(R.id.txtMapTargetDetail).text = if (onTrip) {
            if (trip.nextDestination() == null) "All destinations reached. Complete the booking to finish." else "Customer-selected destination"
        } else if (trip.pickup != null) "Customer-pinned location" else "Exact pickup pin not available"
        findViewById<TextView>(R.id.txtMapNotes).apply {
            text = trip.notes.trim()
            visibility = if (text.isNotBlank()) View.VISIBLE else View.GONE
        }
        findViewById<TextView>(R.id.txtMapSchedule).text = listOf(trip.date, trip.time).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "Schedule not provided" }
        findViewById<TextView>(R.id.txtMapDuration).text = "Booked for ${BookingExtras.duration(trip.totalMinutes())}"
        renderExtension()
        findViewById<View>(R.id.btnReviewStop).visibility=if(trip.pendingDestination!=null)View.VISIBLE else View.GONE
        findViewById<MaterialButton>(R.id.btnReachedStop).apply {
            visibility = if (onTrip && (trip.nextDestination() != null || trip.hasReachedLastDestination())) View.VISIBLE else View.GONE
            text = if (trip.hasReachedLastDestination()) "Complete booking" else "Reached this destination"
        }
        findViewById<View>(R.id.btnReachedStop).isEnabled = !busy
        findViewById<MaterialButton>(R.id.btnTripAction).apply {
            text = if (onTrip || trip.phase == TripPhase.COMPLETED) "View trip details" else DriverBookingAction.label(trip)
            isEnabled = !busy
            visibility = if (trip.phase == TripPhase.REQUESTED) View.GONE else View.VISIBLE
        }
        findViewById<View>(R.id.btnReturnToBookings).visibility = if (trip.phase == TripPhase.COMPLETED) View.VISIBLE else View.GONE
        findViewById<View>(R.id.btnMyLocation).visibility=if(trip.phase in listOf(TripPhase.ACCEPTED,TripPhase.ON_THE_WAY,TripPhase.ARRIVED,TripPhase.IN_PROGRESS))View.VISIBLE else View.GONE
        val shown = if (trip.phase == TripPhase.ACCEPTED || trip.driverLocation == null)
            trip.copy(driverLocation = previewLocation) else trip
        tripMap?.show(TripUi.pins(shown))
        tripMap?.showTripRoute(shown, pickupApproach = trip.phase in listOf(TripPhase.ACCEPTED, TripPhase.ON_THE_WAY, TripPhase.ARRIVED))
    }
    override fun onLocationChanged(location:Location){if(!::trip.isInitialized||trip.phase !in listOf(TripPhase.ACCEPTED,TripPhase.ON_THE_WAY,TripPhase.ARRIVED,TripPhase.IN_PROGRESS))return
        if (tripMap?.updateNavigationLocation(location) != true) return
        val position = TripPlace("Driver position", location.latitude, location.longitude)
        if (trip.phase == TripPhase.ACCEPTED) {
            previewLocation = position
            refresh() // Preview location stays on this device; no booking write or notification.
        } else if (!busy && android.os.SystemClock.elapsedRealtime() - lastLocationSave >= 10_000L) {
            lastLocationSave = android.os.SystemClock.elapsedRealtime()
            store.updateConfirmed(trip.id, { it.copy(driverLocation = position, locationAt = location.time) }) {
                if (!isFinishing && !isDestroyed) refresh()
            }
        }
    }
    override fun onResume(){super.onResume();handler.post(tick)
        if (::trip.isInitialized) TripNotifications.show(this, store.get(trip.id) ?: trip, driver = true)
    }
    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); detailSheet.save(outState) }
    override fun onPause(){handler.removeCallbacks(tick);super.onPause()}
}
