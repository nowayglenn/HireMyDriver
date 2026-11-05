package com.example.hiremydriver

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

/** Reads public driver projection and matching settings real-time. */
data class PublicDriverInfo(
    val name: String,
    val experience: String,
    val bio: String,
    val status: String,
    val thumbnail: String,
    val rate: Long?,
    val vehicles: List<String>,
    val transmissions: List<String>,
    val areas: List<String>,
    val scheduleLabel: String = "Schedule not set",
    val scheduleDays: List<Pair<String, String>> = emptyList(),
    val languages: List<String> = emptyList(), val address: String = "", val scheduleData: Map<String, Any?> = emptyMap()
) {
    val price: String get() = rate?.let { "₱%,d/hr".format(it) } ?: "Rate not set"
    val vehicleLabel: String get() = (vehicles + transmissions).joinToString(" · ").ifBlank { "Not provided" }
    val canRequestBooking: Boolean get() {
        val matching = scheduleData["matching"] as? Map<*, *> ?: return false
        return status == "approved" && matching["accepting"] == true && DrivingInformation.complete(matching)
    }

    companion object {
        fun from(data: Map<String, Any?>): PublicDriverInfo {
            val matching = data["matching"] as? Map<*, *> ?: emptyMap<Any, Any>()
            fun list(key: String) = (matching[key] as? List<*>)?.filterIsInstance<String>().orEmpty()

            val parsedRate = (matching["rate"] as? Number)?.toLong()
                ?: (matching["rate"] as? String)?.toLongOrNull()
                ?: (data["rate"] as? Number)?.toLong()
                ?: (data["rate"] as? String)?.toLongOrNull()

            val parsedExp = (matching["experience"] as? String)?.takeIf { it.isNotBlank() }
                ?: (data["experience"] as? String)?.takeIf { it.isNotBlank() }
                ?: ""

            val parsedBio = (matching["bio"] as? String)?.takeIf { it.isNotBlank() }
                ?: (data["bio"] as? String)
                ?: ""

            val parsedStatus = (data["status"] as? String)?.takeIf { it.isNotBlank() } ?: "pending"

            val parsedAreas = list("areas").ifEmpty {
                (data["areas"] as? List<*>)?.filterIsInstance<String>().orEmpty()
            }

            return PublicDriverInfo(
                scheduleData = data, address = data["address"] as? String ?: "",
                name = data["fullName"] as? String ?: "Driver",
                experience = parsedExp,
                bio = parsedBio,
                status = parsedStatus,
                thumbnail = data["thumbnail"] as? String ?: "",
                rate = parsedRate?.takeIf { it >= 0 },
                vehicles = list("vehicles"),
                transmissions = list("transmissions"),
                areas = parsedAreas,
                scheduleLabel = if (matching["accepting"] != true || !DrivingInformation.complete(matching)) "Not accepting bookings" else "Weekly schedule · Philippine time",
                scheduleDays = ScheduleDisplay.days(matching["weekly"]),
                languages = list("languages").filter { it in DrivingInformation.languages }.distinct()
            )
        }

        fun observe(activity: AppCompatActivity, uid: String, unavailable: () -> Unit = {}, changed: (PublicDriverInfo) -> Unit) {
            if (uid.isBlank()) return
            activity.lifecycle.addObserver(object : DefaultLifecycleObserver {
                var listener: ListenerRegistration? = null
                override fun onStart(owner: LifecycleOwner) {
                    listener = FirebaseFirestore.getInstance().collection("driverProfiles").document(uid)
                        .addSnapshotListener { doc, error ->
                            if (error == null && doc?.exists() == true) {
                                changed(from(doc.data.orEmpty()))
                            } else if (error?.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED ||
                                (error == null && doc != null && !doc.exists() && !doc.metadata.isFromCache)) {
                                unavailable()
                            }
                        }
                }
                override fun onStop(owner: LifecycleOwner) {
                    listener?.remove()
                    listener = null
                }
            })
        }
    }
}
