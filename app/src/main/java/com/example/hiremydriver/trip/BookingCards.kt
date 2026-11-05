package com.example.hiremydriver.trip

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.example.hiremydriver.EmergencyAlertConfirmActivity
import com.example.hiremydriver.R
import com.example.hiremydriver.TripInProgressActivity
import com.example.hiremydriver.maps.DriverTripMapActivity
import java.text.DateFormat
import java.util.Date

/** Binds local bookings to copies of the original customer/driver card designs. */
class BookingCards(private val activity: Activity) {
    private val store = LocalTripStore(activity)
    private val rendered = java.util.WeakHashMap<LinearLayout, Pair<Int, List<TripSession>>>()
    private val pendingActions = mutableSetOf<String>()
    fun trips() = store.all()

    fun customer(container: LinearLayout, trips: List<TripSession>, layout: Int, refresh: () -> Unit) {
        // Preserve the original section title and count; replace only its sample cards.
        if (container.childCount > 1) container.removeViews(1, container.childCount - 1)
        trips.forEach { trip ->
            val card = inflate(container, layout, trip, false)
            card.setOnClickListener { details(trip.id, false) }
            card.findViewById<View>(R.id.bookingCardDetails)?.setOnClickListener { details(trip.id, false) }
            card.findViewById<View>(R.id.bookingCardCancel)?.apply {
                visibility = if (trip.phase == TripPhase.REQUESTED) View.VISIBLE else View.GONE
                setOnClickListener { BookingExtras.cancelPending(activity, trip.id, refresh) }
            }
            card.findViewById<View>(R.id.btnTrackTrip)?.setOnClickListener { openMap(trip.id, false) }
            card.findViewById<View>(R.id.btnEmergencyAccepted)?.setOnClickListener {
                activity.startActivity(Intent(activity, EmergencyAlertConfirmActivity::class.java).putExtra(LocalTripStore.ID, trip.id))
            }
            card.findViewById<View>(R.id.btnEmergencyAccepted)?.visibility =
                if (trip.phase == TripPhase.IN_PROGRESS) View.VISIBLE else View.GONE
        }
    }

    fun driver(container: LinearLayout, trips: List<TripSession>, layout: Int, refresh: () -> Unit) {
        val snapshot = layout to trips.toList()
        if (rendered[container] == snapshot) return
        if (rendered[container]?.first != layout) container.removeAllViews()
        rendered[container] = snapshot
        val wanted = trips.map { "booking:${it.id}" }.toSet()
        for (index in container.childCount - 1 downTo 0) {
            if (container.getChildAt(index).tag !in wanted) container.removeViewAt(index)
        }
        trips.forEachIndexed { index, trip ->
            val card = container.findViewWithTag<View>("booking:${trip.id}")?.also { bind(it, trip, true) }
                ?: inflate(container, layout, trip, true).apply { tag = "booking:${trip.id}" }
            if (container.indexOfChild(card) != index) {
                container.removeView(card); container.addView(card, index)
                bind(card, trip, true)
            }
            card.setOnClickListener { details(trip.id, true) }
            card.findViewById<View>(R.id.bookingCardDetails)?.setOnClickListener { openMap(trip.id, true) }
            card.findViewById<View>(R.id.bookingCardAccept)?.setOnClickListener {
                accept(trip, refresh)
            }
            card.findViewById<View>(R.id.bookingCardDecline)?.setOnClickListener { decline(trip.id, refresh) }
            card.findViewById<View>(R.id.btnStartTrip1)?.setOnClickListener { details(trip.id, true) }
            card.findViewById<View>(R.id.btnMessage1)?.setOnClickListener {
                activity.startActivity(com.example.hiremydriver.messaging.BookingChat.intent(activity, trip.id, true))
            }
        }
    }

    fun empty(container: LinearLayout, message: String) {
        if (container.findViewWithTag<View>("booking_empty") != null) return
        container.addView(TextView(activity).apply {
            tag = "booking_empty"
            text = message; textSize = 14f; setTextColor(Color.parseColor("#667085"))
            val gap = (16 * resources.displayMetrics.density).toInt()
            setPadding(gap, gap, gap, gap)
        }, LinearLayout.LayoutParams(-1, -2))
    }

    private fun inflate(parent: ViewGroup, layout: Int, trip: TripSession, driver: Boolean): View {
        val card = activity.layoutInflater.inflate(layout, parent, false)
        bind(card, trip, driver)
        parent.addView(card)
        return card
    }

    private fun bind(card: View, trip: TripSession, driver: Boolean) {
        fun text(id: Int, value: String) { card.findViewById<TextView>(id)?.text = value }
        text(R.id.bookingCardName, if (driver) trip.customerName else trip.driverName)
        val date = runCatching { java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.ENGLISH).parse(trip.date) }.getOrNull()
        text(R.id.bookingCardMonth, date?.let { java.text.SimpleDateFormat("MMM", java.util.Locale.ENGLISH).format(it).uppercase() } ?: "—")
        text(R.id.bookingCardDay, date?.let { java.text.SimpleDateFormat("d", java.util.Locale.ENGLISH).format(it) } ?: "—")
        text(R.id.bookingCardTime, trip.time)
        text(R.id.bookingCardSchedule, if (trip.phase == TripPhase.IN_PROGRESS && driver)
            "Started: ${trip.startedAt?.let { DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it)) } ?: "—"}" else schedule(trip))
        text(R.id.bookingCardRoute, route(trip))
        text(R.id.bookingCardPackage, "By the hour · ${hours(trip)}${trip.vehicle.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()}")
        if (driver && card.findViewById<View>(R.id.bookingCardFee) == null) {
            text(R.id.bookingCardPackage, "By the Hour (${hours(trip)}) · ${fee(trip)}")
        }
        text(R.id.bookingCardFee, fee(trip))
        text(R.id.bookingCardStatus, when (trip.phase) {
            TripPhase.REQUESTED -> "PENDING"
            TripPhase.ACCEPTED -> "ACCEPTED"
            TripPhase.ON_THE_WAY -> "DRIVER ON THE WAY"
            TripPhase.ARRIVED -> "AT PICKUP"
            TripPhase.IN_PROGRESS -> "ON TRIP"
            TripPhase.COMPLETED -> "COMPLETED"
            TripPhase.CANCELLED -> "Booking cancelled"; TripPhase.DECLINED -> "DECLINED"
        })
        card.findViewById<TextView>(R.id.bookingCardRating)?.apply {
            visibility = if (!driver && trip.driverRating.isNotBlank()) View.VISIBLE else View.GONE
            text = if (trip.driverRating.startsWith("★")) trip.driverRating else "★ ${trip.driverRating}"
        }
        card.findViewById<ImageView>(R.id.bookingCardImage)?.apply {
            imageTintList = null
            clearColorFilter()
            com.example.hiremydriver.PeerProfilePhoto.bind(this,
                if (driver) trip.customerUid else trip.driverUid, !driver, driver)
            contentDescription = if (driver) "Customer profile" else "Driver profile"
        }
    }

    fun accept(trip: TripSession, refresh: () -> Unit) {
        if (trip.id in pendingActions) return
        BookingDialogs.confirm(activity, "Accept booking?",
            "${trip.customerName}\n${route(trip)}\n${schedule(trip)}\n${hours(trip)} · ${fee(trip)}", "Accept booking") {
            if (store.get(trip.id)?.phase != TripPhase.REQUESTED) { stale(); return@confirm }
            if (!pendingActions.add(trip.id)) return@confirm
            store.updateConfirmed(trip.id, { it.accept() }) { saved ->
                pendingActions.remove(trip.id)
                if (saved) Toast.makeText(activity, "Booking accepted · See Upcoming", Toast.LENGTH_SHORT).show()
                refresh()
            }
        }
    }

    fun decline(id: String, refresh: () -> Unit) {
        if (id in pendingActions) return
        val current = store.get(id) ?: return
        if (current.phase != TripPhase.REQUESTED) { stale(); refresh(); return }
        val view = activity.layoutInflater.inflate(R.layout.dialog_decline_booking, null)
        val dialog = AlertDialog.Builder(activity).setView(view).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        val confirm = view.findViewById<View>(R.id.btnConfirmDecline)
        confirm.setOnClickListener {
            if (store.get(id)?.phase == TripPhase.REQUESTED) {
                if (!pendingActions.add(id)) return@setOnClickListener
                confirm.isEnabled = false
                store.updateConfirmed(id, { it.decline() }) { saved ->
                    pendingActions.remove(id)
                    if (saved) Toast.makeText(activity, "Booking declined", Toast.LENGTH_SHORT).show()
                    refresh()
                }
            } else stale()
            dialog.dismiss(); refresh()
        }
        view.findViewById<View>(R.id.btnCancelDecline).setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun details(id: String, driver: Boolean) {
        BookingNavigation.details(activity, id, driver)
    }

    private fun openMap(id: String, driver: Boolean) {
        if (store.get(id) == null) return
        activity.startActivity(Intent(activity, if (driver) DriverTripMapActivity::class.java else TripInProgressActivity::class.java)
            .putExtra(LocalTripStore.ID, id))
    }
    private fun stale() = Toast.makeText(activity, "This request is no longer pending", Toast.LENGTH_SHORT).show()
    private fun route(t: TripSession) = "${t.pickup?.label ?: t.pickupLabel} → ${t.destinations.firstOrNull()?.label ?: t.destinationLabel}" +
        if (t.destinations.size > 1) " · +${t.destinations.size - 1} stop(s)" else ""
    private fun schedule(t: TripSession) = listOf(t.date, t.time).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "Schedule not provided" }
    private fun hours(t: TripSession) = BookingExtras.duration(t.totalMinutes())
    private fun fee(t: TripSession) = if (t.extensionMinutes > 0) BookingExtras.money(t.totalAmount()) else t.fee.ifBlank { "Not specified" }
}
