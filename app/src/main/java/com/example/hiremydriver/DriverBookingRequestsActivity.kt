package com.example.hiremydriver
import android.os.Bundle
import android.view.View
import com.example.hiremydriver.trip.*
class DriverBookingRequestsActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_driver_booking_requests)
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        val cards = BookingCards(this)
        val store = LocalTripStore(this)
        val container = findViewById<android.widget.LinearLayout>(R.id.requestsContainer)
        OfflineSkeleton.bind(this, container, R.layout.item_driver_request_full, 2)
        fun render() {
            if (store.isLoading()) { CardSkeleton.show(container, R.layout.item_driver_request_full); return }
            CardSkeleton.clear(container)
            val trips = cards.trips().forDriver(DriverAuth.currentUser(this)?.uid.orEmpty()).filter { it.phase == TripPhase.REQUESTED }
            cards.driver(container, trips, R.layout.item_driver_request_full, ::render)
            if (trips.isEmpty()) cards.empty(container, store.loadError() ?: "No booking requests yet.")
        }
        render()
        LocalTripStore(this).observe(this, ::render)
        BookingScreenRefresh(this, R.id.driverRequestsRefresh, R.id.driverRequestsScroll, store, ::render)
    }
}
