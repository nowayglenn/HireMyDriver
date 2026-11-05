package com.example.hiremydriver.recommendation

import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

object RegisteredSchedule {
    private val active = setOf("ACCEPTED", "ON_THE_WAY", "ARRIVED", "IN_PROGRESS")
    /** Home discovery has no requested trip time. Booking still validates the chosen slot. */
    fun discoverable(data: Map<String, Any?>, area: String): Boolean {
        if (data["status"] != "approved") return false
        val matching = data["matching"] as? Map<*, *> ?: return false
        if (matching["accepting"] != true || !com.example.hiremydriver.DrivingInformation.complete(matching)) return false
        return (matching["areas"] as? List<*>)?.filterIsInstance<String>()?.any {
            com.example.hiremydriver.ServiceAreaNames.matches(it, area)
        } == true
    }
    fun matches(data: Map<String, Any?>, area: String, vehicle: String, transmission: String,
        start: Long, end: Long, now: Long): Boolean {
        if (data["availabilityReady"] != true || data["status"] != "approved" || end <= start) return false
        val m = data["matching"] as? Map<*, *> ?: return false
        if (!com.example.hiremydriver.DrivingInformation.complete(m)) return false
        if (m["accepting"] != true) return false
        fun supports(key: String, wanted: String): Boolean {
            val values = (m[key] as? List<*>)?.filterIsInstance<String>().orEmpty()
            return values.isNotEmpty() && ((key != "areas" && wanted == "Any") || values.any {
                if (key == "areas") com.example.hiremydriver.ServiceAreaNames.matches(it, wanted)
                else it.trim().equals(wanted.trim(), true)
            })
        }
        if (!supports("areas", area) || !supports("vehicles", vehicle) || !supports("transmissions", transmission)) return false
        val days = m["weekly"] as? List<*> ?: return false
        if (days.size != 7) return false
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Manila")).apply { timeInMillis = start }
        val day = days[(calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7] as? Map<*, *> ?: return false
        val from = (day["start"] as? Number)?.toLong() ?: return false
        val until = (day["end"] as? Number)?.toLong() ?: return false
        if (day["configured"] == false || day["enabled"] != true || from !in 0..1439 || until !in 1..1439 || from >= until) return false
        calendar.set(Calendar.HOUR_OF_DAY, 0); calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0); calendar.set(Calendar.MILLISECOND, 0)
        if (start < calendar.timeInMillis + from * 60000 || end > calendar.timeInMillis + until * 60000) return false
        val slots = data["busySlots"] as? Map<*, *> ?: return false
        return slots.values.none { conflicts(it as? Map<*, *>, start, end, now) }
    }
    fun conflicts(slot: Map<*, *>?, start: Long, end: Long, now: Long): Boolean {
        if (slot == null || slot["phase"] !in active) return true
        val whenBooked = bookingStart(slot) ?: return true
        val hours = (slot["hours"] as? Number)?.toLong() ?: return true
        if (hours !in 1..24) return true
        val actual = if (slot["phase"] == "IN_PROGRESS") (slot["started"] as? Number)?.toLong()?.takeIf { it > 0 } ?: return true else whenBooked
        val until = maxOf(whenBooked, actual) + hours * 3600000L + ((slot["extensionMinutes"] as? Number)?.toLong() ?: 0) * 60000L
        if (slot["phase"] == "IN_PROGRESS" && until < now) return true
        return minOf(whenBooked, actual) < end && start < until
    }

    fun canAccept(booking: Map<*, *>, slots: Map<*, *>, bookingId: String, now: Long): Boolean {
        val start = bookingStart(booking) ?: return false
        val hours = (booking["hours"] as? Number)?.toLong()?.takeIf { it in 1..24 } ?: return false
        return slots.entries.filter { it.key != bookingId }
            .none { conflicts(it.value as? Map<*, *>, start, start + hours * 3600000L, now) }
    }

    fun withinHours(data: Map<String, Any?>, start: Long, end: Long): Boolean {
        if (end <= start) return false
        val window = workingWindow(data, start) ?: return false
        return start >= window.first && end <= window.second
    }

    /** Each driver's saved weekday settings, interpreted in Philippine time. */
    fun workingWindow(data: Map<String, Any?>, date: Long): Pair<Long, Long>? {
        val days = (data["matching"] as? Map<*, *>)?.get("weekly") as? List<*> ?: return null
        if (days.size != 7) return null
        val c = Calendar.getInstance(TimeZone.getTimeZone("Asia/Manila")).apply { timeInMillis = date }
        val day = days[(c.get(Calendar.DAY_OF_WEEK) + 5) % 7] as? Map<*, *> ?: return null
        val from = (day["start"] as? Number)?.toLong() ?: return null
        val until = (day["end"] as? Number)?.toLong() ?: return null
        if (day["configured"] == false || day["enabled"] != true || from !in 0..1439 || until !in 1..1439 || from >= until) return null
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        return (c.timeInMillis + from * 60000) to (c.timeInMillis + until * 60000)
    }

    fun bookingStart(booking: Map<*, *>): Long? {
        val raw = "${booking["date"]} ${booking["time"]}"
        val parser = SimpleDateFormat("MMM d, yyyy h:mm a", Locale.ENGLISH).apply {
            isLenient = false; timeZone = TimeZone.getTimeZone("Asia/Manila")
        }
        val position = ParsePosition(0)
        val parsed = parser.parse(raw, position)?.time ?: return null
        return parsed.takeIf { position.index == raw.length }
    }
}
