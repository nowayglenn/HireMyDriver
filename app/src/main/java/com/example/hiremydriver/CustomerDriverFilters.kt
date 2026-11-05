package com.example.hiremydriver

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

object DriverFilterStore {
    private fun prefs(context: Context) = context.getSharedPreferences("driver_filters_${CustomerAuth.currentUser(context)?.uid.orEmpty()}", Context.MODE_PRIVATE)
    fun read(context: Context): DriverFilterCriteria = prefs(context).let {
        DriverFilterCriteria(it.getInt("experience", 0), it.getString("vehicle", "All") ?: "All",
            it.getString("area", "All Areas") ?: "All Areas", it.getInt("day", 0), it.getInt("rating", 0))
    }
    fun save(context: Context, value: DriverFilterCriteria) {
        prefs(context).edit().putInt("experience", value.experience).putString("vehicle", value.vehicle)
            .putString("area", value.area).putInt("day", value.day).putInt("rating", value.minimumRating).apply()
    }
}

/** Rating filters use the same authorized review records as the displayed ratings. */
class CustomerDriverFilters(private val activity: AppCompatActivity, private val changed: Runnable) : DefaultLifecycleObserver {
    private var criteria = DriverFilterStore.read(activity)
    private var drivers: List<RegisteredDriver> = emptyList()
    private val ratings = mutableMapOf<String, Double?>()
    private val subscriptions = mutableMapOf<String, ListenerRegistration>()
    private var started = false
    init { activity.lifecycle.addObserver(this) }
    fun isActive() = criteria.active
    fun reset() {
        criteria = DriverFilterCriteria()
        DriverFilterStore.save(activity, criteria)
        watchRatings()
        changed.run()
    }
    fun matches(driver: RegisteredDriver) = !criteria.active || criteria.matches(driver.searchData, ratings[driver.uid])
    fun updateDrivers(items: List<RegisteredDriver>) { drivers = items.toList(); watchRatings() }
    override fun onResume(owner: LifecycleOwner) { criteria = DriverFilterStore.read(activity); started = true; watchRatings(); changed.run() }
    override fun onStop(owner: LifecycleOwner) { started = false; subscriptions.values.forEach { it.remove() }; subscriptions.clear() }
    private fun watchRatings() {
        val wanted = if (started && criteria.minimumRating > 0) drivers.map { it.uid }.toSet() else emptySet()
        subscriptions.keys.toList().filter { it !in wanted }.forEach { subscriptions.remove(it)?.remove() }
        wanted.filter { it !in subscriptions }.forEach { uid ->
            subscriptions[uid] = FirebaseFirestore.getInstance().collection("reviews").whereEqualTo("driverUid", uid)
                .addSnapshotListener { snapshot, error ->
                    if (error == null && snapshot != null) {
                        val scores = snapshot.documents.mapNotNull {
                            com.example.hiremydriver.trip.BookingReview.fromRecord(it.id, it.data.orEmpty(), 0L)?.rating
                        }
                        ratings[uid] = scores.takeIf { it.isNotEmpty() }?.average()
                        changed.run()
                    }
                }
        }
    }
}
