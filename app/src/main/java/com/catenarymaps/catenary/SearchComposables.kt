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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SearchResultsOverlay(
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel,
    currentLocation: Pair<Double, Double>?,
    onNominatimClick: (NominatimResult) -> Unit,
    onStopClick: (StopRanking, StopInfo) -> Unit
) {
    val nominatimResults by viewModel.nominatimResults.collectAsState()
    val catenaryResults by viewModel.catenaryResults.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    val stopsSection = catenaryResults?.stopsSection

    Surface(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .padding(top = 64.dp), // Offset for the search bar
        tonalElevation = 6.dp,
        shadowElevation = 8.dp
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                // --- Nominatim Results ---
                items(nominatimResults.take(3)) { item ->
                    NominatimResultItem(
                        item = item,
                        onClick = { onNominatimClick(item) }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                }

                // --- Stop Results ---
                if (stopsSection != null) {
                    items(stopsSection.ranking.take(10)) { ranking ->
                        // Find the stop details from the 'stops' map using 'chateau' and 'gtfsId'
                        val stopInfo = stopsSection.stops[ranking.chateau]?.get(ranking.gtfsId)
                        if (stopInfo != null && stopInfo.parentStation == null) {
                            // Resolve routes
                            val routes = stopInfo.routes.mapNotNull { routeId ->
                                stopsSection.routes[ranking.chateau]?.get(routeId)
                            }

                            // Calculate distance
                            val distanceMetres = currentLocation?.let { (userLat, userLon) ->
                                haversineDistance(
                                    userLat, userLon,
                                    stopInfo.point.y, stopInfo.point.x
                                )
                            }

                            StopResultItem(
                                stopInfo = stopInfo,
                                ranking = ranking,
                                routes = routes,
                                distanceMetres = distanceMetres,
                                onClick = { onStopClick(ranking, stopInfo) }
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        }
                    }
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
            .padding(horizontal = 16.dp, vertical = 12.dp),
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
    distanceMetres: Double?,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
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
            .padding(horizontal = 6.dp, vertical = 2.dp),
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium
    )
}