package com.catenarymaps.catenary

import io.github.dellisd.spatialk.geojson.Feature
import io.github.dellisd.spatialk.geojson.Point
import io.github.dellisd.spatialk.geojson.Position
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import java.lang.Math.abs
import kotlin.math.absoluteValue
import kotlin.math.floor

fun rerenderCategoryLiveDots(
    category: String,
    isDark: Boolean,
    usUnits: Boolean,
    vehicleLocations: Map<String, Map<String, Map<String, VehiclePosition>>>,
    routeCache: Map<String, Map<String, Map<String, RouteCacheEntry>>>
): List<Feature> {
    val categoryLocations = vehicleLocations[category] ?: return emptyList()

    return categoryLocations.flatMap { (chateauId, chateauVehiclesList) ->
        chateauVehiclesList.mapNotNull { (rtId, vehicleData) ->
            if (vehicleData.position == null) {
                return@mapNotNull null
            }

            // Filter out known bad data points
            val lat = vehicleData.position.latitude
            val lon = vehicleData.position.longitude
            if ((lat == 34.099503 && lon == -117.29602) || (lat == 34.250793 && lon == -119.205025) || (lat == 34.05573 && lon == -118.23351)) {
                return@mapNotNull null
            }

            var vehicleLabel = vehicleData.vehicle?.label ?: vehicleData.vehicle?.id ?: ""
            if (chateauId == "new-south-wales" && vehicleLabel.contains(" to ")) {
                vehicleLabel = vehicleData.vehicle?.id ?: ""
            }
            vehicleLabel = vehicleLabel.replace("ineo-tram:", "").replace("ineo-bus:", "")

            var color = "#aaaaaa"
            var textColor = "#000000"
            var tripIdLabel = ""
            var tripShortName: String? = null
            var headsign = ""

            vehicleData.trip?.let { trip ->
                tripIdLabel = trip.trip_short_name ?: ""
                tripShortName = trip.trip_short_name
                if (tripIdLabel.isEmpty() && chateauId == "metra") {
                    tripIdLabel =
                        trip.trip_id?.split('_')?.getOrNull(1)?.filter { it.isDigit() } ?: ""
                }
                headsign = trip.trip_headsign ?: ""
                if (headsign == headsign.uppercase()) {
                    headsign =
                        headsign.lowercase().replaceFirstChar { it.titlecase() } // Basic title case
                }
                if (chateauId == "new-south-wales") {
                    headsign = headsign.replace(" Station", "")
                }
            }
            if (headsign.contains("Line  - ") && chateauId == "metro~losangeles") {
                headsign = headsign.split("-").getOrNull(1)?.trim() ?: ""
            }

            val routeId = vehicleData.trip?.route_id
            var maptag = ""
            var routeShortName: String? = null
            var routeLongName: String? = null

            val chateauRouteCache = routeCache[chateauId]?.get(category)
            if (chateauRouteCache != null && routeId != null) {
                val route = chateauRouteCache[routeId]
                if (route != null) {
                    routeLongName = route.route_long_name
                    routeShortName = route.route_short_name
                    maptag = if (!route.route_short_name.isNullOrEmpty()) {
                        route.route_short_name
                    } else {
                        route.route_long_name ?: ""
                    }
                    color = route.route_colour
                    textColor = route.route_text_colour
                }
            }

            // Agency-specific tag mapping
            maptag = when (maptag) {
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
                else -> maptag
            }

            // Color processing
            val (contrastColor, contrastBearingColor) = processColor(color, isDark)

            // Speed string
            val speedStr = vehicleData.position.speed?.let {
                val speed = it * if (usUnits) 2.23694 else 3.6
                "%.1f %s".format(speed, if (usUnits) "mph" else "km/h")
            } ?: ""

            println(vehicleData.toString())

            // Occupancy symbol
            val crowdSymbol = occupancy_to_symbol(vehicleData.occupancy_status)

            println("Crowd symbol made ${crowdSymbol}")

            // Delay label
            val delayLabel = vehicleData.trip?.delay?.let {
                val prefix = if (it < 0) "-" else "+"
                val absDelay = abs(it)
                val minutes = floor(absDelay / 60.0)
                val hours = floor(minutes / 60.0)
                val remMinutes = minutes % 60
                "%s%s%.0fm".format(
                    prefix,
                    if (hours > 0) "%.0fh".format(hours) else "",
                    remMinutes
                )
            } ?: ""

            // Build GeoJSON properties
            val properties = buildMap<String, JsonElement> {
                put("vehicleIdLabel", JsonPrimitive(vehicleLabel))
                put("speed", JsonPrimitive(speedStr))
                put("color", JsonPrimitive(color))
                put("chateau", JsonPrimitive(chateauId))
                put("route_type", JsonPrimitive(vehicleData.route_type))
                put("tripIdLabel", JsonPrimitive(tripIdLabel))
                vehicleData.position.bearing?.let { put("bearing", JsonPrimitive(it)) }
                put("has_bearing", JsonPrimitive(vehicleData.position.bearing != null))
                put(
                    "maptag", JsonPrimitive(
                        fixRouteName(chateauId, maptag, routeId)
                            .replace(" Branch", "")
                            .replace(" Line", "")
                            .replace("Counterclockwise", "ACW") // TODO: i18n
                            .replace("Clockwise", "CW")
                    )
                )
                tripShortName?.let { put("trip_short_name", JsonPrimitive(it)) }
                routeShortName?.let { put("route_short_name", JsonPrimitive(it)) }
                routeLongName?.let { put("route_long_name", JsonPrimitive(it)) }

                // Add contrast colors based on theme
                if (isDark) {
                    put("contrastdarkmode", JsonPrimitive(contrastColor))
                    put("contrastdarkmodebearing", JsonPrimitive(contrastBearingColor))
                } else {
                    put("contrastlightmode", JsonPrimitive(contrastColor))
                }

                routeId?.let { put("routeId", JsonPrimitive(it)) }
                put(
                    "headsign", JsonPrimitive(
                        fixHeadsignText(headsign, maptag)
                            .replace("Counterclockwise", "ACW") // TODO: i18n
                            .replace("Clockwise", "CW")
                    )
                )
                vehicleData.timestamp?.let { put("timestamp", JsonPrimitive(it)) }
                put("rtid", JsonPrimitive(rtId))
                put("text_color", JsonPrimitive(textColor))
                vehicleData.trip?.trip_id?.let { put("trip_id", JsonPrimitive(it)) }
                vehicleData.trip?.start_time?.let { put("start_time", JsonPrimitive(it)) }
                vehicleData.trip?.start_date?.let { put("start_date", JsonPrimitive(it)) }
                put("crowd_symbol", JsonPrimitive(crowdSymbol))
                vehicleData.occupancy_status?.let { put("occupancy_status", JsonPrimitive(it)) }
                put("delay_label", JsonPrimitive(delayLabel))
                vehicleData.trip?.delay?.let { put("delay", JsonPrimitive(it)) }

                //println("hashcode = ${"$chateauId:$rtId".hashCode().absoluteValue}")

                put("id", JsonPrimitive("$chateauId:$rtId".hashCode().absoluteValue))
            }

            val point = Point(Position(lon, lat))
            // This will now work correctly
            //val featureId = "${category}:${chateauId}:${rtId}"
            Feature(
                id = "$chateauId:$rtId".hashCode().absoluteValue.toString(),
                geometry = point,
                properties = properties
            )
        }
    }
}