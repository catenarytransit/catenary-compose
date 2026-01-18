package com.catenarymaps.catenary

import android.graphics.drawable.Icon
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Subway
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.filled.Tram
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.logEvent
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.utils.EmptyContent.contentType
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.launch

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

        Surface(
                onClick = { onClick(option) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(4.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
        ) {
                Column(modifier = Modifier.padding(2.dp)) {
                        // Route Name
                        if (option.route_long_name != null || option.route_short_name != null) {
                                val short = option.route_short_name
                                val long = option.route_long_name

                                // Check for icons
                                val isRatp =
                                        RatpUtils.isIdfmChateau(option.chateau_id) &&
                                                RatpUtils.isRatpRoute(short)
                                val isMta =
                                        MtaSubwayUtils.MTA_CHATEAU_ID == option.chateau_id &&
                                                short != null &&
                                                MtaSubwayUtils.isSubwayRouteId(short)

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (isRatp) {
                                                val iconUrl = RatpUtils.getRatpIconUrl(short)
                                                if (iconUrl != null) {
                                                        val context = LocalContext.current
                                                        val imageLoader =
                                                                remember(context) {
                                                                        ImageLoader.Builder(context)
                                                                                .components {
                                                                                        add(
                                                                                                SvgDecoder
                                                                                                        .Factory()
                                                                                        )
                                                                                }
                                                                                .build()
                                                                }
                                                        AsyncImage(
                                                                model =
                                                                        ImageRequest.Builder(
                                                                                        context
                                                                                )
                                                                                .data(iconUrl)
                                                                                .crossfade(true)
                                                                                .build(),
                                                                imageLoader = imageLoader,
                                                                contentDescription = short,
                                                                modifier =
                                                                        Modifier
                                                                                .size(24.dp)
                                                                                .padding(end = 0.dp)
                                                        )
                                                }
                                        } else if (isMta) {
                                                val iconUrl = MtaSubwayUtils.getMtaIconUrl(short!!)
                                                if (iconUrl != null) {
                                                        val context = LocalContext.current
                                                        val imageLoader =
                                                                remember(context) {
                                                                        ImageLoader.Builder(context)
                                                                                .components {
                                                                                        add(
                                                                                                SvgDecoder
                                                                                                        .Factory()
                                                                                        )
                                                                                }
                                                                                .build()
                                                                }
                                                        AsyncImage(
                                                                model =
                                                                        ImageRequest.Builder(
                                                                                        context
                                                                                )
                                                                                .data(iconUrl)
                                                                                .crossfade(true)
                                                                                .build(),
                                                                imageLoader = imageLoader,
                                                                contentDescription = short,
                                                                modifier =
                                                                        Modifier
                                                                                .size(24.dp)
                                                                                .padding(end = 0.dp)
                                                        )
                                                }
                                        }

                                        if (short != null && long != null && !long.contains(short)
                                        ) {
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
                        FlowRow(
                                modifier = Modifier.padding(top = 0.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalArrangement = Arrangement.spacedBy(1.dp)
                        ) {
                                // Run Number (simplified from JS logic)
                                val runNumber = option.trip_short_name // ?: option.vehicle_id
                                if (runNumber != null) {
                                        Box(
                                                modifier =
                                                        Modifier
                                                                .clip(RoundedCornerShape(4.dp))
                                                                .background(routeBaseColor)
                                                                .padding(
                                                                        horizontal = 4.dp,
                                                                        vertical = 2.dp
                                                                )
                                        ) {
                                                Text(
                                                        text = runNumber,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color =
                                                                parseColor(
                                                                        option.text_colour,
                                                                        Color.White
                                                                )
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
                                        )
                                }

                                // Spacer(modifier = Modifier.weight(1f))

                                // Vehicle ID
                                if (option.vehicle_id != null && option.vehicle_id != runNumber) {
                                        Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier =
                                                        Modifier
                                                                .clip(RoundedCornerShape(4.dp))
                                                                .background(
                                                                        MaterialTheme.colorScheme
                                                                                .surfaceVariant
                                                                )
                                                                .padding(
                                                                        horizontal = 4.dp,
                                                                        vertical = 2.dp
                                                                )
                                        ) {
                                                Icon(
                                                        when (option.route_type) {
                                                                0 -> Icons.Filled.Tram
                                                                1 -> Icons.Filled.Subway
                                                                2 -> Icons.Filled.Train
                                                                else -> Icons.Filled.DirectionsBus
                                                        },
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
        Surface(
                onClick = { onClick(option) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(4.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
        ) {
                Column(modifier = Modifier.padding(4.dp)) {
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
                                                        text =
                                                                "Platform ${previewData.platform_code}",
                                                        style = MaterialTheme.typography.bodySmall
                                                )
                                        }
                                }

                                // Route Badges
                                if (routeData != null && previewData.routes.isNotEmpty()) {
                                        SelectionRouteBadges(
                                                routeIds = previewData.routes,
                                                resolveRouteInfo = { routeId ->
                                                        val route = routeData[routeId]
                                                        if (route != null) {
                                                                ResolvedRouteBadgeInfo(
                                                                        routeId = routeId,
                                                                        shortName =
                                                                                route.short_name,
                                                                        longName = route.long_name,
                                                                        color = route.color,
                                                                        textColor =
                                                                                route.text_color,
                                                                        agencyId = route.agency_id,
                                                                        chateauId =
                                                                                option.chateau_id
                                                                )
                                                        } else {
                                                                null
                                                        }
                                                }
                                        )
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
        val routeBaseColor = parseColor(option.colour, MaterialTheme.colorScheme.onSurface)

        val routeColor =
                if (isDark) lightenColour(routeBaseColor, minContrast = 8.0)
                else darkenColour(routeBaseColor)

        // Check for special icons (MTA/RATP)
        val routeShortName =
                option.name // route selector often puts short name in name? or need to check?
        // Actually route selector 'name' field usually contains the main display name.
        // For subways it might be "A" or "1".
        // Let's assume 'name' is the short name if it's short, or check if we can parse it.
        // But wait, option.name in RouteSelectionItem might be long name or short name.
        // Let's rely on the same logic as badges if possible, but we only have 'option'.
        // RouteMapSelector has: route_id, name, colour, text_colour.
        // We will try to use name as short name for lookup.

        val isRatp =
                RatpUtils.isIdfmChateau(option.chateau_id) && RatpUtils.isRatpRoute(option.name)
        val isMta =
                MtaSubwayUtils.MTA_CHATEAU_ID == option.chateau_id &&
                        option.name != null &&
                        MtaSubwayUtils.isSubwayRouteId(option.name)

        Surface(
                onClick = { onClick(option) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(4.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
        ) {
                Row(
                        modifier = Modifier.padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                        val iconUrl =
                                if (isRatp) RatpUtils.getRatpIconUrl(option.name)
                                else if (isMta) MtaSubwayUtils.getMtaIconUrl(option.name!!)
                                else null

                        if (iconUrl != null) {
                                val context = LocalContext.current
                                val imageLoader =
                                        remember(context) {
                                                ImageLoader.Builder(context)
                                                        .components { add(SvgDecoder.Factory()) }
                                                        .build()
                                        }
                                AsyncImage(
                                        model =
                                                ImageRequest.Builder(context)
                                                        .data(iconUrl)
                                                        .crossfade(true)
                                                        .build(),
                                        imageLoader = imageLoader,
                                        contentDescription = option.name,
                                        modifier = Modifier
                                                .size(24.dp)
                                                .padding(end = 8.dp)
                                )
                        } else {
                                Text(
                                        text = option.name ?: "Unknown Route",
                                        color = routeColor,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold
                                )
                        }

                        // Show category text if not special (RATP/MTA)

                }
        }
}

@Composable
fun CompactRouteSelectionItem(
        option: MapSelectionSelector.RouteMapSelector,
        onClick: (MapSelectionSelector.RouteMapSelector) -> Unit
) {
        val isDark = isSystemInDarkTheme()
        val routeBaseColor = parseColor(option.colour, MaterialTheme.colorScheme.onSurface)
        val routeColor =
                if (isDark) lightenColour(routeBaseColor, minContrast = 8.0)
                else darkenColour(routeBaseColor)

        val isRatp =
                RatpUtils.isIdfmChateau(option.chateau_id) && RatpUtils.isRatpRoute(option.name)
        val isMta =
                MtaSubwayUtils.MTA_CHATEAU_ID == option.chateau_id &&
                        option.name != null &&
                        MtaSubwayUtils.isSubwayRouteId(option.name)

        Surface(
                onClick = { onClick(option) },
                shape = RoundedCornerShape(4.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                // No margin here, handled by FlowRow arrangement
                ) {
                Row(
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                        if (isRatp) {
                                val iconUrl = RatpUtils.getRatpIconUrl(option.name)
                                if (iconUrl != null) {
                                        val context = LocalContext.current
                                        val imageLoader =
                                                remember(context) {
                                                        ImageLoader.Builder(context)
                                                                .components {
                                                                        add(SvgDecoder.Factory())
                                                                }
                                                                .build()
                                                }
                                        AsyncImage(
                                                model =
                                                        ImageRequest.Builder(context)
                                                                .data(iconUrl)
                                                                .crossfade(true)
                                                                .build(),
                                                imageLoader = imageLoader,
                                                contentDescription = option.name,
                                                modifier = Modifier
                                                        .size(32.dp)
                                                        .padding(end = 4.dp)
                                        )
                                }
                        } else if (isMta) {
                                val iconUrl = MtaSubwayUtils.getMtaIconUrl(option.name!!)
                                if (iconUrl != null) {
                                        val context = LocalContext.current
                                        val imageLoader =
                                                remember(context) {
                                                        ImageLoader.Builder(context)
                                                                .components {
                                                                        add(SvgDecoder.Factory())
                                                                }
                                                                .build()
                                                }
                                        AsyncImage(
                                                model =
                                                        ImageRequest.Builder(context)
                                                                .data(iconUrl)
                                                                .crossfade(true)
                                                                .build(),
                                                imageLoader = imageLoader,
                                                contentDescription = option.name,
                                                modifier = Modifier
                                                        .size(32.dp)
                                                        .padding(end = 4.dp)
                                        )
                                }
                        }

                        if (!isMta && !isRatp) {
                                Text(
                                        text = option.name ?: "?",
                                        color = routeColor,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                )
                        }
                }
        }
}

@Composable
fun OsmStationSelectionItem(
        option: MapSelectionSelector.OsmStationMapSelector,
        previewData: OsmStationPreview?,
        onClick: (MapSelectionSelector.OsmStationMapSelector) -> Unit
) {
        Surface(
                onClick = { onClick(option) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(4.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
        ) {
                Column(modifier = Modifier.padding(4.dp)) {
                        Text(
                                text = option.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold
                        )
                        // Mode type label
                        Text(
                                text =
                                        when (option.mode_type) {
                                                "subway" -> "Metro"
                                                "rail" -> "Rail"
                                                "tram", "light_rail" -> "Tram"
                                                else -> option.mode_type
                                        },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Preview Data (Badges)
                        if (previewData != null && previewData.routes.isNotEmpty()) {
                                val uniqueRoutes =
                                        previewData
                                                .stops
                                                .values
                                                .flatMap { it.values }
                                                .flatMap { it.routes }
                                                .toSet()
                                                .sorted()

                                if (uniqueRoutes.isNotEmpty()) {
                                        SelectionRouteBadges(
                                                routeIds = uniqueRoutes,
                                                resolveRouteInfo = { routeId ->
                                                        var found: ResolvedRouteBadgeInfo? = null
                                                        for ((chateauId, routesMap) in
                                                                previewData.routes.entries) {
                                                                val info = routesMap[routeId]
                                                                if (info != null) {
                                                                        found =
                                                                                ResolvedRouteBadgeInfo(
                                                                                        routeId =
                                                                                                routeId,
                                                                                        shortName =
                                                                                                info.short_name,
                                                                                        longName =
                                                                                                info.long_name,
                                                                                        color =
                                                                                                info.color,
                                                                                        textColor =
                                                                                                info.text_color,
                                                                                        agencyId =
                                                                                                info.agency_id,
                                                                                        chateauId =
                                                                                                chateauId
                                                                                )
                                                                        break
                                                                }
                                                        }
                                                        found
                                                }
                                        )
                                }
                        }
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
                                // param(FirebaseAnalytics.Param.SCREEN_CLASS, "HomeCompose")
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
                val stopsToQuery =
                        screenData
                                .arrayofoptions
                                .mapNotNull { it.data as? MapSelectionSelector.StopMapSelector }
                                .groupBy { it.chateau_id }
                                .mapValues { entry -> entry.value.map { it.stop_id } }

                if (stopsToQuery.isNotEmpty()) {
                        scope.launch {
                                try {
                                        val requestBody =
                                                StopPreviewRequest(chateaus = stopsToQuery)
                                        val response: StopPreviewResponse =
                                                ktorClient
                                                        .post(
                                                                "https://birch.catenarymaps.org/stop_preview"
                                                        ) {
                                                                contentType(
                                                                        ContentType.Application.Json
                                                                )
                                                                setBody(requestBody)
                                                        }
                                                        .body()
                                        stopPreviewData = response
                                } catch (e: Exception) {
                                        Log.e(
                                                "MapSelectionScreen",
                                                "Failed to fetch stop preview: ${e.message}"
                                        )
                                        stopPreviewData = null // Clear on error
                                }
                        }
                } else {
                        stopPreviewData = null // No stops, clear data
                }
        }

        // OSM Station preview state
        var osmStationPreviewData by remember {
                mutableStateOf<Map<String, OsmStationPreview>>(emptyMap())
        }

        // Fetch OSM station preview data
        LaunchedEffect(screenData) {
                val osmStationsToQuery =
                        screenData
                                .arrayofoptions
                                .mapNotNull {
                                        it.data as? MapSelectionSelector.OsmStationMapSelector
                                }
                                .map { it.osm_id }
                                .distinct()

                osmStationsToQuery.forEach { osmId ->
                        if (!osmStationPreviewData.containsKey(osmId)) {
                                scope.launch {
                                        try {
                                                val response: OsmStationPreview =
                                                        ktorClient
                                                                .get(
                                                                        "https://birch.catenarymaps.org/osm_station_preview?osm_station_id=$osmId"
                                                                )
                                                                .body()
                                                osmStationPreviewData =
                                                        osmStationPreviewData + (osmId to response)
                                        } catch (e: Exception) {
                                                Log.e(
                                                        "MapSelectionScreen",
                                                        "Failed to fetch osm station preview for $osmId: ${e.message}"
                                                )
                                        }
                                }
                        }
                }
        }

        // Separate lists with deduplication to prevent LazyColumn crashes
        // Uniqueness is enforced using the same logic as the LazyColumn keys
        val vehicles: List<MapSelectionSelector.VehicleMapSelector> =
                screenData.arrayofoptions
                        .mapNotNull { it.data as? MapSelectionSelector.VehicleMapSelector }
                        .distinctBy {
                                "${it.chateau_id}:${it.vehicle_id ?: it.trip_id ?: it.hashCode()}"
                        }

        val stops =
                screenData.arrayofoptions
                        .mapNotNull { it.data as? MapSelectionSelector.StopMapSelector }
                        .distinctBy { "${it.chateau_id}:${it.stop_id}" }

        val routes =
                screenData.arrayofoptions
                        .mapNotNull { it.data as? MapSelectionSelector.RouteMapSelector }
                        .distinctBy { "${it.chateau_id}:${it.route_id}" }

        val osmStations =
                screenData.arrayofoptions
                        .mapNotNull { it.data as? MapSelectionSelector.OsmStationMapSelector }
                        .distinctBy { it.osm_id }

        // Keep this as-is or swap for rememberSaveable if you want to survive process death.
        val lazyListState = rememberLazyListState()

        LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxWidth(),
                // REMOVE the extra .scrollable(...) – LazyColumn already scrolls itself
                // .scrollable(state = lazyListState, orientation = Orientation.Vertical)
                contentPadding = PaddingValues(bottom = 64.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
                // Use safe drawing insets, but bottom-only to avoid top inset jank during flings
                //
                // .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                ) {
                // Give the summary row a stable key
                item(key = "summary") {
                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                                Text(
                                        text =
                                                pluralStringResource(
                                                        R.plurals.items_selected,
                                                        screenData.arrayofoptions.size,
                                                        screenData.arrayofoptions.size
                                                ),
                                        style = MaterialTheme.typography.headlineSmall,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                )
                                NavigationControls(onBack = onBack, onHome = onHome)
                        }
                }

                if (vehicles.isNotEmpty()) {
                        // Header has a key
                        item(key = "header-vehicles") {
                                Text(
                                        stringResource(R.string.vehicles),
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.padding(top = 8.dp)
                                )
                        }
                        // Items have stable keys
                        items(
                                items = vehicles,
                                key = { v: MapSelectionSelector.VehicleMapSelector ->
                                        // Ensure uniqueness even when trip_id is null
                                        "veh:${v.chateau_id}:${v.vehicle_id ?: v.trip_id ?: "${v.hashCode()}"}"
                                },
                                contentType = { "vehicle" }
                        ) { vehicle: MapSelectionSelector.VehicleMapSelector ->
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        VehicleSelectionItem(
                                                option = vehicle,
                                                onClick = {
                                                        val newStackItem =
                                                                if (it.trip_id != null) {
                                                                        CatenaryStackEnum
                                                                                .SingleTrip(
                                                                                        chateau_id =
                                                                                                it.chateau_id,
                                                                                        trip_id =
                                                                                                it.trip_id,
                                                                                        route_id =
                                                                                                it.route_id,
                                                                                        start_time =
                                                                                                it.start_time,
                                                                                        start_date =
                                                                                                it.start_date,
                                                                                        vehicle_id =
                                                                                                it.vehicle_id,
                                                                                        route_type =
                                                                                                it.route_type
                                                                                )
                                                                } else {
                                                                        CatenaryStackEnum
                                                                                .VehicleSelectedStack(
                                                                                        chateau_id =
                                                                                                it.chateau_id,
                                                                                        vehicle_id =
                                                                                                it.vehicle_id,
                                                                                        gtfs_id =
                                                                                                it.gtfs_id
                                                                                )
                                                                }
                                                        onStackPush(newStackItem)
                                                }
                                        )
                                }
                        }
                }

                if (osmStations.isNotEmpty()) {
                        item(key = "header-stations") {
                                Text(
                                        stringResource(R.string.stations),
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.padding(top = 8.dp)
                                )
                        }
                        items(
                                items = osmStations,
                                key = { s -> "osm:${s.osm_id}" },
                                contentType = { "osm_station" }
                        ) { station ->
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        val preview = osmStationPreviewData[station.osm_id]
                                        OsmStationSelectionItem(
                                                option = station,
                                                previewData = preview,
                                                onClick = {
                                                        onStackPush(
                                                                CatenaryStackEnum.OsmStationStack(
                                                                        osm_station_id = it.osm_id,
                                                                        station_name = it.name,
                                                                        mode_type = it.mode_type
                                                                )
                                                        )
                                                }
                                        )
                                }
                        }
                }

                if (stops.isNotEmpty()) {
                        item(key = "header-stops") {
                                Text(
                                        stringResource(R.string.stops),
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.padding(top = 4.dp)
                                )
                        }
                        items(
                                items = stops,
                                key = { s -> "stop:${s.chateau_id}:${s.stop_id}" },
                                contentType = { "stop" }
                        ) { stop ->
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        val preview =
                                                stopPreviewData
                                                        ?.stops
                                                        ?.get(stop.chateau_id)
                                                        ?.get(stop.stop_id)
                                        val routeCache =
                                                stopPreviewData?.routes?.get(stop.chateau_id)

                                        StopSelectionItem(
                                                option = stop,
                                                previewData = preview,
                                                routeData = routeCache,
                                                onClick = {
                                                        onStackPush(
                                                                CatenaryStackEnum.StopStack(
                                                                        it.chateau_id,
                                                                        it.stop_id
                                                                )
                                                        )
                                                }
                                        )
                                }
                        }
                }

                if (routes.isNotEmpty()) {
                        // Group routes by category type
                        // 2=Rail, 1=Metro, 0=Tram, 3=Bus, else=Other
                        val bucketedRoutes =
                                routes.groupBy {
                                        when (it.route_type) {
                                                2 -> 2 // Rail
                                                1 -> 1 // Metro
                                                0 -> 0 // Tram
                                                3 -> 3 // Bus
                                                else -> -1 // Other
                                        }
                                }

                        // Priority: Rail(2), Metro(1), Tram(0), Other(-1), Bus(3)
                        val displayOrder = listOf(2, 1, 0, -1, 3)

                        item(key = "header-routes-main") {
                                Text(
                                        stringResource(R.string.routes),
                                        style = MaterialTheme.typography.titleLarge,
                                        modifier = Modifier.padding(top = 4.dp, bottom = 0.dp)
                                )
                        }

                        displayOrder.forEach { catId ->
                                val bucket = bucketedRoutes[catId]
                                if (!bucket.isNullOrEmpty()) {
                                        val titleRes =
                                                when (catId) {
                                                        2 -> R.string.route_category_rail
                                                        1 -> R.string.route_category_metro
                                                        0 -> R.string.route_category_tram
                                                        3 -> R.string.route_category_bus
                                                        else -> R.string.route_category_other
                                                }

                                        item(key = "header-routes-sub-$catId") {
                                                Text(
                                                        stringResource(titleRes),
                                                        style =
                                                                MaterialTheme.typography
                                                                        .titleMedium,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.padding(vertical = 0.dp)
                                                )
                                        }

                                        if (catId == 1 || catId == 0) {
                                                // Metro(1) or Tram(0): Wrapping Layout
                                                item(key = "routes-grid-$catId") {
                                                        FlowRow(
                                                                horizontalArrangement =
                                                                        Arrangement.spacedBy(0.dp),
                                                                verticalArrangement =
                                                                        Arrangement.spacedBy(1.dp),
                                                                modifier =
                                                                        Modifier
                                                                                .fillMaxWidth()
                                                                                .padding(
                                                                                        bottom =
                                                                                                4.dp
                                                                                )
                                                        ) {
                                                                bucket.forEach { route ->
                                                                        CompactRouteSelectionItem(
                                                                                option = route,
                                                                                onClick = {
                                                                                        onStackPush(
                                                                                                CatenaryStackEnum
                                                                                                        .RouteStack(
                                                                                                                it.chateau_id,
                                                                                                                it.route_id
                                                                                                        )
                                                                                        )
                                                                                }
                                                                        )
                                                                }
                                                        }
                                                }
                                        } else {
                                                // Rail(2), Bus(3), Other(-1): List Layout
                                                item(key = "routes-col-$catId") {
                                                        Column(
                                                                verticalArrangement =
                                                                        Arrangement.spacedBy(0.dp)
                                                        ) {
                                                                bucket.forEach { route ->
                                                                        RouteSelectionItem(
                                                                                option = route,
                                                                                onClick = {
                                                                                        onStackPush(
                                                                                                CatenaryStackEnum
                                                                                                        .RouteStack(
                                                                                                                it.chateau_id,
                                                                                                                it.route_id
                                                                                                        )
                                                                                        )
                                                                                }
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
