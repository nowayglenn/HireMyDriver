package com.example.hiremydriver

import com.example.hiremydriver.recommendation.*
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class DriverRecommendationEngineTest {
    private val now = 1_000_000L
    private val point = Coordinates(0.0, 0.0)
    private val request = RecommendationRequest(point, TimeWindow(now + 10_000, now + 20_000), "SUV", "Automatic")
    private fun driver(id: String = "a", location: Coordinates? = point) = DriverCandidate(
        id, "Driver $id", true, true, setOf("SUV"), setOf("Automatic"), location, now,
        listOf(TimeWindow(now, now + 100_000)), rating = 4.8, completedTrips = 10)

    @Test fun homeAndSeeAllRankNearestBeforeRatingWithoutInventingTripFilters() {
        val candidates = listOf(
            driver("far", Coordinates(0.0, 0.1)).copy(serviceAreas = setOf("Bohol"), rating = 5.0),
            driver("near", Coordinates(0.0, 0.01)).copy(serviceAreas = setOf("Bohol"), rating = null, availability = emptyList()))
        val matches = DriverRecommendationEngine.rankNearby(point, "Bohol", candidates, now)
        assertEquals(listOf("near", "far"), matches.map { it.driver.id })
        assertEquals(1.112, matches.first().distanceKm, 0.001)
        // A specific trip still requires a schedule; Home does not claim one was selected.
        assertTrue(DriverRecommendationEngine.rank(request, listOf(candidates[1]), now).isEmpty())
    }

    @Test fun nearbyBrowsingExcludesOtherAreasUnavailableAndStaleDrivers() {
        val base = driver().copy(serviceAreas = setOf("Bohol"))
        val candidates = listOf(base,
            base.copy(id = "cebu", serviceAreas = setOf("Cebu")),
            base.copy(id = "pending", approved = false),
            base.copy(id = "offline", acceptingBookings = false),
            base.copy(id = "stale", locationAt = now - DriverRecommendationEngine.LOCATION_MAX_AGE_MS - 1),
            base.copy(id = "missing", location = null),
            base.copy(id = "far", location = Coordinates(0.0, 1.0)))
        assertEquals(listOf("a"), DriverRecommendationEngine.rankNearby(point, "Bohol", candidates, now).map { it.driver.id })
        assertTrue(DriverRecommendationEngine.rankNearby(point, "Bohol", emptyList(), now).isEmpty())
        assertThrows(IllegalArgumentException::class.java) { DriverRecommendationEngine.rankNearby(point, "", candidates, now) }
    }

    @Test fun movingBetweenAreasChangesNearbyDemoResults() {
        val candidates = DemoDriverSource.drivers(now, now)
        val bohol = DriverRecommendationEngine.rankNearby(Coordinates(9.688, 123.974), "Bohol", candidates, now)
        val cebu = DriverRecommendationEngine.rankNearby(Coordinates(10.3157, 123.8854), "Cebu", candidates, now)
        assertEquals("A", bohol.first().driver.id)
        assertEquals("I", cebu.first().driver.id)
        assertTrue(bohol.all { "Bohol" in it.driver.serviceAreas })
        assertTrue(cebu.all { "Cebu" in it.driver.serviceAreas })
    }

    @Test fun haversineKnownDistancesAndAntimeridian() {
        assertEquals(0.0, DriverRecommendationEngine.distanceKm(point, point), 0.000001)
        assertEquals(111.195, DriverRecommendationEngine.distanceKm(point, Coordinates(0.0, 1.0)), 0.001)
        assertEquals(222.390, DriverRecommendationEngine.distanceKm(Coordinates(0.0, 179.0), Coordinates(0.0, -179.0)), 0.002)
    }

    @Test fun filtersUnapprovedUnavailableAndIncompatible() {
        val candidates = listOf(driver("approved"), driver("pending").copy(approved = false),
            driver("off").copy(acceptingBookings = false), driver("van").copy(vehicles = setOf("Van")),
            driver("manual").copy(transmissions = setOf("Manual")))
        assertEquals(listOf("approved"), DriverRecommendationEngine.rank(request, candidates, now).map { it.driver.id })
    }

    @Test fun entireTripMustFitIncludingOvernight() {
        assertTrue(DriverRecommendationEngine.rank(request, listOf(driver().copy(
            availability = listOf(TimeWindow(now, request.trip.end - 1)))), now).isEmpty())
        // Absolute windows can span midnight; the engine does not silently truncate the trip.
        val overnight = request.copy(trip = TimeWindow(now + 86_000_000, now + 90_000_000))
        assertEquals(1, DriverRecommendationEngine.rank(overnight, listOf(driver().copy(
            availability = listOf(TimeWindow(now, now + 100_000_000)))), now).size)
    }

    @Test fun adjacentBookingsAllowedButEveryOverlapRejected() {
        val adjacent = driver().copy(bookings = listOf(TimeWindow(now, request.trip.start),
            TimeWindow(request.trip.end, request.trip.end + 5000)))
        assertEquals(1, DriverRecommendationEngine.rank(request, listOf(adjacent), now).size)
        val conflicts = listOf(TimeWindow(now, request.trip.start + 1),
            TimeWindow(request.trip.start + 1, request.trip.end - 1),
            TimeWindow(request.trip.end - 1, request.trip.end + 1), request.trip)
        conflicts.forEach { busy ->
            assertTrue(DriverRecommendationEngine.rank(request, listOf(driver().copy(bookings = listOf(busy))), now).isEmpty())
        }
    }

    @Test fun missingStaleAndFutureLocationsAreExcluded() {
        val candidates = listOf(driver("missing", null), driver("noTime").copy(locationAt = null),
            driver("stale").copy(locationAt = now - DriverRecommendationEngine.LOCATION_MAX_AGE_MS - 1),
            driver("future").copy(locationAt = now + 1),
            driver("boundary").copy(locationAt = now - DriverRecommendationEngine.LOCATION_MAX_AGE_MS))
        assertEquals(listOf("boundary"), DriverRecommendationEngine.rank(request, candidates, now).map { it.driver.id })
    }

    @Test fun nearestFirstWithRatingTripsAndIdForExactTies() {
        val candidates = listOf(driver("far", Coordinates(0.0, 0.1)).copy(rating = 5.0),
            driver("new").copy(rating = null, completedTrips = 0),
            driver("z").copy(rating = 4.9, completedTrips = 20),
            driver("a").copy(rating = 4.9, completedTrips = 20),
            driver("lessTrips").copy(rating = 4.9, completedTrips = 2), driver("lessRating"))
        assertEquals(listOf("a", "z", "lessTrips", "lessRating", "new", "far"),
            DriverRecommendationEngine.rank(request, candidates, now).map { it.driver.id })
    }

    @Test fun radiusAndNoMatches() {
        assertTrue(DriverRecommendationEngine.rank(request, listOf(driver(location = Coordinates(0.0, 1.0))), now).isEmpty())
        assertTrue(DriverRecommendationEngine.rank(request, emptyList(), now).isEmpty())
        assertTrue(DriverRecommendationEngine.rank(request.copy(trip = TimeWindow(now - 1, now + 1)), listOf(driver()), now).isEmpty())
    }

    @Test fun anySupportsNewDriversButDoesNotInventMissingSkills() {
        val any = request.copy(vehicle = "Any", transmission = "Any")
        val candidates = listOf(driver("new").copy(rating = null), driver("unknown").copy(vehicles = emptySet()))
        assertEquals(listOf("new"), DriverRecommendationEngine.rank(any, candidates, now).map { it.driver.id })
        assertEquals(1, DriverRecommendationEngine.rank(request.copy(vehicle = "suv", transmission = "automatic"), listOf(driver()), now).size)
    }

    @Test fun rejectsInvalidCoordinates() {
        listOf(Double.NaN, Double.POSITIVE_INFINITY, 91.0).forEach { invalid ->
            assertThrows(IllegalArgumentException::class.java) { Coordinates(invalid, 0.0) }
        }
        assertThrows(IllegalArgumentException::class.java) { Coordinates(0.0, 181.0) }
    }

    @Test fun serviceAreaFilterExcludesNearerDriverAcrossAnIslandBoundary() {
        val candidates = listOf(driver("other", point).copy(serviceAreas = setOf("Cebu")),
            driver("local", Coordinates(0.0, 0.1)).copy(serviceAreas = setOf("Bohol")))
        assertEquals(listOf("local"), DriverRecommendationEngine.rank(request.copy(serviceArea = "Bohol"), candidates, now).map { it.driver.id })
    }

    @Test fun currentAreaFilteringStillHonorsVehicleTransmissionAndSchedule() {
        val candidates = listOf(driver("match").copy(serviceAreas = setOf("Bohol")),
            driver("wrongVehicle").copy(serviceAreas = setOf("Bohol"), vehicles = setOf("Van")),
            driver("manual").copy(serviceAreas = setOf("Bohol"), transmissions = setOf("Manual")),
            driver("busy").copy(serviceAreas = setOf("Bohol"), bookings = listOf(request.trip)),
            driver("cebu").copy(serviceAreas = setOf("Cebu")))
        assertEquals(listOf("match"), DriverRecommendationEngine.rank(request.copy(serviceArea = "Bohol"), candidates, now).map { it.driver.id })
    }

    @Test fun resolvesGeocoderAreasWithoutDestinationAllowList() {
        assertEquals("Bohol", RecommendationArea.fromAddress("PH", listOf("Central Visayas", "Bohol", "Sikatuna")))
        assertEquals("Cebu", RecommendationArea.fromAddress("PH", listOf("Central Visayas", null, "Lapu-Lapu City")))
        assertEquals("Negros Oriental", RecommendationArea.fromAddress("PH", listOf("Central Visayas", "Negros Oriental", "Dumaguete")))
        assertEquals("Metro Manila", RecommendationArea.fromAddress("PH", listOf("Metro Manila", null, "Manila")))
        assertEquals("Davao del Sur", RecommendationArea.fromAddress("PH", listOf("Davao Region", "Davao del Sur", "Digos")))
        assertEquals("California", RecommendationArea.fromAddress("US", listOf("California", null, "Los Angeles")))
        assertEquals("Singapore", RecommendationArea.fromAddress("SG", listOf(null, null, "Singapore")))
        assertEquals("New Area", RecommendationArea.fromAddress("PH", listOf(null, "  New Area  ", null)))
        assertNull(RecommendationArea.fromAddress("PH", listOf(null, " ", null)))
        assertNull(RecommendationArea.fromAddress(null, listOf("Unknown")))
    }

    @Test fun arbitraryDetectedAreaSupportsNearbyAndFilteredRanking() {
        val origin = Coordinates(7.073, 125.612)
        val area = RecommendationArea.fromAddress("PH", listOf("Davao Region", "Davao del Sur", "Davao City"))!!
        val candidates = listOf(
            driver("far", Coordinates(7.09, 125.62)).copy(serviceAreas = setOf(area)),
            driver("near", Coordinates(7.074, 125.612)).copy(serviceAreas = setOf(area)),
            driver("other-area", origin).copy(serviceAreas = setOf("Bohol")))
        assertEquals(listOf("near", "far"), DriverRecommendationEngine.rankNearby(origin, area, candidates, now).map { it.driver.id })
        val trip = request.copy(pickup = origin, serviceArea = area)
        assertEquals(listOf("near", "far"), DriverRecommendationEngine.rank(trip, candidates, now).map { it.driver.id })
        assertTrue(DriverRecommendationEngine.rank(trip.copy(vehicle = "Van"), candidates, now).isEmpty())
    }

    @Test fun locationWithoutNearbyDemoDriversDoesNotFallBackToAnotherProvince() {
        val origin = Coordinates(14.5995, 120.9842)
        val area = RecommendationArea.fromAddress("PH", listOf("Metro Manila", null, "Manila"))!!
        assertTrue(DriverRecommendationEngine.rankNearby(origin, area, DemoDriverSource.drivers(now, now), now).isEmpty())
    }

    @Test fun rejectsOldFutureAndVeryInaccurateCustomerFixes() {
        assertTrue(RecommendationArea.isFreshFix(120_000, 5000f))
        assertFalse(RecommendationArea.isFreshFix(120_001, 10f))
        assertFalse(RecommendationArea.isFreshFix(-1, 10f))
        assertFalse(RecommendationArea.isFreshFix(0, 5001f))
        assertFalse(RecommendationArea.isFreshFix(0, Float.NaN))
    }

    @Test fun cebuDemoSearchNeverReturnsBoholDrivers() {
        val start = Calendar.getInstance(DemoDriverSource.zone).apply {
            set(2030, Calendar.JANUARY, 7, 9, 0, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val snapshot = start - 3_600_000
        val cebu = RecommendationRequest(Coordinates(10.3157, 123.8854),
            TimeWindow(start, start + 3_600_000), "Any", "Any", serviceArea = "Cebu")
        val matches = DriverRecommendationEngine.rank(cebu, DemoDriverSource.drivers(start, snapshot), snapshot)
        assertEquals("I", matches.first().driver.id)
        assertEquals(setOf("I", "J", "K"), matches.map { it.driver.id }.toSet())
    }

    @Test fun demoInputsActuallyChangeRankingAndAvailability() {
        val start = Calendar.getInstance(DemoDriverSource.zone).apply {
            set(2030, Calendar.JANUARY, 7, 9, 0, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val snapshot = start - 3_600_000
        val candidates = DemoDriverSource.drivers(start, snapshot)
        val sikatuna = RecommendationRequest(DemoDriverSource.pickups[0].second,
            TimeWindow(start, start + 3_600_000), "Any", "Any")
        val ranks = DriverRecommendationEngine.rank(sikatuna, candidates, snapshot)
        assertEquals("A", ranks.first().driver.id)
        assertFalse(ranks.any { it.driver.id in setOf("F", "G", "H") })
        assertEquals("C", DriverRecommendationEngine.rank(sikatuna.copy(pickup = DemoDriverSource.pickups[1].second), candidates, snapshot).first().driver.id)
        assertTrue(DriverRecommendationEngine.rank(sikatuna.copy(vehicle = "Luxury"), candidates, snapshot).isEmpty())
        assertFalse(DriverRecommendationEngine.rank(sikatuna.copy(trip = TimeWindow(start, start + 4 * 3_600_000L)), candidates, snapshot).any { it.driver.id == "B" })
        assertTrue(DriverRecommendationEngine.rank(sikatuna.copy(trip = TimeWindow(start, start + 24 * 3_600_000L)), candidates, snapshot).isEmpty())
    }
}
