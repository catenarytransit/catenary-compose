package com.catenarymaps.catenary

import org.junit.Assert.*
import org.junit.Test

class NearbyDeparturesMemoryCacheTest {
    private val gps = 33.6405 to -117.8443
    private val pin = 34.0522 to -118.2437
    private val otherPin = 34.0523 to -118.2437
    private val nearbyResponse = NearbyResponseV3()
    private val pinnedResponse = NearbyResponseV3(local = listOf(
        RouteGroupV3(chateauId = "city", routeId = "route", routeType = 3, closestDistance = 50.0)
    ))

    @Test fun keepsGpsAndPinnedResultsSeparateAcrossNavigation() {
        val cache = NearbyDeparturesMemoryCache()
        cache.put(NearbyDeparturesLocationMode.CURRENT_LOCATION, gps, null, nearbyResponse, 1_000)
        cache.put(NearbyDeparturesLocationMode.PINNED_LOCATION, pin, null, pinnedResponse, 2_000)

        assertSame(nearbyResponse, cache.get(
            NearbyDeparturesLocationMode.CURRENT_LOCATION, gps, null, 3_000)?.response)
        assertSame(pinnedResponse, cache.get(
            NearbyDeparturesLocationMode.PINNED_LOCATION, pin, null, 3_000)?.response)
        assertNull(cache.get(NearbyDeparturesLocationMode.CURRENT_LOCATION, pin, null, 3_000))
        assertNull(cache.get(NearbyDeparturesLocationMode.PINNED_LOCATION, gps, null, 3_000))
    }

    @Test fun neverReusesPinnedResultsAtDifferentCoordinates() {
        val cache = NearbyDeparturesMemoryCache()
        cache.put(NearbyDeparturesLocationMode.PINNED_LOCATION, pin, null, pinnedResponse, 1_000)
        assertNull(cache.get(NearbyDeparturesLocationMode.PINNED_LOCATION, otherPin, null, 1_001))

        cache.put(NearbyDeparturesLocationMode.PINNED_LOCATION, otherPin, null, nearbyResponse, 1_002)
        assertSame(nearbyResponse, cache.get(
            NearbyDeparturesLocationMode.PINNED_LOCATION, otherPin, null, 1_003)?.response)
        assertNull(cache.get(NearbyDeparturesLocationMode.PINNED_LOCATION, pin, null, 1_003))
    }

    @Test fun toleratesSmallGpsDriftButNotMovingToAnotherNeighborhood() {
        val cache = NearbyDeparturesMemoryCache()
        cache.put(NearbyDeparturesLocationMode.CURRENT_LOCATION, gps, null, nearbyResponse, 1_000)
        assertSame(nearbyResponse, cache.get(
            NearbyDeparturesLocationMode.CURRENT_LOCATION,
            33.6406 to -117.8443, null, 1_500)?.response)
        assertNull(cache.get(NearbyDeparturesLocationMode.CURRENT_LOCATION,
            33.6605 to -117.8443, null, 1_500))
    }

    @Test fun expiresOldResultsAndDistinguishesSelectedDepartureTimes() {
        val cache = NearbyDeparturesMemoryCache(maxAgeMillis = 10_000)
        cache.put(NearbyDeparturesLocationMode.CURRENT_LOCATION, gps, null, nearbyResponse, 1_000)
        assertNull(cache.get(NearbyDeparturesLocationMode.CURRENT_LOCATION, gps, 1_700_000_000L, 2_000))
        assertNotNull(cache.get(NearbyDeparturesLocationMode.CURRENT_LOCATION, gps, null, 11_000))
        assertNull(cache.get(NearbyDeparturesLocationMode.CURRENT_LOCATION, gps, null, 11_001))
    }
}
