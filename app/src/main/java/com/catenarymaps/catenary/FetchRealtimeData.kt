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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.maplibre.compose.camera.CameraState
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

private const val TAG = "FetchRealtimeData"

@Serializable
data class BoundsInputPerLevel(
    val min_x: Int,
    val max_x: Int,
    val min_y: Int,
    val max_y: Int
)

@Serializable
data class BoundsInput(
    val level5: BoundsInputPerLevel,
    val level7: BoundsInputPerLevel,
    val level8: BoundsInputPerLevel,
    val level10: BoundsInputPerLevel
)

@Serializable
data class SubCategoryAskParamsV2(
    val last_updated_time_ms: Long,
    val prev_user_min_x: Int? = null,
    val prev_user_max_x: Int? = null,
    val prev_user_min_y: Int? = null,
    val prev_user_max_y: Int? = null
)

@Serializable
data class CategoryAskParamsV2(
    val bus: SubCategoryAskParamsV2? = null,
    val metro: SubCategoryAskParamsV2? = null,
    val rail: SubCategoryAskParamsV2? = null,
    val other: SubCategoryAskParamsV2? = null
)

@Serializable
data class ChateauAskParamsV2(
    val category_params: CategoryAskParamsV2
)

@Serializable
data class BulkRealtimeRequestV2(
    val categories: List<String>,
    val chateaus: Map<String, ChateauAskParamsV2>,
    val bounds_input: BoundsInput
)

@Serializable
data class EachCategoryPayloadV2(
    val vehicle_positions: Map<String, Map<String, Map<String, VehiclePosition>>>? = null,
    val last_updated_time_ms: Long,
    val replaces_all: Boolean,
    val z_level: Int,
    val list_of_agency_ids: List<String>? = null
)

@Serializable
data class PositionDataCategoryV2(
    val metro: EachCategoryPayloadV2? = null,
    val bus: EachCategoryPayloadV2? = null,
    val rail: EachCategoryPayloadV2? = null,
    val other: EachCategoryPayloadV2? = null
)

@Serializable
data class EachChateauResponseV2(
    val categories: PositionDataCategoryV2? = null
)

@Serializable
data class BulkRealtimeResponseV2(
    val chateaus: Map<String, EachChateauResponseV2>
)

data class TileBbox(val north: Int, val south: Int, val east: Int, val west: Int)

fun getTileBoundaries(camera: CameraState, zoom: Int): TileBbox? {
    val projection = camera.projection ?: return null
    // Use queryVisibleBoundingBox as it's available on the projection
    val visibleBounds = try {
        projection.queryVisibleBoundingBox()
    } catch (e: Exception) {
        Log.e(TAG, "Could not query visible bounding box", e)
        return null
    }

    val north = visibleBounds.northeast.latitude
    val south = visibleBounds.southwest.latitude
    val east = visibleBounds.northeast.longitude
    val west = visibleBounds.southwest.longitude

    val n = Math.pow(2.0, zoom.toDouble())

    val latRadNorth = Math.toRadians(north)
    val latRadSouth = Math.toRadians(south)

    val xtileWest = floor((west + 180) / 360 * n).toInt()
    val xtileEast = floor((east + 180) / 360 * n).toInt()

    val ytileNorth = floor((1 - Math.log(Math.tan(latRadNorth) + 1 / Math.cos(latRadNorth)) / Math.PI) / 2 * n).toInt()
    val ytileSouth = floor((1 - Math.log(Math.tan(latRadSouth) + 1 / Math.cos(latRadSouth)) / Math.PI) / 2 * n).toInt()

    return TileBbox(ytileNorth, ytileSouth, xtileEast, xtileWest)
}

fun boundsInputCalculate(camera: CameraState): BoundsInput {
    val levels = listOf(5, 7, 8, 10)
    val boundsInputMap = mutableMapOf<Int, BoundsInputPerLevel>()

    val currentZoom = camera.position.zoom

    for (zoom in levels) {
        val boundaries = getTileBoundaries(camera, zoom)
        if (boundaries != null) {
            val maxTiles = (Math.pow(2.0, zoom.toDouble()) - 1).toInt()
            val padding = if (currentZoom > 10) 0 else 2

            boundsInputMap[zoom] = BoundsInputPerLevel(
                min_x = max(0, boundaries.west - padding),
                max_x = min(maxTiles, boundaries.east + padding),
                min_y = max(0, boundaries.north - padding),
                max_y = min(maxTiles, boundaries.south + padding)
            )
        } else {
            // Fallback if boundaries can't be calculated
            boundsInputMap[zoom] = BoundsInputPerLevel(0, 0, 0, 0)
        }
    }

    return BoundsInput(
        level5 = boundsInputMap[5]!!,
        level7 = boundsInputMap[7]!!,
        level8 = boundsInputMap[8]!!,
        level10 = boundsInputMap[10]!!
    )
}


fun fetchRealtimeData(
    scope: CoroutineScope,
    zoom: Double,
    settings: AllLayerSettings,
    isFetchingRealtimeData: AtomicBoolean,
    visibleChateaus: List<String>,
    realtimeVehicleLocationsLastUpdated: MutableState<Map<String, Map<String, Long>>>,
    ktorClient: HttpClient,
    realtimeVehicleRouteCache: MutableState<Map<String, Map<String, RouteCacheEntry>>>,
    camera: CameraState,
    previousTileBoundariesStore: MutableState<Map<String, Map<String, TileBounds>>>,
    realtimeVehicleLocationsStoreV2: MutableState<Map<String, Map<String, Map<Int, Map<Int, Map<String, VehiclePosition>>>>>>
) {
    if (!isFetchingRealtimeData.compareAndSet(false, true)) {
        Log.d(TAG, "Skipping fetch, another one is already in progress.")
        return
    }

    scope.launch {
        try {
            val categoriesToRequest = mutableListOf<String>()

            val busThreshold = 8

            if (settings.bus.visiblerealtimedots && zoom >= busThreshold) {
                categoriesToRequest.add("bus")
            }
            if (settings.intercityrail.visiblerealtimedots && zoom >= 3) {
                categoriesToRequest.add("rail")
            }
            if (settings.localrail.visiblerealtimedots && zoom >= 4) {
                categoriesToRequest.add("metro")
            }
            if (settings.other.visiblerealtimedots && zoom >= 3) {
                categoriesToRequest.add("other")
            }

            if (categoriesToRequest.isEmpty() || visibleChateaus.isEmpty()) {
                return@launch
            }

            val boundsInput = boundsInputCalculate(camera)
            val lastUpdatedMap = realtimeVehicleLocationsLastUpdated.value
            val previousTileBoundaries = previousTileBoundariesStore.value

            val chateausToFetch = visibleChateaus.associateWith { chateauId ->
                val busParams = SubCategoryAskParamsV2(
                    last_updated_time_ms = lastUpdatedMap[chateauId]?.get("bus") ?: 0L,
                    prev_user_min_x = previousTileBoundaries[chateauId]?.get("bus")?.min_x,
                    prev_user_max_x = previousTileBoundaries[chateauId]?.get("bus")?.max_x,
                    prev_user_min_y = previousTileBoundaries[chateauId]?.get("bus")?.min_y,
                    prev_user_max_y = previousTileBoundaries[chateauId]?.get("bus")?.max_y
                )
                val metroParams = SubCategoryAskParamsV2(
                    last_updated_time_ms = lastUpdatedMap[chateauId]?.get("metro") ?: 0L,
                    prev_user_min_x = previousTileBoundaries[chateauId]?.get("metro")?.min_x,
                    prev_user_max_x = previousTileBoundaries[chateauId]?.get("metro")?.max_x,
                    prev_user_min_y = previousTileBoundaries[chateauId]?.get("metro")?.min_y,
                    prev_user_max_y = previousTileBoundaries[chateauId]?.get("metro")?.max_y
                )
                val railParams = SubCategoryAskParamsV2(
                    last_updated_time_ms = lastUpdatedMap[chateauId]?.get("rail") ?: 0L,
                    prev_user_min_x = previousTileBoundaries[chateauId]?.get("rail")?.min_x,
                    prev_user_max_x = previousTileBoundaries[chateauId]?.get("rail")?.max_x,
                    prev_user_min_y = previousTileBoundaries[chateauId]?.get("rail")?.min_y,
                    prev_user_max_y = previousTileBoundaries[chateauId]?.get("rail")?.max_y
                )
                val otherParams = SubCategoryAskParamsV2(
                    last_updated_time_ms = lastUpdatedMap[chateauId]?.get("other") ?: 0L,
                    prev_user_min_x = previousTileBoundaries[chateauId]?.get("other")?.min_x,
                    prev_user_max_x = previousTileBoundaries[chateauId]?.get("other")?.max_x,
                    prev_user_min_y = previousTileBoundaries[chateauId]?.get("other")?.min_y,
                    prev_user_max_y = previousTileBoundaries[chateauId]?.get("other")?.max_y
                )

                ChateauAskParamsV2(
                    category_params = CategoryAskParamsV2(
                        bus = busParams,
                        metro = metroParams,
                        rail = railParams,
                        other = otherParams
                    )
                )
            }

            val requestBody = BulkRealtimeRequestV2(
                categories = categoriesToRequest,
                chateaus = chateausToFetch,
                bounds_input = boundsInput
            )

            try {
                val rawresponse: String =
                    ktorClient.post("https://birch.catenarymaps.org/bulk_realtime_fetch_v2") {
                        contentType(ContentType.Application.Json)
                        setBody(requestBody)
                    }.body()

                val json = Json { ignoreUnknownKeys = true }
                val response = json.decodeFromString<BulkRealtimeResponseV2>(rawresponse)

                processRealtimeDataV2(
                    response,
                    boundsInput,
                    realtimeVehicleLocationsStoreV2,
                    previousTileBoundariesStore,
                    realtimeVehicleLocationsLastUpdated,
                    realtimeVehicleRouteCache,
                    ktorClient
                )

            } catch (e: ClientRequestException) {
                val errorBody: String = e.response.body()
                Log.e(TAG, "Failed to fetch realtime data (Client Error ${e.response.status}): $errorBody")
            } catch (e: ServerResponseException) {
                val errorBody: String = e.response.body()
                Log.e(TAG, "Failed to fetch realtime data (Server Error ${e.response.status}): $errorBody")
            } catch (e: Exception) {
                Log.e(TAG, "An unexpected error occurred: ${e.message}", e)
            }

        } finally {
            isFetchingRealtimeData.set(false)
        }
    }
}
