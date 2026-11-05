package com.example.hiremydriver

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.example.hiremydriver.trip.*

class DriverBookingActivity : DriverNavigationActivity() {
    private var selectedTab = 1
    private lateinit var cards: BookingCards
    private lateinit var ongoing: LinearLayout
    private val tabs = listOf(R.id.tabRequests, R.id.tabUpcoming, R.id.tabCompleted)
    private val labels = listOf(R.id.txtTabRequests, R.id.txtTabUpcoming, R.id.txtTabCompleted)
    private val lines = listOf(R.id.lineTabRequests, R.id.lineTabUpcoming, R.id.lineTabCompleted)
    private val contents = listOf(R.id.contentRequests, R.id.contentUpcoming, R.id.contentCompleted)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_driver_booking)
        animateNavActiveBackground(findViewById(R.id.iconBooking))
        cards = BookingCards(this)
        findViewById<View>(R.id.localTripsContainer).visibility = View.GONE
        // Replace the sample ongoing card with a holder for every actual active booking.
        val sample = findViewById<View>(R.id.cardOngoingTrip)
        val parent = sample.parent as LinearLayout
        val index = parent.indexOfChild(sample)
        parent.removeView(sample)
        ongoing = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        parent.addView(ongoing, index, LinearLayout.LayoutParams(-1, -2))
        tabs.forEachIndexed { index, id -> findViewById<View>(id).setOnClickListener { selectTab(index + 1) } }
        setupDriverNavigation()
        selectedTab = savedInstanceState?.getInt("selected_tab") ?: when (intent.getStringExtra("OPEN_TAB")) {
            "UPCOMING" -> 2
            "COMPLETED" -> 3
            else -> 1
        }
        render()
        LocalTripStore(this).observe(this, ::render)
    }

    private fun selectTab(tab: Int) {
        selectedTab = tab
        tabs.indices.forEach { index ->
            val selected = index + 1 == tab
            findViewById<TextView>(labels[index]).apply {
                setTextColor(Color.parseColor(if (selected) "#FF7900" else "#667085"))
                typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            }
            findViewById<View>(lines[index]).setBackgroundColor(if (selected) Color.parseColor("#FF7900") else Color.TRANSPARENT)
            findViewById<View>(contents[index]).visibility = if (selected) View.VISIBLE else View.GONE
        }
    }

    private fun render() {
        val trips = cards.trips().forDriver(DriverAuth.currentUser(this)?.uid.orEmpty())
        val active = trips.filter { it.driverGroup() == DriverBookingGroup.ONGOING }
        cards.driver(ongoing, active, R.layout.item_driver_booking_ongoing, ::render)
        ongoing.visibility = if (active.isEmpty()) View.GONE else View.VISIBLE
        val groups = listOf(DriverBookingGroup.REQUESTS, DriverBookingGroup.UPCOMING, DriverBookingGroup.COMPLETED)
        val layouts = listOf(R.layout.item_driver_booking_request, R.layout.item_driver_booking_upcoming, R.layout.item_driver_booking_completed)
        val empty = listOf("No pending requests.", "No upcoming bookings.", "No completed bookings yet.")
        groups.forEachIndexed { index, group ->
            val records = if (group == DriverBookingGroup.COMPLETED) trips.completedNewestFirst()
                else trips.filter { it.driverGroup() == group }
            val container = findViewById<LinearLayout>(contents[index])
            cards.driver(container, records, layouts[index], ::render)
            if (records.isEmpty()) cards.empty(container, empty[index])
        }
        selectTab(selectedTab)
    }

    override fun onResume() { super.onResume(); if (::cards.isInitialized) render() }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("selected_tab", selectedTab)
        super.onSaveInstanceState(outState)
    }
}
