package com.example.hiremydriver.recommendation

import android.content.Context
import com.example.hiremydriver.CustomerAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source

/** Firestore-only matching. Public schedules contain no customer identities or trip locations. */
object FirebaseMatching {

    private fun extractDriverCoordinates(docData: Map<String, Any?>, matchingMap: Map<*, *>): Coordinates? {
        val locMap = docData["savedLocation"] as? Map<*, *> ?: docData["location"] as? Map<*, *> ?: matchingMap["location"] as? Map<*, *>
        if (locMap != null) {
            val lat = (locMap["latitude"] as? Number)?.toDouble() ?: (locMap["lat"] as? Number)?.toDouble()
            val lng = (locMap["longitude"] as? Number)?.toDouble() ?: (locMap["lng"] as? Number)?.toDouble()
            if (lat != null && lng != null && lat in -90.0..90.0 && lng in -180.0..180.0) {
                return Coordinates(lat, lng)
            }
        }
        val rootLat = (docData["latitude"] as? Number)?.toDouble()
        val rootLng = (docData["longitude"] as? Number)?.toDouble()
        if (rootLat != null && rootLng != null && rootLat in -90.0..90.0 && rootLng in -180.0..180.0) {
            return Coordinates(rootLat, rootLng)
        }
        return null
    }

    fun search(
        context: Context,
        area: String,
        start: Long? = null,
        duration: Int = 1,
        vehicle: String = "Any",
        transmission: String = "Any",
        originCoords: Coordinates? = null,
        watch: Boolean = false,
        browsing: Boolean = false,
        done: (List<DriverMatch>, String?) -> Unit
    ): com.google.firebase.firestore.ListenerRegistration? {
        val uid = CustomerAuth.currentUser(context)?.uid ?: run { done(emptyList(), "Please sign in again."); return null }
        val now = System.currentTimeMillis()
        val from = start ?: now
        if (duration !in 1..24 || from < now - 60000 || from > now + 366L * 86400000) {
            done(emptyList(), "Please choose a future trip time."); return null
        }

        val customerCoords = originCoords
        val query = FirebaseFirestore.getInstance().collection("driverProfiles").whereEqualTo("status", "approved")

        fun receive(snapshot: com.google.firebase.firestore.QuerySnapshot?, error: Exception?) {
            if (CustomerAuth.currentUser(context)?.uid != uid) return
            if (error != null || snapshot == null) {
                done(emptyList(), "Could not check driver availability. Please check your connection and try again.")
                return
            }
            // An empty local cache is not an authoritative empty directory after tab navigation.
            if (snapshot.isEmpty && snapshot.metadata.isFromCache) return
            val currentNow = System.currentTimeMillis()
            val requestedStart = start ?: currentNow

            val matches = snapshot.documents.mapNotNull { doc ->
                val data = doc.data ?: return@mapNotNull null
                val eligible = if (browsing) RegisteredSchedule.discoverable(data, area)
                    else RegisteredSchedule.matches(data, area, vehicle, transmission, requestedStart,
                        requestedStart + duration * 3600000L, currentNow)
                if (!eligible) return@mapNotNull null

                val m = data["matching"] as? Map<*, *> ?: return@mapNotNull null
                fun strings(key: String) = (m[key] as? List<*>)?.filterIsInstance<String>()?.toSet().orEmpty()
                val name = data["fullName"] as? String ?: return@mapNotNull null
                if (name.isBlank()) return@mapNotNull null

                val rate = (m["rate"] as? Number)?.toInt()
                    ?: (data["rate"] as? Number)?.toInt()

                val driverCoords = extractDriverCoordinates(data, m)
                val distKm = if (customerCoords != null && driverCoords != null) {
                    DriverRecommendationEngine.distanceKm(customerCoords, driverCoords)
                } else {
                    Double.POSITIVE_INFINITY
                }

                DriverMatch(
                    DriverCandidate(
                        id = doc.id,
                        name = name,
                        approved = data["status"] == "approved",
                        acceptingBookings = true,
                        vehicles = strings("vehicles"),
                        transmissions = strings("transmissions"),
                        location = driverCoords,
                        locationAt = (data["savedLocation"] as? Map<*, *>)?.get("capturedAt") as? Long,
                        availability = emptyList(),
                        hourlyRate = rate?.coerceAtLeast(0) ?: 0,
                        serviceAreas = strings("areas"),
                        profileStatus = data["status"] as? String ?: "pending",
                        thumbnail = data["thumbnail"] as? String ?: "",
                        experience = (m["experience"] as? String)?.takeIf { it.isNotBlank() }
                            ?: data["experience"] as? String ?: "",
                        bio = data["bio"] as? String ?: "",
                        rateProvided = rate != null
                    ),
                    distanceKm = distKm
                )
            }.sortedWith(
                compareBy<DriverMatch> { it.distanceKm }
                    .thenBy { it.driver.name.lowercase() }
                    .thenBy { it.driver.id }
            )

            done(matches, null)
        }

        if (watch) {
            // Re-check passing schedule boundaries using the latest snapshot, without another database read.
            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            var latest: com.google.firebase.firestore.QuerySnapshot? = null
            val refresh = object : Runnable {
                override fun run() { latest?.let { receive(it, null) }; handler.postDelayed(this, 60000) }
            }
            val listener = query.addSnapshotListener(com.google.firebase.firestore.MetadataChanges.INCLUDE) { snapshot, error ->
                if (error == null) latest = snapshot else latest = null
                receive(snapshot, error)
            }
            handler.postDelayed(refresh, 60000)
            return com.google.firebase.firestore.ListenerRegistration {
                handler.removeCallbacks(refresh); latest = null; listener.remove()
            }
        }
        query.get(Source.SERVER).addOnCompleteListener { task ->
            receive(if (task.isSuccessful) task.result else null, task.exception)
        }
        return null
    }
}
