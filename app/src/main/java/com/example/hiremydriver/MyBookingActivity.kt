package com.example.hiremydriver

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.example.hiremydriver.trip.*

class MyBookingActivity : CustomerNavigationActivity() {
    private var selectedTab = R.id.tabAll
    private lateinit var cards: BookingCards
    private val sections = listOf(
        Triple(CustomerBookingGroup.PENDING, R.id.sectionPending, R.layout.item_customer_booking_pending),
        Triple(CustomerBookingGroup.ACCEPTED, R.id.sectionAccepted, R.layout.item_customer_booking_accepted),
        Triple(CustomerBookingGroup.COMPLETED, R.id.sectionCompleted, R.layout.item_customer_booking_completed),
        Triple(CustomerBookingGroup.CANCELLED, R.id.sectionCancelled, R.layout.item_customer_booking_cancelled)
    )
    private val tabs = listOf(R.id.tabAll, R.id.tabPending, R.id.tabAccepted, R.id.tabCompleted, R.id.tabCancelled)
    private val countIds = listOf(R.id.txtPendingCount, R.id.txtAcceptedCount, R.id.txtCompletedCount, R.id.txtCancelledCount)
    private val groupTabs = listOf(R.id.tabPending, R.id.tabAccepted, R.id.tabCompleted, R.id.tabCancelled)
    private lateinit var emptyMessage: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_customer_booking)
        animateNavActiveBackground(findViewById(R.id.iconBookings))
        cards = BookingCards(this)
        selectedTab = savedInstanceState?.getInt("selected_tab", R.id.tabAll) ?: R.id.tabAll
        findViewById<View>(R.id.localTripsContainer).visibility = View.GONE
        val parent = findViewById<View>(R.id.sectionPending).parent as LinearLayout
        emptyMessage = TextView(this).apply {
            textSize = 14f; setTextColor(Color.parseColor("#667085"))
            val gap = (20 * resources.displayMetrics.density).toInt()
            setPadding(0, gap, 0, gap)
        }
        parent.addView(emptyMessage)
        tabs.forEach { id -> findViewById<View>(id).setOnClickListener { selectedTab = id; render() } }
        setupCustomerNavigation()
        render()
        LocalTripStore(this).observe(this, ::render)
    }

    private fun render() {
        val trips = cards.trips()
        tabs.forEach { id ->
            findViewById<TextView>(id).apply {
                setBackgroundResource(if (id == selectedTab) R.drawable.bg_booking_tab_active else R.drawable.bg_booking_tab)
                setTextColor(if (id == selectedTab) Color.WHITE else Color.rgb(90, 90, 90))
            }
        }
        var visibleCount = 0
        sections.forEachIndexed { index, (group, id, layout) ->
            val records = trips.filter { it.customerGroup() == group }
            val section = findViewById<LinearLayout>(id)
            cards.customer(section, records, layout, ::render)
            findViewById<TextView>(countIds[index]).text = records.size.toString()
            val visible = (selectedTab == R.id.tabAll || selectedTab == groupTabs[index]) && records.isNotEmpty()
            section.visibility = if (visible) View.VISIBLE else View.GONE
            if (visible) visibleCount += records.size
        }
        emptyMessage.text = if (trips.isEmpty()) "No bookings yet. Your requests will appear here." else "No bookings in this category."
        emptyMessage.visibility = if (visibleCount == 0) View.VISIBLE else View.GONE
    }

    override fun onResume() { super.onResume(); if (::cards.isInitialized) render() }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("selected_tab", selectedTab)
        super.onSaveInstanceState(outState)
    }
}
