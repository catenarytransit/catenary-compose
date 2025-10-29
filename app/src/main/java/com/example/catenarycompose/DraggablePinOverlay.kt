package com.catenarymaps.catenary

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.dellisd.spatialk.geojson.FeatureCollection
import io.github.dellisd.spatialk.geojson.Point
import io.github.dellisd.spatialk.geojson.Position
import org.maplibre.compose.camera.CameraState
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.image
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.GeoJsonSource
import org.maplibre.compose.sources.rememberGeoJsonSource
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource

data class PinState(
    val active: Boolean = false, val position: Position? = null
)

/* ----------------------- Map content (inside MaplibreMap) ----------------------- */

@Composable
fun DraggablePinLayers(pin: PinState, pinSource: GeoJsonSource) {


    /*
    LaunchedEffect(pin.active, pin.position) {
        if (pin.active && pin.position != null) {
            pinSource.setData(GeoJsonData.Features(Point(pin.position)))
        } else {
            pinSource.setData(GeoJsonData.Features(FeatureCollection(emptyList())))
        }
    }*/


    val pinIcon = painterResource(id = R.drawable.map_marker_1)

    SymbolLayer(
        id = "picked-pin",
        source = pinSource,
        iconImage = image(pinIcon),
        iconAllowOverlap = const(true),
        iconIgnorePlacement = const(true),
        visible = pin.active
    )
}

/* ----------------------- Gestures (outside MaplibreMap) ----------------------- *//* Only a small hitbox around the pin is interactive; the rest of the map pans/zooms normally. */
@Composable
fun DraggablePinOverlay(
    camera: CameraState,
    mapSize: IntSize,
    pin: PinState,
    onActivatePin: () -> Unit,
    // called when drag is finished; update your pin state & map source in here (or upstream)
    onDragEndCommit: (Position) -> Unit,
    markerSize: Dp = 40.dp,
    hitSize: Dp = 40.dp,
    anchorYBottom: Boolean = true
) {
    val position = pin.position
    if (!pin.active || position == null) return
    val projection = camera.projection ?: return
    val density = LocalDensity.current

    // Cached screen position of the pin in dp. Recomputed only when needed.
    var centerDp by remember { mutableStateOf(DpOffset(0.dp, 0.dp)) }

    // Recompute the cached screen location:
    //  - when the pin’s geo changes
    //  - when the camera stops moving (prevents per-frame recomputes)

    LaunchedEffect(pin.position) {
        centerDp = projection.screenLocationFromPosition(position)
    }
    LaunchedEffect(camera.isCameraMoving) {
        if (!camera.isCameraMoving) {
            centerDp = projection.screenLocationFromPosition(position)
        }
    }


    // Drag state in PX (to keep the movement buttery-smooth)
    var dragPx by remember { mutableStateOf(Offset.Zero) }

    // Visual offsets
    val dragDp: DpOffset = with(density) { DpOffset(dragPx.x.toDp(), dragPx.y.toDp()) }

    val visualCenter = DpOffset(centerDp.x + dragDp.x, centerDp.y + dragDp.y)
    val visualMarkerTopLeft = if (anchorYBottom) {
        DpOffset(visualCenter.x - markerSize / 2, visualCenter.y - markerSize)
    } else {
        DpOffset(visualCenter.x - markerSize / 2, visualCenter.y - markerSize / 2)
    }

    val halfHit = hitSize / 2
    val hitTopLeft = DpOffset(visualCenter.x - halfHit, visualCenter.y - halfHit)

    // 2) Small hitbox centered on the marker that handles drag
    Box(
        Modifier
            .offset(x = hitTopLeft.x, y = hitTopLeft.y)
            .size(hitSize)
            .background(color = Color.Blue.copy(alpha = 0.5f))
            .pointerInput(Unit) {
                detectDragGestures(onDragStart = {
                    if (!pin.active) onActivatePin()
                    dragPx = Offset.Zero // reset local drag delta
                }, onDrag = { change, dragAmount ->
                    // accumulate pixel delta locally; no projection here
                    dragPx += dragAmount
                }, onDragEnd = {
                    // Project once at the end: cached center + total drag
                    val finalDp = with(density) {
                        DpOffset(centerDp.x + dragPx.x.toDp(), centerDp.y + dragPx.y.toDp())
                    }

                    val newGeo = projection.positionFromScreenLocation(finalDp)
                    dragPx = Offset.Zero
                    onDragEndCommit(newGeo)
                }, onDragCancel = {
                    dragPx = Offset.Zero
                })
            })
}

