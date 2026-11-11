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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.unit.TextUnit
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
        modifier: Modifier = Modifier,
        textSize: TextUnit = 10.sp,
        compact: Boolean = false
) {
    val routeInfos =
        remember(routeIds) {
            routeIds.mapNotNull { resolveRouteInfo(it) }.distinctBy {
                val name =
                    it.shortName
                        ?: it.longName?.replace(" Line", "") ?: it.routeId
                it.color to name
            }
        }

        if (routeInfos.isEmpty()) return

        val handledRoutes = mutableSetOf<String>()

        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = modifier) {
                // --- National Rail Logic ---
                val nrRoutes = routeInfos.filter { it.chateauId == "nationalrailuk" }
                if (nrRoutes.isNotEmpty()) {
                    val groupedRoutes =
                        nrRoutes
                            .mapNotNull { route ->
                                NationalRailUtils.getAgencyInfo(
                                    route.agencyId,
                                    null
                                )
                                    ?.let { it to route }
                            }
                            .groupBy({ it.first }, { it.second })

                    groupedRoutes.forEach { (info, routes) ->
                        routes.forEach { handledRoutes.add(it.routeId) }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier =
                                Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        MaterialTheme.colorScheme
                                            .surfaceVariant
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            if (info.icon != null) {
                                AgencyIcon(
                                    iconName = info.icon,
                                    contentDescription = info.name
                                )
                            }
                            if (compact) {
                                FadingBadgeText(
                                    text = info.name, style = MaterialTheme.typography.labelSmall,
                                    fontSize = textSize, fontWeight = FontWeight.Normal
                                )
                            } else {
                                Text(
                                    text = info.name, style = MaterialTheme.typography.labelSmall,
                                    fontSize = if (textSize == 10.sp) TextUnit.Unspecified else textSize,
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
                                                                modifier = Modifier.size(if (compact) 16.dp else 20.dp)
                                                        )
                                                } else {
                                                        // Fallback to standard badge if icon fails?
                                                        // Or just skip?
                                                        // Usually existing logic falls back to
                                                        // standard badge.
                                                        StandardRouteBadge(route, textSize, compact)
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
                                                                modifier = Modifier.size(if (compact) 16.dp else 20.dp)
                                                        )
                                                } else {
                                                        StandardRouteBadge(route, textSize, compact)
                                                }
                                        } else {
                                                StandardRouteBadge(route, textSize, compact)
                                        }
                                }
                        }
                }
        }
}

@Composable
fun StandardRouteBadge(route: ResolvedRouteBadgeInfo, textSize: TextUnit = 10.sp, compact: Boolean = false) {
        Box(
                modifier =
                        Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(parseColor(route.color, Color.Black))
                            .padding(horizontal = if (compact) 3.dp else 4.dp, vertical = if (compact) 1.dp else 2.dp)
        ) {
                 val label = route.shortName ?: route.longName?.replace(" Line", "") ?: route.routeId
                 if (compact) {
                     FadingBadgeText(
                         text = label, color = parseColor(route.textColor, Color.White),
                         style = MaterialTheme.typography.labelSmall, fontSize = textSize,
                         fontWeight = FontWeight.Normal
                     )
                 } else {
                     Text(
                         text = label, color = parseColor(route.textColor, Color.White),
                         style = MaterialTheme.typography.labelSmall, fontSize = textSize
                     )
                 }
        }
}

/**
 * Compact timeline badges must occupy exactly one line. Mask only clipped text so the
 * route-color background stays solid while the label fades at its allocated right edge.
 */
@Composable
internal fun FadingBadgeText(
    text: String, fontSize: TextUnit, fontWeight: FontWeight? = null,
    color: Color = LocalContentColor.current, style: TextStyle = LocalTextStyle.current
) {
    var isClipped by remember(text) { mutableStateOf(false) }
    val fade = if (isClipped) Modifier
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            if (size.width > 0f) {
                val fadeStart = (size.width - 12.dp.toPx()).coerceAtLeast(0f) / size.width
                drawRect(
                    brush = Brush.horizontalGradient(
                        0f to Color.Black, fadeStart to Color.Black, 1f to Color.Transparent,
                        startX = 0f, endX = size.width
                    ),
                    blendMode = BlendMode.DstIn
                )
            }
        } else Modifier

    Text(
        text = text, modifier = fade, style = style, fontSize = fontSize,
        fontWeight = fontWeight, color = color, maxLines = 1, softWrap = false,
        overflow = TextOverflow.Clip, onTextLayout = { isClipped = it.didOverflowWidth }
    )
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
                modifier = Modifier
                    .size(12.dp)
                    .padding(end = 4.dp)
        )
}
