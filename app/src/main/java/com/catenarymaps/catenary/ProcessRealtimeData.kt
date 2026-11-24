package com.catenarymaps.catenary

import androidx.compose.runtime.MutableState
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.Point
import org.maplibre.spatialk.geojson.Position
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.absoluteValue
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

val realtimeDataMutex = Mutex()

@Serializable
data class AgencyFilterRequest(val agency_filter: List<String>?, val chateau: String)

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
                    !routeCacheAgenciesKnownForThisChateau.contains(
                        it
                    )
                } else agencyIdList, chateau = chateauId)
            val newRoutes: List<RouteCacheEntry> =
                ktorClient.post("https://birch.catenarymaps.org/getroutesofchateauwithagencyv2") {
                    contentType(ContentType.Application.Json)
                    setBody(requestBody)
                }.body()
            println("Fetched ${newRoutes.size} routes for chateau $chateauId")

            realtimeDataMutex.withLock {
                val currentCache = routeCache.value.toMutableMap()
                val chateauCache = currentCache.getOrPut(chateauId) { emptyMap() }.toMutableMap()
                newRoutes.forEach { route ->
                    val routeId = route.route_id
                    chateauCache[routeId] = route
                }
                currentCache[chateauId] = chateauCache.toMap()
                routeCache.value = currentCache.toMap()

                val currentAgenciesKnown = routeCacheAgenciesKnown.value.toMutableMap()
                val chateauAgenciesKnown =
                    currentAgenciesKnown.getOrPut(chateauId) { emptyList() }.toMutableList()

                newRoutes.forEach { route ->
                    val agencyId = route.agency_id
                    if (!chateauAgenciesKnown.contains(agencyId)) {
                        if (agencyId != null) {
                            chateauAgenciesKnown.add(agencyId)
                        }
                    }
                }
                currentAgenciesKnown[chateauId] = chateauAgenciesKnown.toList()
                routeCacheAgenciesKnown.value = currentAgenciesKnown.toMap()
            }

        } catch (e: Exception) {
            // Log error
            println("Error fetching routes: ${e.message}")
        }
    }
}

suspend fun processRealtimeDataV2(
    response: BulkRealtimeResponseV2,
    bounds: BoundsInput,
    realtimeVehicleLocationsStoreV2: MutableState<Map<String, Map<String, Map<Int, Map<Int, Map<String, VehiclePosition>>>>>>,
    previousTileBoundariesStore: MutableState<Map<String, Map<String, TileBounds>>>,
    realtimeVehicleLocationsLastUpdated: MutableState<Map<String, Map<String, Long>>>,
    realtimeVehicleRouteCache: MutableState<Map<String, Map<String, RouteCacheEntry>>>,
    routeCacheAgenciesKnown: MutableState<Map<String, List<String>>>,
    ktorClient: HttpClient
) = withContext(Dispatchers.Default) {
    realtimeDataMutex.withLock {
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
                            12 -> bounds.level12
                            else -> null
                        }

                        if (boundsForLevel != null) {
                            val chateauPrevBounds =
                                newPrevBounds.getOrPut(chateauId) { emptyMap() }.toMutableMap()
                            chateauPrevBounds[categoryName] = TileBounds(
                                boundsForLevel.min_x,
                                boundsForLevel.max_x,
                                boundsForLevel.min_y,
                                boundsForLevel.max_y
                            )
                            newPrevBounds[chateauId] = chateauPrevBounds.toMap()
                        }


                        if (categoryData.vehicle_positions != null) {

                            //iter through all vehicles
                            categoryData.vehicle_positions.forEach { (x, yMap) ->
                                yMap.forEach { (y, vehicleMap) ->
                                    vehicleMap.forEach { (_, vehicleData) ->

                                        val routeId = vehicleData.trip?.route_id
                                        if (routeId != null) {
                                            //check realtimeVehicleLocationsStoreV2
                                            if (realtimeVehicleRouteCache.value.containsKey(
                                                    chateauId
                                                )
                                            ) {
                                                if (realtimeVehicleRouteCache.value[chateauId]!!.containsKey(
                                                        routeId
                                                    )
                                                ) {

                                                } else {
                                                    shouldFetchRoutes = true

                                                }
                                            } else {
                                                //println("Instructing chateau ${chateauId} to fetch routes because no route index exists yet")
                                                shouldFetchRoutes = true

                                            }
                                        }

                                    }
                                }
                            }


                            val categoryLocations =
                                newLocations.getOrPut(categoryName) { emptyMap() }.toMutableMap()
                            if (categoryData.replaces_all) {
                                val chateauLocations =
                                    categoryLocations.getOrPut(chateauId) { emptyMap() }
                                        .toMutableMap()
                                // The structure is Map<String, Map<String, ...>> but should be Map<Int, Map<Int, ...>>
                                // Assuming kotlinx.serialization handles the string-to-int key conversion.
                                val vehiclePositionsIntKeys =
                                    categoryData.vehicle_positions.mapKeys { it.key.toInt() }
                                        .mapValues { entry -> entry.value.mapKeys { it.key.toInt() } }



                                chateauLocations.clear()
                                chateauLocations.putAll(vehiclePositionsIntKeys)
                                categoryLocations[chateauId] = chateauLocations.toMap()
                            } else {
                                val chateauLocations =
                                    categoryLocations.getOrPut(chateauId) { emptyMap() }
                                        .toMutableMap()
                                categoryData.vehicle_positions.forEach { (x, yMap) ->
                                    val xInt = x.toInt()
                                    val yMapIntKeys = yMap.mapKeys { it.key.toInt() }
                                    val xLocations = chateauLocations.getOrPut(xInt) { emptyMap() }
                                        .toMutableMap()
                                    xLocations.putAll(yMapIntKeys)
                                    chateauLocations[xInt] = xLocations.toMap()
                                }
                                categoryLocations[chateauId] = chateauLocations.toMap()
                            }

                            if (shouldFetchRoutes) {
                                if (categoryData.list_of_agency_ids != null) {
                                    agency_ids_to_fetch.addAll(categoryData.list_of_agency_ids.toList())
                                }

                            }
                            newLocations[categoryName] = categoryLocations.toMap()
                        }

                        val chateauLastUpdated =
                            newLastUpdated.getOrPut(chateauId) { emptyMap() }.toMutableMap()
                        chateauLastUpdated[categoryName] = categoryData.last_updated_time_ms
                        newLastUpdated[chateauId] = chateauLastUpdated.toMap()


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

        realtimeVehicleLocationsStoreV2.value = newLocations.toMap()
        previousTileBoundariesStore.value = newPrevBounds.toMap()
        realtimeVehicleLocationsLastUpdated.value = newLastUpdated.toMap()
    }
}


suspend fun rerenderCategoryLiveDots(
    category: String,
    isDark: Boolean,
    usUnits: Boolean,
    vehicleLocationsV2: Map<String, Map<String, Map<Int, Map<Int, Map<String, VehiclePosition>>>>>,
    routeCache: Map<String, Map<String, RouteCacheEntry>>
): List<Feature<Point, JsonObject>> = withContext(Dispatchers.Default) {
    val categoryLocations = vehicleLocationsV2[category] ?: return@withContext emptyList()

    val featureJobs = categoryLocations.map { (chateauId, gridData) ->
        async {
            val chateauVehiclesList: Map<String, VehiclePosition> =
                gridData.values.flatMap { it.values }.flatMap { it.entries }
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

                val vehicleLabel = vehicleMapVehicleLabel(
                    chateauId, vehicleData.vehicle?.label, vehicleData.vehicle?.id
                )
                var color = "#aaaaaa"
                var textColor = "#000000"
                val tripShortName = vehicleData.trip?.trip_short_name
                val tripIdLabel = vehicleMapTripLabel(tripShortName, vehicleData.trip?.trip_id)

                val routeId = vehicleData.trip?.route_id
                var routeShortName: String? = null
                var routeLongName: String? = null

                val chateauRouteCache = routeCache[chateauId]
                if (chateauRouteCache != null && routeId != null) {
                    val route = chateauRouteCache[routeId]
                    if (route != null) {
                        routeLongName = route.long_name
                        routeShortName = route.short_name
                        color = route.color
                        textColor = route.text_color
                    }
                }

                val routeLabels = vehicleMapRouteLabels(
                    chateauId, routeId, routeShortName, routeLongName,
                    vehicleData.trip?.trip_headsign
                )

                // Color processing
                val (contrastColor, contrastBearingColor) = processColor(color, isDark)

                val speedStr = vehicleMapSpeedLabel(vehicleData.position.speed, usUnits)

                //println(vehicleData.toString())

                // Occupancy symbol
                val crowdSymbol = occupancy_to_symbol(vehicleData.occupancy_status)

                //println("Crowd symbol made ${crowdSymbol}")

                val delayLabel = vehicleMapDelayLabel(vehicleData.trip?.delay)

                // Build GeoJSON properties
                val properties = JsonObject(
                    buildMap<String, JsonElement> {
                        put("vehicleIdLabel", JsonPrimitive(vehicleLabel))
                        put("speed", JsonPrimitive(speedStr))
                        put("color", JsonPrimitive(color))
                        put("chateau", JsonPrimitive(chateauId))
                        put("route_type", JsonPrimitive(vehicleData.route_type))
                        put("tripIdLabel", JsonPrimitive(tripIdLabel))
                        vehicleData.position.bearing?.let { put("bearing", JsonPrimitive(it)) }
                        put("has_bearing", JsonPrimitive(vehicleData.position.bearing != null))
                        put("maptag", JsonPrimitive(routeLabels.route))
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
                        put("headsign", JsonPrimitive(routeLabels.headsign))
                        vehicleData.timestamp?.let { put("timestamp", JsonPrimitive(it)) }
                        put("rtid", JsonPrimitive(rtId))
                        put("text_color", JsonPrimitive(textColor))
                        vehicleData.trip?.trip_id?.let { put("trip_id", JsonPrimitive(it)) }
                        vehicleData.trip?.start_time?.let { put("start_time", JsonPrimitive(it)) }
                        vehicleData.trip?.start_date?.let { put("start_date", JsonPrimitive(it)) }
                        put("crowd_symbol", JsonPrimitive(crowdSymbol))
                        vehicleData.occupancy_status?.let {
                            put(
                                "occupancy_status",
                                JsonPrimitive(it)
                            )
                        }
                        put("delay_label", JsonPrimitive(delayLabel))
                        vehicleData.trip?.delay?.let { put("delay", JsonPrimitive(it)) }

                        //println("hashcode = ${"$chateauId:$rtId".hashCode().absoluteValue}")

                        put(
                            "id",
                            JsonPrimitive("$chateauId:$rtId".hashCode().absoluteValue.toString())
                        )
                    })

                val point = Point(Position(lon, lat))
                // This will now work correctly
                //val featureId = "${category}:${chateauId}:${rtId}"
                Feature(
                    id = JsonPrimitive("$chateauId:$rtId".hashCode().absoluteValue.toString()),
                    geometry = point,
                    properties = properties
                )
            }
        }
    }

    featureJobs.awaitAll().flatten()
}