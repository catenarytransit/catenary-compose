package com.catenarymaps.catenary

import android.util.Log
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.maplibre.compose.camera.CameraState

private const val TAG = "FetchRealtimeData"

@Serializable
data class BoundsInputPerLevel(val min_x: Int, val max_x: Int, val min_y: Int, val max_y: Int)

@Serializable
data class BoundsInput(
        val level5: BoundsInputPerLevel,
        val level7: BoundsInputPerLevel,
        val level8: BoundsInputPerLevel,
        val level12: BoundsInputPerLevel
)

// Request classes removed as they are replaced by SpruceWebSocket.MapViewportUpdate

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

@Serializable data class EachChateauResponseV2(val categories: PositionDataCategoryV2? = null)

@Serializable data class BulkRealtimeResponseV2(val chateaus: Map<String, EachChateauResponseV2>)

data class TileBbox(val north: Int, val south: Int, val east: Int, val west: Int)

fun getTileBoundaries(camera: CameraState, zoom: Int): TileBbox? {
    val projection = camera.projection ?: return null
    // Use queryVisibleBoundingBox as it's available on the projection
    val visibleBounds =
            try {
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

    val ytileNorth =
            floor(
                            (1 -
                                    Math.log(Math.tan(latRadNorth) + 1 / Math.cos(latRadNorth)) /
                                            Math.PI) / 2 * n
                    )
                    .toInt()
    val ytileSouth =
            floor(
                            (1 -
                                    Math.log(Math.tan(latRadSouth) + 1 / Math.cos(latRadSouth)) /
                                            Math.PI) / 2 * n
                    )
                    .toInt()

    return TileBbox(ytileNorth, ytileSouth, xtileEast, xtileWest)
}

fun boundsInputCalculate(camera: CameraState): BoundsInput {
    val levels = listOf(5, 7, 8, 12)
    val boundsInputMap = mutableMapOf<Int, BoundsInputPerLevel>()

    val currentZoom = camera.position.zoom

    for (zoom in levels) {
        val boundaries = getTileBoundaries(camera, zoom)
        if (boundaries != null) {
            val maxTiles = (Math.pow(2.0, zoom.toDouble()) - 1).toInt()

            val padding =
                    when (zoom) {
                        12 -> if (currentZoom > 11) 0 else 1
                        else -> if (currentZoom > 9) 0 else 1
                    }

            boundsInputMap[zoom] =
                    BoundsInputPerLevel(
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
            level12 = boundsInputMap[12]!!
    )
}

fun sendMapUpdate(
        scope: CoroutineScope,
        zoom: Double,
        settings: AllLayerSettings,
        visibleChateaus: List<String>,
        camera: CameraState,
) {
    scope.launch {
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

        SpruceWebSocket.updateMap(
                categories = categoriesToRequest,
                chateaus = visibleChateaus,
                boundsInput = boundsInput
        )
    }
}
