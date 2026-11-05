package com.example.hiremydriver.recommendation

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.hiremydriver.DriverAuth
import com.example.hiremydriver.DriverScheduleStore
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import java.util.UUID

/** Only explicit edits are uploaded; clean device caches never overwrite another device's settings. */
object DriverMatchingSettings {
    private val epochs = mutableMapOf<String, Long>()
    private fun changed(c: Context) { DriverAuth.currentUser(c)?.uid?.let { epochs[it] = (epochs[it] ?: 0) + 1 } }
    private val busy = mutableSetOf<String>()
    private val waiters = mutableMapOf<String, MutableList<(Boolean) -> Unit>>()
    private fun prefs(c: Context, name: String) = c.getSharedPreferences(DriverAuth.preferenceName(c, name), Context.MODE_PRIVATE)
    fun markDriving(c: Context) { changed(c); prefs(c, "driver_driving_info").edit().putString("cloud_pending", UUID.randomUUID().toString()).apply() }
    fun markSchedule(c: Context) { changed(c); prefs(c, DriverScheduleStore.PREFERENCES).edit().putString("cloud_pending", UUID.randomUUID().toString()).apply() }
    fun markAccepting(c: Context) { changed(c); prefs(c, DriverScheduleStore.PREFERENCES).edit().putString("accepting_pending", UUID.randomUUID().toString()).apply() }

    fun hydrate(context: Context, done: (Boolean) -> Unit = {}) {
        val uid = DriverAuth.currentUser(context)?.uid ?: run { done(false); return }
        val epoch = epochs[uid] ?: 0
        FirebaseFirestore.getInstance(DriverAuth.firebaseApp(context)).collection("driverProfiles").document(uid)
            .get(Source.SERVER).addOnCompleteListener {
                if (DriverAuth.currentUser(context)?.uid != uid) { done(false); return@addOnCompleteListener }
                if (it.isSuccessful) {
                    if ((epochs[uid] ?: 0) == epoch) cache(context, it.result.get("matching") as? Map<*, *> ?: emptyMap<Any, Any>())
                    if (it.result.exists() && it.result.getBoolean("availabilityReady") != true) BusySchedule.refresh(context.applicationContext)
                }
                done(it.isSuccessful)
            }
    }
    private fun cache(c: Context, m: Map<*, *>) {
        val driving = prefs(c, "driver_driving_info")
        if (!driving.contains("cloud_pending")) driving.edit().apply {
            putBoolean("cloud_confirmed", com.example.hiremydriver.DrivingInformation.complete(m))
            putString("experience", m["experience"] as? String ?: "")
            putString("rate", (m["rate"] as? Number)?.toLong()?.toString() ?: "")
            putString("area", (m["areas"] as? List<*>)?.filterIsInstance<String>()?.joinToString(", ") ?: "")
            putStringSet("languages", (m["languages"] as? List<*>)?.filterIsInstance<String>()?.toSet() ?: emptySet())
            val selected = ((m["vehicles"] as? List<*>).orEmpty() + (m["transmissions"] as? List<*>).orEmpty()).toSet()
            listOf("Sedan", "SUV", "Van", "Pickup", "Truck", "Automatic", "Manual").forEach {
                putBoolean(if (it == "SUV") "chkSuv" else "chk$it", it in selected)
            }
        }.apply()
        val schedule = prefs(c, DriverScheduleStore.PREFERENCES)
        schedule.edit().apply {
            if (!schedule.contains("accepting_pending")) putBoolean("accepting_bookings", m["accepting"] == true && com.example.hiremydriver.DrivingInformation.complete(m))
            if (!schedule.contains("cloud_pending")) com.example.hiremydriver.DriverSchedule.decode(m["weekly"]).forEachIndexed { i, day ->
                putBoolean("day_" + i + "_enabled", day.enabled)
                putInt("day_" + i + "_start", day.start)
                putInt("day_" + i + "_end", day.end)
                putBoolean("day_" + i + "_configured", day.configured)
            }
        }.apply()
    }
    fun drivingComplete(c: Context): Boolean {
        val p = prefs(c, "driver_driving_info")
        return p.getBoolean("cloud_confirmed", false) && !p.contains("cloud_pending") &&
            com.example.hiremydriver.DrivingInformation.complete(mapOf(
                "experience" to p.getString("experience", ""), "rate" to p.getString("rate", "")?.toLongOrNull(),
                "languages" to p.getStringSet("languages", emptySet()).orEmpty().toList(),
                "areas" to p.getString("area", "").orEmpty().split(',').map(String::trim).filter(String::isNotBlank),
                "vehicles" to com.example.hiremydriver.DrivingInformation.vehicles.filter { p.getBoolean(if (it == "SUV") "chkSuv" else "chk$it", false) },
                "transmissions" to com.example.hiremydriver.DrivingInformation.transmissions.filter { p.getBoolean("chk$it", false) }))
    }
    fun sync(context: Context, done: ((Boolean) -> Unit)? = null) {
        val c = context.applicationContext
        val uid = DriverAuth.currentUser(c)?.uid ?: run { done?.invoke(false); return }
        done?.let { waiters.getOrPut(uid) { mutableListOf() }.add(it) }
        if (!busy.add(uid)) return
        val driving = prefs(c, "driver_driving_info")
        val schedule = prefs(c, DriverScheduleStore.PREFERENCES)
        val dr = driving.getString("cloud_pending", null)
        val sr = schedule.getString("cloud_pending", null)
        val ar = schedule.getString("accepting_pending", null)
        fun finish(ok: Boolean) { busy.remove(uid); waiters.remove(uid)?.forEach { it(ok) } }
        if (dr == null && sr == null && ar == null) {
            hydrate(c) { loaded ->
                // An edit may arrive while the initial server read is in flight.
                if (driving.contains("cloud_pending") || schedule.contains("cloud_pending") || schedule.contains("accepting_pending")) {
                    busy.remove(uid); sync(c)
                } else finish(loaded)
            }
            return
        }
        val edits = mutableMapOf<String, Any?>()
        if (dr != null) {
            edits["areas"] = driving.getString("area", "").orEmpty().split(',').map(String::trim).filter { it.isNotEmpty() && it != "Select service areas" }.distinct()
            edits["experience"] = driving.getString("experience", "").orEmpty().trim()
            edits["languages"] = com.example.hiremydriver.DrivingInformation.languages.filter { it in driving.getStringSet("languages", emptySet()).orEmpty() }
            edits["rate"] = driving.getString("rate", "")?.toLongOrNull()
            edits["vehicles"] = listOf("Sedan", "SUV", "Van", "Pickup", "Truck").filter { driving.getBoolean(if (it == "SUV") "chkSuv" else "chk$it", false) }
            edits["transmissions"] = listOf("Automatic", "Manual").filter { driving.getBoolean("chk$it", false) }
        }
        if (sr != null) edits["weekly"] = DriverScheduleStore(c).load().map { mapOf("enabled" to it.enabled, "start" to it.start, "end" to it.end, "configured" to it.configured) }
        if (ar != null) edits["accepting"] = schedule.getBoolean("accepting_bookings", false)
        val db = FirebaseFirestore.getInstance(DriverAuth.firebaseApp(c))
        val ref = db.collection("driverProfiles").document(uid)
        db.runTransaction { tx ->
            check(DriverAuth.currentUser(c)?.uid == uid)
            val doc = tx.get(ref)
            check(doc.exists()) { "Driver profile is not available yet" }
            val merged = mutableMapOf<String, Any?>("areas" to emptyList<String>(), "vehicles" to emptyList<String>(),
                "transmissions" to emptyList<String>(), "rate" to null, "accepting" to false, "weekly" to emptyList<Any>())
            (doc.get("matching") as? Map<*, *>)?.forEach { (k, v) -> if (k is String) merged[k] = v }
            merged.putAll(edits)
            if (doc.getString("status") != "approved" || !com.example.hiremydriver.DrivingInformation.complete(merged)) {
                check(edits["accepting"] != true) { "Complete Driving Information before going online" }
                merged["accepting"] = false
            }
            val update = mutableMapOf<String, Any?>("matching" to merged)
            if (!doc.contains("busySlots")) { update["busySlots"] = emptyMap<String, Any>(); update["availabilityReady"] = false }
            tx.update(ref, update)
            merged
        }.addOnCompleteListener { task ->
            if (DriverAuth.currentUser(c)?.uid != uid) { finish(false); return@addOnCompleteListener }
            if (!task.isSuccessful) { Log.w("DriverSettings", "Server save failed", task.exception); finish(false); return@addOnCompleteListener }
            fun clear(p: SharedPreferences, key: String, revision: String?) {
                if (revision != null && p.getString(key, null) == revision) p.edit().remove(key).apply()
            }
            clear(driving, "cloud_pending", dr); clear(schedule, "cloud_pending", sr); clear(schedule, "accepting_pending", ar)
            cache(c, task.result)
            // A separate availability refresh must not turn an acknowledged information save into a failure.
            BusySchedule.refresh(c)
            if (driving.contains("cloud_pending") || schedule.contains("cloud_pending") || schedule.contains("accepting_pending")) {
                busy.remove(uid); sync(c)
            } else finish(true)
        }
    }
}
