package com.example.hiremydriver

import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.hiremydriver.trip.LocalTripStore

/** The refresh indicator follows the actual server read, retaining existing cards. */
class BookingScreenRefresh(activity: AppCompatActivity, refreshId: Int, scrollId: Int,
    store: LocalTripStore, render: () -> Unit) {
    init {
        val refresh = activity.findViewById<SwipeRefreshLayout>(refreshId)
        val scroll = activity.findViewById<View>(scrollId)
        var pending = false
        fun reload() {
            if (pending) return
            if (!NetworkStatus.requireConnection(activity) { reload() }) { refresh.isRefreshing = false; return }
            pending = true
            refresh.isRefreshing = true
            store.refresh {
                pending = false
                if (!activity.isDestroyed && !activity.isFinishing) {
                    render()
                    refresh.isRefreshing = false
                }
            }
            render()
        }
        refresh.setColorSchemeColors(android.graphics.Color.rgb(255, 122, 0))
        refresh.setProgressBackgroundColorSchemeColor(android.graphics.Color.WHITE)
        refresh.setOnChildScrollUpCallback { _, _ -> scroll.canScrollVertically(-1) }
        refresh.setOnRefreshListener { reload() }
        ViewCompat.addAccessibilityAction(refresh, activity.getString(R.string.refresh_bookings)) { _, _ -> reload(); true }
    }
}
