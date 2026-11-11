package com.catenarymaps.catenary

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

internal enum class NearbyDeparturesLocationMode {
    CURRENT_LOCATION,
    PINNED_LOCATION
}

internal data class CachedNearbyDepartures(
    val origin: Pair<Double, Double>,
    val departureTimeSec: Long?,
    val response: NearbyResponseV3,
    val fetchedAtMillis: Long
)

/**
 * Keeps completed nearby responses across screen changes without retaining the Compose screen.
 * Only the most recent GPS and pinned responses are held, keeping memory use bounded.
 */
internal class NearbyDeparturesMemoryCache(
    private val maxAgeMillis: Long = 120_000L,
    private val gpsReuseRadiusMeters: Double = 150.0
) {
    private var currentLocation: CachedNearbyDepartures? = null
    private var pinnedLocation: CachedNearbyDepartures? = null

    @Synchronized
    fun get(
        mode: NearbyDeparturesLocationMode,
        origin: Pair<Double, Double>,
        departureTimeSec: Long?,
        nowMillis: Long = System.currentTimeMillis()
    ): CachedNearbyDepartures? {
        val cached = when (mode) {
            NearbyDeparturesLocationMode.CURRENT_LOCATION -> currentLocation
            NearbyDeparturesLocationMode.PINNED_LOCATION -> pinnedLocation
        } ?: return null

        if (nowMillis < cached.fetchedAtMillis ||
            nowMillis - cached.fetchedAtMillis > maxAgeMillis ||
            cached.departureTimeSec != departureTimeSec
        ) return null

        // GPS fixes drift slightly. Pinned coordinates, however, must match exactly:
        // moving the map pin even a short distance must not show another pin's data.
        val matches = when (mode) {
            NearbyDeparturesLocationMode.CURRENT_LOCATION ->
                distanceMeters(origin, cached.origin) <= gpsReuseRadiusMeters
            NearbyDeparturesLocationMode.PINNED_LOCATION -> origin == cached.origin
        }
        return cached.takeIf { matches }
    }

    @Synchronized
    fun put(
        mode: NearbyDeparturesLocationMode,
        origin: Pair<Double, Double>,
        departureTimeSec: Long?,
        response: NearbyResponseV3,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        val entry = CachedNearbyDepartures(origin, departureTimeSec, response, nowMillis)
        when (mode) {
            NearbyDeparturesLocationMode.CURRENT_LOCATION -> currentLocation = entry
            NearbyDeparturesLocationMode.PINNED_LOCATION -> pinnedLocation = entry
        }
    }

    private fun distanceMeters(a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
        val latitudeDelta = Math.toRadians(a.first - b.first)
        val longitudeDelta = Math.toRadians(a.second - b.second)
        val haversine = sin(latitudeDelta / 2).pow(2) +
            cos(Math.toRadians(a.first)) * cos(Math.toRadians(b.first)) *
            sin(longitudeDelta / 2).pow(2)
        return 2 * 6_371_000.0 * asin(sqrt(min(1.0, haversine)))
    }
}

// Process-memory cache: survives navigation but is released when the app process exits.
internal val nearbyDeparturesMemoryCache = NearbyDeparturesMemoryCache()
