package com.catenarymaps.catenary

/** Only abbreviate stop labels when *every* stop in a rail trip uses the same suffix. */
private val trailingStationWord = Regex("""\s+Station$""", RegexOption.IGNORE_CASE)

internal fun shouldShortenTrainStationStops(routeType: Int?, stopNames: List<String?>): Boolean {
    if (routeType == null || routeType !in 0..2 || stopNames.isEmpty()) return false
    return stopNames.all { name ->
        val trimmed = name?.trim().orEmpty()
        val suffix = trailingStationWord.find(trimmed)
        // Keep an isolated "Station" (or an empty/missing stop name) intact.
        suffix != null && suffix.range.first > 0
    }
}

/** Presentation-only: GTFS names and stop identities must retain their original values. */
internal fun displayTrainStationStopName(name: String, shorten: Boolean): String =
    if (shorten) name.trimEnd().replace(trailingStationWord, "") else name
