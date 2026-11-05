package com.example.hiremydriver

import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.hiremydriver.recommendation.*
import com.example.hiremydriver.trip.LocalTripStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AiResultsActivity : BaseActivity() {
    private var nearby: NearbyRecommendations? = null
    private var matchingWatch: com.google.firebase.firestore.ListenerRegistration? = null
    private lateinit var refresh: SwipeRefreshLayout
    private var userRefreshing = false
    private lateinit var cards: android.widget.LinearLayout
    private val rendered = mutableMapOf<String, Pair<DriverMatch, View>>()
    private val matchingHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val matchingTimeout = Runnable {
        if (::cards.isInitialized) CardSkeleton.clear(cards)
        if (!isFinishing && !isDestroyed) findViewById<TextView>(R.id.txtRecommendationSummary).apply {
            text = "Could not check driver schedules. Check your connection and tap here to retry."
            visibility = View.VISIBLE
            setOnClickListener { showMatches() }
        }
    }
    private val cardIds = listOf(R.id.result1, R.id.result2, R.id.result3, R.id.result4, R.id.result5)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_results)
        refresh = findViewById(R.id.recommendedRefresh)
        refresh.isEnabled = intent.getBooleanExtra("BROWSE_RECOMMENDED", false)
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        cards = findViewById<View>(R.id.result1).parent as android.widget.LinearLayout
        cards.removeAllViews()
        OfflineSkeleton.bind(this, cards, R.layout.item_ai_result, 2)
        CardSkeleton.show(cards, R.layout.item_ai_result)
        if (intent.getBooleanExtra("BROWSE_RECOMMENDED", false)) showNearby() 
    }

    private fun showNearby() {
        val summary = findViewById<TextView>(R.id.txtRecommendationSummary)
        summary.isClickable = true
        summary.isFocusable = true
        summary.minHeight = (48 * resources.displayMetrics.density).toInt()
        summary.setOnClickListener { nearby?.retry() }
        nearby = NearbyRecommendations(this) { state ->
            summary.text = state.message
            summary.visibility = if (state.matches.isNotEmpty() || (state.loading && cards.childCount > 0)) View.GONE else View.VISIBLE
            refresh.isRefreshing = userRefreshing && state.loading
            if (state.loading) return@NearbyRecommendations
            userRefreshing = false
            displayMatches(state.matches, state.area.orEmpty(), null)
        }
        refresh.setColorSchemeColors(android.graphics.Color.rgb(255, 122, 0))
        refresh.setProgressBackgroundColorSchemeColor(android.graphics.Color.WHITE)
        refresh.setOnChildScrollUpCallback { _, _ ->
            findViewById<View>(R.id.recommendedScroll).canScrollVertically(-1)
        }
        refresh.setOnRefreshListener { refreshDrivers() }
        ViewCompat.addAccessibilityAction(refresh, getString(R.string.refresh_drivers)) { _, _ ->
            refreshDrivers()
            true
        }
    }

    private fun refreshDrivers() {
        userRefreshing = true
        refresh.isRefreshing = true
        nearby?.refresh()
    }

    override fun onResume() {
        super.onResume()
        if (!intent.getBooleanExtra("BROWSE_RECOMMENDED", false)) showMatches()
    }

    override fun onPause() {
        matchingHandler.removeCallbacks(matchingTimeout)
        matchingWatch?.remove(); matchingWatch = null
        super.onPause()
        userRefreshing = false
        refresh.isRefreshing = false
    }

    private fun showMatches() {
        matchingHandler.removeCallbacks(matchingTimeout)
        val summary = findViewById<TextView>(R.id.txtRecommendationSummary)
        val area = intent.getStringExtra("recommend_area")
        val start = intent.getLongExtra("recommend_start", 0)
        val duration = intent.getIntExtra("recommend_duration", 0)
        if (area.isNullOrBlank() || duration !in 1..24 || start <= System.currentTimeMillis()) {
            CardSkeleton.clear(cards)
            summary.text = "Return to Find My Best Driver and check your saved address and trip time."
            return
        }
        summary.text = "Checking driver schedules…"
        if (rendered.isEmpty()) CardSkeleton.show(cards, R.layout.item_ai_result)
        summary.visibility = View.VISIBLE
        summary.setOnClickListener(null)
        matchingWatch?.remove()
        matchingHandler.postDelayed(matchingTimeout, 15000)
        matchingWatch = FirebaseMatching.search(this, area, start, duration,
            intent.getStringExtra("recommend_vehicle") ?: "Any",
            intent.getStringExtra("recommend_transmission") ?: "Any",
            originCoords = if (intent.hasExtra("origin_lat") && intent.hasExtra("origin_lng")) runCatching {
                Coordinates(intent.getDoubleExtra("origin_lat", Double.NaN), intent.getDoubleExtra("origin_lng", Double.NaN))
            }.getOrNull() else null, watch = true) { matches, error ->
            if (isFinishing || isDestroyed) return@search
            matchingHandler.removeCallbacks(matchingTimeout)
            summary.text = error ?: if (matches.isEmpty()) getString(R.string.recommendation_no_matches)
                else getString(R.string.recommendation_summary, matches.size, area)
            summary.visibility = if (error == null && matches.isNotEmpty()) View.GONE else View.VISIBLE
            displayMatches(matches, area, "Selected trip")
        }
    }

    private fun displayMatches(matches: List<DriverMatch>, area: String, tripDescription: String?) {
        CardSkeleton.clear(cards)
        val ids = matches.map { it.driver.id }.toSet()
        rendered.keys.toList().filter { it !in ids }.forEach { id -> cards.removeView(rendered.remove(id)?.second) }
        matches.forEachIndexed { index, match ->
            val previous = rendered[match.driver.id]
            val card = previous?.second ?: layoutInflater.inflate(R.layout.item_ai_result, cards, false)
            val moved = card.parent != null && cards.indexOfChild(card) != index
            if (card.parent == null) cards.addView(card, index)
            else if (moved) { cards.removeView(card); cards.addView(card, index) }
            if (previous?.first == match && !moved) return@forEachIndexed
            rendered[match.driver.id] = match to card
            val driver = match.driver
            card.visibility = View.VISIBLE
            card.findViewById<TextView>(R.id.txtDriverName).text = driver.name
            val rating = driver.rating?.let { String.format(Locale.getDefault(), "★ %.1f", it) }
                ?: getString(R.string.recommendation_no_rating)
            card.findViewById<TextView>(R.id.txtRating).text = rating
            val distance = if (match.distanceKm.isFinite()) String.format(Locale.getDefault(), "%.1f km · saved location", match.distanceKm) else area
            card.findViewById<TextView>(R.id.txtMatchPercent).text = distance
            card.findViewById<TextView>(R.id.txtDetails).text = if (tripDescription == null)
                "Serves $area · ${driver.vehicles.joinToString(", ")} · ${driver.transmissions.joinToString(" / ")}"
                else getString(R.string.recommendation_details, driver.vehicles.joinToString(", "), driver.transmissions.joinToString(" / "))
            card.findViewById<TextView>(R.id.txtPrice).text = if (driver.rateProvided) getString(R.string.price_per_hour, driver.hourlyRate.toString()) else "Rate not set"
            com.example.hiremydriver.PeerProfilePhoto.bind(card.findViewById(R.id.imgDriver), driver.id, true)
            com.example.hiremydriver.trip.RatingDisplay.bind(card.findViewById(R.id.txtRating), driver.id)
            val openProfile = View.OnClickListener {
                startActivity(RecommendedDriverProfile.intent(this, driver))
            }
            card.setOnClickListener(openProfile)
            card.findViewById<View>(R.id.btnViewProfile).setOnClickListener(openProfile)
        }
    }
}
