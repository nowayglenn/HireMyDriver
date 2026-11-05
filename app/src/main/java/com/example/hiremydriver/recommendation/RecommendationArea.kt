package com.example.hiremydriver.recommendation

import java.util.Locale

/** Uses geocoder address fields, without a list of supported destinations. */
object RecommendationArea {
    /** names are admin area, sub-admin area (usually province), then locality. */
    fun fromAddress(country: String?, names: List<String?>): String? {
        if (country.isNullOrBlank()) return null
        val fields = names.map { it?.trim()?.takeIf(String::isNotEmpty) }
        // Compatibility aliases for the existing service areas, not an allow-list.
        if (country.equals("PH", ignoreCase = true)) {
            val normalized = fields.filterNotNull().map { it.lowercase(Locale.ROOT) }
            if (normalized.any { it in setOf("bohol", "province of bohol", "tagbilaran", "tagbilaran city", "city of tagbilaran", "corella", "panglao") }) return "Bohol"
            if (normalized.any { it in setOf("cebu", "province of cebu", "cebu city", "city of cebu", "mandaue", "mandaue city", "city of mandaue", "lapu-lapu", "lapu-lapu city", "city of lapu-lapu", "talisay", "consolacion") }) return "Cebu"
        }
        return fields.getOrNull(1) ?: fields.getOrNull(0) ?: fields.getOrNull(2)
    }

    fun isFreshFix(ageMs: Long, accuracyMeters: Float): Boolean =
        ageMs in 0..120_000 && accuracyMeters.isFinite() && accuracyMeters in 0f..5_000f

}
