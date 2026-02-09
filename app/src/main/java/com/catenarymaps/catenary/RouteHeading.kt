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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
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
                MaterialTheme.typography.titleMedium.copy(
                        textDecoration = TextDecoration.Underline,
                        lineHeight = MaterialTheme.typography.titleLarge.fontSize * 0.8
                )
            } else {
                MaterialTheme.typography.titleMedium.copy(
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
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(
                                end = 48.dp
                            ) // adjust if your controls are wider/narrower
            ) {
                // Route Name Header
                // Define logic variables early to flatten the UI tree
                val isRatp = chateauId?.startsWith("ratp") == true // Assuming check based on utils
                val isMta = chateauId?.startsWith("mta") == true // Assuming check based on utils
                val isNationalRail = chateauId == "nationalrailuk"
                val isLondonOverground = shortName?.startsWith("LO-") == true
                val isElizabethLine = shortName == "XR-ELIZABETH"

                // Determine if we have a graphical icon (Ratp or MTA)
                // This logic mirrors your original nesting but separates layout from logic
                var iconComposable: (@Composable () -> Unit)? = null

                if (isRatp) {
                    val iconUrl = RatpUtils.getRatpIconUrl(shortName)
                    if (iconUrl != null) {
                        iconComposable = {
                            val context = LocalContext.current
                            AsyncImage(
                                    model =
                                            ImageRequest.Builder(context)
                                                    .data(iconUrl)
                                                    .crossfade(true)
                                                .decoderFactory(SvgDecoder.Factory())
                                                    .build(),
                                    contentDescription = shortName,
                                modifier = Modifier
                                    .height(32.dp)
                                    .padding(end = 8.dp)
                            )
                        }
                    }
                } else if (isMta && shortName != null) {
                    val iconUrl = MtaSubwayUtils.getMtaIconUrl(shortName)
                    if (iconUrl != null) {
                        iconComposable = {
                            val context = LocalContext.current
                            AsyncImage(
                                    model =
                                            ImageRequest.Builder(context)
                                                    .data(iconUrl)
                                                    .crossfade(true)
                                                .decoderFactory(SvgDecoder.Factory())
                                                    .build(),
                                    contentDescription = shortName,
                                modifier = Modifier
                                    .height(32.dp)
                                    .padding(end = 8.dp)
                            )
                        }
                    } else {
                        // MTA fallback: Colored Box
                        val mtaColor = MtaSubwayUtils.getMtaSubwayColor(shortName)
                        val symbolShortName = MtaSubwayUtils.getMtaSymbolShortName(shortName)
                        iconComposable = {
                            Box(
                                    modifier =
                                        Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(mtaColor),
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
                        }
                    }
                }

                // Route Name Header Row
                Row(modifier = clickableModifier, verticalAlignment = Alignment.CenterVertically) {
                    // 1. Render the Icon (if it exists)
                    iconComposable?.invoke()

                    // 2. Render the Text Flow (Short + Long + Trip)
                    // We build a single AnnotatedString so it wraps like a standard paragraph
                    val routeText = buildAnnotatedString {

                        // Part A: Short Name (Only if no icon was displayed)
                        if (iconComposable == null &&
                            !shortName.isNullOrBlank() &&
                                        (!isNationalRail || isLondonOverground || isElizabethLine)
                        ) {
                            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                                append(shortName.replace(" Line", ""))
                            }
                            append(" ") // Spacer
                        }

                        // Part B: Long Name
                        if (!longName.isNullOrBlank() && chateauId != "metrolinktrains") {
                            val hasTo = longName.contains(" to ", ignoreCase = true)
                            val shouldShow =
                                when {
                                    chateauId == "viarail" -> true
                                    !isNationalRail -> true
                                    else -> !hasTo || isLondonOverground || isElizabethLine
                                }

                            if (shouldShow) {
                                append(longName)
                            }
                        }

                        // Part C: Trip Short Name
                        if (tripShortName != null) {
                            // Ensure visual separation if we have previous text
                            if (length > 0) append(" ")

                            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                                append(tripShortName)
                            }
                        }
                    }

                    // Render the consolidated text
                    Text(
                        text = routeText,
                        style = textStyle,
                        color = displayColor,
                        // weight(1f) ensures the text takes available space and wraps,
                        // preventing the row from pushing content off-screen.
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }

                if (headsign != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = headsign,
                        )
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
