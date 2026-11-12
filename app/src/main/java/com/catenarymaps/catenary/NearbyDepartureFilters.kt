package com.catenarymaps.catenary

internal data class NearbyDepartureFilters(
        val rail: Boolean = false,
        val metro: Boolean = false,
        val tram: Boolean = false,
        val bus: Boolean = false,
        val other: Boolean = false,
        val funicular: Boolean = false,
        val monorail: Boolean = false,
        val cableTram: Boolean = false
) {
    val isActive: Boolean
        get() = rail || metro || tram || bus || other || funicular || monorail || cableTram

    // Saved filters must remain reachable even where only buses (or no results) are returned.
    fun shouldShowControls(hasNonBusDepartures: Boolean): Boolean = hasNonBusDepartures || isActive
}

// Rough mapping mirroring the JS filter buckets
internal fun isAllowedByFilter(
        routeType: Int,
        f: NearbyDepartureFilters,
        hasFunicular: Boolean,
        hasMonorail: Boolean,
        hasCableTram: Boolean
): Boolean {
        val allDisabled = !f.rail && !f.metro && !f.tram && !f.bus && !f.other &&
                !(hasFunicular && f.funicular) &&
                !(hasMonorail && f.monorail) &&
                !(hasCableTram && f.cableTram)
        if (allDisabled) return true
        return when (routeType) {
                3, 11, 700 -> f.bus
                1 -> f.metro
                0, 900 -> f.tram
                5 -> if (hasCableTram) f.cableTram else f.tram
                7 -> if (hasFunicular) f.funicular else f.metro
                12 -> if (hasMonorail) f.monorail else f.metro
                2, 106, 107, 101, 100, 102, 103 -> f.rail
                4 -> f.other // ferries
                else -> true
        }
}

