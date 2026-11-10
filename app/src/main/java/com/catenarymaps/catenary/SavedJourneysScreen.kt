package com.catenarymaps.catenary

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
internal fun HomeNavigation(
    tripsSelected: Boolean, onExplore: () -> Unit, onTrips: () -> Unit, modifier: Modifier = Modifier
) {
    NavigationBar(modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))) {
        NavigationBarItem(selected = !tripsSelected, onClick = onExplore,
            icon = { Icon(Icons.Default.Map, null) }, label = { Text(stringResource(R.string.home_explore)) })
        NavigationBarItem(selected = tripsSelected, onClick = onTrips,
            icon = { Icon(Icons.Default.Star, null) }, label = { Text(stringResource(R.string.home_trips)) })
    }
}

@Composable
internal fun SavedJourneysScreen(
    streetUsUnits: Boolean, wide: Boolean, foreground: Boolean,
    onExplore: () -> Unit, onPlan: () -> Unit, modifier: Modifier = Modifier
) {
    val context = LocalContext.current.applicationContext
    val planner: TripPlannerViewModel = androidx.lifecycle.viewmodel.compose.viewModel(key = "saved-journey-details")
    var snapshots by remember { mutableStateOf<List<SavedJourney>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    var selectedIdentity by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(refresh, foreground) {
        if (foreground) while (true) {
            try {
                snapshots = withContext(Dispatchers.IO) { SavedJourneyStore(context).use { it.readAll() } }
                failed = false
                val selected = snapshots.firstOrNull { it.journey.identity() == selectedIdentity }
                if (selected == null) { selectedIdentity = null; planner.details = false }
                else if (planner.selectedJourney?.identity() != selectedIdentity) planner.openSavedJourney(selected)
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { failed = true }
            loaded = true
            delay(60_000)
        }
    }
    BackHandler {
        if (planner.details) { planner.details = false; selectedIdentity = null } else onExplore()
    }
    Surface(modifier, color = TripPlannerColors.background, contentColor = TripPlannerColors.content) {
        Row(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))) {
            if (wide || !planner.details) Column(Modifier.weight(1f).fillMaxHeight()) {
                Text(stringResource(R.string.home_trips), style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold, modifier = Modifier.padding(20.dp))
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (!loaded) item { CircularProgressIndicator() }
                    if (failed) item { Text(stringResource(R.string.directions_save_error)) }
                    if (loaded && !failed && snapshots.isEmpty()) item {
                        Text(stringResource(R.string.saved_journeys_empty), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(16.dp))
                        Text(stringResource(R.string.saved_journeys_description))
                    }
                    items(snapshots, key = { it.journey.identity() }) { snapshot ->
                        Card(onClick = {
                            selectedIdentity = snapshot.journey.identity()
                            planner.openSavedJourney(snapshot)
                        }, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
                            containerColor = TripPlannerColors.card, contentColor = TripPlannerColors.content)) {
                            Text("${snapshot.origin?.name.orEmpty()} → ${snapshot.destination?.name.orEmpty()}",
                                fontWeight = FontWeight.Bold, modifier = Modifier.padding(12.dp))
                            JourneyPreviewContent(snapshot.journey, streetUsUnits)
                        }
                    }
                }
                Button(onClick = onPlan, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(stringResource(R.string.saved_journeys_plan))
                }
            }
            if (wide) VerticalDivider()
            if (wide || planner.details) Column(Modifier.weight(1f).fillMaxHeight()) {
                if (planner.details) {
                    JourneySummary(planner, streetUsUnits, showBack = !wide, showMapAction = false,
                        onSavedChanged = { refresh++ })
                    Box(Modifier.weight(1f)) {
                        planner.selectedJourney?.let { JourneyDetails(planner, it, streetUsUnits) }
                    }
                } else Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    Text(stringResource(R.string.saved_journeys_select), modifier = Modifier.padding(24.dp))
                }
            }
        }
    }
}
