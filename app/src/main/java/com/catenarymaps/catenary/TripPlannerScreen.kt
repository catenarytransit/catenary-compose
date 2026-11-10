package com.catenarymaps.catenary

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.flow.first
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.layout.onSizeChanged
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

/** Shared by the drawer surface and its cards, using the active app theme. */
internal object TripPlannerColors {
    private val dark: Boolean
        @Composable get() = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    val background: Color
        @Composable get() = if (dark) Color(0xFF111214) else Color(0xFFF7F7F7)
    val card: Color
        @Composable get() = if (dark) Color(0xFF202124) else Color.White
    val content: Color
        @Composable get() = if (dark) Color(0xFFF1F3F4) else Color(0xFF202124)
    val secondaryContent: Color
        @Composable get() = if (dark) Color(0xFFBDC1C6) else Color(0xFF5F6368)
    val streetLine: Color
        @Composable get() = if (dark) Color(0xFF67E8F9) else Color(0xFF2563EB)
    val streetBorder: Color
        @Composable get() = if (dark) Color(0xFF5F6368) else Color(0xFF9AA0A6)
}

/** Fullscreen results and drawer details share rendering, but never share scroll state. */
@Composable
fun TripPlannerScreen(
    planner: TripPlannerViewModel, client: HttpClient, streetUsUnits: Boolean,
    detailsTopPadding: androidx.compose.ui.unit.Dp = 0.dp,
    resultsOnly: Boolean = false,
    resultsState: LazyListState = rememberLazyListState(),
    onSelectJourney: (DirectionsItinerary) -> Unit = {}
) {
    val focus = LocalFocusManager.current
    // Match the web planner: resolve a new journey after choosing endpoints or changing options.
    LaunchedEffect(planner.origin, planner.destination, planner.time, planner.now, planner.arriveBy, planner.options) {
        if (planner.origin != null && planner.destination != null) {
            kotlinx.coroutines.delay(300)
            if (!planner.searched) planner.search(client)
        }
    }
    androidx.activity.compose.BackHandler(enabled = planner.details) { planner.details = false }
    if (planner.details && !resultsOnly) {
        planner.selectedJourney?.let { journey -> JourneyDetails(planner, journey, streetUsUnits, detailsTopPadding) }
    } else {
        ConnectionResults(planner, client, streetUsUnits, resultsState) { journey ->
            planner.selectJourney(journey)
            focus.clearFocus()
            onSelectJourney(journey)
        }
    }
}

@Composable
internal fun JourneyDetails(
    planner: TripPlannerViewModel, journey: DirectionsItinerary, streetUsUnits: Boolean,
    topPadding: androidx.compose.ui.unit.Dp = 0.dp
) {
    key(journey.identity()) {
        val state = rememberLazyListState()
        LaunchedEffect(state) {
            snapshotFlow { state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > 0 }
                .collect { planner.detailsScrolled = it }
        }
        LazyColumn(Modifier.fillMaxSize().padding(top = topPadding), state = state, contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { JourneyEndpoint(planner.origin?.name.orEmpty(), true) }
            items(journey.detailLegs()) { leg -> JourneyLegDetails(leg, streetUsUnits) }
            item { JourneyEndpoint(planner.destination?.name.orEmpty(), false) }
        }
    }
}

@Composable
private fun ConnectionResults(
    planner: TripPlannerViewModel, client: HttpClient, streetUsUnits: Boolean,
    state: LazyListState,
    onSelectJourney: (DirectionsItinerary) -> Unit
) {
    val journeys = planner.itineraries
    LaunchedEffect(planner.searched) {
        if (planner.searched && state.firstVisibleItemIndex == 0 && state.firstVisibleItemScrollOffset == 0) {
            snapshotFlow { planner.itineraries.isNotEmpty() }.first { it }
            // Align the first connection with the inputs; skip the idle progress slot.
            state.requestScrollToItem(2)
        }
    }
    PreserveConnectionScrollPosition(state, journeys.map { it.identity() })
    var scrollDirection by remember { mutableIntStateOf(0) }
    fun loadAtEdge(direction: Int) {
        if (planner.error != null || planner.itineraries.isEmpty()) return
        val page = connectionPageAtEdge(direction, state.firstVisibleItemIndex,
            state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1, planner.itineraries.size)
        if (page == ConnectionDirection.BEFORE && planner.canLoadEarlier) {
            planner.loadConnections(client, earlier = true)
        } else if (page == ConnectionDirection.AFTER && planner.canLoadLater) {
            planner.loadConnections(client, earlier = false)
        }
    }
    // Observe layout after scrolling, including fling movement and cursor availability.
    LaunchedEffect(state, planner, client) {
        snapshotFlow {
            listOf(state.isScrollInProgress, state.firstVisibleItemIndex,
                state.layoutInfo.visibleItemsInfo.lastOrNull()?.index,
                scrollDirection, planner.canLoadEarlier, planner.canLoadLater, planner.error)
        }.collect { if (state.isScrollInProgress) loadAtEdge(scrollDirection) }
    }
    val paging = remember(state, planner, client) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y != 0f) {
                    scrollDirection = if (available.y > 0) 1 else -1
                }
                return Offset.Zero
            }
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                loadAtEdge(scrollDirection)
                return Offset.Zero
            }
            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                scrollDirection = 0
                return Velocity.Zero
            }
        }
    }
    LazyColumn(Modifier.fillMaxSize().background(TripPlannerColors.background).nestedScroll(paging), state = state,
        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 8.dp)) {
        item(key = "results-status") {
            planner.error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(8.dp)) }
            if (planner.searched && !planner.loading && planner.error == null && journeys.isEmpty()) {
                Text(stringResource(R.string.directions_no_journeys))
            }
        }
        item(key = "earlier") { ConnectionLoadingEdge(planner, client, earlier = true) }
        items(journeys, key = { it.identity() }) { journey ->
            JourneyPreview(journey, streetUsUnits, onClick = { onSelectJourney(journey) },
                modifier = Modifier.padding(bottom = 8.dp))
        }
        if (planner.loading && journeys.isEmpty()) items(3, key = { "skeleton-$it" }) {
            Box(Modifier.padding(bottom = 8.dp)) { ConnectionSkeleton() }
        }
        item(key = "later") { ConnectionLoadingEdge(planner, client, earlier = false) }
    }
}

@Composable
private fun ConnectionLoadingEdge(planner: TripPlannerViewModel, client: HttpClient, earlier: Boolean) {
    val busy = if (earlier) planner.earlierLoading else planner.laterLoading
    val retry = planner.error != null && (if (earlier) planner.canLoadEarlier else planner.canLoadLater)
    // Idle sentinels have no height, so even a short first page starts beneath the inputs.
    Box(Modifier.fillMaxWidth().height(if (busy || retry) 48.dp else 0.dp), contentAlignment = Alignment.Center) {
        if (busy) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        else if (retry) {
            IconButton(onClick = { planner.loadConnections(client, earlier) }) {
                Icon(Icons.Default.Refresh, stringResource(R.string.directions_retry))
            }
        }
    }
}

@Composable
private fun ConnectionSkeleton() {
    val transition = rememberInfiniteTransition(label = "connection-loading")
    val alpha by transition.animateFloat(0.25f, 0.65f,
        infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "connection-pulse")
    Card(colors = CardDefaults.cardColors(containerColor = TripPlannerColors.card), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.fillMaxWidth(0.65f).height(18.dp).background(TripPlannerColors.secondaryContent.copy(alpha = alpha), MaterialTheme.shapes.small))
            Box(Modifier.fillMaxWidth().height(28.dp).background(TripPlannerColors.secondaryContent.copy(alpha = alpha), MaterialTheme.shapes.small))
        }
    }
}

@Composable
fun TripPlannerHeader(
    planner: TripPlannerViewModel, search: SearchViewModel,
    currentLocation: Pair<Double, Double>?, mapCenter: Position, stationHistory: StationHistory,
    streetUsUnits: Boolean,
    modifier: Modifier = Modifier, cardModifier: Modifier = Modifier,
    compact: Boolean = false, collapseInputs: Boolean = false, collapseDetails: Boolean = false,
    onBack: () -> Unit = {}, onExpandedHeight: (Int) -> Unit = {},
    onDetailsHeight: (Int) -> Unit = {},
    forceInputs: Boolean = false
) {
    val focus = LocalFocusManager.current
    val originFocus = remember { FocusRequester() }
    val destinationFocus = remember { FocusRequester() }
    LaunchedEffect(planner.editing, forceInputs, planner.details) {
        if (!planner.details || forceInputs) {
            when (planner.editing) {
                true -> originFocus.requestFocus()
                false -> destinationFocus.requestFocus()
                null -> focus.clearFocus()
            }
        }
    }
    val rows by search.searchRows.collectAsState()
    val searchLoading by search.isLoading.collectAsState()
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("directions_recent", android.content.Context.MODE_PRIVATE) }
    var recent by remember {
        mutableStateOf(runCatching {
            Json.decodeFromString<List<DirectionsLocation>>(prefs.getString("places", "[]")!!)
        }.getOrDefault(emptyList()).filter { it.isValid() })
    }
    fun select(isOrigin: Boolean, location: DirectionsLocation, save: Boolean = true) {
        planner.choose(isOrigin, location)
        if (planner.editing == null) focus.clearFocus()
        if (save) {
            recent = (listOf(location) + recent.filter {
                it.name != location.name || kotlin.math.abs(it.lat - location.lat) > 0.00001 ||
                    kotlin.math.abs(it.lon - location.lon) > 0.00001
            }).take(20)
            prefs.edit().putString("places", Json.encodeToString(recent)).apply()
        }
    }
    LaunchedEffect(planner.editing) { if (planner.editing == null) focus.clearFocus() }
    val query = when (planner.editing) { true -> planner.originText; false -> planner.destinationText; null -> "" }
    LaunchedEffect(query, planner.editing) {
        search.onSearchQueryChanged(if (planner.editing == null) "" else query, currentLocation, mapCenter)
    }
    DisposableEffect(Unit) { onDispose { search.onSearchQueryChanged("", null, mapCenter) } }

    androidx.activity.compose.BackHandler(enabled = planner.editing != null) {
        planner.editing = null
        focus.clearFocus()
    }
    var inputsExpanded by remember { mutableStateOf(false) }
    LaunchedEffect(collapseInputs) { inputsExpanded = false }
    val collapsed = collapseInputs && !inputsExpanded && planner.editing == null
    val originName = planner.originText.ifBlank { stringResource(R.string.directions_origin) }
    val destinationName = planner.destinationText.ifBlank { stringResource(R.string.directions_destination) }
    Column(modifier.imePadding().onSizeChanged { if (planner.details && !forceInputs) onDetailsHeight(it.height) }) {
        if (planner.details && !forceInputs) {
            JourneySummary(planner, streetUsUnits, collapseDetails, cardModifier)
        } else ElevatedCard(modifier = cardModifier.fillMaxWidth().padding(8.dp)
                .then(Modifier.animateContentSize()),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 8.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = TripPlannerColors.card, contentColor = TripPlannerColors.content)) {
            if (collapsed) Row(Modifier.fillMaxWidth().clickable { inputsExpanded = true }.padding(end = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.osm_back)) }
                Text("$originName → $destinationName", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            } else Column(Modifier.onSizeChanged { onExpandedHeight(it.height) }
                .padding(horizontal = if (compact) 4.dp else 12.dp, vertical = if (compact) 0.dp else 4.dp)) {
                Row(Modifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack, modifier = Modifier.align(Alignment.Top).size(if (compact) 40.dp else 48.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.osm_back))
                    }
                    EndpointPearls(planner.origin?.isCurrentLocation == true, planner.destination?.isCurrentLocation == true)
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.weight(1f)) {
                        Column {
                            EndpointInput(planner.originText, stringResource(R.string.directions_start_placeholder),
                                stringResource(R.string.directions_origin), { planner.edit(true, it) }, { planner.editing = true }, originFocus, compact)
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            EndpointInput(planner.destinationText, stringResource(R.string.directions_destination_placeholder),
                                stringResource(R.string.directions_destination), { planner.edit(false, it) }, { planner.editing = false }, destinationFocus, compact)
                        }
                        Surface(modifier = Modifier.align(Alignment.CenterEnd), shape = CircleShape,
                            color = TripPlannerColors.card, contentColor = TripPlannerColors.content, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
                            IconButton(onClick = { focus.clearFocus(); planner.swap() }) {
                                Icon(Icons.Default.SwapVert, stringResource(R.string.directions_swap))
                            }
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(start = 32.dp), color = MaterialTheme.colorScheme.outlineVariant)
                JourneyTimeControls(planner, Modifier.padding(start = 32.dp))
            }
        }
        if (planner.editing != null) {
            Surface(Modifier.fillMaxWidth().weight(1f), color = MaterialTheme.colorScheme.surface) {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                    item {
                        TextButton(onClick = { planner.editing = null; focus.clearFocus() }) {
                            Text(stringResource(R.string.cancel))
                        }
                        if (query.isBlank()) {
                            SearchResultRow(stringResource(R.string.directions_choose_on_map), icon = Icons.Default.LocationOn,
                                onClick = { planner.mapPicking = planner.editing; planner.editing = null; focus.clearFocus() })
                        }
                        SearchResultRow(stringResource(R.string.directions_my_location), icon = Icons.Default.MyLocation,
                            enabled = currentLocation != null, onClick = {
                                currentLocation?.let { select(planner.editing ?: true,
                                    DirectionsLocation(context.getString(R.string.directions_my_location), it.first, it.second, isCurrentLocation = true), false) }
                            })
                        parseDirectionsCoordinate(query)?.let { coordinate ->
                            val reference = currentLocation ?: (mapCenter.latitude to mapCenter.longitude)
                            SearchResultRow(coordinate.name, icon = Icons.Default.GridOn,
                                distance = plannerDistance(haversineDistance(reference.first, reference.second, coordinate.lat, coordinate.lon), streetUsUnits),
                                onClick = { select(planner.editing ?: true, coordinate) })
                        }
                        if (searchLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    val history = stationHistory.entries.mapNotNull { item ->
                        val latitude = item.latitude; val longitude = item.longitude
                        if (latitude != null && longitude != null) DirectionsLocation(item.name, latitude, longitude).takeIf { it.isValid() } else null
                    }
                    val reference = currentLocation ?: (mapCenter.latitude to mapCenter.longitude)
                    if (query.isBlank()) {
                        val suggestions = (recent + history).distinctBy { Triple(it.name, it.lat, it.lon) }
                        items(suggestions.take(12)) { location ->
                            SearchResultRow(location.name, icon = Icons.Default.History,
                                distance = plannerDistance(haversineDistance(reference.first, reference.second, location.lat, location.lon), streetUsUnits),
                                onClick = { select(planner.editing ?: true, location) })
                        }
                    } else {
                        // Retain the source row so addresses, location types and route badges are shared with regular search.
                        val suggestions = rows.mapNotNull { row -> row.directionsLocation()?.let { row to it } }
                            .distinctBy { (_, location) -> Triple(location.name, location.lat, location.lon) }
                        items(suggestions.take(12)) { (row, location) ->
                            val distance = plannerDistance(haversineDistance(reference.first, reference.second, location.lat, location.lon), streetUsUnits)
                            val onClick = { select(planner.editing ?: true, location) }
                            when (row) {
                                is SearchRow.CypressRow -> CypressResultItem(row.item, distance, onClick)
                                is SearchRow.OsmStationRow -> OsmStationResultItem(row.result, distance, onClick, false)
                                is SearchRow.StopRow -> StopResultItem(row.stopInfo, row.ranking, row.routes,
                                    row.agencyNames, distanceMetres = null, distance = distance, onClick = onClick, false)
                                is SearchRow.RouteRow -> Unit
                            }
                            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }
    }
}

/** Compact whole-row targets with a consistent divider and optional straight-line distance. */
@Composable
private fun SearchResultRow(
    name: String, icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    distance: String? = null, enabled: Boolean = true, onClick: () -> Unit
) {
    Row(Modifier.fillMaxWidth().clickable(enabled = enabled, role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
        .heightIn(min = 48.dp).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (icon != null) Icon(icon, null, modifier = Modifier.size(24.dp),
            tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
        Text(name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f))
        if (distance != null) Text(distance, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun RowScope.EndpointPearls(originIsCurrent: Boolean, destinationIsCurrent: Boolean) {
    val rail = MaterialTheme.colorScheme.onSurface
    val surface = TripPlannerColors.card
    Box(Modifier.width(24.dp).fillMaxHeight()) {
        Canvas(Modifier.matchParentSize()) {
            val x = size.width / 2
            drawLine(rail, androidx.compose.ui.geometry.Offset(x, size.height / 4),
                androidx.compose.ui.geometry.Offset(x, size.height * 3 / 4), 2.dp.toPx())
        }
        Column(Modifier.fillMaxSize()) {
            listOf(originIsCurrent, destinationIsCurrent).forEach { current ->
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (current) Icon(Icons.Default.MyLocation, stringResource(R.string.directions_my_location),
                        tint = Color(0xFF2196F3), modifier = Modifier.size(20.dp).background(surface, CircleShape))
                    else Canvas(Modifier.size(16.dp)) {
                        drawCircle(surface, 6.dp.toPx())
                        drawCircle(rail, 6.dp.toPx(), style = Stroke(2.dp.toPx()))
                        drawCircle(rail, 2.5.dp.toPx(), style = Stroke(1.dp.toPx()))
                    }
                }
            }
        }
    }
}

/** Borderless fields retain a 48 dp touch target with just 4 dp of input padding. */
@Composable
private fun EndpointInput(
    value: String, placeholder: String, label: String, onChange: (String) -> Unit,
    onFocus: () -> Unit, requester: FocusRequester, compact: Boolean = false
) {
    var focused by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(end = 52.dp), verticalAlignment = Alignment.CenterVertically) {
        BasicTextField(value = value, onValueChange = onChange, singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.weight(1f).heightIn(min = if (compact) 36.dp else 48.dp)
                .focusRequester(requester).semantics { contentDescription = label }
                .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocus() },
            decorationBox = { input ->
                Box(Modifier.padding(4.dp), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    input()
                }
            })
        if (focused) IconButton(onClick = { onChange(""); requester.requestFocus() }) {
            Icon(Icons.Default.Close, stringResource(R.string.directions_clear_endpoint, label))
        }
    }
}

@Composable
private fun JourneyTimeControls(planner: TripPlannerViewModel, modifier: Modifier = Modifier) {
    var showOptions by remember { mutableStateOf(false) }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
            TimeSelectorButton(epochSeconds = planner.time, timezoneId = ZoneId.systemDefault().id,
                isNow = planner.now, onTimeChange = { planner.invalidate(); planner.time = it },
                onIsNowChange = { planner.invalidate(); planner.now = it; if (it) planner.arriveBy = false },
                arriveBy = planner.arriveBy,
                onArriveByChange = { planner.invalidate(); planner.arriveBy = it; if (it) planner.now = false },
                labelPrefix = stringResource(if (planner.arriveBy) R.string.directions_arrival else R.string.directions_departure))
        }
        VerticalDivider(Modifier.height(40.dp), color = MaterialTheme.colorScheme.outlineVariant)
        IconButton(onClick = { showOptions = true }) {
            BadgedBox(badge = { if (planner.options != DirectionsOptions()) Badge() }) {
                Icon(Icons.Default.Tune, stringResource(R.string.directions_speed_options))
            }
        }
    }
    if (showOptions) AlertDialog(onDismissRequest = { showOptions = false },
        title = { Text(stringResource(R.string.directions_speed_options)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) { StreetOptions(planner) } },
        confirmButton = { TextButton(onClick = { showOptions = false }) { Text(stringResource(R.string.directions_done)) } })
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
        Text(stringResource(R.string.directions_access_limit, directionsDuration(options.accessSeconds.toDouble())))
        Slider(value = options.accessSeconds, onValueChange = { update(options.copy(accessSeconds = it)) }, valueRange = 60f..3600f)
        Text(stringResource(R.string.directions_egress_limit, directionsDuration(options.egressSeconds.toDouble())))
        Slider(value = options.egressSeconds, onValueChange = { update(options.copy(egressSeconds = it)) }, valueRange = 60f..3600f)
        Text(stringResource(R.string.directions_direct_limit, directionsDuration(options.directSeconds.toDouble())))
        Slider(value = options.directSeconds, onValueChange = { update(options.copy(directSeconds = it)) }, valueRange = 60f..3600f)
        TextButton(onClick = { update(DirectionsOptions()) }) { Text(stringResource(R.string.directions_reset)) }
    }
}

/** Android ICU supplies translated distance units for the app's selected language. */
@Composable
private fun plannerDistance(meters: Double, imperial: Boolean): String {
    val locale = LocalConfiguration.current.locales[0]
    val (value, unit, decimals) = when {
        imperial && meters >= 1609.344 -> Triple(meters / 1609.344, android.icu.util.MeasureUnit.MILE, 1)
        imperial -> Triple((meters / 0.3048).roundToInt().toDouble(), android.icu.util.MeasureUnit.FOOT, 0)
        meters >= 1000 -> Triple(meters / 1000, android.icu.util.MeasureUnit.KILOMETER, 1)
        else -> Triple(meters.roundToInt().toDouble(), android.icu.util.MeasureUnit.METER, 0)
    }
    val numberFormat = android.icu.text.NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = decimals
        maximumFractionDigits = decimals
    }
    return android.icu.text.MeasureFormat.getInstance(
        locale, android.icu.text.MeasureFormat.FormatWidth.SHORT, numberFormat
    ).format(android.icu.util.Measure(value, unit))
}

internal fun directionsTime(value: String?): String = runCatching {
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withZone(ZoneId.systemDefault()).format(Instant.parse(value))
}.getOrDefault("")
@Composable
internal fun directionsDuration(seconds: Double): String {
    val minutes = (seconds / 60).roundToInt().coerceAtLeast(0)
    return if (minutes >= 60) stringResource(R.string.directions_hours_minutes, minutes / 60, minutes % 60)
        else stringResource(R.string.directions_minutes, minutes)
}
private fun legColor(leg: DirectionsLeg) = parseColor(leg.routeColor ?: "", Color(0xFF2563EB))

@Composable
private fun legModeLabel(leg: DirectionsLeg): String = stringResource(when {
        leg.mode.uppercase() in setOf("WALK") -> R.string.directions_walk
        leg.mode.uppercase() in setOf("BIKE", "BICYCLE") -> R.string.directions_cycle
        leg.mode.uppercase() in setOf("BUS", "COACH") || leg.routeType == 3 -> R.string.heading_bus
        leg.mode.uppercase() in setOf("SUBWAY", "METRO") || leg.routeType == 1 -> R.string.heading_metro
        leg.mode.uppercase() in setOf("TRAM", "LIGHT_RAIL") || leg.routeType == 0 -> R.string.heading_tram
        leg.mode.uppercase() == "FERRY" || leg.routeType == 4 -> R.string.directions_ferry
        leg.mode.uppercase() in setOf("RAIL", "TRAIN", "REGIONAL_RAIL", "HIGH_SPEED_RAIL") || leg.routeType == 2 -> R.string.heading_intercity_rail
        else -> R.string.heading_other
    })

@Composable
private fun LegModeIcon(leg: DirectionsLeg, preview: Boolean = false) {
    val modeLabel = legModeLabel(leg)
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
            contentDescription = modeLabel, colorFilter = ColorFilter.tint(LocalContentColor.current), modifier = Modifier.size(if (preview) 16.dp else 24.dp))
    } else Icon(when (leg.mode.uppercase()) {
        "BIKE", "BICYCLE" -> Icons.Default.DirectionsBike
        "WALK" -> Icons.Default.DirectionsWalk
        "FERRY" -> Icons.Default.DirectionsBoat
        else -> Icons.Default.Train
    }, modeLabel, modifier = Modifier.size(if (preview) 14.dp else if (leg.street) 18.dp else 24.dp))
}

@Composable
private fun LegBadge(leg: DirectionsLeg, preview: Boolean = false) {
    val label = leg.routeShortName ?: leg.routeLongName ?: leg.routeId ?: legModeLabel(leg)
    SelectionRouteBadges(routeIds = listOf(leg.routeId ?: label), textSize = if (preview) 10.sp else 12.sp, compact = preview, resolveRouteInfo = {
        ResolvedRouteBadgeInfo(leg.routeId ?: label, leg.routeShortName ?: label.takeIf { leg.routeLongName == null && leg.routeId == null }, leg.routeLongName,
            leg.routeColor ?: "2563EB", leg.routeTextColor ?: "FFFFFF", chateauId = leg.chateau)
    })
}

@Composable
private fun JourneyPreview(journey: DirectionsItinerary, streetUsUnits: Boolean, onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth().then(if (onClick != null)
            Modifier.clip(MaterialTheme.shapes.medium).clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick) else Modifier),
        colors = CardDefaults.cardColors(containerColor = TripPlannerColors.card, contentColor = TripPlannerColors.content),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        JourneyPreviewContent(journey, streetUsUnits)
    }
}

@Composable
internal fun JourneyPreviewContent(journey: DirectionsItinerary, streetUsUnits: Boolean) {
    val visible = remember(journey) { journey.previewLegs() }
    val times = remember(journey) { journey.previewTimeGroups() }
    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        PreviewTimeRow(times, directionsDuration(journey.seconds))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // Keep icons and their labels in the same segment column even on long journeys.
            val timelineWidth = maxOf(maxWidth, (visible.size * 48).dp)
            val weights = timelineWeights(visible, timelineWidth.value)
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                Row(Modifier.width(timelineWidth)) {
                    visible.forEachIndexed { index, leg ->
                        val rawColor = legColor(leg)
                        val color = if (leg.street) TripPlannerColors.secondaryContent
                            else if (kotlin.math.abs(rawColor.luminance() - TripPlannerColors.card.luminance()) < 0.15f)
                                TripPlannerColors.content else rawColor
                        Column(Modifier.weight(weights[index]).clipToBounds()) {
                            Row(Modifier.fillMaxWidth().height(24.dp), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                val trailingStreet = leg.street && index == visible.lastIndex && index > 0
                                if (!trailingStreet) LegModeIcon(leg, preview = true)
                                Canvas(Modifier.weight(1f).fillMaxHeight()) {
                                    if (leg.street) {
                                        val radius = 1.dp.toPx()
                                        val spacing = 6.dp.toPx()
                                        var x = radius
                                        while (x + radius <= size.width) {
                                            drawCircle(color, radius, androidx.compose.ui.geometry.Offset(x, size.height / 2))
                                            x += spacing
                                        }
                                    } else {
                                        drawLine(color, androidx.compose.ui.geometry.Offset(0f, size.height / 2),
                                            androidx.compose.ui.geometry.Offset(size.width, size.height / 2), strokeWidth = 2.dp.toPx())
                                    }
                                }
                                if (trailingStreet) LegModeIcon(leg, preview = true)
                            }
                            if (leg.street) {
                                Text(directionsDuration(leg.seconds) +
                                    (directionsDistance(leg)?.let { " · ${plannerDistance(it, streetUsUnits)}" } ?: ""), fontSize = 10.sp,
                                    modifier = Modifier.padding(top = 2.dp, end = 4.dp),
                                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                            } else {
                                Box(Modifier.padding(top = 2.dp, end = 4.dp)) { LegBadge(leg, preview = true) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewTimeRow(times: PreviewTimeGroups, duration: String) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val style = LocalTextStyle.current
    fun groupWidth(markers: List<PreviewTimeMarker>): Int = markers.sumOf { marker ->
        measurer.measure(previewClockTime(marker.time), style.copy(
            fontSize = if (marker.transit) 16.sp else 14.sp,
            fontWeight = if (marker.transit) FontWeight.Bold else FontWeight.Normal), maxLines = 1).size.width
    } + with(density) { (8.dp * (markers.size - 1).coerceAtLeast(0)).roundToPx() }
    val sides = maxOf(groupWidth(times.departure), groupWidth(times.arrival))
    val durationWidth = measurer.measure(duration, style.copy(fontSize = 14.sp), maxLines = 1).size.width
    val requiredWidth = with(density) { (sides * 2 + durationWidth).toDp() } + 16.dp
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // Scroll only when narrow screens or large fonts cannot fit all times on one row.
        val width = maxOf(maxWidth, requiredWidth)
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            Row(Modifier.width(width), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { PreviewTimes(times.departure) }
                Text(duration, modifier = Modifier.padding(horizontal = 8.dp), fontSize = 14.sp, maxLines = 1)
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { PreviewTimes(times.arrival) }
            }
        }
    }
}

private fun previewClockTime(time: String): String = runCatching {
    DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()).format(Instant.parse(time))
}.getOrDefault("")

@Composable
private fun PreviewTimes(markers: List<PreviewTimeMarker>) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        markers.forEach { marker ->
            Text(previewClockTime(marker.time), fontWeight = if (marker.transit) FontWeight.Bold else FontWeight.Normal,
                fontSize = if (marker.transit) 16.sp else 14.sp, maxLines = 1)
        }
    }
}

@Composable
private fun JourneyEndpoint(name: String, first: Boolean) {
    Card(colors = CardDefaults.cardColors(containerColor = TripPlannerColors.card, contentColor = TripPlannerColors.content)) {
        Box(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
            ConnectionRow(rail = TripPlannerColors.content, pearl = true, first = first, last = !first) {
                Text(name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

/** Each row owns a stretching rail, so wrapped names and expanded stops cannot leave gaps. */
@Composable
private fun ConnectionRow(
    rail: Color, time: String = "", platform: String = "", pearl: Boolean = false,
    first: Boolean = false, last: Boolean = false, dotted: Boolean = false,
    content: @Composable RowScope.() -> Unit
) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
        Text(time, modifier = Modifier.width(60.dp), fontSize = 14.sp, fontWeight = FontWeight.Bold,
            textAlign = androidx.compose.ui.text.style.TextAlign.End)
        Spacer(Modifier.width(8.dp))
        Box(Modifier.width(20.dp).fillMaxHeight()) {
            Canvas(Modifier.matchParentSize()) {
                val x = size.width / 2
                drawLine(rail, androidx.compose.ui.geometry.Offset(x, if (first) size.height / 2 else 0f),
                    androidx.compose.ui.geometry.Offset(x, if (last) size.height / 2 else size.height), 2.dp.toPx(),
                    pathEffect = if (dotted) PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 4.dp.toPx())) else null)
                if (pearl) drawCircle(rail, 4.dp.toPx(), androidx.compose.ui.geometry.Offset(x, size.height / 2))
            }
        }
        Spacer(Modifier.width(8.dp))
        Row(Modifier.weight(1f).heightIn(min = 28.dp).padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), content = content)
        if (platform.isNotBlank()) Text(platform, modifier = Modifier.widthIn(max = 48.dp).padding(start = 8.dp),
            style = MaterialTheme.typography.labelMedium, textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun JourneyLegDetails(leg: DirectionsLeg, streetUsUnits: Boolean) {
    var expanded by remember(leg) { mutableStateOf(false) }
    val surface = TripPlannerColors.card
    val rawRail = legColor(leg)
    // Keep near-white/black route colors visible against the current card surface.
    val rail = if (leg.street) TripPlannerColors.secondaryContent
        else if (kotlin.math.abs(rawRail.luminance() - surface.luminance()) < 0.15f) TripPlannerColors.content else rawRail
    Card(modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = surface, contentColor = TripPlannerColors.content),
        border = if (leg.street) BorderStroke(1.dp, TripPlannerColors.streetBorder) else null) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
            if (leg.street) {
                ConnectionRow(rail, time = directionsDuration(leg.seconds), dotted = true) {
                    LegModeIcon(leg)
                    Text(directionsDistance(leg)?.let { plannerDistance(it, streetUsUnits) }
                        ?: stringResource(R.string.directions_distance_unavailable), style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                ConnectionRow(rail, directionsTime(leg.startTime), leg.fromPlatform ?: leg.from?.platform ?: leg.from?.platformCode ?: "",
                    pearl = true, first = true) {
                    Text(leg.from?.name.orEmpty(), fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyLarge)
                }
                ConnectionRow(rail) {
                    LegModeIcon(leg)
                    FlowRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Box(Modifier.align(Alignment.CenterVertically)) { LegBadge(leg) }
                        leg.tripShortName?.takeIf { it.isNotBlank() }?.let { tripShortName ->
                            Text(tripShortName, modifier = Modifier.align(Alignment.CenterVertically),
                                fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
                val direction = leg.headsign?.takeIf { it.isNotBlank() }
                    ?: leg.routeLongName?.takeIf { it.isNotBlank() }
                if (direction != null) ConnectionRow(rail) {
                    Text(direction, style = MaterialTheme.typography.bodyMedium)
                }
                ConnectionRow(rail) {
                    val stopsDuration = stringResource(R.string.directions_stops_duration,
                        leg.intermediateStops.size, directionsDuration(leg.seconds))
                    if (leg.intermediateStops.isEmpty()) {
                        Text(stopsDuration, style = MaterialTheme.typography.bodyMedium)
                    } else TextButton(onClick = { expanded = !expanded }, modifier = Modifier.height(32.dp), contentPadding = PaddingValues(0.dp)) {
                        Text(stopsDuration, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            stringResource(if (expanded) R.string.directions_hide_stops else R.string.directions_show_stops))
                    }
                }
                if (expanded) leg.intermediateStops.forEach { stop ->
                    ConnectionRow(rail, directionsTime(stop.departure ?: stop.arrival ?: stop.scheduledDeparture ?: stop.scheduledArrival), pearl = true) {
                        Text(stop.name, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                ConnectionRow(rail, directionsTime(leg.endTime), leg.toPlatform ?: leg.to?.platform ?: leg.to?.platformCode ?: "",
                    pearl = true, last = true) {
                    Text(leg.to?.name.orEmpty(), fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyLarge)
                }
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
