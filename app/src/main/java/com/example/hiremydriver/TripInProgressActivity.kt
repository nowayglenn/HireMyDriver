package com.example.hiremydriver

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import com.example.hiremydriver.maps.MapDetailSheet
import android.location.Location
import com.example.hiremydriver.maps.LocationPickerActivity
import com.example.hiremydriver.maps.MapScreenActivity
import com.example.hiremydriver.maps.TripMapController
import com.example.hiremydriver.trip.*

class TripInProgressActivity : MapScreenActivity() {
    private lateinit var detailSheet: MapDetailSheet
    private lateinit var store:LocalTripStore
    private lateinit var trip:TripSession
    private val handler=Handler(Looper.getMainLooper())
    private val tick=object:Runnable{override fun run(){
        if (!::trip.isInitialized) return
        findViewById<TextView>(R.id.txtStartTime).text = BookingExtras.timer(trip)
        if (trip.phase == TripPhase.IN_PROGRESS) handler.postDelayed(this, 1000)
    }}
    private val addDestination=registerForActivityResult(ActivityResultContracts.StartActivityForResult()){result->
        if(result.resultCode==RESULT_OK){LocalTripStore.readPlace(result.data?.getStringExtra("PLACE"))?.let{point->
            BookingDialogs.confirm(this, "Request another destination?",
                point.label+"\nYour current destinations stay unchanged until the driver accepts. The service timer continues.",
                "Request destination", icon = R.drawable.ic_location) {
                    store.updateConfirmed(trip.id, { it.requestStop(point) }) { refresh() }
                }
        }}
    }
    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);setContentView(R.layout.activity_trip_in_progress)
        store=LocalTripStore(this);trip=store.fromIntent(intent)
        tripMap=TripMapController(this,findViewById(R.id.mapContainer),savedInstanceState,topOverlayDp=160,bottomOverlayDp=20)
        findViewById<View>(R.id.btnBackToHome).setOnClickListener{finish()}
        findViewById<View>(R.id.btnMapRecenter).setOnClickListener { showOwnLocation(follow = true) }
        showOwnLocation()
        detailSheet = MapDetailSheet(this, savedInstanceState)
        findViewById<View>(R.id.mapOverlayContent).addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
            tripMap?.setTopOverlayHeight(view.height)
        }
        findViewById<View>(R.id.btnExpandTripMap).setOnClickListener{startActivity(Intent(this,LiveTrackingActivity::class.java).putExtra(LocalTripStore.ID,trip.id))}
        findViewById<View>(R.id.btnEmergency).setOnClickListener{startActivity(Intent(this,EmergencyAlertConfirmActivity::class.java).putExtra(LocalTripStore.ID,trip.id))}
        findViewById<View>(R.id.btnTripMessage).setOnClickListener {
            startActivity(com.example.hiremydriver.messaging.BookingChat.intent(this, trip.id, false))
        }
        findViewById<View>(R.id.btnAddTripDestination).setOnClickListener{
            addDestination.launch(Intent(this,LocationPickerActivity::class.java).putExtra("PICKER_TITLE","Add destination"))
        };refresh()
        findViewById<View>(R.id.txtStartTime).setOnClickListener { BookingExtras.request(this,trip,::refresh) }
        store.observe(this, ::refresh)
    }
    private fun refresh(){if(!::trip.isInitialized)return;trip=store.get(trip.id)?:trip
        handler.removeCallbacks(tick)
        if (trip.phase == TripPhase.IN_PROGRESS && lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) handler.postDelayed(tick, 1000)
        TripNotifications.show(this, trip, driver = false)
        findViewById<TextView>(R.id.txtDriverName).text=trip.driverName
        PeerProfilePhoto.bind(findViewById(R.id.imgDriverSmall), trip.driverUid, true)
        findViewById<TextView>(R.id.txtRating).text=trip.driverRating
        RatingDisplay.bind(findViewById(R.id.txtRating), trip.driverUid)
        findViewById<TextView>(R.id.txtVehicle).text=trip.vehicle
        val timeline = findViewById<android.widget.LinearLayout>(R.id.tripRouteTimeline)
        val entries = trip.bookingRoute()
        if (timeline.tag != entries) {
            BookingRouteView.render(timeline, entries, review = true)
            timeline.tag = entries
        }
        findViewById<TextView>(R.id.txtMapTitle).text = when (trip.phase) {
            TripPhase.REQUESTED -> "Your booking"
            TripPhase.ACCEPTED -> "Accepted"
            TripPhase.ON_THE_WAY -> "Driver on the way"
            TripPhase.ARRIVED -> "Your driver is here"
            TripPhase.IN_PROGRESS -> "On your trip"
            TripPhase.COMPLETED -> "Trip complete"
            TripPhase.CANCELLED -> "Booking cancelled"; TripPhase.DECLINED -> "Booking declined"
        }
        findViewById<TextView>(R.id.txtTripHeading).text = when (trip.phase) {
            TripPhase.REQUESTED -> "Your booking is pending."
            TripPhase.ACCEPTED -> "Your booking has been accepted."
            TripPhase.ON_THE_WAY -> "Your driver is heading to your pickup."
            TripPhase.ARRIVED -> "Meet at your pickup."
            TripPhase.IN_PROGRESS -> "Enjoy your journey."
            TripPhase.COMPLETED -> "Thanks for riding."
            TripPhase.CANCELLED -> "Booking cancelled"; TripPhase.DECLINED -> "Request not accepted."
        }
        findViewById<TextView>(R.id.txtStartTime).apply {
            text=BookingExtras.timer(trip)
            val canRequest = trip.phase == TripPhase.IN_PROGRESS && trip.extensionStatus != "PENDING"
            isClickable = canRequest
            setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, if (canRequest) R.drawable.ic_extension_arrow else 0, 0)
            compoundDrawablePadding = (12 * resources.displayMetrics.density).toInt()
        }
        findViewById<TextView>(R.id.txtTripStateBadge).text=when(trip.phase){TripPhase.REQUESTED->"Pending";TripPhase.ACCEPTED->"Accepted";TripPhase.ON_THE_WAY->"Driver on the way";TripPhase.ARRIVED->"Driver has arrived";TripPhase.IN_PROGRESS->"In Progress";TripPhase.COMPLETED->"Completed";TripPhase.CANCELLED -> "Booking cancelled"; TripPhase.DECLINED->"Declined"}
        findViewById<TextView>(R.id.txtTripRequestStatus).apply{
            text=trip.pendingDestination?.let{"Requested: "+it.label+"\nWaiting for driver confirmation"}?:trip.requestStatus
            visibility=if(text.isBlank())View.GONE else View.VISIBLE
        }
        findViewById<View>(R.id.btnAddTripDestination).apply{visibility=if(trip.phase==TripPhase.IN_PROGRESS)View.VISIBLE else View.GONE;isEnabled=trip.pendingDestination==null}
        findViewById<View>(R.id.btnEmergency).visibility = if (trip.phase == TripPhase.IN_PROGRESS) View.VISIBLE else View.GONE
        tripMap?.show(TripUi.pins(trip))
        tripMap?.showTripRoute(trip)
    }
    override fun onLocationChanged(location: Location) { tripMap?.updateNavigationLocation(location) }
    override fun onResume(){super.onResume();refresh()}
    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); detailSheet.save(outState) }
    override fun onPause(){handler.removeCallbacks(tick);super.onPause()}
}
