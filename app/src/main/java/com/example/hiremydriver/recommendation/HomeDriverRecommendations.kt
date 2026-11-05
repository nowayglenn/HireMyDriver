package com.example.hiremydriver.recommendation

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.hiremydriver.R
import java.util.Locale

class HomeDriverRecommendations(activity: AppCompatActivity, reloadDrivers: Runnable, skeletonUntil: Long) {
    private val originalCard = activity.findViewById<View>(R.id.aiRecommendedDriver)
    private val cards = android.widget.LinearLayout(activity).apply { orientation = android.widget.LinearLayout.VERTICAL }
    private val rendered = mutableMapOf<String, Pair<DriverMatch, View>>()
    private val status = activity.findViewById<TextView>(R.id.txtNearbyStatus)
    private val nearby: NearbyRecommendations
    private val refresh = activity.findViewById<SwipeRefreshLayout>(R.id.homeRefresh)
    private var userRefreshing = false

    init {
        val parent = originalCard.parent as android.view.ViewGroup
        val position = parent.indexOfChild(originalCard)
        parent.removeView(originalCard)
        parent.addView(cards, position)
        com.example.hiremydriver.OfflineSkeleton.bind(activity, cards, R.layout.item_ai_driver, 1)
        com.example.hiremydriver.CardSkeleton.show(cards, R.layout.item_ai_driver, 1)
        fun renderState(state: NearbyRecommendations.State) {
            status.text = state.message
            status.visibility = if (state.matches.isNotEmpty() || (state.loading && cards.childCount > 0)) View.GONE else View.VISIBLE
            refresh.isRefreshing = userRefreshing && state.loading
            if (!state.loading) userRefreshing = false
            // Keep the previous cards on screen while the location request is running.
            if (state.loading) { status.visibility = View.GONE; return }
            com.example.hiremydriver.CardSkeleton.clear(cards)
            val matches = state.matches.take(5)
            val ids = matches.map { it.driver.id }.toSet()
            rendered.keys.toList().filter { it !in ids }.forEach { id -> cards.removeView(rendered.remove(id)?.second) }
            matches.forEachIndexed { index, match ->
                val previous = rendered[match.driver.id]
                val card = previous?.second ?: activity.layoutInflater.inflate(R.layout.item_ai_driver, cards, false)
                val moved = card.parent != null && cards.indexOfChild(card) != index
                if (card.parent == null) cards.addView(card, index)
                else if (moved) { cards.removeView(card); cards.addView(card, index) }
                if (previous?.first == match && !moved) return@forEachIndexed
                rendered[match.driver.id] = match to card
                val driver = match.driver
                val distance = if (match.distanceKm.isFinite()) String.format(Locale.getDefault(), "%.1f km", match.distanceKm) else "Distance unavailable"
                card.findViewById<TextView>(R.id.txtDriverName).text = driver.name
                card.findViewById<TextView>(R.id.txtRating).text = driver.rating?.let {
                    String.format(Locale.getDefault(), "★ %.1f", it)
                } ?: activity.getString(R.string.recommendation_no_rating)
                card.findViewById<TextView>(R.id.txtDetails).text = driver.vehicles.joinToString(", ") + "\n" + driver.transmissions.joinToString(" / ")
                card.findViewById<TextView>(R.id.txtNearbyDistance).text = distance
                com.example.hiremydriver.PeerProfilePhoto.bind(card.findViewById(R.id.imgDriver), driver.id, true)
                com.example.hiremydriver.trip.RatingDisplay.bind(card.findViewById(R.id.txtRating), driver.id)
                card.setOnClickListener {
                    activity.startActivity(RecommendedDriverProfile.intent(activity, driver))
                }
            }
        }
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        var pendingState: NearbyRecommendations.State? = null
        val reveal = Runnable {
            if (!activity.isFinishing && !activity.isDestroyed) pendingState?.let(::renderState)
        }
        status.visibility = View.GONE
        nearby = NearbyRecommendations(activity) { state ->
            pendingState = state
            if (android.os.SystemClock.elapsedRealtime() >= skeletonUntil) renderState(state)
        }
        handler.postDelayed(reveal, (skeletonUntil - android.os.SystemClock.elapsedRealtime()).coerceAtLeast(0))
        status.setOnClickListener { nearby.retry() }
        refresh.setColorSchemeColors(android.graphics.Color.rgb(255, 122, 0))
        refresh.setProgressBackgroundColorSchemeColor(android.graphics.Color.WHITE)
        refresh.setOnChildScrollUpCallback { _, _ ->
            activity.findViewById<View>(R.id.homeScrollView).canScrollVertically(-1)
        }
        val reload = {
            userRefreshing = true
            reloadDrivers.run()
            nearby.refresh()
        }
        refresh.setOnRefreshListener { reload() }
        ViewCompat.addAccessibilityAction(refresh, activity.getString(R.string.refresh_drivers)) { _, _ ->
            reload()
            true
        }
        activity.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) { handler.removeCallbacks(reveal) }
            override fun onPause(owner: LifecycleOwner) {
                userRefreshing = false
                refresh.isRefreshing = false
            }
        })
    }
}
