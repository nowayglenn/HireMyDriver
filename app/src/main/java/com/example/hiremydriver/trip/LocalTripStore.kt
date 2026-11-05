package com.example.hiremydriver.trip

import android.content.Context
import android.content.Intent
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Account-scoped cache. Confirmed remote bookings are saved only after server acknowledgement. */
class LocalTripStore(context: Context) {
    private val screenContext = context
    private val appContext = context.applicationContext
    private val scope = com.example.hiremydriver.AccountDisplay.scope(context)
    fun isLoading(): Boolean = scope !in settledScopes && all().isEmpty()
    fun loadError(): String? = loadErrors[scope]
    internal fun loaded() { settledScopes.add(scope); loadErrors.remove(scope) }
    internal fun loadFailed() {
        settledScopes.add(scope)
        loadErrors[scope] = "Could not load bookings. Check your connection and try again."
    }
    fun refresh(done: () -> Unit) {
        settledScopes.remove(scope)
        loadErrors.remove(scope)
        FirebaseTripRepository(screenContext, this).refresh(done)
    }
    private val prefs = context.getSharedPreferences("account_map_trips_${com.example.hiremydriver.AccountDisplay.scope(context)}", Context.MODE_PRIVATE)
    fun get(id: String): TripSession? = prefs.getString(id, null)?.let { runCatching { decode(JSONObject(it)) }.getOrNull() }
    fun all(): List<TripSession> = prefs.all.keys.mapNotNull(::get).filter { it.localBooking }.reversed()
    fun save(trip: TripSession) {
        prefs.edit().putString(trip.id, encode(trip).toString()).apply()
        if (trip.phase != TripPhase.IN_PROGRESS) TripNotifications.cancel(appContext, trip.id)
    }
    internal fun remove(id: String) { prefs.edit().remove(id).apply() }
    fun update(id: String, change: (TripSession) -> TripSession): TripSession? {
        val before = get(id) ?: return null
        if (before.synced) {
            updateConfirmed(id, change) { }
            return before
        }
        return change(before).also { after ->
            save(after)
            TripNotifications.onChange(appContext, before, after)
        }
    }

    fun updateConfirmed(id: String, change: (TripSession) -> TripSession, done: (Boolean) -> Unit) {
        FirebaseTripRepository(screenContext, this).update(id, change, done)
    }

    fun observe(owner: androidx.lifecycle.LifecycleOwner, changed: () -> Unit) {
        FirebaseTripRepository(screenContext, this).observe(owner, changed)
    }

    fun create(intent: Intent): TripSession {
        val id = intent.getStringExtra(ID) ?: UUID.randomUUID().toString().also { intent.putExtra(ID, it) }
        get(id)?.let { return it }
        val points = readPlaces(intent.getStringExtra("TRIP_DESTINATIONS"))
        val pickup = readPlace(intent.getStringExtra("TRIP_PICKUP"))
        return TripSession(id, intent.getStringExtra("DRIVER_NAME") ?: "Selected driver", com.example.hiremydriver.CustomerAuth.displayName(appContext),
            intent.getStringExtra("PICKUP_LOCATION") ?: pickup?.label.orEmpty(),
            intent.getStringExtra("DESTINATION") ?: points.firstOrNull()?.label.orEmpty(), pickup, points,
            intent.getIntExtra("DURATION_HOURS", 4).coerceIn(1, 24),
            intent.getStringExtra("VEHICLE_TYPE").orEmpty(), intent.getStringExtra("ADDITIONAL_NOTES").orEmpty(),
            intent.getStringExtra("TOTAL_AMOUNT").orEmpty(), intent.getStringExtra("BOOKING_DATE").orEmpty(),
            intent.getStringExtra("BOOKING_TIME").orEmpty(),
            driverImage = intent.getIntExtra("DRIVER_IMAGE", 0),
            driverRating = intent.getStringExtra("DRIVER_RATING").orEmpty(),
            customerUid = com.example.hiremydriver.CustomerAuth.currentUser(appContext)?.uid.orEmpty(),
            driverUid = intent.getStringExtra("DRIVER_UID").orEmpty())
    }

    /** Legacy sample cards get their own record, never coordinates invented from their labels. */
    fun fromIntent(intent: Intent): TripSession {
        intent.getStringExtra(ID)?.let { get(it)?.let { t -> return t } }
        val pickup = intent.getStringExtra("PICKUP_LOCATION") ?: "Pickup not pinned"
        val destination = intent.getStringExtra("DESTINATION") ?: intent.getStringExtra("DROPOFF_LOCATION") ?: "Destination not pinned"
        val customer = intent.getStringExtra("CUSTOMER_NAME") ?: "Customer"
        val id = "sample-" + UUID.nameUUIDFromBytes("$customer|$pickup|$destination".toByteArray())
        intent.putExtra(ID, id)
        return get(id) ?: TripSession(id, intent.getStringExtra("DRIVER_NAME") ?: "Selected driver",
            customer, pickup, destination, fee = intent.getStringExtra("TOTAL_AMOUNT").orEmpty(),
            phase = TripPhase.ACCEPTED, localBooking = false).also(::save)
    }

    companion object {
        private val settledScopes = mutableSetOf<String>()
        private val loadErrors = mutableMapOf<String, String>()
        const val ID = "LOCAL_TRIP_ID"
        fun placeJson(p: TripPlace) = JSONObject().put("label", p.label).put("lat", p.latitude).put("lon", p.longitude)
        fun readPlace(raw: String?): TripPlace? = raw?.let { runCatching { place(JSONObject(it)) }.getOrNull() }
        private fun place(j: JSONObject) = TripPlace(j.getString("label"), j.getDouble("lat"), j.getDouble("lon"))
        fun placesJson(points: List<TripPlace>) = JSONArray().apply { points.forEach { put(placeJson(it)) } }.toString()
        fun readPlaces(raw: String?): List<TripPlace> = runCatching { val a = JSONArray(raw ?: "[]"); (0 until a.length()).map { place(a.getJSONObject(it)) } }.getOrDefault(emptyList())
        internal fun encode(t: TripSession) = JSONObject().apply {
            put("id",t.id);put("driver",t.driverName);put("customer",t.customerName)
            put("pickupLabel",t.pickupLabel);put("destinationLabel",t.destinationLabel)
            put("pickup",t.pickup?.let(::placeJson));put("destinations",JSONArray(placesJson(t.destinations)))
            put("hours",t.durationHours);put("vehicle",t.vehicle);put("notes",t.notes);put("fee",t.fee)
            put("date",t.date);put("time",t.time);put("phase",t.phase.name);put("started",t.startedAt);put("completed",t.completedAt)
            put("pending",t.pendingDestination?.let(::placeJson));put("request",t.requestStatus);put("reached",t.reachedStops)
            put("location",t.driverLocation?.let(::placeJson));put("locationAt",t.locationAt);put("local",t.localBooking)
            put("driverImage", t.driverImage);put("driverRating", t.driverRating)
            put("customerUid", t.customerUid);put("driverUid", t.driverUid);put("synced", t.synced)
            if(t.extensionMinutes > 0) put("extensionMinutes",t.extensionMinutes); if(t.requestedMinutes > 0) put("requestedMinutes",t.requestedMinutes); if(t.extensionStatus.isNotBlank()) put("extensionStatus",t.extensionStatus); if(t.extensionSequence > 0) put("extensionSequence",t.extensionSequence); if(t.hourlyRate > 0) put("hourlyRate",t.hourlyRate); if(t.cancellationReason.isNotBlank()) put("cancellationReason",t.cancellationReason)
        }
        internal fun decode(j: JSONObject) = TripSession(j.getString("id"),j.getString("driver"),j.getString("customer"),
            j.getString("pickupLabel"),j.getString("destinationLabel"),j.optJSONObject("pickup")?.let(::place),
            readPlaces(j.optJSONArray("destinations")?.toString()),j.getInt("hours"),j.optString("vehicle"),j.optString("notes"),
            j.optString("fee"),j.optString("date"),j.optString("time"),TripPhase.valueOf(j.getString("phase")),
            if(j.has("started"))j.getLong("started")else null,if(j.has("completed"))j.getLong("completed")else null,
            j.optJSONObject("pending")?.let(::place),j.optString("request"),j.optInt("reached"),
            j.optJSONObject("location")?.let(::place),if(j.has("locationAt"))j.getLong("locationAt")else null,j.optBoolean("local",true),
            j.optInt("driverImage"),j.optString("driverRating"),
            j.optString("customerUid"),j.optString("driverUid"),j.optBoolean("synced"), j.optInt("extensionMinutes"),j.optInt("requestedMinutes"),j.optString("extensionStatus"),j.optInt("extensionSequence"),j.optInt("hourlyRate"),j.optString("cancellationReason"))
    }
}
