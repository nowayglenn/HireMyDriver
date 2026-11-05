package com.example.hiremydriver

import java.text.Normalizer
import java.util.Locale

/** Province-qualified labels avoid confusing municipalities with the same name. */
object ServiceAreaNames {
    fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)
        .replace(Regex("\\([^)]*\\)"), " ")
        .replace(Regex("\\b(city of|municipality of|province of)\\b"), " ")
        .replace(Regex("\\b(city|municipality|province)\\b"), " ")
        .replace(Regex("[^a-z0-9]+"), " ").trim().replace(Regex("\\s+"), " ")

    fun parent(value: String): String? = Regex("\\(([^)]+)\\)\\s*$").find(value)?.groupValues?.get(1)
    fun label(name: String, province: String): String = "$name ($province)"

    fun matches(saved: String, requested: String): Boolean {
        val a = normalize(saved); val b = normalize(requested)
        if (a.isEmpty() || b.isEmpty()) return false
        val ap = parent(saved)?.let(::normalize); val bp = parent(requested)?.let(::normalize)
        if (ap != null && bp != null && ap != bp) return false
        return a == b || (bp == null && ap == b) || (ap == null && bp == a)
    }

    fun contains(saved: String, query: String): Boolean {
        val needle = normalize(query)
        if (needle.isBlank()) return false
        val wantedParent = parent(query)?.let(::normalize)
        if (wantedParent != null && parent(saved)?.let(::normalize) != wantedParent) return false
        val municipalityAndProvince = listOfNotNull(normalize(saved), parent(saved)?.let(::normalize)).joinToString(" ")
        return municipalityAndProvince.contains(needle) ||
            (wantedParent == null && parent(saved)?.let(::normalize)?.contains(needle) == true)
    }
}

data class ServiceArea(val code: String, val name: String, val provinceCode: String, val province: String) {
    val label get() = ServiceAreaNames.label(name, province)
}

class ServiceAreaDirectory(val areas: List<ServiceArea>) {
    /** Geocoder province first; unique city is a fallback for independent cities. Never guess an ambiguous place. */
    fun province(country: String?, admin: String?, subAdmin: String?, locality: String?): String? {
        if (!country.equals("PH", true)) return null
        val groups = areas.distinctBy { it.provinceCode }
        for (field in listOf(subAdmin, admin)) {
            val name = field?.let(ServiceAreaNames::normalize)?.takeIf { it.isNotEmpty() } ?: continue
            groups.singleOrNull { ServiceAreaNames.normalize(it.province) == name }?.let { return it.provinceCode }
        }
        val city = locality?.let(ServiceAreaNames::normalize)?.takeIf { it.isNotEmpty() } ?: return null
        return areas.filter { ServiceAreaNames.normalize(it.name) == city }
            .map { it.provinceCode }.distinct().singleOrNull()
    }

    fun suggestions(province: String?, query: String): List<ServiceArea> = areas.filter {
        if (query.isBlank()) province != null && it.provinceCode == province
        else ServiceAreaNames.contains(it.label, query)
    }.sortedWith(compareBy<ServiceArea> { it.name }.thenBy { it.province })
}
