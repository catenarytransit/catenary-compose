// SearchComposables.kt
package com.catenarymaps.catenary

import android.graphics.Color.parseColor
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ────────────────────────────────────────────────────────────────────────────────
// Weight knobs: tune relative influence of each category on the combined ranking.
// Example: increase NOMINATIM_WEIGHT to prefer address/place results; increase
// STOP_WEIGHT to bias toward stops; increase ROUTE_WEIGHT to bias toward routes.
// ────────────────────────────────────────────────────────────────────────────────
private const val NOMINATIM_WEIGHT: Double = 2.0
private const val ROUTE_WEIGHT: Double = 1.0
private const val STOP_WEIGHT: Double = 1.0

// Internal row model that lets us render different result types in one list
private sealed class SearchRow(val weightedScore: Double) {
    class NominatimRow(
        val item: NominatimResult,
        score: Double
    ) : SearchRow(score)

    class RouteRow(
        val ranking: RouteRanking,
        val routeInfo: RouteInfo,
        val agency: Agency?,
        score: Double
    ) : SearchRow(score)

    class StopRow(
        val ranking: StopRanking,
        val stopInfo: StopInfo,
        val routes: List<RouteInfo>,
        val agencyNames: List<String>,
        val distanceMetres: Double?,
        score: Double
    ) : SearchRow(score)
}

// Helper to convert a 1-based rank position into a 0..1 score (1.0 is best)
private fun rankToUnitScore(index: Int, total: Int): Double {
    if (total <= 0) return 0.0
    // Top item => 1.0; last => 1/total. Clamped just in case.
    return ((total - index).toDouble() / total).coerceIn(0.0, 1.0)
}

@Composable
fun SearchResultsOverlay(
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel,
    currentLocation: Pair<Double, Double>?,
    onNominatimClick: (NominatimResult) -> Unit,
    onStopClick: (String, String, StopRanking, StopInfo) -> Unit,
    onRouteClick: (RouteRanking, RouteInfo, Agency?) -> Unit
) {
    val nominatimResults by viewModel.nominatimResults.collectAsState()
    val catenaryResults by viewModel.catenaryResults.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    val stopsSection = catenaryResults?.stopsSection
    val routesSection = catenaryResults?.routesSection

    // Build a single, intermingled list of rows, each with a weighted score.
    val combinedRows = remember(nominatimResults, stopsSection, routesSection, currentLocation) {
        val rows = mutableListOf<SearchRow>()

        // Nominatim (limit to 3, matching prior behavior; tweak as desired)
        nominatimResults
            .take(10)
            .forEach { item ->
                val base = item.importance
                    .toDouble()
                    .coerceIn(0.0, 1.0)
                val score = base * NOMINATIM_WEIGHT
                rows += SearchRow.NominatimRow(item, score)
            }

        // Routes (limit to 10, keep existing matching)
        routesSection?.let { section ->
            val ranked = section.ranking.take(10)
            val total = ranked.size.coerceAtLeast(1)
            ranked.forEachIndexed { index, ranking ->
                val routeInfo = section.routes[ranking.chateau]?.get(ranking.gtfsId)
                val agency = routeInfo?.agencyId?.let { section.agencies[ranking.chateau]?.get(it) }
                if (routeInfo != null) {
                    val base = rankToUnitScore(index, total)
                    val score = base * ROUTE_WEIGHT
                    rows += SearchRow.RouteRow(ranking, routeInfo, agency, score)
                }
            }
        }

        // Stops (limit to 10, keep existing matching)
        stopsSection?.let { section ->
            val ranked = section.ranking.take(10)
            val total = ranked.size.coerceAtLeast(1)
            ranked.forEachIndexed { index, ranking ->
                val stopInfo = section.stops[ranking.chateau]?.get(ranking.gtfsId)
                if (stopInfo != null && stopInfo.parentStation == null) {
                    // Resolve routes for the stop
                    val routes = stopInfo.routes.mapNotNull { routeId ->
                        section.routes[ranking.chateau]?.get(routeId)
                    }

                    // Resolve agencies for those routes
                    val agencyNames = routes.mapNotNull { route ->
                        route.agencyId?.let { section.agencies[ranking.chateau]?.get(it)?.agencyName }
                    }.distinct()

                    // Distance from user, if available
                    val distanceMetres = currentLocation?.let { (userLat, userLon) ->
                        haversineDistance(
                            userLat, userLon,
                            stopInfo.point.y, stopInfo.point.x
                        )
                    }

                    val base = rankToUnitScore(index, total)
                    val score = base * STOP_WEIGHT
                    rows += SearchRow.StopRow(
                        ranking = ranking,
                        stopInfo = stopInfo,
                        routes = routes,
                        agencyNames = agencyNames,
                        distanceMetres = distanceMetres,
                        score = score
                    )
                }
            }
        }

        // Sort across categories by weighted score (descending)
        rows.sortedByDescending { it.weightedScore }
    }

    Surface(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .padding(top = 64.dp), // Offset for the search bar
        tonalElevation = 6.dp,
        shadowElevation = 8.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
            ) {
                // Single, intermingled list
                items(combinedRows) { row ->
                    when (row) {
                        is SearchRow.NominatimRow -> {
                            NominatimResultItem(
                                item = row.item,
                                onClick = { onNominatimClick(row.item) }
                            )
                        }

                        is SearchRow.RouteRow -> {
                            RouteResultItem(
                                routeInfo = row.routeInfo,
                                agency = row.agency,
                                onClick = { onRouteClick(row.ranking, row.routeInfo, row.agency) }
                            )
                        }

                        is SearchRow.StopRow -> {
                            StopResultItem(
                                stopInfo = row.stopInfo,
                                ranking = row.ranking,
                                routes = row.routes,
                                agencyNames = row.agencyNames,
                                distanceMetres = row.distanceMetres,
                                onClick = {
                                    onStopClick(
                                        row.ranking.chateau,
                                        row.ranking.gtfsId,
                                        row.ranking,
                                        row.stopInfo
                                    )
                                }
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                }
            }

            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(32.dp)
                        .align(Alignment.Center)
                )
            }
        }
    }
}

@Composable
fun NominatimResultItem(
    item: NominatimResult,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = item.name,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = item.addressType,
                fontWeight = FontWeight.Light,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = item.displayName,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StopResultItem(
    stopInfo: StopInfo,
    ranking: StopRanking,
    routes: List<RouteInfo>,
    agencyNames: List<String>,
    distanceMetres: Double?,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Row for Name + Distance
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stopInfo.name,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f, fill = false),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (distanceMetres != null) {
                val distanceText = if (distanceMetres > 1000) {
                    String.format("%.1f km", distanceMetres / 1000)
                } else {
                    String.format("%.0f m", distanceMetres)
                }
                Text(
                    text = distanceText,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }

        // Agency Names
        if (agencyNames.isNotEmpty()) {
            Text(
                text = agencyNames.joinToString(" • "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // FlowRow for Route Badges
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            routes.forEach { route ->
                RouteBadge(route = route)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RouteResultItem(
    routeInfo: RouteInfo,
    agency: Agency?,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RouteBadge(route = routeInfo)
            if (routeInfo.longName != null) {
                Text(
                    text = routeInfo.longName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        agency?.agencyName?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun RouteBadge(route: RouteInfo) {
    // Helper to parse color strings, with fallback
    fun parseColorSafe(colorStr: String, default: Color): Color {
        return try {
            Color(parseColor("$colorStr"))
        } catch (e: Exception) {
            default
        }
    }

    val bgColor = parseColorSafe(route.color, MaterialTheme.colorScheme.surfaceVariant)
    val textColor = parseColorSafe(route.textColor, MaterialTheme.colorScheme.onSurfaceVariant)
    val text = route.shortName ?: route.longName ?: ""

    Text(
        text = text,
        color = textColor,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bgColor)
            .padding(horizontal = 6.dp, vertical = 1.dp),
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium
    )
}
