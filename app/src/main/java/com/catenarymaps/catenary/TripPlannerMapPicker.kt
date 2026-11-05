package com.catenarymaps.catenary

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import org.maplibre.compose.camera.CameraState
import java.util.Locale

/** The pin tip is at the viewport centre, independent of camera padding and map gestures. */
@Composable
fun TripPlannerMapPicker(camera: CameraState, mapSize: IntSize, planner: TripPlannerViewModel, modifier: Modifier = Modifier) {
    val endpoint = planner.mapPicking ?: return
    val density = LocalDensity.current
    val locationName = stringResource(R.string.directions_map_location)
    val pinLabel = stringResource(R.string.directions_choose_on_map)
    fun cancel() { planner.mapPicking = null; planner.editing = endpoint }
    BackHandler { cancel() }
    Box(modifier) {
        Surface(Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp),
            shape = MaterialTheme.shapes.medium, shadowElevation = 4.dp) {
            Text(stringResource(R.string.directions_align_pin), modifier = Modifier.padding(16.dp))
        }
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(R.drawable.map_picker_pin),
            contentDescription = pinLabel,
            // The vector tip lies at y=22/24, aligned exactly with the viewport centre.
            modifier = Modifier.align(Alignment.Center).offset(y = (-20).dp).size(48.dp))
        Surface(Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp),
            shape = MaterialTheme.shapes.large, shadowElevation = 4.dp) {
            Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                TextButton(onClick = { cancel() }) { Text(stringResource(R.string.cancel)) }
                Button(enabled = camera.projection != null && mapSize.width > 0 && mapSize.height > 0, onClick = {
                    val projection = camera.projection ?: return@Button
                    val point = with(density) { projection.positionFromScreenLocation(
                        DpOffset((mapSize.width / 2f).toDp(), (mapSize.height / 2f).toDp())) }
                    planner.choose(endpoint, DirectionsLocation(
                        "$locationName (%.5f, %.5f)".format(Locale.ROOT, point.latitude, point.longitude), point.latitude, point.longitude))
                }) { Text(stringResource(android.R.string.ok)) }
            }
        }
    }
}
