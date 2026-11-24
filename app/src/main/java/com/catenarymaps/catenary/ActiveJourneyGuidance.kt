package com.catenarymaps.catenary

import android.content.Context
import android.util.Log
import android.content.SharedPreferences
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant

/** Backed by app-private preferences so closing the planner or recreating the Activity does not lose tracking. */
internal object ActiveJourneySession {
    private const val KEY = "active"
    private var preferences: SharedPreferences? = null
    private var appContext: Context? = null
    var current by mutableStateOf<ActiveJourney?>(null)
        private set

    /** Used by WorkManager/FCM without initializing Compose or overwriting the active UI state. */
    fun persisted(context: Context): ActiveJourney? =
        context.applicationContext.getSharedPreferences("active-journey", Context.MODE_PRIVATE)
            .getString(KEY, null)?.let { serialized ->
                runCatching { Json.decodeFromString<ActiveJourney>(serialized) }.getOrNull()
            }?.takeIf { it.snapshot.journey.legs.isNotEmpty() }

    fun initialize(context: Context) {
        if (preferences != null) return
        val app = context.applicationContext
        val prefs = app.getSharedPreferences("active-journey", Context.MODE_PRIVATE)
        current = persisted(app)
        preferences = prefs
        appContext = app
        if (current == null) prefs.edit().remove(KEY).apply()
    }

    /** Replaces the previous active session atomically; bookmarks remain independent. */
    fun start(snapshot: SavedJourney, at: Long = Instant.now().epochSecond) {
        require(snapshot.journey.legs.isNotEmpty())
        val active = ActiveJourney(snapshot, at)
        preferences?.edit()?.putString(KEY, Json.encodeToString(active))?.apply()
        ActiveJourneyLiveState.clear()
        current = active
        appContext?.let { app ->
            JourneyNotifierSyncWorker.enqueue(app)
            ActiveJourneyProgressService.start(app)
        }
    }

    fun end(expectedIdentity: String? = null) {
        if (expectedIdentity != null && current?.snapshot?.journey?.identity() != expectedIdentity) return
        preferences?.edit()?.remove(KEY)?.apply()
        ActiveJourneyLiveState.clear()
        current = null
        appContext?.let { app ->
            ActiveJourneyProgressService.stop(app)
            JourneyNotifierSyncWorker.enqueue(app)
        }
    }
}

private const val ROUTE_BADGE_MARKER = "__catenary_route_badge__"

/** Reuse planner route badges, but show the trip short name as plain text on non-exempt UK rail. */
@Composable
private fun ActiveJourneyServiceLabel(leg: DirectionsLeg) {
    if (!NationalRailUtils.showsRouteName(leg.chateau, leg.agencyId)) {
        Text(
            journeyServiceName(leg), color = Color.White, fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis
        )
        return
    }
    val shortName = leg.routeShortName?.let(::timelineRouteName)?.takeIf { it.isNotBlank() }
    val longName = leg.routeLongName?.let(::timelineRouteName)?.takeIf { it.isNotBlank() }
    val label = shortName ?: longName ?: leg.tripShortName?.takeIf { it.isNotBlank() }
    ?: leg.routeId?.takeIf { it.isNotBlank() } ?: leg.mode
    SelectionRouteBadges(
        routeIds = listOf(leg.routeId ?: label),
        modifier = Modifier.widthIn(max = 112.dp),
        textSize = 10.sp, compact = true, flush = true,
        resolveRouteInfo = { id ->
            ResolvedRouteBadgeInfo(
                routeId = id,
                shortName = shortName ?: label.takeIf { longName == null && leg.routeId == null },
                longName = longName,
                color = leg.routeColor ?: "2563EB",
                textColor = leg.routeTextColor ?: "FFFFFF",
                chateauId = leg.chateau
            )
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ActiveJourneyBar(
    active: ActiveJourney, position: Pair<Double, Double>?, streetUsUnits: Boolean,
    onOpenDetails: () -> Unit, modifier: Modifier = Modifier
) {
    val snapshot = active.snapshot
    val identity = snapshot.journey.identity()
    val journey = rememberLiveJourney(snapshot.journey)
    LaunchedEffect(identity, journey) {
        // Reuse Ramonda predictions already held by the app while it is visible.
        // The notification service falls back to the persisted timetable offline.
        ActiveJourneyLiveState.update(identity, journey)
    }
    var now by remember(identity) { mutableLongStateOf(Instant.now().epochSecond) }
    LaunchedEffect(identity) {
        while (true) {
            now = Instant.now().epochSecond
            delay(10_000L)
        }
    }
    LaunchedEffect(identity, now, position, journey) {
        if (hasArrivedAtJourneyDestination(journey, snapshot.destination, now, position)) {
            ActiveJourneySession.end(identity)
        }
    }
    val progress = journeyGuidance(journey, now, position, snapshot.destination)
    // Keep the route identifier as its own composable while retaining the existing
    // localized word order; some languages place the service name in the middle.
    val guidanceText = when (progress) {
        is JourneyGuidance.Boarding -> {
            val departure =
                directionsTime(progress.leg.departureCurrent, progress.leg.from?.timezone)
            val stopName = progress.leg.from?.name.orEmpty()
            val main = if (progress.transfer) stringResource(
                R.string.journey_tracking_transfer,
                stopName,
                ROUTE_BADGE_MARKER,
                departure,
                progress.minutes
            ) else stringResource(
                R.string.journey_tracking_departing, ROUTE_BADGE_MARKER, departure, progress.minutes
            )
            val platform = journeyPlatform(progress.leg)
            if (platform == null) main else main + " · " +
                    stringResource(R.string.journey_tracking_platform, platform)
        }

        is JourneyGuidance.Riding -> stringResource(
            R.string.journey_tracking_alight, progress.stop, progress.minutes
        )

        is JourneyGuidance.FinalStreet -> {
            val distance = progress.remainingMeters?.let { formatDistanceMeters(it, streetUsUnits) }
                ?: stringResource(R.string.directions_distance_unavailable)
            stringResource(R.string.journey_tracking_remaining, distance)
        }

        is JourneyGuidance.Arriving -> stringResource(
            R.string.journey_tracking_arriving,
            progress.destination
        )
    }
    val serviceFragments = if (progress is JourneyGuidance.Boarding)
        guidanceText.split(ROUTE_BADGE_MARKER, limit = 2) else emptyList()
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onOpenDetails),
        color = Color.Black, contentColor = Color.White, shadowElevation = 6.dp
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                // Keep the surface edge-to-edge and reserve only the inset actually needed.
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                .heightIn(min = 42.dp)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)
        ) {
            if (progress is JourneyGuidance.FinalStreet) {
                Icon(
                    if (progress.cycling) Icons.Default.DirectionsBike else Icons.Default.DirectionsWalk,
                    contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp)
                )
            }
            // Let long stops and localized fragments wrap around the badge while keeping
            // the badge and chevron part of a centered, compact group.
            FlowRow(
                modifier = Modifier.weight(1f, fill = false),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.Center
            ) {
                @Composable
                fun GuidanceText(value: String) {
                    if (value.isNotBlank()) Text(
                        value.trim(), color = Color.White, fontSize = 12.sp,
                        lineHeight = 15.sp, fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center, maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (progress is JourneyGuidance.Boarding && serviceFragments.size == 2) {
                    GuidanceText(serviceFragments[0])
                    ActiveJourneyServiceLabel(progress.leg)
                    GuidanceText(serviceFragments[1])
                } else {
                    GuidanceText(guidanceText)
                }
            }
            Icon(
                Icons.Default.ChevronRight, stringResource(R.string.directions_show_details),
                modifier = Modifier.size(18.dp), tint = Color.White
            )

        }
    }
}
