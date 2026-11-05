package com.example.hiremydriver.trip

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.example.hiremydriver.*
import com.example.hiremydriver.maps.DriverTripMapActivity
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.Locale

/** Actual saved data and actions inside the original full-page detail layouts. */
class SavedBookingDetails(private val activity: Activity) {
    private val store = LocalTripStore(activity)
    private val id = activity.intent.getStringExtra(LocalTripStore.ID).orEmpty()
    private var watching = false
    private var busy = false
    private val readOnly get() = activity.intent.getBooleanExtra(BookingNavigation.READ_ONLY, false)
    private fun watch(render: () -> Unit) {
        if (!watching && activity is androidx.lifecycle.LifecycleOwner) {
            watching = true
            store.observe(activity) { if (!activity.isFinishing && !activity.isDestroyed) render() }
            LocalReviewStore(activity).observe(activity) { if (!activity.isFinishing && !activity.isDestroyed) render() }
        }
    }
    private fun trip(): TripSession? = store.get(id).also {
        if (it == null) {
            Toast.makeText(activity, "Booking no longer available", Toast.LENGTH_SHORT).show()
            activity.finish()
        }
    }
    private fun text(id: Int, value: String) { activity.findViewById<TextView>(id)?.text = value }
    private fun action(id: Int, block: () -> Unit) { activity.findViewById<View>(id).setOnClickListener { block() } }
    private fun row(id: Int, value: String) {
        activity.findViewById<TextView>(id).apply { text = value; (parent as View).visibility = View.VISIBLE }
    }
    private fun destinations(t: TripSession) = t.destinations.takeIf { it.isNotEmpty() }
        ?.map { it.label }
        ?.joinToString("\n") ?: t.destinationLabel
    private fun route(t: TripSession, driver: Boolean) {
        if (driver) {
            activity.findViewById<View>(R.id.legacyRouteGraphic).visibility = View.GONE
            activity.findViewById<View>(R.id.legacyRouteText).visibility = View.GONE
        } else {
            (activity.findViewById<View>(R.id.txtTripPickup).parent as View).visibility = View.GONE
            (activity.findViewById<View>(R.id.txtTripDestination).parent as View).visibility = View.GONE
        }
        val completedCustomer = !driver && t.phase == TripPhase.COMPLETED
        activity.findViewById<View>(R.id.completedTripDetailsCard)?.visibility =
            if (completedCustomer) View.VISIBLE else View.GONE
        activity.findViewById<View>(R.id.savedRouteTimeline).visibility =
            if (completedCustomer) View.GONE else View.VISIBLE
        val container = activity.findViewById<android.widget.LinearLayout>(
            if (completedCustomer) R.id.completedRouteTimeline else R.id.savedRouteTimeline)
        container.visibility = View.VISIBLE
        BookingRouteView.render(container, t.bookingRoute())
    }
    private fun duration(t: TripSession) = BookingExtras.duration(t.totalMinutes())
    private fun map(driver: Boolean) {
        activity.startActivity(Intent(activity, if (driver) DriverTripMapActivity::class.java else TripInProgressActivity::class.java)
            .putExtra(LocalTripStore.ID, id))
    }
    private fun message(t: TripSession, driver: Boolean) {
        activity.startActivity(com.example.hiremydriver.messaging.BookingChat.intent(activity, t.id, driver))
    }
    private fun driverData(t: TripSession, request: Boolean) {
        CustomerBookingProfile.bind(activity, t)
        PeerProfilePhoto.bind(activity.findViewById(R.id.imgCustomerProfile), t.customerUid, false, true)
        text(R.id.txtPickupLocation, t.pickup?.label ?: t.pickupLabel)
        text(R.id.txtDropoffLocation, destinations(t))
        route(t, true)
        text(R.id.txtBookingType, "By the Hour (${duration(t)})")
        text(R.id.txtDateTime, listOf(t.date, t.time).filter { it.isNotBlank() }.joinToString(" · "))
        text(R.id.txtTotalAmount, if (t.extensionMinutes > 0) BookingExtras.money(t.totalAmount()) else t.fee.ifBlank { "Not specified" })
        text(R.id.txtPaymentMethod, "Cash")
        // Compact display reference only; all lookups and actions keep the full booking UUID.
        text(R.id.txtBookingId, "Booking ID: HMD-${t.id.replace("-", "").take(8).uppercase(Locale.ROOT)}")
        text(R.id.txtBookingStatus, TripUi.status(t))
        row(R.id.savedBookingVehicle, t.vehicle.ifBlank { "Not specified" })
        row(R.id.savedBookingNotes, t.notes.ifBlank { "No additional notes" } +
            if (t.phase == TripPhase.DECLINED) "\n\n${t.requestStatus}" else "")
        val date = runCatching { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).apply { isLenient = false }.parse(t.date) }.getOrNull()
        text(if (request) R.id.txtReqMonth else R.id.txtUpcomingMonth,
            date?.let { SimpleDateFormat("MMM", Locale.getDefault()).format(it).uppercase() } ?: "—")
        text(if (request) R.id.txtReqDay else R.id.txtUpcomingDay,
            date?.let { SimpleDateFormat("d", Locale.getDefault()).format(it) } ?: "—")
        text(if (request) R.id.txtReqTime else R.id.txtUpcomingTime, t.time.ifBlank { "—" })
        action(R.id.btnBack) { activity.finish() }
        action(R.id.btnViewMap) { map(true) }
    }

    fun request() {
        watch(::request)
        val t = trip() ?: return
        if (t.detailPage(true) == BookingDetailPage.DRIVER_BOOKING) {
            BookingNavigation.details(activity, id, true, readOnly); activity.finish(); return
        }
        driverData(t, true)
        BookingExtras.details(activity,t,true,::request)
        val pending = t.phase == TripPhase.REQUESTED
        activity.findViewById<View>(R.id.bottomActionArea).visibility = if (pending && !readOnly) View.VISIBLE else View.GONE
        activity.findViewById<View>(R.id.btnAcceptBooking).isEnabled = pending && !busy
        activity.findViewById<View>(R.id.btnDeclineBooking).isEnabled = pending && !busy
        action(R.id.btnAcceptBooking) {
            BookingDialogs.confirm(activity, "Accept booking?",
                "${t.customerName}\n${t.pickupLabel} → ${t.destinationLabel}\n${t.date} · ${t.time}", "Accept booking") {
                    if (busy) return@confirm
                    busy = true
                    activity.findViewById<View>(R.id.btnAcceptBooking).isEnabled = false
                    activity.findViewById<View>(R.id.btnDeclineBooking).isEnabled = false
                    store.updateConfirmed(id, { it.accept() }) { saved ->
                        busy = false
                        if (!activity.isFinishing && !activity.isDestroyed) {
                            if (saved) {
                                BookingNavigation.details(activity, id, true); activity.finish()
                            } else request()
                        }
                    }
                }
        }
        action(R.id.btnDeclineBooking) { BookingCards(activity).decline(id) { request() } }
    }

    fun driverBooking() {
        watch(::driverBooking)
        val t = trip() ?: return
        if (t.detailPage(true) == BookingDetailPage.DRIVER_REQUEST) {
            BookingNavigation.details(activity, id, true, readOnly); activity.finish(); return
        }
        driverData(t, false)
        BookingExtras.details(activity,t,true,::driverBooking)
        val active = t.phase == TripPhase.IN_PROGRESS
        val complete = t.phase == TripPhase.COMPLETED
        activity.findViewById<View>(R.id.bottomActionArea).visibility = if (readOnly) View.GONE else View.VISIBLE
        activity.findViewById<View>(R.id.cardTripInProgress).visibility = if (active) View.VISIBLE else View.GONE
        activity.findViewById<View>(R.id.cardTripCompleted).visibility = if (complete) View.VISIBLE else View.GONE
        activity.findViewById<MaterialButton>(R.id.btnStartService).apply {
            visibility = if (complete) View.GONE else View.VISIBLE
            text = DriverBookingAction.label(t)
            isEnabled = !busy
        }
        activity.findViewById<View>(R.id.btnViewReview).visibility = if (complete) View.VISIBLE else View.GONE
        activity.findViewById<TextView>(R.id.txtBookingStatus).apply {
            setBackgroundResource(if (active) R.drawable.bg_info_light_blue else R.drawable.bg_status_accepted_green)
            setTextColor(Color.parseColor(if (active) "#1463D9" else "#00B85A"))
        }
        action(R.id.btnStartService) {
            if (!busy) DriverBookingAction.confirm(activity, store.get(id) ?: t) { change ->
                busy = true; driverBooking()
                store.updateConfirmed(id, change) { saved ->
                    busy = false
                    if (!activity.isFinishing && !activity.isDestroyed) {
                        driverBooking()
                        if (saved && t.phase == TripPhase.ACCEPTED) map(true)
                    }
                }
            }
        }
        action(R.id.btnMessageCustomer) { message(t, true) }
        action(R.id.btnViewReview) {
            activity.startActivity(Intent(activity, CustomerReviewsActivity::class.java)
                .putExtra(LocalTripStore.ID, id).putExtra("DRIVER_REVIEW", true))
        }
    }

    fun customer() {
        watch(::customer)
        val t = trip() ?: return
        action(R.id.btnBack) { activity.finish() }
        text(R.id.txtDriverName, t.driverName)
        text(R.id.txtRating, t.driverRating.ifBlank { "Rating not provided" })
        RatingDisplay.bind(activity.findViewById(R.id.txtRating), t.driverUid)
        text(R.id.txtExperience, "Your selected driver")
        text(R.id.txtVehicle, t.vehicle.ifBlank { "Not specified" })
        PeerProfilePhoto.bind(activity.findViewById(R.id.imgDriverThumb), t.driverUid, true)
        text(R.id.txtTripDate, t.date.ifBlank { "Not specified" })
        text(R.id.txtTripTime, t.time.ifBlank { "Not specified" })
        text(R.id.txtTripDuration, duration(t))
        text(R.id.txtTripPickup, t.pickup?.label ?: t.pickupLabel)
        text(R.id.txtTripDestination, destinations(t))
        route(t, false)
        text(R.id.txtTripVehicle, t.vehicle.ifBlank { "Not specified" })
        text(R.id.txtPaymentMethod, "Cash")
        text(R.id.txtTotalAmount, if (t.extensionMinutes > 0) BookingExtras.money(t.totalAmount()) else t.fee.ifBlank { "Not specified" })
        row(R.id.savedBookingNotes, t.notes.ifBlank { "No additional notes" })
        BookingExtras.details(activity,t,false,::customer)
        val completed = t.phase == TripPhase.COMPLETED
        activity.findViewById<View>(R.id.btnViewMapHistory).apply {
            visibility = if (completed) View.VISIBLE else View.GONE
            setOnClickListener {
                activity.startActivity(Intent(activity, LiveTrackingActivity::class.java)
                    .putExtra(LocalTripStore.ID, id))
            }
        }
        val declined = t.phase == TripPhase.DECLINED || t.phase == TripPhase.CANCELLED
        val pending = t.phase == TripPhase.REQUESTED
        val color = Color.parseColor(if (declined) "#F04438" else if (completed || t.phase == TripPhase.ACCEPTED) "#159447" else "#1463D9")
        activity.findViewById<View>(R.id.bannerStatus).setBackgroundResource(when {
            declined -> R.drawable.bg_status_cancelled
            completed || t.phase == TripPhase.ACCEPTED -> R.drawable.bg_status_banner
            pending -> R.drawable.bg_important_box
            else -> R.drawable.bg_info_light_blue
        })
        activity.findViewById<ImageView>(R.id.imgStatusIcon).apply {
            setImageResource(when { declined -> R.drawable.ic_help; completed -> R.drawable.ic_check_circle; pending -> R.drawable.ic_schedule; else -> R.drawable.ic_car_all })
            imageTintList = ColorStateList.valueOf(color)
            backgroundTintList = ColorStateList.valueOf(Color.parseColor(if (declined) "#FEE4E2" else if (completed) "#E7F6EC" else "#E0E9F5"))
        }
        text(R.id.txtStatusTitle, TripUi.status(t))
        text(R.id.txtStatusSubtitle, when (t.phase) {
            TripPhase.ACCEPTED -> "Your booking has been accepted."
            TripPhase.ON_THE_WAY -> "Your driver is heading to your pickup location."
            TripPhase.ARRIVED -> "Your driver is now at the pickup location."
            TripPhase.IN_PROGRESS -> "Your service has started."
            else -> if (declined) t.requestStatus else TripUi.timer(t)
        })
        activity.findViewById<TextView>(R.id.txtStatusTitle).setTextColor(color)
        activity.findViewById<TextView>(R.id.txtStatusSubtitle).setTextColor(color)
        val left = activity.findViewById<MaterialButton>(R.id.btnLeft)
        val right = activity.findViewById<MaterialButton>(R.id.btnRight)
        left.text = if (completed) "Book Again" else if (declined) "Back to Home" else if (pending) "View Map" else "Track Trip"
        val reviewed = completed && LocalReviewStore(activity).get(id) != null
        right.text = if (reviewed) "View Review" else if (completed) "Rate Driver" else if (declined) "Rebook Driver" else "Message Driver"
        right.backgroundTintList = ColorStateList.valueOf(Color.parseColor(if (declined) "#061B41" else "#FF7900"))
        left.setOnClickListener {
            when { completed -> rebook(t); declined -> { activity.startActivity(Intent(activity, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)); activity.finish() }; else -> map(false) }
        }
        right.setOnClickListener {
            when { completed -> activity.startActivity(Intent(activity, if (reviewed) CustomerReviewsActivity::class.java else RateDriverActivity::class.java).putExtra(LocalTripStore.ID, id).putExtra("DRIVER_NAME", t.driverName)); declined -> rebook(t); else -> message(t, false) }
        }
    }

    private fun rebook(t: TripSession) {
        // A new booking must never inherit the old record ID, pins or service state.
        activity.startActivity(Intent(activity, BookDriverActivity::class.java)
            .putExtra("DRIVER_UID", t.driverUid)
            .putExtra("DRIVER_NAME", t.driverName).putExtra("DRIVER_IMAGE", t.driverImage)
            .putExtra("DRIVER_PRICE", if (t.rate() > 0) "${BookingExtras.money(t.rate())}/hr" else "Rate not set")
            .putExtra("DRIVER_RATING", t.driverRating))
    }
}
