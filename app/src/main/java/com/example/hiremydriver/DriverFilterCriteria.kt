package com.example.hiremydriver

data class DriverFilterCriteria(
    val experience: Int = 0, val vehicle: String = "All", val area: String = "All Areas",
    val day: Int = 0, val minimumRating: Int = 0
) {
    val active get() = experience != 0 || vehicle != "All" || area != "All Areas" || day != 0 || minimumRating != 0
    fun matches(data: Map<String, Any?>, rating: Double?): Boolean {
        if (data["status"] != "approved") return false
        val m = data["matching"] as? Map<*, *> ?: return false
        if (m["accepting"] != true || !DrivingInformation.complete(m)) return false
        val years = (m["experience"] ?: data["experience"])?.toString()?.toDoubleOrNull() ?: return false
        if (experience == 1 && years !in 1.0..3.0 || experience == 2 && years !in 3.0..5.0 || experience == 3 && years < 5) return false
        if (vehicle != "All" && (m["vehicles"] as? List<*>)?.contains(vehicle) != true) return false
        if (area != "All Areas" && (m["areas"] as? List<*>)?.filterIsInstance<String>()?.any { ServiceAreaNames.matches(it, area) } != true) return false
        if (day != 0) {
            val working = (m["weekly"] as? List<*>)?.getOrNull(day - 1) as? Map<*, *> ?: return false
            val from = (working["start"] as? Number)?.toInt() ?: return false
            val until = (working["end"] as? Number)?.toInt() ?: return false
            if (working["configured"] == false || working["enabled"] != true || from !in 0..1439 || until !in 1..1439 || from >= until) return false
        }
        return minimumRating == 0 || (rating != null && rating >= minimumRating)
    }
}
