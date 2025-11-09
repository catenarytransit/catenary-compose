package com.catenarymaps.catenary

import android.util.Log
import androidx.compose.runtime.MutableState
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "FetchRealtimeData"

fun fetchRealtimeData(
    scope: CoroutineScope,
    zoom: Double,
    settings: AllLayerSettings,
    isFetchingRealtimeData: AtomicBoolean,
    visibleChateaus: List<String>,
    realtimeVehicleLocationsLastUpdated: MutableState<Map<String, Map<String, Long>>>,
    realtimeVehicleRouteCacheHash: MutableState<Map<String, Map<String, ULong>>>,
    ktorClient: HttpClient,
    realtimeVehicleLocations: MutableState<Map<String, Map<String, Map<String, VehiclePosition>>>>,
    realtimeVehicleRouteCache: MutableState<Map<String, Map<String, Map<String, RouteCacheEntry>>>>
) {
    if (!isFetchingRealtimeData.compareAndSet(false, true)) {
        Log.d(TAG, "Skipping fetch, another one is already in progress.")
        return
    }

    scope.launch {
        try {
            val categoriesToRequest = mutableListOf<String>()

            val busThreshold = 8
            if ((settings.bus as LayerCategorySettings).visiblerealtimedots && zoom >= busThreshold) {
                categoriesToRequest.add("bus")
            }
            if ((settings.intercityrail as LayerCategorySettings).visiblerealtimedots && zoom >= 3) {
                categoriesToRequest.add("rail")
            }
            if ((settings.localrail as LayerCategorySettings).visiblerealtimedots && zoom >= 4) {
                categoriesToRequest.add("metro")
            }
            if ((settings.other as LayerCategorySettings).visiblerealtimedots && zoom >= 3) {
                categoriesToRequest.add("other")
            }


            if (categoriesToRequest.isEmpty() || visibleChateaus.isEmpty()) {
                // Don't fetch if no categories are visible or no chateaus are in view
                return@launch
            }

            // Build chateaus_to_fetch object
            val chateausToFetch = mutableMapOf<String, ChateauFetchParams>()
            val lastUpdatedMap = realtimeVehicleLocationsLastUpdated.value
            val hashCacheMap = realtimeVehicleRouteCacheHash.value

            visibleChateaus.forEach { chateauId ->
                

                //println("Chateau id $chateauId")
                val categoryParams = mutableMapOf<String, CategoryParams>()
                val cats = listOf("bus", "rail", "metro", "other")

                cats.forEach { cat ->
                    val lastUpdated = lastUpdatedMap[chateauId]?.get(cat) ?: 0L
                    val hash = hashCacheMap[chateauId]?.get(cat) ?: ULong.MIN_VALUE
                    categoryParams[cat] = CategoryParams(
                        hash_of_routes = hash,
                        last_updated_time_ms = lastUpdated
                    )

                    //println("last updated for $cat in $chateauId is $lastUpdated")
                }
                chateausToFetch[chateauId] =
                    ChateauFetchParams(category_params = categoryParams)
            }

            val requestBody = BulkRealtimeRequest(
                categories = categoriesToRequest, chateaus = chateausToFetch
            )

            try {

                // You must run this inside a coroutine scope (e.g., in a suspend function)
                try {
                    val rawresponse: String =
                        ktorClient.post("https://birch.catenarymaps.org/bulk_realtime_fetch_v1") {
                            contentType(ContentType.Application.Json)
                            setBody(requestBody)
                        }.body()

                    // println("Bulk Fetch: $rawresponse")

                    val json_for_this = Json {
                        ignoreUnknownKeys = true
                        prettyPrint = true
                        isLenient = true
                        encodeDefaults = true
                    }
                    val response =
                        json_for_this.decodeFromString<BulkRealtimeResponse>(rawresponse)

                    println("Recieved live dots")

                    // Process the response (porting process_realtime_vehicle_locations_v2)
                    val newLocations = realtimeVehicleLocations.value.toMutableMap()
                    val newRouteCache = realtimeVehicleRouteCache.value.toMutableMap()
                    val newLastUpdated =
                        realtimeVehicleLocationsLastUpdated.value.toMutableMap()
                    val newHashCache = realtimeVehicleRouteCacheHash.value.toMutableMap()

                    response.chateaus.forEach { (chateauId, chateauData) ->
                        // println("Processing Chateau $chateauId")
                        chateauData.categories.forEach { (category, categoryData) ->
                            if (categoryData != null) {
                                // Update Locations
                                if (categoryData.vehicle_positions != null) {
                                    val chateauLocations =
                                        newLocations.getOrPut(category) { mutableMapOf() }
                                            .toMutableMap()
                                    chateauLocations[chateauId] = categoryData.vehicle_positions
                                    newLocations[category] = chateauLocations
                                    //   println("set value for new vehicle locations for $category with $chateauId and length ${categoryData.vehicle_positions.size}")
                                }
                                // Update Route Cache
                                if (categoryData.vehicle_route_cache != null) {
                                    val chateauCache =
                                        newRouteCache.getOrPut(chateauId) { mutableMapOf() }
                                            .toMutableMap()
                                    chateauCache[category] = categoryData.vehicle_route_cache
                                    newRouteCache[chateauId] = chateauCache
                                }
                                // Update Last Updated Time
                                val chateauLastUpdated =
                                    newLastUpdated.getOrPut(chateauId) { mutableMapOf() }
                                        .toMutableMap()
                                chateauLastUpdated[category] = categoryData.last_updated_time_ms
                                newLastUpdated[chateauId] = chateauLastUpdated

                                // Update Hash
                                val chateauHash =
                                    newHashCache.getOrPut(chateauId) { mutableMapOf() }
                                        .toMutableMap()
                                chateauHash[category] = categoryData.hash_of_routes
                                newHashCache[chateauId] = chateauHash
                            }
                        }
                    }

                    // Atomically update the state variables
                    println("set value for new vehicle locations")
                    realtimeVehicleLocations.value = newLocations
                    realtimeVehicleRouteCache.value = newRouteCache
                    realtimeVehicleLocationsLastUpdated.value = newLastUpdated
                    realtimeVehicleRouteCacheHash.value = newHashCache

                } catch (e: ClientRequestException) {
                    // This block catches 4xx errors, including your 400 Bad Request
                    val errorBody: String = e.response.body()
                    println("Failed to fetch realtime data (Client Error ${e.response.status}): $errorBody")

                } catch (e: ServerResponseException) {
                    // This block catches 5xx server errors
                    val errorBody: String = e.response.body()
                    println("Failed to fetch realtime data (Server Error ${e.response.status}): $errorBody")

                } catch (e: Exception) {
                    // This catches other errors (network connection, DNS, etc.)
                    println("An unexpected error occurred: ${e.message}")
                }


            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch realtime data: ${e.message}")
            }
        } finally {
            isFetchingRealtimeData.set(false)
        }
    }
}
