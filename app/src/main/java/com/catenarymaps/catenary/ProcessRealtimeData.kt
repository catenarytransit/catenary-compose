package com.catenarymaps.catenary

import androidx.compose.runtime.MutableState
import io.github.dellisd.spatialk.geojson.Feature
import io.github.dellisd.spatialk.geojson.Point
import io.github.dellisd.spatialk.geojson.Position
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import java.lang.Math.abs
import kotlin.math.absoluteValue
import kotlin.math.floor

@Serializable
data class AgencyFilterRequest(val agency_filter: List<String>?)

fun fetchRoutesOfChateauByAgency(
    chateauId: String,
    agencyIdList: List<String>,
    routeCache: MutableState<Map<String, Map<String, RouteCacheEntry>>>,
    routeCacheAgenciesKnown: MutableState<Map<String, List<String>>>,
    ktorClient: HttpClient
) {
    println("Fetching routes")
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val routeCacheAgenciesKnownForThisChateau = routeCacheAgenciesKnown.value[chateauId]

            val requestBody =
                AgencyFilterRequest(agency_filter = if (agencyIdList.isEmpty()) null else if (routeCacheAgenciesKnownForThisChateau != null) agencyIdList.filter {
                    !routeCacheAgenciesKnownForThisChateau!!.contains(
                        it
                    )
                } else agencyIdList)
            val newRoutes: List<RouteCacheEntry> =
                ktorClient.post("https://birch.catenarymaps.org/getroutesofchateauwithagency/$chateauId") {
                    contentType(ContentType.Application.Json)
                    setBody(requestBody)
                }.body()
            println("Fetched ${newRoutes.size} routes for chateau $chateauId")

            val currentCache = routeCache.value.toMutableMap()
            val chateauCache = currentCache.getOrPut(chateauId) { mutableMapOf() }.toMutableMap()
            newRoutes.forEach { route ->
                val routeId = route.route_id
                chateauCache[routeId] = route
                // println("saving route $routeId in chateau cache $chateauId")
            }
            currentCache[chateauId] = chateauCache
            routeCache.value = currentCache

            val currentAgenciesKnown = routeCacheAgenciesKnown.value.toMutableMap()

            val chateauAgenciesKnown =
                currentAgenciesKnown.getOrPut(chateauId) { mutableListOf() }.toMutableList()

            newRoutes.forEach { route ->
                val agencyId = route.agency_id
                if (!chateauAgenciesKnown.contains(agencyId)) {
                    if (agencyId != null) {
                        chateauAgenciesKnown.add(agencyId)
                    }
                }

            }
            currentAgenciesKnown[chateauId] = chateauAgenciesKnown
            routeCacheAgenciesKnown.value = currentAgenciesKnown

        } catch (e: Exception) {
            // Log error
            println("Error fetching routes: ${e.message}")
        }
    }
}

fun processRealtimeDataV2(
    response: BulkRealtimeResponseV2,
    bounds: BoundsInput,
    realtimeVehicleLocationsStoreV2: MutableState<Map<String, Map<String, Map<Int, Map<Int, Map<String, VehiclePosition>>>>>>,
    previousTileBoundariesStore: MutableState<Map<String, Map<String, TileBounds>>>,
    realtimeVehicleLocationsLastUpdated: MutableState<Map<String, Map<String, Long>>>,
    realtimeVehicleRouteCache: MutableState<Map<String, Map<String, RouteCacheEntry>>>,
    routeCacheAgenciesKnown: MutableState<Map<String, List<String>>>,
    ktorClient: HttpClient
) {
    val newLocations = realtimeVehicleLocationsStoreV2.value.toMutableMap()
    val newLastUpdated = realtimeVehicleLocationsLastUpdated.value.toMutableMap()
    val newPrevBounds = previousTileBoundariesStore.value.toMutableMap()

    response.chateaus.forEach { (chateauId, chateauData) ->
        chateauData.categories?.let { categories ->
            val categoryMap = mapOf(
                "bus" to categories.bus,
                "metro" to categories.metro,
                "rail" to categories.rail,
                "other" to categories.other
            )


            var shouldFetchRoutes = false
            val agency_ids_to_fetch = mutableListOf<String>()

            categoryMap.forEach { (categoryName, categoryData) ->
                if (categoryData != null) {
                    val zLevel = categoryData.z_level
                    val boundsForLevel = when (zLevel) {
                        5 -> bounds.level5
                        7 -> bounds.level7
                        8 -> bounds.level8
                        10 -> bounds.level10
                        else -> null
                    }

                    if (boundsForLevel != null) {
                        val chateauPrevBounds =
                            newPrevBounds.getOrPut(chateauId) { mutableMapOf() }.toMutableMap()
                        chateauPrevBounds[categoryName] = TileBounds(
                            boundsForLevel.min_x,
                            boundsForLevel.max_x,
                            boundsForLevel.min_y,
                            boundsForLevel.max_y
                        )
                        newPrevBounds[chateauId] = chateauPrevBounds
                    }


                    if (categoryData.vehicle_positions != null) {

                        //iter through all vehicles
                        categoryData.vehicle_positions.forEach { (x, yMap) ->
                            yMap.forEach { (y, vehicleMap) ->
                                vehicleMap.forEach { (_, vehicleData) ->

                                    val routeId = vehicleData.trip?.route_id
                                    if (routeId != null) {
                                        //check realtimeVehicleLocationsStoreV2
                                        if (realtimeVehicleLocationsStoreV2.value.containsKey(
                                                chateauId
                                            )
                                        ) {
                                            if (realtimeVehicleLocationsStoreV2.value[chateauId]!!.containsKey(
                                                    routeId
                                                )
                                            ) {

                                            } else {
                                                shouldFetchRoutes = true

                                            }
                                        } else {
                                            shouldFetchRoutes = true

                                        }
                                    }

                                }
                            }
                        }


                        val categoryLocations =
                            newLocations.getOrPut(categoryName) { mutableMapOf() }.toMutableMap()
                        if (categoryData.replaces_all) {
                            val chateauLocations =
                                categoryLocations.getOrPut(chateauId) { mutableMapOf() }
                                    .toMutableMap()
                            // The structure is Map<String, Map<String, ...>> but should be Map<Int, Map<Int, ...>>
                            // Assuming kotlinx.serialization handles the string-to-int key conversion.
                            val vehiclePositionsIntKeys =
                                categoryData.vehicle_positions.mapKeys { it.key.toInt() }
                                    .mapValues { entry -> entry.value.mapKeys { it.key.toInt() } }



                            chateauLocations.clear()
                            chateauLocations.putAll(vehiclePositionsIntKeys)
                            categoryLocations[chateauId] = chateauLocations
                        } else {
                            val chateauLocations =
                                categoryLocations.getOrPut(chateauId) { mutableMapOf() }
                                    .toMutableMap()
                            categoryData.vehicle_positions.forEach { (x, yMap) ->
                                val xInt = x.toInt()
                                val yMapIntKeys = yMap.mapKeys { it.key.toInt() }
                                val xLocations = chateauLocations.getOrPut(xInt) { mutableMapOf() }
                                    .toMutableMap()
                                xLocations.putAll(yMapIntKeys)
                                chateauLocations[xInt] = xLocations
                            }
                            categoryLocations[chateauId] = chateauLocations
                        }

                        if (shouldFetchRoutes) {
                            if (categoryData.list_of_agency_ids != null) {
                                agency_ids_to_fetch.addAll(categoryData.list_of_agency_ids!!.toList())
                            }

                        }
                        newLocations[categoryName] = categoryLocations
                    }

                    val chateauLastUpdated =
                        newLastUpdated.getOrPut(chateauId) { mutableMapOf() }.toMutableMap()
                    chateauLastUpdated[categoryName] = categoryData.last_updated_time_ms
                    newLastUpdated[chateauId] = chateauLastUpdated


                }


            }

            if (shouldFetchRoutes) {
                fetchRoutesOfChateauByAgency(
                    chateauId,
                    agency_ids_to_fetch,
                    realtimeVehicleRouteCache,
                    routeCacheAgenciesKnown,
                    ktorClient
                )

            }
        }
    }

    realtimeVehicleLocationsStoreV2.value = newLocations
    previousTileBoundariesStore.value = newPrevBounds
    realtimeVehicleLocationsLastUpdated.value = newLastUpdated
}


fun rerenderCategoryLiveDots(
    category: String,
    isDark: Boolean,
    usUnits: Boolean,
    vehicleLocationsV2: Map<String, Map<String, Map<Int, Map<Int, Map<String, VehiclePosition>>>>>,
    routeCache: Map<String, Map<String, RouteCacheEntry>>
): List<Feature> {
    val categoryLocations = vehicleLocationsV2[category] ?: return emptyList()

    return categoryLocations.flatMap { (chateauId, gridData) ->
        val chateauVehiclesList = gridData.values.flatMap { it.values }.flatMap { it.entries }
            .associate { it.key to it.value }

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

            val chateauRouteCache = routeCache[chateauId]
            if (chateauRouteCache != null && routeId != null) {
                val route = chateauRouteCache[routeId]
                if (route != null) {
                    routeLongName = route.long_name
                    routeShortName = route.short_name
                    maptag = if (!route.short_name.isNullOrEmpty()) {
                        route.short_name
                    } else {
                        route.long_name ?: ""
                    }
                    color = route.color
                    textColor = route.text_color
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

            //println(vehicleData.toString())

            // Occupancy symbol
            val crowdSymbol = occupancy_to_symbol(vehicleData.occupancy_status)

            //println("Crowd symbol made ${crowdSymbol}")

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