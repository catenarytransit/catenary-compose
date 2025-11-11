package com.catenarymaps.catenary

import androidx.compose.runtime.*
import org.maplibre.compose.camera.CameraState
import org.maplibre.spatialk.geojson.Position

/**
 * Controller you can hand to any component so they can:
 *  - activate/deactivate the geo lock,
 *  - notify the map that some *external* camera move happened (which disables the lock).
 */
class GeoLockController internal constructor(
    private val setActive: (Boolean) -> Unit,
    val isActive: () -> Boolean
) {
    private var internalMove by mutableStateOf(false)

    fun activate() = setActive(true)
    fun deactivate() = setActive(false)

    fun beginInternalMove() {
        internalMove = true
    }

    fun endInternalMove() {
        internalMove = false
    }

    fun isInternalMove(): Boolean = internalMove

    /** If some other UI teleports/animates the map, call this to disable the lock. */
    fun notifyExternalCameraMove() = deactivate()
}

@Composable
fun rememberGeoLockController(): GeoLockController {
    var active by remember { mutableStateOf(false) }
    return remember { GeoLockController(setActive = { active = it }, isActive = { active }) }
}

suspend fun teleportCamera(
    camera: CameraState,
    controller: GeoLockController,
    lat: Double,
    lon: Double,
    zoom: Double? = null
) {
    controller.beginInternalMove()
    try {
        camera.animateTo(
            camera.position.copy(
                target = Position(lon, lat),
                zoom = zoom ?: camera.position.zoom
            )
        )
    } finally {
        controller.endInternalMove()
    }
}
