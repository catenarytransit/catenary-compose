package com.catenarymaps.catenary

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import io.ktor.client.HttpClient
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Shared between fullscreen detail, the tablet detail pane, and the map overlay. */
@Composable
internal fun JourneySummary(
    planner: TripPlannerViewModel, streetUsUnits: Boolean,
    collapseFraction: Float = planner.detailsCollapse, modifier: Modifier = Modifier,
    showBack: Boolean = true, showMapAction: Boolean = true, onSavedChanged: () -> Unit = {}, client: HttpClient
) {
    val journey = planner.selectedJourney ?: return
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val language = LocalConfiguration.current.locales[0].language
    val identity = journey.identity()
    var saved by remember(identity) { mutableStateOf(false) }
    var busy by remember(identity) { mutableStateOf(true) }
    var failed by remember(identity) { mutableStateOf(false) }
    LaunchedEffect(identity) {
        try {
            saved = withContext(Dispatchers.IO) { SavedJourneyStore(context).use { it.contains(identity) } }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { failed = true }
        finally { busy = false }
    }
    Column(modifier) {
        if (showBack) FilledIconButton(onClick = { planner.details = false }, modifier = Modifier.padding(start = 20.dp, top = 4.dp).size(32.dp),
            shape = CircleShape, colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = TripPlannerColors.card, contentColor = TripPlannerColors.content)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.osm_back), modifier = Modifier.size(18.dp))
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
                        JourneyPreviewContent(journey, streetUsUnits)
                    }
                }
            }
            Row(Modifier.align(androidx.compose.ui.Alignment.BottomStart).padding(start = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SummaryAction(
                    enabled = !busy,
                    onClick = {
                        busy = true; failed = false
                        val wasSaved = saved
                        val snapshot = SavedJourney(journey, planner.origin, planner.destination)
                        scope.launch {
                            try {
                                val namedSnapshot = if (wasSaved) snapshot else snapshot.withNamedCurrentLocations { location ->
                                    reverseMapLocationName(client, location.lat, location.lon, language)
                                }
                                withContext(Dispatchers.IO) {
                                    SavedJourneyStore(context).use { store ->
                                        if (wasSaved) store.remove(identity) else store.save(namedSnapshot)
                                    }
                                }
                                saved = !wasSaved
                                onSavedChanged()
                            } catch (e: CancellationException) { throw e }
                            catch (_: Exception) { failed = true }
                            finally { busy = false }
                        }
                    }
                ) {
                    Icon(if (saved) Icons.Default.Star else Icons.Default.StarBorder,
                        stringResource(if (saved) R.string.directions_unsave_trip else R.string.directions_save_trip))
                }
                if (showMapAction && !planner.streetOnly) SummaryAction(onClick = { planner.mapMode = !planner.usingMap }) {
                    Icon(if (planner.usingMap) Icons.AutoMirrored.Filled.List else Icons.Default.Map,
                        stringResource(if (planner.usingMap) R.string.directions_list_view else R.string.directions_map_view))
                }
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
