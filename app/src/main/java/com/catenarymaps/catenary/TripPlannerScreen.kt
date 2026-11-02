package com.catenarymaps.catenary

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import io.ktor.client.HttpClient
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.maplibre.spatialk.geojson.Position
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt

@Composable
fun DirectionsActions(location: DirectionsLocation?, onDirections: (Boolean, DirectionsLocation) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 8.dp)) {
        OutlinedButton(enabled = location?.isValid() == true, onClick = { location?.let { onDirections(true, it) } }) {
            Text(stringResource(R.string.directions_as_origin))
        }
        OutlinedButton(enabled = location?.isValid() == true, onClick = { location?.let { onDirections(false, it) } }) {
            Text(stringResource(R.string.directions_as_destination))
        }
    }
}

@Composable
fun TripPlannerScreen(
    planner: TripPlannerViewModel, client: HttpClient, search: SearchViewModel,
    currentLocation: Pair<Double, Double>?, mapCenter: Position, stationHistory: StationHistory,
    onSelectJourney: (DirectionsItinerary) -> Unit, onBack: () -> Unit, onHome: () -> Unit
) {
    val focus = LocalFocusManager.current
    val rows by search.searchRows.collectAsState()
    val searchLoading by search.isLoading.collectAsState()
    var showOptions by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("directions_recent", android.content.Context.MODE_PRIVATE) }
    var recent by remember {
        mutableStateOf(runCatching {
            Json.decodeFromString<List<DirectionsLocation>>(prefs.getString("places", "[]")!!)
        }.getOrDefault(emptyList()).filter { it.isValid() })
    }
    fun select(isOrigin: Boolean, location: DirectionsLocation, save: Boolean = true) {
        planner.choose(isOrigin, location)
        focus.clearFocus()
        if (save) {
            recent = (listOf(location) + recent.filter {
                it.name != location.name || kotlin.math.abs(it.lat - location.lat) > 0.00001 ||
                    kotlin.math.abs(it.lon - location.lon) > 0.00001
            }).take(20)
            prefs.edit().putString("places", Json.encodeToString(recent)).apply()
        }
    }
    // Match the web planner: resolve a new journey after choosing endpoints or changing options.
    LaunchedEffect(planner.origin, planner.destination, planner.time, planner.now, planner.arriveBy, planner.options) {
        if (planner.origin != null && planner.destination != null) {
            kotlinx.coroutines.delay(300)
            if (!planner.searched) planner.search(client)
        }
    }
    androidx.activity.compose.BackHandler(enabled = planner.details) { planner.details = false }
    LaunchedEffect(planner.editing) { if (planner.editing == null) focus.clearFocus() }
    val query = when (planner.editing) { true -> planner.originText; false -> planner.destinationText; null -> "" }
    LaunchedEffect(query, planner.editing) {
        search.onSearchQueryChanged(if (planner.editing == null) "" else query, currentLocation, mapCenter)
    }
    DisposableEffect(Unit) { onDispose { search.onSearchQueryChanged("", null, mapCenter) } }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.directions_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            NavigationControls(onBack = { if (planner.details) planner.details = false else onBack() }, onHome = onHome)
        }
        if (planner.details) {
            TextButton(onClick = { planner.details = false }) { Text(stringResource(R.string.directions_all_journeys)) }
            planner.itineraries.getOrNull(planner.selected)?.let { journey ->
                JourneyPreview(journey, true, onClick = { onSelectJourney(journey) }, onDetails = { planner.details = false })
                LazyColumn(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    items(journey.detailLegs()) { leg -> JourneyLegDetails(leg) }
                }
            }
        } else {
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    OutlinedTextField(value = planner.originText, onValueChange = { planner.edit(true, it) },
                        placeholder = { Text(stringResource(R.string.directions_start_placeholder)) },
                        label = { Text(stringResource(R.string.directions_origin)) }, singleLine = true,
                        modifier = Modifier.fillMaxWidth().onFocusChanged { if (it.isFocused) planner.editing = true })
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        IconButton(onClick = { focus.clearFocus(); planner.swap() }) {
                            Icon(Icons.Default.SwapVert, stringResource(R.string.directions_swap))
                        }
                    }
                    OutlinedTextField(value = planner.destinationText, onValueChange = { planner.edit(false, it) },
                        placeholder = { Text(stringResource(R.string.directions_destination_placeholder)) },
                        label = { Text(stringResource(R.string.directions_destination)) }, singleLine = true,
                        modifier = Modifier.fillMaxWidth().onFocusChanged { if (it.isFocused) planner.editing = false })
                }
                if (planner.editing != null) {
                    item {
                        Text(stringResource(R.string.directions_map_hint), style = MaterialTheme.typography.bodySmall)
                        TextButton(enabled = currentLocation != null, onClick = {
                            currentLocation?.let { select(planner.editing ?: true,
                                DirectionsLocation(context.getString(R.string.directions_my_location), it.first, it.second), false) }
                        }) { Icon(Icons.Default.MyLocation, null); Text(stringResource(R.string.directions_my_location)) }
                        parseDirectionsCoordinate(query)?.let { coordinate ->
                            TextButton(onClick = { select(planner.editing ?: true, coordinate) }) {
                                Icon(Icons.Default.GridOn, null); Text(coordinate.name)
                            }
                        }
                        if (searchLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    val history = stationHistory.entries.mapNotNull { item ->
                        val latitude = item.latitude; val longitude = item.longitude
                        if (latitude != null && longitude != null) DirectionsLocation(item.name, latitude, longitude).takeIf { it.isValid() } else null
                    }
                    val suggestions = (if (query.isBlank()) recent + history else rows.mapNotNull { it.directionsLocation() })
                        .distinctBy { Triple(it.name, it.lat, it.lon) }
                    items(suggestions.take(12)) { location ->
                        ListItem(headlineContent = { Text(location.name) },
                            supportingContent = { Text("%.5f, %.5f".format(location.lat, location.lon)) },
                            modifier = Modifier.clickable { select(planner.editing ?: true, location) })
                    }
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FilterChip(selected = !planner.arriveBy, onClick = { planner.invalidate(); planner.arriveBy = false },
                            label = { Text(stringResource(R.string.directions_depart_at)) })
                        Spacer(Modifier.width(8.dp))
                        FilterChip(selected = planner.arriveBy, onClick = {
                            planner.invalidate(); planner.arriveBy = true; planner.now = false
                        }, label = { Text(stringResource(R.string.directions_arrive_by)) })
                    }
                    TimeSelectorButton(epochSeconds = planner.time, timezoneId = ZoneId.systemDefault().id,
                        isNow = planner.now, onTimeChange = { planner.invalidate(); planner.time = it; planner.now = false },
                        onIsNowChange = { planner.invalidate(); planner.now = it; if (it) planner.arriveBy = false })
                    TextButton(onClick = { showOptions = !showOptions }) {
                        Icon(Icons.Default.Tune, null)
                        Text(stringResource(R.string.directions_speed_options))
                        if (planner.options != DirectionsOptions()) Text(" •", color = MaterialTheme.colorScheme.primary)
                    }
                    if (showOptions) StreetOptions(planner)
                    Button(enabled = planner.origin != null && planner.destination != null && !planner.loading,
                        onClick = { focus.clearFocus(); planner.search(client) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.directions_search))
                    }
                    if (planner.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    planner.error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(8.dp))
                    }
                    if (planner.searched && !planner.loading && planner.error == null && planner.itineraries.isEmpty()) {
                        Text(stringResource(R.string.directions_no_journeys))
                    }
                }
                items(planner.itineraries.size) { index ->
                    val journey = planner.itineraries[index]
                    JourneyPreview(journey, index == planner.selected,
                        onClick = { planner.selected = index; planner.showTransfers = true; focus.clearFocus(); planner.editing = null; onSelectJourney(journey) },
                        onDetails = { planner.selected = index; planner.showTransfers = true; planner.details = true; planner.editing = null; focus.clearFocus(); onSelectJourney(journey) })
                }
            }
        }
    }
}

@Composable
private fun StreetOptions(planner: TripPlannerViewModel) {
    val options = planner.options
    fun update(value: DirectionsOptions) { planner.invalidate(); planner.options = value }
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !options.cycling, onClick = { update(options.copy(cycling = false, accessSeconds = 900f, egressSeconds = 900f)) },
                label = { Text(stringResource(R.string.directions_walk)) }, leadingIcon = { Icon(Icons.Default.DirectionsWalk, null) })
            FilterChip(selected = options.cycling, onClick = { update(options.copy(cycling = true, accessSeconds = 1800f, egressSeconds = 1800f)) },
                label = { Text(stringResource(R.string.directions_cycle)) }, leadingIcon = { Icon(Icons.Default.DirectionsBike, null) })
        }
        Text(stringResource(R.string.directions_walk_pace, (options.walkingPace / 60).toInt(), (options.walkingPace % 60).toInt(), 3600 / options.walkingPace))
        Slider(value = 1440 - options.walkingPace, onValueChange = { update(options.copy(walkingPace = 1440 - it)) }, valueRange = 240f..1200f)
        Text(stringResource(R.string.directions_cycle_speed, options.cyclingKmh))
        Slider(value = options.cyclingKmh, onValueChange = { update(options.copy(cyclingKmh = it)) }, valueRange = 5f..40f)
        fun limitLabel(seconds: Float) = "${(seconds / 60).roundToInt()} min"
        Text(stringResource(R.string.directions_access_limit, limitLabel(options.accessSeconds)))
        Slider(value = options.accessSeconds, onValueChange = { update(options.copy(accessSeconds = it)) }, valueRange = 0f..7200f)
        Text(stringResource(R.string.directions_egress_limit, limitLabel(options.egressSeconds)))
        Slider(value = options.egressSeconds, onValueChange = { update(options.copy(egressSeconds = it)) }, valueRange = 0f..7200f)
        Text(stringResource(R.string.directions_direct_limit, limitLabel(options.directSeconds)))
        Slider(value = options.directSeconds, onValueChange = { update(options.copy(directSeconds = it)) }, valueRange = 0f..14400f)
        TextButton(onClick = { update(DirectionsOptions()) }) { Text(stringResource(R.string.directions_reset)) }
    }
}

internal fun directionsTime(value: String?): String = runCatching {
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withZone(ZoneId.systemDefault()).format(Instant.parse(value))
}.getOrDefault("")
internal fun directionsDuration(seconds: Double): String {
    val minutes = (seconds / 60).roundToInt().coerceAtLeast(0)
    return if (minutes >= 60) "${minutes / 60} h ${minutes % 60} min" else "$minutes min"
}
private fun legColor(leg: DirectionsLeg) = parseColor(leg.routeColor ?: "", Color(0xFF2563EB))

@Composable
private fun LegModeIcon(leg: DirectionsLeg) {
    val icon = when {
        leg.street -> null
        leg.mode.uppercase() in setOf("BUS", "COACH") || leg.routeType == 3 -> "bus"
        leg.mode.uppercase() in setOf("SUBWAY", "METRO") || leg.routeType == 1 -> "metro"
        leg.mode.uppercase() in setOf("TRAM", "LIGHT_RAIL") || leg.routeType == 0 -> "tram"
        leg.mode.uppercase() in setOf("RAIL", "TRAIN", "REGIONAL_RAIL", "HIGH_SPEED_RAIL") || leg.routeType == 2 -> "train"
        else -> null
    }
    if (icon != null) {
        val context = LocalContext.current
        val loader = remember(context) { ImageLoader.Builder(context).components { add(SvgDecoder.Factory()) }.build() }
        AsyncImage(model = "file:///android_asset/directions/$icon.svg", imageLoader = loader,
            contentDescription = leg.mode, colorFilter = ColorFilter.tint(LocalContentColor.current), modifier = Modifier.size(24.dp))
    } else Icon(when (leg.mode.uppercase()) {
        "BIKE", "BICYCLE" -> Icons.Default.DirectionsBike
        "WALK" -> Icons.Default.DirectionsWalk
        "FERRY" -> Icons.Default.DirectionsBoat
        else -> Icons.Default.Train
    }, leg.mode, modifier = Modifier.size(24.dp))
}

@Composable
private fun LegBadge(leg: DirectionsLeg) {
    SelectionRouteBadges(routeIds = listOf(leg.routeId ?: leg.label), resolveRouteInfo = {
        ResolvedRouteBadgeInfo(leg.routeId ?: leg.label, leg.routeShortName, leg.routeLongName,
            leg.routeColor ?: "2563EB", leg.routeTextColor ?: "FFFFFF", chateauId = leg.chateau)
    })
}

@Composable
private fun JourneyPreview(journey: DirectionsItinerary, selected: Boolean, onClick: () -> Unit, onDetails: () -> Unit) {
    val visible = remember(journey) { journey.previewLegs() }
    val firstTransit = visible.firstOrNull { !it.street }
    val lastTransit = visible.lastOrNull { !it.street }
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(directionsTime(journey.startTime), fontWeight = if (visible.firstOrNull()?.street == false) FontWeight.Bold else FontWeight.Normal)
                    if (visible.firstOrNull()?.street == true && firstTransit != null) Text(directionsTime(firstTransit.startTime), fontWeight = FontWeight.Bold)
                }
                Text(directionsDuration(journey.seconds), fontWeight = FontWeight.Bold)
                Column(horizontalAlignment = Alignment.End) {
                    if (visible.lastOrNull()?.street == true && lastTransit != null) Text(directionsTime(lastTransit.endTime), fontWeight = FontWeight.Bold)
                    Text(directionsTime(journey.endTime), fontWeight = if (visible.lastOrNull()?.street == false) FontWeight.Bold else FontWeight.Normal)
                }
            }
            Row(Modifier.fillMaxWidth().height(12.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                visible.forEach { leg ->
                    val color = if (leg.street) MaterialTheme.colorScheme.onSurfaceVariant else legColor(leg)
                    Canvas(Modifier.weight(leg.seconds.toFloat().coerceAtLeast(1f)).fillMaxHeight()) {
                        drawLine(color, androidx.compose.ui.geometry.Offset(0f, size.height / 2),
                            androidx.compose.ui.geometry.Offset(size.width, size.height / 2), strokeWidth = 6f,
                            pathEffect = if (leg.street) PathEffect.dashPathEffect(floatArrayOf(3f, 7f)) else null)
                    }
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                visible.forEach { leg ->
                    LegModeIcon(leg)
                    if (leg.street) {
                        Text(directionsDuration(leg.seconds) + (directionsDistance(leg)?.let { " · ${it.roundToInt()} m" } ?: ""), fontSize = 12.sp)
                    } else LegBadge(leg)
                }
            }
            TextButton(onClick = onDetails) { Text(stringResource(R.string.directions_show_details)) }
        }
    }
}

@Composable
private fun JourneyLegDetails(leg: DirectionsLeg) {
    var expanded by remember(leg) { mutableStateOf(false) }
    val rail = if (leg.street) MaterialTheme.colorScheme.onSurfaceVariant else legColor(leg)
    Row(Modifier.fillMaxWidth()) {
        Column(Modifier.width(62.dp)) {
            Text(directionsTime(leg.startTime), fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            Text(directionsTime(leg.endTime))
        }
        // Pearl line: outlined stop dots with a solid transit or dotted walking connection.
        Canvas(Modifier.width(20.dp).height(110.dp)) {
            drawLine(rail, androidx.compose.ui.geometry.Offset(size.width / 2, 8f),
                androidx.compose.ui.geometry.Offset(size.width / 2, size.height - 8f), strokeWidth = 4f,
                pathEffect = if (leg.street) PathEffect.dashPathEffect(floatArrayOf(3f, 8f)) else null)
            drawCircle(rail, 6f, androidx.compose.ui.geometry.Offset(size.width / 2, 8f), style = Stroke(3f))
            drawCircle(rail, 6f, androidx.compose.ui.geometry.Offset(size.width / 2, size.height - 8f), style = Stroke(3f))
        }
        Column(Modifier.weight(1f).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(leg.from?.name.orEmpty(), modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                Text(leg.fromPlatform ?: leg.from?.platform ?: leg.from?.platformCode ?: "", style = MaterialTheme.typography.labelSmall)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LegModeIcon(leg)
                if (leg.street) Text(directionsDuration(leg.seconds) + (directionsDistance(leg)?.let { " · ${it.roundToInt()} m" } ?: ""))
                else LegBadge(leg)
            }
            if (!leg.street) {
                TextButton(onClick = { expanded = !expanded }, contentPadding = PaddingValues(0.dp)) {
                    Text(stringResource(R.string.directions_stops_duration, leg.intermediateStops.size, directionsDuration(leg.seconds)))
                    Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                }
                if (expanded) leg.intermediateStops.forEach {
                    Text("${directionsTime(it.departure ?: it.arrival ?: it.scheduledDeparture ?: it.scheduledArrival)}  ${it.name}", style = MaterialTheme.typography.bodySmall)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(leg.to?.name.orEmpty(), modifier = Modifier.weight(1f))
                Text(leg.toPlatform ?: leg.to?.platform ?: leg.to?.platformCode ?: "", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/** Combine consecutive street legs for presentation while retaining the original map geometry. */
internal fun DirectionsItinerary.detailLegs(): List<DirectionsLeg> = legs.fold(emptyList<DirectionsLeg>()) { result, leg ->
    val previous = result.lastOrNull()
    if (previous?.street == true && previous.mode == leg.mode && leg.street) {
        // A combined geometry would otherwise draw an artificial connector across missing paths.
        val a = directionsCoordinates(previous); val b = directionsCoordinates(leg)
        val geometry = if (a.isNotEmpty() && b.isNotEmpty()) DirectionsGeometry(encodeDirectionsPolyline(a + b)) else null
        result.dropLast(1) + previous.copy(to = leg.to, endTime = leg.endTime, duration = previous.seconds + leg.seconds, legGeometry = geometry)
    } else result + leg
}
internal fun DirectionsItinerary.previewLegs(): List<DirectionsLeg> {
    val combined = detailLegs()
    return combined.filterIndexed { index, leg ->
        if (!leg.street) true
        else if (index == 0 || index == combined.lastIndex) directionsDistance(leg)?.let { it >= 25 } ?: true
        else leg.seconds > 300
    }
}
