package com.example.hiremydriver
import android.os.Bundle
import android.view.View
import com.example.hiremydriver.trip.*
class DriverUpcomingBookingsActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_driver_upcoming_bookings)
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        val cards = BookingCards(this)
        val store = LocalTripStore(this)
        val container = findViewById<android.view.ViewGroup>(R.id.driverUpcomingScroll).getChildAt(0) as android.widget.LinearLayout
        OfflineSkeleton.bind(this, container, R.layout.item_driver_upcoming_full, 2)
        fun render() {
            if (store.isLoading()) { CardSkeleton.show(container, R.layout.item_driver_upcoming_full); return }
            CardSkeleton.clear(container)
            val trips = cards.trips().forDriver(DriverAuth.currentUser(this)?.uid.orEmpty()).filter { it.driverGroup() == DriverBookingGroup.UPCOMING }
            cards.driver(container, trips, R.layout.item_driver_upcoming_full, ::render)
            if (trips.isEmpty()) cards.empty(container, store.loadError() ?: "No upcoming bookings.")
        }
        render()
        LocalTripStore(this).observe(this, ::render)
        BookingScreenRefresh(this, R.id.driverUpcomingRefresh, R.id.driverUpcomingScroll, store, ::render)
    }
}
