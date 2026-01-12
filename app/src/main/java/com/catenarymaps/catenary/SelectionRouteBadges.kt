package com.catenarymaps.catenary

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest

data class ResolvedRouteBadgeInfo(
        val routeId: String,
        val shortName: String?,
        val longName: String?,
        val color: String,
        val textColor: String,
        val agencyId: String? = null,
        val chateauId: String? = null
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SelectionRouteBadges(
        routeIds: List<String>,
        resolveRouteInfo: (String) -> ResolvedRouteBadgeInfo?,
        modifier: Modifier = Modifier
) {
        val routeInfos = remember(routeIds) { routeIds.mapNotNull { resolveRouteInfo(it) } }

        if (routeInfos.isEmpty()) return

        val handledRoutes = mutableSetOf<String>()

        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = modifier) {
                // --- National Rail Logic ---
                val nrRoutes = routeInfos.filter { it.chateauId == "nationalrailuk" }
                if (nrRoutes.isNotEmpty()) {
                        val agencyGroups =
                                mapOf(
                                        "GW" to
                                                Pair(
                                                        "Great Western Railway",
                                                        "GreaterWesternRailway.svg"
                                                ),
                                        "SW" to
                                                Pair(
                                                        "South Western Railway",
                                                        "SouthWesternRailway.svg"
                                                ),
                                        "SN" to Pair("Southern", "SouthernIcon.svg"),
                                        "CC" to Pair("c2c", "c2c_logo.svg"),
                                        "LE" to Pair("Greater Anglia", null)
                                )

                        agencyGroups.forEach { (agencyId, info) ->
                                val name = info.first
                                val icon = info.second
                                val matchingRoutes = nrRoutes.filter { it.agencyId == agencyId }

                                if (matchingRoutes.isNotEmpty()) {
                                        matchingRoutes.forEach { handledRoutes.add(it.routeId) }

                                        Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier =
                                                        Modifier.clip(RoundedCornerShape(4.dp))
                                                                .background(
                                                                        MaterialTheme.colorScheme
                                                                                .surfaceVariant
                                                                )
                                                                .padding(
                                                                        horizontal = 6.dp,
                                                                        vertical = 2.dp
                                                                )
                                        ) {
                                                if (icon != null) {
                                                        AgencyIcon(
                                                                iconName = icon,
                                                                contentDescription = name
                                                        )
                                                }
                                                Text(
                                                        text = name,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.SemiBold
                                                )
                                        }
                                }
                        }
                }

                // --- Remaining Routes (Including MTA / RATP) ---
                val remainingRoutes = routeInfos.filter { !handledRoutes.contains(it.routeId) }

                if (remainingRoutes.isNotEmpty()) {
                        FlowRow(
                                modifier = Modifier.padding(top = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                                remainingRoutes.forEach { route ->
                                        // check RATP
                                        val isRatp =
                                                RatpUtils.isIdfmChateau(route.chateauId) &&
                                                        RatpUtils.isRatpRoute(route.shortName)
                                        // check MTA
                                        val isMta =
                                                MtaSubwayUtils.MTA_CHATEAU_ID == route.chateauId &&
                                                        !route.shortName.isNullOrEmpty() &&
                                                        MtaSubwayUtils.isSubwayRouteId(
                                                                route.shortName!!
                                                        )

                                        if (isRatp) {
                                                val iconUrl =
                                                        RatpUtils.getRatpIconUrl(route.shortName)
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
                                                                contentDescription =
                                                                        route.shortName,
                                                                modifier = Modifier.size(20.dp)
                                                        )
                                                } else {
                                                        // Fallback to standard badge if icon fails?
                                                        // Or just skip?
                                                        // Usually existing logic falls back to
                                                        // standard badge.
                                                        StandardRouteBadge(route)
                                                }
                                        } else if (isMta) {
                                                val iconUrl =
                                                        MtaSubwayUtils.getMtaIconUrl(
                                                                route.shortName!!
                                                        )
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
                                                                contentDescription =
                                                                        route.shortName,
                                                                modifier = Modifier.size(20.dp)
                                                        )
                                                } else {
                                                        StandardRouteBadge(route)
                                                }
                                        } else {
                                                StandardRouteBadge(route)
                                        }
                                }
                        }
                }
        }
}

@Composable
fun StandardRouteBadge(route: ResolvedRouteBadgeInfo) {
        Box(
                modifier =
                        Modifier.clip(RoundedCornerShape(4.dp))
                                .background(parseColor(route.color, Color.Black))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
                Text(
                        text = route.shortName
                                        ?: route.longName?.replace(" Line", "") ?: route.routeId,
                        color = parseColor(route.textColor, Color.White),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                )
        }
}

@Composable
fun AgencyIcon(iconName: String, contentDescription: String?) {
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
                                .data("https://maps.catenarymaps.org/agencyicons/$iconName")
                                .decoderFactory(SvgDecoder.Factory())
                                .build(),
                imageLoader = imageLoader,
                contentDescription = contentDescription,
                modifier = Modifier.size(12.dp).padding(end = 4.dp)
        )
}
