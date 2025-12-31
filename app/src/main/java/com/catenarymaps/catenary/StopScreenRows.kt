package com.catenarymaps.catenary

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.catenarymaps.catenary.StopEvent // redundant but safe?
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.ZoneId
import java.util.Locale
import coil.compose.AsyncImage
import coil.ImageLoader
import coil.decode.SvgDecoder
import androidx.compose.ui.platform.LocalContext
import coil.request.ImageRequest

private fun getAgencyIconUrl(agencyId: String?, agencyName: String?): String? {
    val base = "https://maps.catenarymaps.org/agencyicons"
    return when {
        agencyId == "GWR" || agencyName?.trim()?.lowercase() == "gwr" -> "$base/GreaterWesternRailway.svg" // Note: Svelte had Brighter variant for dark mode, ignoring for now or could handle with isSystemInDarkTheme()
        agencyName?.trim()?.lowercase() == "london overground" -> "$base/uk-london-overground.svg"
        agencyId == "CC" || agencyName?.trim()?.lowercase() == "c2c" -> "$base/c2c_logo.svg"
        agencyName?.trim()?.lowercase() == "elizabeth line" -> "$base/Elizabeth_line_roundel.svg" // User said all SVG
        else -> null
    }
}

@Composable
fun StationScreenTrainRow(
    event: StopEvent,
    routeInfo: StopRouteInfo?,
    agencies: Map<String, AgencyInfo>?, // Agency ID -> Info
    currentTime: Long,
    zoneId: ZoneId,
    locale: Locale,
    showSeconds: Boolean = true,
    useSymbolSign: Boolean = true,
    modifier: Modifier = Modifier
) {
    val rtTime = if (event.last_stop == true) event.realtime_arrival else event.realtime_departure
    val schedTime = if (event.last_stop == true) event.scheduled_arrival else event.scheduled_departure
    val agencyId = routeInfo?.agency_id
    val agencyName = if (agencyId != null) agencies?.get(agencyId)?.agency_name else null
    val showRouteName = event.chateau != "nationalrailuk" || 
                        listOf("TW", "ME", "LO", "XR", "HX").contains(agencyId)

    val isPast = (rtTime ?: schedTime ?: 0) < (currentTime - 60)
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: Time (Vertical Stack)
        Column(
            modifier = Modifier.width(80.dp).padding(end = 8.dp),
            horizontalAlignment = Alignment.Start
        ) {
            if (event.trip_cancelled == true) {
                Text(text = stringResource(R.string.cancelled), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                if (schedTime != null) {
                    FormattedTimeText(
                        timezone = zoneId.id,
                        timeSeconds = schedTime,
                        showSeconds = showSeconds,
                        style = MaterialTheme.typography.labelSmall,
                        textDecoration = TextDecoration.LineThrough,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            } else if (event.trip_deleted == true) {
                Text(text = stringResource(R.string.deleted), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
            } else if (event.stop_cancelled == true) {
                Text(text = stringResource(R.string.stop_cancelled), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
            } else {
                if (rtTime != null) {
                    // Vertical Mode
                    if (schedTime != null && rtTime != schedTime) {
                        FormattedTimeText(
                            timezone = zoneId.id,
                            timeSeconds = schedTime,
                            showSeconds = showSeconds,
                            style = MaterialTheme.typography.labelSmall, // xs
                            textDecoration = TextDecoration.LineThrough,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        DelayDiff(diff = rtTime - schedTime, show_seconds = showSeconds, use_symbol_sign = useSymbolSign)
                        FormattedTimeText(
                            timezone = zoneId.id,
                            timeSeconds = rtTime,
                            showSeconds = showSeconds,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), // font-medium
                            color = if (isPast) MaterialTheme.colorScheme.primary.copy(alpha=0.7f) else MaterialTheme.colorScheme.primary
                        )
                    } else {
                        // On Time
                        FormattedTimeText(
                            timezone = zoneId.id,
                            timeSeconds = rtTime,
                            showSeconds = showSeconds,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = if (isPast) MaterialTheme.colorScheme.primary.copy(alpha=0.7f) else MaterialTheme.colorScheme.primary
                        )
                    }
                } else if (schedTime != null) {
                    FormattedTimeText(
                        timezone = zoneId.id,
                        timeSeconds = schedTime,
                        showSeconds = showSeconds,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isPast) MaterialTheme.colorScheme.onSurface.copy(alpha=0.7f) else MaterialTheme.colorScheme.onSurface
                    )
                }
                
                // Countdown
                 val target = rtTime ?: schedTime
                 if (target != null) {
                     DiffTimer(
                         diff = (target - currentTime).toDouble(),
                         showBrackets = false,
                         showSeconds = showSeconds,
                         showDays = false,
                         numSize = 12.sp,
                         showPlus = false
                     )
                 }
            }
        }

        // Middle: Info
        Column(
            modifier = Modifier.weight(1f).padding(end = 4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = event.headsign ?: "",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (!event.trip_short_name.isNullOrBlank()) {
                    Text(
                        text = " ${event.trip_short_name}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
            Row(
                modifier = Modifier.padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (showRouteName && routeInfo?.short_name != null) {
                     Text(
                        text = routeInfo.short_name,
                        color = parseColor(routeInfo.text_color, Color.White),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier
                            .clip(RoundedCornerShape(2.dp))
                            .background(parseColor(routeInfo.color, Color.Gray))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }

                if (agencyName != null) {
                     val iconUrl = getAgencyIconUrl(agencyId, agencyName)
                     if (iconUrl != null) {
                         val context = LocalContext.current
                         val imageLoader = androidx.compose.runtime.remember(context) {
                             ImageLoader.Builder(context)
                                 .components {
                                     add(SvgDecoder.Factory())
                                 }
                                 .build()
                         }
                         
                         AsyncImage(
                             model = ImageRequest.Builder(context)
                                 .data(iconUrl)
                                 .crossfade(true)
                                 .build(),
                             imageLoader = imageLoader,
                             contentDescription = agencyName,
                             modifier = Modifier.size(16.dp),
                             colorFilter = null // SVGs might have their own colors
                         )
                     }
                     
                     Text(
                         text = agencyName,
                         style = MaterialTheme.typography.bodySmall,
                         color = MaterialTheme.colorScheme.onSurfaceVariant
                     )
                     Text(text = "•", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                if (showRouteName && routeInfo?.short_name == null && routeInfo?.long_name != null) {
                     Text(
                        text = routeInfo.long_name,
                        color = parseColor(routeInfo.text_color, Color.White),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                         modifier = Modifier
                            .clip(RoundedCornerShape(2.dp))
                            .background(parseColor(routeInfo.color, Color.Gray))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }
        }

        // Right: Platform
        Column(
            modifier = Modifier.width(80.dp),
            horizontalAlignment = Alignment.End
        ) {
            if (!event.platform_string_realtime.isNullOrBlank()) {
                Text(
                    text = stringResource(R.string.platform) + " " + event.platform_string_realtime.replace("Track", "").trim(),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
fun StopScreenRow(
    event: StopEvent,
    routeInfo: StopRouteInfo?,
    currentTime: Long,
    zoneId: ZoneId,
    locale: Locale,
    showSeconds: Boolean = true,
    vertical: Boolean = false, // Not really used in Svelte 'StopScreen' directly except it calculates it?
    // Actually Svelte passed vertical={false} or derived it?
    // In Svelte StopScreen.svelte, StopScreenRow is used with default vertical (false?)
    // Actually the Svelte code I saw didn't pass vertical, so it's false (default).
    // EXCEPT inside StopScreenRow, it toggles? No, it's a prop.
    // I will stick to Horizontal layout mainly, but if we need Vertical we can add it.
    // The request implies duplicating Svelte structure. Svelte StopScreenRow has vertical prop.
    
    modifier: Modifier = Modifier
) {
    val rtTime = if (event.last_stop == true) event.realtime_arrival else event.realtime_departure
    val schedTime = if (event.last_stop == true) event.scheduled_arrival else event.scheduled_departure
    
    val isPast = (rtTime ?: schedTime ?: 0) < (currentTime - 60)
    
    // We can use a simpler layout for "Bus" style
    Column(modifier = modifier.fillMaxWidth()) {
        if (event.trip_cancelled == true) {
             Row(Modifier.fillMaxWidth()) {
                 Text(stringResource(R.string.cancelled), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                 Spacer(Modifier.weight(1f))
                 if (schedTime != null) {
                      FormattedTimeText(
                        timezone = zoneId.id,
                        timeSeconds = schedTime,
                        showSeconds = showSeconds,
                        textDecoration = TextDecoration.LineThrough,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=0.7f)
                    )
                 }
             }
        } else if (event.trip_deleted == true) {
             Row(Modifier.fillMaxWidth()) {
                 Text(stringResource(R.string.deleted), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                 Spacer(Modifier.weight(1f))
                 if (schedTime != null) {
                      FormattedTimeText(
                        timezone = zoneId.id,
                        timeSeconds = schedTime,
                        showSeconds = showSeconds,
                        textDecoration = TextDecoration.LineThrough,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=0.7f)
                    )
                 }
             }
        } else if (event.stop_cancelled == true) {
             Row(Modifier.fillMaxWidth()) {
                 Text(stringResource(R.string.stop_cancelled), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                 Spacer(Modifier.weight(1f))
                 if (schedTime != null) {
                      FormattedTimeText(
                        timezone = zoneId.id,
                        timeSeconds = schedTime,
                        showSeconds = showSeconds,
                        textDecoration = TextDecoration.LineThrough,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=0.7f)
                    )
                 }
             }
        } else {
             // Main Row
             Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                 // Info
                 Column(Modifier.weight(1f)) {
                     Row(verticalAlignment = Alignment.CenterVertically) {
                         if (routeInfo?.short_name != null) {
                             Text(
                                text = routeInfo.short_name,
                                color = parseColor(routeInfo.text_color, Color.White),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(parseColor(routeInfo.color, Color.Gray))
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                         } else if (routeInfo?.long_name != null) {
                             Text(
                                text = routeInfo.long_name,
                                color = parseColor(routeInfo.text_color, Color.White),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(parseColor(routeInfo.color, Color.Gray))
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                         }
                         
                         if (!event.trip_short_name.isNullOrBlank()) {
                             Text(event.trip_short_name, fontWeight = FontWeight.Bold, style=MaterialTheme.typography.bodyMedium)
                             Spacer(Modifier.width(4.dp))
                         }
                         Text(event.headsign ?: "", style=MaterialTheme.typography.bodyMedium)
                     }
                     if (event.last_stop == true) {
                         Text(stringResource(R.string.last_stop), style=MaterialTheme.typography.labelSmall, fontWeight=FontWeight.Bold)
                     }
                 }
                 
                 // Times
                 // !vertical implies TimeDiff + Diff?
                 // Svelte:
                 // if !vertical: TimeDiff (minutes) + DelayDiff
                 // AND Right side: Clock
                 
                 Column(horizontalAlignment = Alignment.End) {
                     // Countdown
                     val diff = (rtTime ?: schedTime ?: 0) - currentTime
                     if ((rtTime ?: schedTime) != null) {
                         DiffTimer(
                             diff = diff.toDouble(),
                             showBrackets = false,
                             showSeconds = showSeconds,
                             showDays = false,
                             numSize = 14.sp,
                             showPlus = false
                         )
                     }
                     
                     Row(verticalAlignment = Alignment.CenterVertically) {
                         if (rtTime != null && schedTime != null && rtTime != schedTime) {
                             // Delay
                             DelayDiff(diff = rtTime - schedTime, show_seconds = showSeconds, use_symbol_sign = false) // use_symbol_sign false for non-rail?
                             Spacer(Modifier.width(4.dp))
                             
                             // Strikethrough
                             FormattedTimeText(
                                timezone = zoneId.id,
                                timeSeconds = schedTime,
                                showSeconds = showSeconds,
                                textDecoration = TextDecoration.LineThrough,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=0.7f)
                             )
                             Spacer(Modifier.width(4.dp))
                         }
                         
                         // Real time / Scheduled
                         if (rtTime != null) {
                             FormattedTimeText(
                                timezone = zoneId.id,
                                timeSeconds = rtTime,
                                showSeconds = showSeconds,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight=FontWeight.Medium),
                                color = if (isPast) MaterialTheme.colorScheme.primary.copy(alpha=0.7f) else MaterialTheme.colorScheme.primary
                             )
                         } else if (schedTime != null) {
                              FormattedTimeText(
                                timezone = zoneId.id,
                                timeSeconds = schedTime,
                                showSeconds = showSeconds,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isPast) MaterialTheme.colorScheme.onSurface.copy(alpha=0.7f) else MaterialTheme.colorScheme.onSurface
                             )
                         }
                     }
                 }
             }
             
             // Extra info line
             Row {
                 if (!event.platform_string_realtime.isNullOrBlank()) {
                     Text("${stringResource(R.string.platform)} ${event.platform_string_realtime}", style=MaterialTheme.typography.bodySmall)
                     Spacer(Modifier.width(8.dp))
                 }
                 if (!event.vehicle_number.isNullOrBlank()) {
                     Text("${stringResource(R.string.vehicle)}: ${event.vehicle_number}", style=MaterialTheme.typography.bodySmall)
                 }
             }
        }
    }
}
