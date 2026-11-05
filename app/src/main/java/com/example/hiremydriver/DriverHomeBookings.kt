package com.example.hiremydriver

import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.example.hiremydriver.trip.*
import java.text.SimpleDateFormat
import java.util.Locale

/** Real records bound to the original home calendar card and request-row template. */
class DriverHomeBookings(private val activity: DriverHomeActivity) {
    private val store = LocalTripStore(activity)
    private val cards = BookingCards(activity)
    private val requests = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    private val rows = linkedMapOf<String, View>()
    private val upcomingSkeleton = CardSkeleton.cover(activity.findViewById(R.id.upcomingBookingContent))
    init {
        // Keep the section heading and View All control in place.
        val firstRequest = activity.findViewById<View>(R.id.requestContainer1)
        val section = firstRequest.parent as LinearLayout
        val first = section.indexOfChild(firstRequest)
        section.removeViews(first, section.childCount - first)
        section.addView(requests, LinearLayout.LayoutParams(-1, -2))
        OfflineSkeleton.bind(activity, requests, R.layout.item_driver_home_request, 2)
        OfflineSkeleton.watch(activity) { offline -> upcomingSkeleton.loading(offline || store.isLoading()) }
    }
    fun render() {
        val loading = store.isLoading()
        upcomingSkeleton.loading(loading || !NetworkStatus.connected(activity))
        if (loading) {
            activity.findViewById<TextView>(R.id.txtPendingCount).text = "—"
            activity.findViewById<TextView>(R.id.txtCompletedCount).text = "—"
            if (requests.findViewWithTag<View>("card_skeleton") == null) {
                repeat(2) {
                    val template = activity.layoutInflater.inflate(R.layout.item_driver_home_request, requests, false)
                    val params = template.layoutParams
                    requests.addView(CardSkeleton(template), params)
                }
            }
            return
        }
        CardSkeleton.clear(requests)
        val trips = store.all().forDriver(DriverAuth.currentUser(activity)?.uid.orEmpty())
        fun text(id: Int, value: String) { activity.findViewById<TextView>(id).text = value }
        text(R.id.txtPendingCount, trips.count { it.phase == TripPhase.REQUESTED }.toString())
        text(R.id.txtCompletedCount, trips.count { it.phase == TripPhase.COMPLETED }.toString())
        val upcoming = trips.firstOrNull { it.driverGroup() == DriverBookingGroup.UPCOMING }
        text(R.id.txtUpcomingCustomer, upcoming?.customerName ?: store.loadError() ?: activity.getString(R.string.driver_no_upcoming))
        val date = upcoming?.let { runCatching { SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH).parse(it.date) }.getOrNull() }
        text(R.id.txtUpcomingMonth, date?.let { SimpleDateFormat("MMM", Locale.ENGLISH).format(it).uppercase() } ?: "—")
        text(R.id.txtUpcomingDay, date?.let { SimpleDateFormat("d", Locale.ENGLISH).format(it) } ?: "—")
        text(R.id.txtUpcomingTime, upcoming?.time.orEmpty())
        listOf(R.id.txtUpcomingRoute, R.id.txtBookingType, R.id.txtUpcomingStatus, R.id.imgUpcomingCustomer).forEach {
            activity.findViewById<View>(it).visibility = if (upcoming == null) View.GONE else View.VISIBLE
        }
        if (upcoming != null) {
            text(R.id.txtUpcomingRoute, route(upcoming))
            text(R.id.txtBookingType, "By the Hour (${upcoming.durationHours} hours)")
            text(R.id.txtUpcomingStatus, TripUi.status(upcoming))
            PeerProfilePhoto.bind(activity.findViewById(R.id.imgUpcomingCustomer), upcoming.customerUid, false, true)
        }
        activity.findViewById<View>(R.id.cardUpcomingBooking).setOnClickListener {
            if (upcoming != null) BookingNavigation.details(activity, upcoming.id, true)
            else activity.startActivity(android.content.Intent(activity, DriverUpcomingBookingsActivity::class.java))
        }
        val pending = trips.filter { it.phase == TripPhase.REQUESTED }.take(10)
        rows.keys.filter { id -> pending.none { it.id == id } }.forEach { id -> requests.removeView(rows.remove(id)) }
        requests.findViewWithTag<View>("real_data_empty")?.let { requests.removeView(it) }
        pending.forEachIndexed { index, trip ->
            val row = rows.getOrPut(trip.id) {
                activity.layoutInflater.inflate(R.layout.item_driver_home_request, requests, false).also { requests.addView(it) }
            }
            if (requests.indexOfChild(row) != index) {
                requests.removeView(row); requests.addView(row, index)
            }
            row.findViewById<TextView>(R.id.txtCustomer1).text = trip.customerName
            row.findViewById<TextView>(R.id.txtRequestDate1).text = "${trip.date} · ${trip.time}"
            row.findViewById<TextView>(R.id.txtRequestRoute1).text = route(trip)
            PeerProfilePhoto.bind(row.findViewById<ImageView>(R.id.imgCustomer1), trip.customerUid, false, true)
            row.setOnClickListener { BookingNavigation.details(activity, trip.id, true) }
            row.findViewById<View>(R.id.btnAccept1).setOnClickListener { cards.accept(trip, ::render) }
            row.findViewById<View>(R.id.btnDecline1).setOnClickListener { cards.decline(trip.id, ::render) }
        }
        if (pending.isEmpty()) AccountDisplay.empty(requests, store.loadError() ?: activity.getString(R.string.driver_no_requests))
    }
    private fun route(trip: TripSession) = "${trip.pickup?.label ?: trip.pickupLabel} → ${trip.destinations.firstOrNull()?.label ?: trip.destinationLabel}"
}
