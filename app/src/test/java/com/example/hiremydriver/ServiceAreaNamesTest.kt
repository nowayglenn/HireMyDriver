package com.example.hiremydriver

import org.junit.Assert.*
import org.junit.Test

class ServiceAreaNamesTest {
    private val directory = ServiceAreaDirectory(listOf(
        ServiceArea("1", "Sikatuna", "b", "Bohol"),
        ServiceArea("2", "Balilihan", "b", "Bohol"),
        ServiceArea("3", "City of Tagbilaran", "b", "Bohol"),
        ServiceArea("4", "City of Cebu", "c", "Cebu"),
        ServiceArea("5", "Talisay", "c", "Cebu"),
        ServiceArea("6", "Talisay", "n", "Negros Occidental")
    ))

    @Test fun detectedProvinceProvidesAllItsPlacesOnly() {
        val province = directory.province("PH", "Central Visayas", "Province of Bohol", "Sikatuna")
        assertEquals(listOf("Balilihan", "City of Tagbilaran", "Sikatuna"), directory.suggestions(province, "").map { it.name })
        assertEquals("c", directory.province("PH", "Central Visayas", null, "Cebu City"))
        assertEquals(2, directory.suggestions("c", "").size)
    }

    @Test fun unknownOrAmbiguousLocationRequiresManualSearch() {
        assertNull(directory.province("US", "Bohol", null, null))
        assertNull(directory.province("PH", null, null, "Talisay"))
        assertTrue(directory.suggestions(null, "").isEmpty())
        assertEquals(2, directory.suggestions(null, "talisay").size)
        assertEquals("City of Tagbilaran", directory.suggestions(null, "Tagbilaran City").single().name)
    }

    @Test fun savedAreasMatchIndividuallyWithoutExpandingToNeighbouringTowns() {
        assertTrue(ServiceAreaNames.matches("Sikatuna (Bohol)", "sikatuna"))
        assertTrue(ServiceAreaNames.matches("City of Tagbilaran (Bohol)", "Tagbilaran City"))
        assertFalse(ServiceAreaNames.matches("Sikatuna (Bohol)", "Balilihan (Bohol)"))
        assertFalse(ServiceAreaNames.matches("Talisay (Cebu)", "Talisay (Negros Occidental)"))
    }

    @Test fun legacyProvinceAndCityLabelsRemainCompatible() {
        assertTrue(ServiceAreaNames.matches("Bohol", "Sikatuna (Bohol)"))
        assertTrue(ServiceAreaNames.matches("Sikatuna (Bohol)", "Bohol"))
        assertTrue(ServiceAreaNames.matches("Tagbilaran City", "City of Tagbilaran (Bohol)"))
        assertTrue(ServiceAreaNames.contains("City of Las Piñas (National Capital Region)", "Las Pinas"))
        assertFalse(ServiceAreaNames.contains("Talisay (Cebu)", "Talisay (Negros Occidental)"))
    }
}
