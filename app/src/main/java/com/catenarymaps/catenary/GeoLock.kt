package com.catenarymaps.catenary

import androidx.compose.foundation.layout.PaddingValues
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
    var internalMove by mutableStateOf(false)

    fun activate() = setActive(true)
    fun deactivate() = setActive(false)

    var internalPos = mutableStateOf<Position?>(null)


    fun beginInternalMove() {
        internalMove = true
    }

    fun setInternalPos(pos: Position) {
        internalPos.value = pos
    }

    fun endInternalMove() {
        internalMove = false
    }

    fun getInternalPos(): Position? = internalPos.value

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
    zoom: Double? = null,
    padding: PaddingValues? = null
) {
    controller.beginInternalMove()
    println("begin internal move started")
    try {
        controller.setInternalPos(pos = Position(lon, lat))

        camera.animateTo(
            camera.position.copy(
                target = Position(lon, lat),
                zoom = zoom ?: camera.position.zoom,
                padding = padding ?: camera.position.padding
            )
        )
    } finally {
        controller.internalMove = false
        println("internal camera move finished")
    }
}
