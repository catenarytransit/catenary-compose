package com.catenarymaps.catenary

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest

@Composable
fun RouteHeading(
        color: String,
        textColor: String,
        routeType: Int?,
        agencyName: String?,
        shortName: String?,
        longName: String?,
        description: String? = null,
        tripShortName: String? = null,
        chateauId: String? = null, // Added chateauId
        isCompact: Boolean,
        routeClickable: Boolean = false,
        headsign: String? = null,
        onRouteClick: (() -> Unit)? = null,
        controls: @Composable () -> Unit = {}
) {
    if (isCompact) {
        // Compact version not implemented based on svelte file
        return
    }

    println("route heading, $shortName $longName")

    val routeColor = parseColor(color, MaterialTheme.colorScheme.primary)
    val routeTextColor = parseColor(textColor, MaterialTheme.colorScheme.onPrimary)

    val displayColor =
            if (androidx.compose.foundation.isSystemInDarkTheme()) {
                lightenColour(routeColor, minContrast = 7.0)
            } else {
                darkenColour(routeColor)
            }

    val clickableModifier =
            if (routeClickable && onRouteClick != null) {
                Modifier.clickable(onClick = onRouteClick)
            } else {
                Modifier
            }

    val textStyle =
            if (routeClickable) {
                MaterialTheme.typography.titleLarge.copy(
                        textDecoration = TextDecoration.Underline,
                        lineHeight = MaterialTheme.typography.titleLarge.fontSize * 0.8
                )
            } else {
                MaterialTheme.typography.titleLarge.copy(
                        lineHeight = MaterialTheme.typography.titleLarge.fontSize * 0.8
                )
            }

    // Determine special rendering
    val isRatp = RatpUtils.isIdfmChateau(chateauId) && RatpUtils.isRatpRoute(shortName)
    val isMta =
            MtaSubwayUtils.MTA_CHATEAU_ID == chateauId &&
                    !shortName.isNullOrEmpty() &&
                    MtaSubwayUtils.isSubwayRouteId(shortName)

    Column {
        // ⬇️ This Box replaces the Row, putting controls in the top-right
        Box(modifier = Modifier.fillMaxWidth()) {
            // Main text content; padded on the end so it "wraps around" controls
            Column(
                    modifier =
                            Modifier.align(Alignment.TopStart)
                                    .padding(
                                            end = 48.dp
                                    ) // adjust if your controls are wider/narrower
            ) {
                // Route Name Header
                Row(modifier = clickableModifier, verticalAlignment = Alignment.CenterVertically) {
                    if (isRatp) {
                        val iconUrl = RatpUtils.getRatpIconUrl(shortName)
                        if (iconUrl != null) {
                            AsyncImage(
                                    model =
                                            ImageRequest.Builder(LocalContext.current)
                                                    .data(iconUrl)
                                                    .crossfade(true)
                                                    .build(),
                                    contentDescription = shortName,
                                    modifier =
                                            Modifier.height(32.dp) // Match roughly the text height
                                                    .padding(end = 8.dp)
                            )
                        } else {
                            // Fallback
                            Text(
                                    text = shortName ?: "",
                                    style = textStyle.copy(fontWeight = FontWeight.Bold),
                                    color = displayColor
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                    } else if (isMta && shortName != null) {
                        val mtaColor = MtaSubwayUtils.getMtaSubwayColor(shortName)
                        val symbolShortName = MtaSubwayUtils.getMtaSymbolShortName(shortName)
                        Box(
                                modifier =
                                        Modifier.size(32.dp).clip(CircleShape).background(mtaColor),
                                contentAlignment = Alignment.Center
                        ) {
                            Text(
                                    text = symbolShortName,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    } else {
                        // Default Text Rendering for Short Name
                        if (!shortName.isNullOrBlank()) {
                            Text(
                                    text = shortName,
                                    style = textStyle.copy(fontWeight = FontWeight.Bold),
                                    color = displayColor
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                    }

                    // Long Name
                    if (!longName.isNullOrBlank()) {
                        Text(text = longName, style = textStyle, color = displayColor)
                    }
                }

                if (headsign != null || tripShortName != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (tripShortName != null) {
                            Box(
                                    modifier =
                                            Modifier.clip(RoundedCornerShape(4.dp))
                                                    .background(routeColor)
                                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            ) { Text(text = tripShortName, color = routeTextColor) }

                            Spacer(Modifier.size(4.dp))
                        }

                        if (headsign != null) {
                            Text(
                                    text = headsign,
                            )
                        }
                    }
                }

                if (!agencyName.isNullOrBlank()) {
                    Text(text = agencyName ?: "", style = MaterialTheme.typography.titleMedium)
                }

                if (!description.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = description ?: "", style = MaterialTheme.typography.bodyMedium)
                }
            }

            // Controls anchored to the top-right
            Box(modifier = Modifier.align(Alignment.TopEnd)) { controls() }
        }
    }
}
