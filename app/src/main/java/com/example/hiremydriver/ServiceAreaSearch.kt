package com.example.hiremydriver

/** Search the public directory; booking eligibility is checked when requesting a driver. */
object ServiceAreaSearch {
    fun matches(data: Map<String, Any?>, query: String, now: Long): Boolean {
        if (query.isBlank() || data["status"] != "approved") return false
        val matching = data["matching"] as? Map<*, *>
        val areas = (matching?.get("areas") as? List<*>)?.filterIsInstance<String>().orEmpty()
        val nameMatches = ServiceAreaNames.normalize(data["fullName"] as? String ?: "")
            .contains(ServiceAreaNames.normalize(query).takeIf { it.isNotBlank() } ?: return false)
        return nameMatches || areas.any { area -> ServiceAreaNames.contains(area, query) }
    }
}
