package com.catenarymaps.catenary

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.PopupPositionProvider
import io.ktor.client.HttpClient
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Shared between fullscreen detail, the tablet detail pane, and the map overlay. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun JourneySummary(
    planner: TripPlannerViewModel, streetUsUnits: Boolean,
    collapseFraction: Float = planner.detailsCollapse, modifier: Modifier = Modifier,
    showBack: Boolean = true, showMapAction: Boolean = true, onSavedChanged: () -> Unit = {}, client: HttpClient,
    onHome: () -> Unit, onBack: (() -> Unit)? = null
) {
    val journey = planner.selectedJourney ?: return
    val visibleJourney = rememberLiveJourney(journey)
    val activityContext = LocalContext.current
    val context = activityContext.applicationContext
    // Stored with the notifier installation credentials; excluded from Android backup.
    val notificationPrefs = remember(context) {
        context.getSharedPreferences("journey-notifier", Context.MODE_PRIVATE)
    }
    val requestNotificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Guidance continues even when notifications are denied. */ }
    val requestNotificationsOnce: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !notificationPrefs.getBoolean("notification_permission_requested", false)) {
            val granted = ContextCompat.checkSelfPermission(
                activityContext, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (notificationPrefs.edit().putBoolean("notification_permission_requested", true)
                    .commit() && !granted) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
    val bookmarkOnboarding = remember(context) { BookmarkTripOnboardingStore(context) }
    var hasBookmarkedTrip by remember(bookmarkOnboarding) { mutableStateOf(bookmarkOnboarding.hasBookmarkedTrip()) }
    val scope = rememberCoroutineScope()
    val language = LocalConfiguration.current.locales[0].language
    val identity = journey.identity()
    var saved by remember(identity) { mutableStateOf(false) }
    var busy by remember(identity) { mutableStateOf(true) }
    var failed by remember(identity) { mutableStateOf(false) }
    var saveable by remember(identity, journey.endTime) { mutableStateOf(journey.canBeSaved()) }
    LaunchedEffect(identity, journey.endTime) {
        while (true) {
            saveable = journey.canBeSaved()
            delay(1_000)
        }
    }
    LaunchedEffect(identity) {
        try {
            saved = withContext(Dispatchers.IO) { SavedJourneyStore(context).use { it.contains(identity) } }
            // An already-saved journey indicates the feature was used on an earlier visit.
            if (saved && !hasBookmarkedTrip) {
                bookmarkOnboarding.markBookmarkedTrip()
                hasBookmarkedTrip = true
            }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { failed = true }
        finally { busy = false }
    }
    val showBookmarkTooltip = shouldShowBookmarkTooltip(hasBookmarkedTrip, saved, busy, saveable)
    val bookmarkTooltipState = rememberTooltipState(isPersistent = true)
    LaunchedEffect(identity, showBookmarkTooltip) {
        if (showBookmarkTooltip) bookmarkTooltipState.show() else bookmarkTooltipState.dismiss()
    }
    Column(modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            if (showBack) FilledIconButton(
                onClick = { if (onBack != null) onBack() else planner.details = false },
                modifier = Modifier.size(32.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = TripPlannerColors.card, contentColor = TripPlannerColors.content
                )
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.osm_back), modifier = Modifier.size(18.dp))
            } else Spacer(Modifier.size(32.dp))
            FilledIconButton(
                onClick = onHome,
                modifier = Modifier.size(32.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = TripPlannerColors.card, contentColor = TripPlannerColors.content
                )
            ) {
                Icon(Icons.Filled.Home, stringResource(R.string.osm_home), modifier = Modifier.size(18.dp))
            }
        }
        // Buttons overlap the summary's lower edge while retaining full touch targets.
        Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 4.dp)) {
            Card(Modifier.fillMaxWidth().padding(bottom = 24.dp),
                colors = CardDefaults.cardColors(containerColor = TripPlannerColors.card, contentColor = TripPlannerColors.content),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
                Column(Modifier.padding(bottom = 12.dp)) {
                    Text("${planner.originText} → ${planner.destinationText}",
                        fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp))
                    // Measure at full height, then reveal only the fraction controlled by the gesture.
                    // No animation or content replacement: reversing a gesture reverses the height.
                    Box(Modifier.clipToBounds().layout { measurable, constraints ->
                        val preview = measurable.measure(constraints.copy(minHeight = 0))
                        planner.detailsCollapseRangePx = preview.height.toFloat().coerceAtLeast(1f)
                        val height = (preview.height * (1f - collapseFraction.coerceIn(0f, 1f))).toInt()
                        layout(preview.width, height) { preview.placeRelative(0, 0) }
                    }) {
                        JourneyPreviewContent(visibleJourney, streetUsUnits)
                    }
                }
            }
            Row(Modifier.align(androidx.compose.ui.Alignment.BottomStart).padding(start = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TooltipBox(
                    // Always below the bookmark: Material 3 points its caret upward.
                    positionProvider = rememberBookmarkTooltipPositionProvider(),
                    tooltip = {
                        RichTooltip(
                            modifier = Modifier.widthIn(max = 280.dp)
                                .semantics { liveRegion = LiveRegionMode.Polite },
                            caretShape = TooltipDefaults.caretShape()
                        ) {
                            Text(stringResource(R.string.directions_bookmark_tooltip))
                        }
                    },
                    state = bookmarkTooltipState,
                    enableUserInput = false
                ) {
                    SummaryAction(
                        enabled = !busy && (saved || saveable),
                        onClick = {
                            if (!saved && !journey.canBeSaved()) return@SummaryAction
                            busy = true; failed = false
                            val wasSaved = saved
                            val snapshot = SavedJourney(journey, planner.origin, planner.destination)
                            scope.launch {
                                try {
                                    val namedSnapshot = if (wasSaved) snapshot else snapshot.withNamedCurrentLocations { location ->
                                        reverseMapLocationName(client, location.lat, location.lon, language)
                                    }
                                    val changed = withContext(Dispatchers.IO) {
                                        // Geocoding may have crossed the journey's arrival time.
                                        if (!wasSaved && !journey.canBeSaved()) return@withContext false
                                        SavedJourneyStore(context).use { store ->
                                            if (wasSaved) store.remove(identity) else store.save(namedSnapshot)
                                        }
                                        true
                                    }
                                    if (!changed) { saveable = false; return@launch }
                                    saved = !wasSaved
                                    if (!wasSaved) {
                                        // Hide the guidance permanently only after storage succeeds.
                                        bookmarkOnboarding.markBookmarkedTrip()
                                        hasBookmarkedTrip = true
                                        // Request only after a first successful save, and only once
                                        // even if the user declines. Android 12 and earlier are exempt.
                                        requestNotificationsOnce()
                                    }
                                    onSavedChanged()
                                } catch (e: CancellationException) { throw e }
                                catch (_: Exception) { failed = true }
                                finally { busy = false }
                            }
                        }
                    ) {
                        Icon(if (saved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            stringResource(if (saved) R.string.directions_unsave_trip else R.string.directions_save_trip))
                    }
                }
                if (showMapAction && !planner.streetOnly) SummaryAction(onClick = { planner.mapMode = !planner.usingMap }) {
                    Icon(if (planner.usingMap) Icons.AutoMirrored.Filled.List else Icons.Default.Map,
                        stringResource(if (planner.usingMap) R.string.directions_list_view else R.string.directions_map_view))
                }
            }
            // Every summary variant (planner, map and saved journeys) uses the same active trip state.
            val existingJourney = ActiveJourneySession.current
            val tracking = existingJourney?.snapshot?.journey?.identity() == identity
            var confirmSwitch by remember(identity) { mutableStateOf(false) }
            val startJourney: () -> Unit = {
                ActiveJourneySession.start(SavedJourney(journey, planner.origin, planner.destination))
                requestNotificationsOnce()
            }
            val darkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5f
            Button(
                onClick = {
                    when {
                        tracking -> ActiveJourneySession.end(identity)
                        existingJourney != null -> confirmSwitch = true
                        else -> startJourney()
                    }
                },
                modifier = Modifier.align(androidx.compose.ui.Alignment.BottomEnd)
                    .padding(end = 12.dp).height(44.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (tracking) {
                        if (darkTheme) Color(0xFFFCA5A5) else Color(0xFFB91C1C)
                    } else {
                        if (darkTheme) Color(0xFF93C5FD) else Color(0xFF1D4ED8)
                    },
                    contentColor = if (tracking) {
                        if (darkTheme) Color(0xFF7F1D1D) else Color.White
                    } else {
                        if (darkTheme) Color(0xFF172554) else Color.White
                    }
                )
            ) {
                Text(stringResource(if (tracking) R.string.journey_tracking_end else R.string.journey_tracking_start),
                    fontWeight = FontWeight.SemiBold)
            }
            if (confirmSwitch) {
                val currentDestination = existingJourney?.snapshot?.destination?.name
                    ?.takeIf { it.isNotBlank() }
                    ?: existingJourney?.snapshot?.journey?.legs?.lastOrNull()?.to?.name
                        ?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.app_name)
                AlertDialog(
                    onDismissRequest = { confirmSwitch = false },
                    title = { Text(stringResource(R.string.journey_tracking_switch_title)) },
                    text = {
                        Text(stringResource(R.string.journey_tracking_switch_message, currentDestination))
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmSwitch = false }) {
                            Text(stringResource(R.string.journey_tracking_switch_dismiss))
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            confirmSwitch = false
                            startJourney()
                        }) {
                            Text(stringResource(R.string.journey_tracking_switch_confirm))
                        }
                    }
                )
            }
        }
        if (failed) Text(stringResource(R.string.directions_save_error),
            color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 20.dp))
    }
}

@Composable
private fun SummaryAction(enabled: Boolean = true, onClick: () -> Unit, content: @Composable () -> Unit) {
    Surface(shape = CircleShape, color = TripPlannerColors.card, contentColor = TripPlannerColors.content,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), shadowElevation = 3.dp) {
        IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(44.dp), content = content)
    }
}

/** Keep the popup below its bookmark icon so Material 3 renders the caret pointing upward. */
@Composable
private fun rememberBookmarkTooltipPositionProvider(): PopupPositionProvider {
    val gapPx = with(LocalDensity.current) { 4.dp.roundToPx() }
    return remember(gapPx) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize
            ): IntOffset = bookmarkTooltipPosition(anchorBounds, windowSize, popupContentSize, gapPx)
        }
    }
}

/** Clamp horizontally, but never flip to a position above the bookmark icon. */
internal fun bookmarkTooltipPosition(
    anchorBounds: IntRect,
    windowSize: IntSize,
    popupContentSize: IntSize,
    gapPx: Int
): IntOffset {
    val maxX = (windowSize.width - popupContentSize.width).coerceAtLeast(0)
    return IntOffset(anchorBounds.left.coerceIn(0, maxX), anchorBounds.bottom + gapPx)
}
