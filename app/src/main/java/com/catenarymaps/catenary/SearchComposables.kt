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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest

@Composable
fun SearchResultsOverlay(
        modifier: Modifier = Modifier,
        viewModel: SearchViewModel,
        onCypressClick: (CypressFeature) -> Unit,
        onStopClick: (String, String, StopRanking, StopInfo) -> Unit,
        onRouteClick: (RouteRanking, RouteInfo, Agency?) -> Unit,
        onOsmStationClick: (OsmStationSearchResult) -> Unit
) {
    val combinedRows by viewModel.searchRows.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    Surface(
            modifier =
                    modifier
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(top = 64.dp), // Offset for the search bar
            tonalElevation = 6.dp,
            shadowElevation = 8.dp
    ) {
        Box(modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)) {
            LazyColumn(
                    modifier =
                            Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surface)
                                .windowInsetsPadding(WindowInsets.safeDrawing)
            ) {
                // Single, intermingled list
                items(combinedRows) { row ->
                    when (row) {
                        is SearchRow.CypressRow -> {
                            CypressResultItem(
                                    item = row.item,
                                    onClick = { onCypressClick(row.item) }
                            )
                        }
                        is SearchRow.OsmStationRow -> {
                            OsmStationResultItem(
                                result = row.result,
                                onClick = { onOsmStationClick(row.result) }
                            )
                        }
                        is SearchRow.RouteRow -> {
                            RouteResultItem(
                                    routeInfo = row.routeInfo,
                                    agency = row.agency,
                                    onClick = {
                                        onRouteClick(row.ranking, row.routeInfo, row.agency)
                                    }
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
                CircularProgressIndicator(modifier = Modifier
                    .size(32.dp)
                    .align(Alignment.Center))
            }
        }
    }
}

@Composable
fun CypressResultItem(item: CypressFeature, onClick: () -> Unit) {
    val props = item.properties
    // Construct a title/subtitle
    val title = props.name ?: props.names?.get("default") ?: "Unknown"

    // Construct address: street, housenumber, locality, region, country etc.
    // Logic: if address layer, show street/housenumber.
    // if venue, show address components.
    val addressParts =
            listOfNotNull(
                    props.housenumber,
                    props.street,
                    props.neighbourhood,
                    props.locality ?: props.county,
                    props.region,
                    props.country
            )
    val subtitle =
            if (addressParts.isNotEmpty()) {
                addressParts.joinToString(", ")
            } else {
                props.layer.replaceFirstChar { it.uppercase() }
            }

    Column(
            modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { onClick() }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                    text = title,
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface
            )
            // Optional: Show layer type or category tag
            val tag = props.categories?.firstOrNull()?.split(":")?.getOrNull(1) ?: props.layer
            Text(
                    text = tag,
                    fontWeight = FontWeight.Light,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
                text = subtitle,
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
            modifier =
                    Modifier
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
                val distanceText =
                        if (distanceMetres > 1000) {
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

        // Updated RouteBadge calls in StopResultItem
        FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
        ) { routes.forEach { route -> RouteBadge(route = route, chateauId = ranking.chateau) } }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RouteResultItem(routeInfo: RouteInfo, agency: Agency?, onClick: () -> Unit) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RouteBadge(route = routeInfo, chateauId = routeInfo.chateau)

                Column(modifier = Modifier.padding(start = 8.dp)) {
                    Text(
                        text =
                            routeInfo.shortName
                                ?: routeInfo.longName
                                ?: routeInfo.routeId,
                        fontWeight = FontWeight.Medium,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (!routeInfo.longName.isNullOrBlank()) {
                        Text(
                            text = routeInfo.longName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (agency != null) {
                Text(
                    text = agency.agencyName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun OsmStationResultItem(result: OsmStationSearchResult, onClick: () -> Unit) {
    val hierarchy = result.adminHierarchy
    val subtitleParts =
        listOfNotNull(
            hierarchy?.neighbourhood?.name,
            hierarchy?.county?.name,
            hierarchy?.region?.name
        )
    val subtitle = subtitleParts.joinToString(", ")

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = result.name ?: "Unknown station",
            fontWeight = FontWeight.Medium,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        if (subtitle.isNotBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (result.routes.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                result.routes.forEach { route ->
                    // For OSM station chips we may not always know the chateau; fall back to
                    // route.chateau when present, else an empty string.
                    RouteBadge(route = route, chateauId = route.chateau)
                }
            }
        }
    }
}

@Composable
fun RouteBadge(route: RouteInfo, chateauId: String) {
    // Helper to parse color strings, with fallback
    fun parseColorSafe(colorStr: String, default: Color): Color {
        return try {
            Color(parseColor("$colorStr"))
        } catch (e: Exception) {
            default
        }
    }

    val isRatp = RatpUtils.isIdfmChateau(chateauId) && RatpUtils.isRatpRoute(route.shortName)
    val isMta =
            MtaSubwayUtils.MTA_CHATEAU_ID == chateauId &&
                    !route.shortName.isNullOrEmpty() &&
                    MtaSubwayUtils.isSubwayRouteId(route.shortName!!)

    if (isRatp) {
        val iconUrl = RatpUtils.getRatpIconUrl(route.shortName)
        if (iconUrl != null) {
            AsyncImage(
                    model =
                            ImageRequest.Builder(LocalContext.current)
                                    .data(iconUrl)
                                    .crossfade(true)
                                    .build(),
                    contentDescription = route.shortName,
                    modifier = Modifier
                        .size(20.dp)
                        .padding(end = 2.dp)
            )
            return
        }
    } else if (isMta) {
        val mtaColor = MtaSubwayUtils.getMtaSubwayColor(route.shortName!!)
        val symbolShortName = MtaSubwayUtils.getMtaSymbolShortName(route.shortName)
        Box(
                modifier =
                        Modifier
                            .size(20.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(mtaColor),
                contentAlignment = Alignment.Center
        ) {
            Text(
                    text = symbolShortName,
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
        return
    }

    val bgColor = parseColorSafe(route.color, MaterialTheme.colorScheme.surfaceVariant)
    val textColor = parseColorSafe(route.textColor, MaterialTheme.colorScheme.onSurfaceVariant)
    val text = route.shortName ?: route.longName ?: ""

    Text(
            text = text,
            color = textColor,
            modifier =
                    Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(bgColor)
                        .padding(horizontal = 6.dp, vertical = 1.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
    )
}
