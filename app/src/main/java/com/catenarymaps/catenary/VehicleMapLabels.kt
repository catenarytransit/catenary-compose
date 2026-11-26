package com.catenarymaps.catenary

import kotlin.math.abs

/** Shared display values for both regular live dots and selected vehicle context dots. */
internal data class VehicleMapRouteLabels(val route: String, val headsign: String)

internal fun vehicleMapRouteLabels(
    chateauId: String,
    routeId: String?,
    shortName: String?,
    longName: String?,
    tripHeadsign: String?
): VehicleMapRouteLabels {
    val raw = shortName?.takeIf { it.isNotEmpty() } ?: longName.orEmpty()
    val mapTag = when (raw) {
        "Metro E Line" -> "E"
        "Metro A Line" -> "A"
        "Metro B Line" -> "B"
        "Metro C Line" -> "C"
        "Metro D Line" -> "D"
        "Metro L Line" -> "L"
        "Metro K Line" -> "K"
        "Metrolink Ventura County Line" -> "Ventura"
        "Metrolink Antelope Valley Line" -> "Antelope"
        "Metrolink San Bernardino Line" -> "SB"
        "Metrolink Riverside Line" -> "Riverside"
        "Metrolink Orange County Line" -> "Orange"
        "Metrolink 91/Perris Valley Line" -> "91/Perris"
        "Metrolink Inland Empire-Orange County Line" -> "IE-OC"
        else -> raw
    }
    var headsign = tripHeadsign.orEmpty()
    if (headsign == headsign.uppercase()) {
        headsign = headsign.lowercase().replaceFirstChar { it.titlecase() }
    }
    if (chateauId == "new-south-wales") {
        headsign = headsign.replace(" Station", "")
    }
    if (headsign.contains("Line  - ") && chateauId == "metro~losangeles") {
        headsign = headsign.split("-").getOrNull(1)?.trim().orEmpty()
    }
    return VehicleMapRouteLabels(
        route = fixRouteName(chateauId, mapTag, routeId)
            .replace(" Branch", "")
            .replace(" Line", "")
            .replace("Counterclockwise", "ACW")
            .replace("Clockwise", "CW"),
        headsign = fixHeadsignText(headsign, mapTag)
            .replace("Counterclockwise", "ACW")
            .replace("Clockwise", "CW")
    )
}

internal fun vehicleMapVehicleLabel(chateauId: String, label: String?, id: String?): String {
    var result = label?.takeIf { it.isNotBlank() } ?: id.orEmpty()
    if (chateauId == "new-south-wales" && result.contains(" to ")) {
        result = id.orEmpty()
    }
    return result.replace("ineo-tram:", "").replace("ineo-bus:", "")
}

internal fun vehicleMapTripLabel(shortName: String?, tripId: String?): String =
    shortName?.takeIf { it.isNotEmpty() }
        ?: tripId?.split('_')?.getOrNull(1)?.filter { it.isDigit() }.orEmpty()

internal fun vehicleMapSpeedLabel(speed: Number?, usUnits: Boolean): String {
    val metersPerSecond = speed?.toDouble()?.takeIf { it.isFinite() } ?: return ""
    val converted = metersPerSecond * if (usUnits) 2.23694 else 3.6
    return "%.1f %s".format(converted, if (usUnits) "mph" else "km/h")
}

internal fun vehicleMapDelayLabel(delay: Int?): String {
    delay ?: return ""
    val prefix = if (delay < 0) "-" else "+"
    val minutes = abs(delay.toLong()) / 60
    val hours = minutes / 60
    return "$prefix${if (hours > 0) "${hours}h" else ""}${minutes % 60}m"
}
