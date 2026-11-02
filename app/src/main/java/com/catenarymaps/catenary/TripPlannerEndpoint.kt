package com.catenarymaps.catenary

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import org.maplibre.compose.camera.CameraState
import org.maplibre.spatialk.geojson.Position

/** Small draggable endpoint targets leave the rest of the map available for pan and zoom. */
@Composable
fun TripPlannerEndpoints(camera: CameraState, mapSize: IntSize, planner: TripPlannerViewModel, onDragStart: () -> Unit) {
    val projection = camera.projection ?: return
    val density = LocalDensity.current
    listOf(true to planner.origin, false to planner.destination).forEach { (isOrigin, location) ->
        key(isOrigin) {
            if (location != null) {
                val center = remember(camera.position, location, mapSize) {
                    projection.screenLocationFromPosition(Position(location.lon, location.lat))
                }
                var delta by remember(location) { mutableStateOf(Offset.Zero) }
                val currentCenter by rememberUpdatedState(center)
                val currentProjection by rememberUpdatedState(projection)
                val moved = with(density) { DpOffset(center.x + delta.x.toDp(), center.y + delta.y.toDp()) }
                Box(
                    Modifier.offset(moved.x - 20.dp, moved.y - 20.dp).size(40.dp)
                        .semantics { contentDescription = if (isOrigin) "Drag starting point" else "Drag destination" }
                        .pointerInput(location, density) {
                            detectDragGestures(
                                onDragStart = { delta = Offset.Zero; onDragStart() },
                                onDragCancel = { delta = Offset.Zero },
                                onDrag = { change, amount -> change.consume(); delta += amount },
                                onDragEnd = {
                                    val point = with(density) {
                                        currentProjection.positionFromScreenLocation(
                                            DpOffset(currentCenter.x + delta.x.toDp(), currentCenter.y + delta.y.toDp())
                                        )
                                    }
                                    delta = Offset.Zero
                                    planner.choose(isOrigin, DirectionsLocation(
                                        "%.6f, %.6f".format(java.util.Locale.ROOT, point.latitude, point.longitude), point.latitude, point.longitude
                                    ))
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(Modifier.size(22.dp).background(if (isOrigin) Color(0xFF16A34A) else Color(0xFFDC2626), CircleShape),
                        contentAlignment = Alignment.Center) {
                        Text(if (isOrigin) "A" else "B", color = Color.White)
                    }
                }
            }
        }
    }
}
