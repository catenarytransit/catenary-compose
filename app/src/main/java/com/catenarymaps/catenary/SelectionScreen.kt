package com.catenarymaps.catenary

import android.graphics.drawable.Icon
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.utils.EmptyContent.contentType
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.launch

import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.analytics.GoogleAnalytics
import com.google.android.gms.analytics.HitBuilders
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.logEvent

@Composable
fun VehicleSelectionItem(
    option: MapSelectionSelector.VehicleMapSelector,
    onClick: (MapSelectionSelector.VehicleMapSelector) -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val routeBaseColor = parseColor(option.colour, MaterialTheme.colorScheme.onSurface)
    val routeColor = if (isDark) lightenColour(routeBaseColor) else darkenColour(routeBaseColor)
    // The Svelte code lightens the colour in dark mode.
    // For simplicity, we'll just use the parsed colour, but you could add a similar helper.

    Card(
        onClick = { onClick(option) },
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
        )
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            // Route Name
            if (option.route_long_name != null || option.route_short_name != null) {
                val short = option.route_short_name
                val long = option.route_long_name
                Row {
                    if (short != null && long != null && !long.contains(short)) {
                        Text(
                            text = short,
                            fontWeight = FontWeight.Bold,
                            color = routeColor,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = long,
                            color = routeColor,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    } else {
                        Text(
                            text = long ?: short ?: "Unknown Route",
                            fontWeight = FontWeight.Bold,
                            color = routeColor,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }

            // Headsign and Vehicle ID
            Row(
                modifier = Modifier.padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Run Number (simplified from JS logic)
                val runNumber = option.trip_short_name ?: option.vehicle_id
                if (runNumber != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(routeColor)
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = runNumber,
                            color = parseColor(option.text_colour, Color.White)
                        )
                    }
                }

                // Headsign
                if (option.headsign.isNotBlank()) {
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = option.headsign,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // Vehicle ID
                if (option.vehicle_id != null && option.vehicle_id != runNumber) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            Icons.Filled.DirectionsBus,
                            contentDescription = "Vehicle",
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = option.vehicle_id,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(start = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StopSelectionItem(
    option: MapSelectionSelector.StopMapSelector,
    previewData: StopPreviewDetail?,
    routeData: Map<String, RoutePreviewDetail>?,
    onClick: (MapSelectionSelector.StopMapSelector) -> Unit
) {
    Card(
        onClick = { onClick(option) },
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
        )
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                text = option.stop_name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )

            if (previewData != null) {
                // Platform/Level Info
                Row {
                    if (previewData.level_id != null) {
                        Text(
                            text = "Level ${previewData.level_id}",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                    if (previewData.platform_code != null) {
                        Text(
                            text = "Platform ${previewData.platform_code}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                // Route Badges
                if (routeData != null && previewData.routes.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        previewData.routes.forEach { routeId ->
                            routeData[routeId]?.let { route ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(parseColor(route.color, Color.Black))
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = route.short_name ?: route.long_name ?: routeId,
                                        color = parseColor(route.text_color, Color.White),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp, // Svelte: text-xs
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RouteSelectionItem(
    option: MapSelectionSelector.RouteMapSelector,
    onClick: (MapSelectionSelector.RouteMapSelector) -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val routeColor = parseColor(option.colour, MaterialTheme.colorScheme.onSurface)

    Card(
        onClick = { onClick(option) },
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
        )
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                text = option.name ?: "Unknown Route",
                color = routeColor,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun MapSelectionScreen(
    screenData: CatenaryStackEnum.MapSelectionScreen,
    onStackPush: (CatenaryStackEnum) -> Unit,
    onBack: () -> Unit,
    onHome: () -> Unit
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        try {
            val firebaseAnalytics = FirebaseAnalytics.getInstance(context)
            firebaseAnalytics.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW) {
                param(FirebaseAnalytics.Param.SCREEN_NAME, "MapSelectionScreen")
                //param(FirebaseAnalytics.Param.SCREEN_CLASS, "HomeCompose")
            }
        } catch (e: Exception) {
            // Log the error or handle it gracefully
            android.util.Log.e("GA", "Failed to log screen view", e)
        }
    }

    // Stop preview state
    var stopPreviewData by remember { mutableStateOf<StopPreviewResponse?>(null) }
    val scope = rememberCoroutineScope()

    // Fetch stop preview data
    LaunchedEffect(screenData) {
        val stopsToQuery = screenData.arrayofoptions
            .mapNotNull { it.data as? MapSelectionSelector.StopMapSelector }
            .groupBy { it.chateau_id }
            .mapValues { entry -> entry.value.map { it.stop_id } }

        if (stopsToQuery.isNotEmpty()) {
            scope.launch {
                try {
                    val requestBody = StopPreviewRequest(chateaus = stopsToQuery)
                    val response: StopPreviewResponse =
                        ktorClient.post("https://birch.catenarymaps.org/stop_preview") {
                            contentType(ContentType.Application.Json)
                            setBody(requestBody)
                        }.body()
                    stopPreviewData = response
                } catch (e: Exception) {
                    Log.e("MapSelectionScreen", "Failed to fetch stop preview: ${e.message}")
                    stopPreviewData = null // Clear on error
                }
            }
        } else {
            stopPreviewData = null // No stops, clear data
        }
    }

    // Separate lists
    val vehicles =
        screenData.arrayofoptions.mapNotNull { it.data as? MapSelectionSelector.VehicleMapSelector }
    val stops =
        screenData.arrayofoptions.mapNotNull { it.data as? MapSelectionSelector.StopMapSelector }
    val routes =
        screenData.arrayofoptions.mapNotNull { it.data as? MapSelectionSelector.RouteMapSelector }

    val lazyListState = rememberLazyListState()
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .scrollable(state = lazyListState, orientation = Orientation.Vertical)
            // .windowInsetsBottomHeight(WindowInsets(bottom = WindowInsets.safeDrawing.getBottom(density = LocalDensity.current)))
            .windowInsetsPadding(WindowInsets(bottom = WindowInsets.safeDrawing.getBottom(density = LocalDensity.current))),
        state = lazyListState,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        contentPadding = PaddingValues(bottom = 64.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${screenData.arrayofoptions.size} items selected", // TODO: i18n
                    style = MaterialTheme.typography.headlineSmall, // Svelte: text-lg md:text-2xl
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                NavigationControls(onBack = onBack, onHome = onHome)
            }
        }
        if (vehicles.isNotEmpty()) {
            item {
                Text(
                    "Vehicles", // TODO: i18n
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            items(vehicles) { vehicle ->
                VehicleSelectionItem(
                    option = vehicle,
                    onClick = {
                        val newStackItem = if (it.trip_id != null) {
                            CatenaryStackEnum.SingleTrip(
                                chateau_id = it.chateau_id,
                                trip_id = it.trip_id,
                                route_id = it.route_id,
                                start_time = it.start_time,
                                start_date = it.start_date,
                                vehicle_id = it.vehicle_id,
                                route_type = it.route_type
                            )
                        } else {
                            CatenaryStackEnum.VehicleSelectedStack(
                                chateau_id = it.chateau_id,
                                vehicle_id = it.vehicle_id,
                                gtfs_id = it.gtfs_id
                            )
                        }
                        onStackPush(newStackItem)
                    }
                )
            }
        }

        if (stops.isNotEmpty()) {
            item {
                Text(
                    "Stops", // TODO: i18n
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            items(stops) { stop ->
                val preview = stopPreviewData?.stops?.get(stop.chateau_id)?.get(stop.stop_id)
                val routeCache = stopPreviewData?.routes?.get(stop.chateau_id)

                StopSelectionItem(
                    option = stop,
                    previewData = preview,
                    routeData = routeCache,
                    onClick = {
                        onStackPush(CatenaryStackEnum.StopStack(it.chateau_id, it.stop_id))
                    }
                )
            }
        }

        if (routes.isNotEmpty()) {
            item {
                Text(
                    "Routes", // TODO: i18n
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            items(routes) { route ->
                RouteSelectionItem(
                    option = route,
                    onClick = {
                        onStackPush(CatenaryStackEnum.RouteStack(it.chateau_id, it.route_id))
                    }
                )
            }
        }
    }
}