package com.catenarymaps.catenary

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import java.time.ZoneId
import java.util.Locale

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
        val rtTime =
                if (event.last_stop == true) event.realtime_arrival else event.realtime_departure
        val schedTime =
                if (event.last_stop == true) event.scheduled_arrival else event.scheduled_departure

        // Only show seconds if the scheduled time isn't on an exact minute
        val effectiveShowSeconds = showSeconds && (schedTime?.rem(60) != 0L)

        val agencyId = routeInfo?.agency_id
        val agencyName = if (agencyId != null) agencies?.get(agencyId)?.agency_name else null
        val showRouteName =
                event.chateau != "nationalrailuk" ||
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
                    modifier = Modifier
                        .width(80.dp)
                        .padding(end = 8.dp),
                        horizontalAlignment = Alignment.Start
                ) {
                        if (event.trip_cancelled == true) {
                                Text(
                                        text = stringResource(R.string.cancelled),
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.labelSmall
                                )
                                if (schedTime != null) {
                                        FormattedTimeText(
                                                timezone = zoneId.id,
                                                timeSeconds = schedTime,
                                                showSeconds = effectiveShowSeconds,
                                                style = MaterialTheme.typography.labelSmall,
                                                textDecoration = TextDecoration.LineThrough,
                                                color =
                                                        MaterialTheme.colorScheme.onSurfaceVariant
                                                                .copy(alpha = 0.7f)
                                        )
                                }
                        } else if (event.trip_deleted == true) {
                                Text(
                                        text = stringResource(R.string.deleted),
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.labelSmall
                                )
                        } else if (event.stop_cancelled == true) {
                                Text(
                                        text = stringResource(R.string.stop_cancelled),
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.labelSmall
                                )
                        } else {
                                if (rtTime != null) {
                                        // Vertical Mode
                                        if (schedTime != null && rtTime != schedTime) {
                                                FormattedTimeText(
                                                        timezone = zoneId.id,
                                                        timeSeconds = schedTime,
                                                        showSeconds = effectiveShowSeconds,
                                                        style =
                                                                MaterialTheme.typography
                                                                        .labelSmall, // xs
                                                        textDecoration = TextDecoration.LineThrough,
                                                        color =
                                                                MaterialTheme.colorScheme
                                                                        .onSurfaceVariant
                                                )
                                                DelayDiff(
                                                        diff = rtTime - schedTime,
                                                        show_seconds = effectiveShowSeconds,
                                                        use_symbol_sign = useSymbolSign
                                                )
                                                FormattedTimeText(
                                                        timezone = zoneId.id,
                                                        timeSeconds = rtTime,
                                                        showSeconds = effectiveShowSeconds,
                                                        style =
                                                                MaterialTheme.typography.bodyMedium
                                                                        .copy(
                                                                                fontWeight =
                                                                                        FontWeight
                                                                                                .Medium
                                                                        ), // font-medium
                                                        color =
                                                                if (isPast)
                                                                        MaterialTheme.colorScheme
                                                                                .primary.copy(
                                                                                alpha = 0.7f
                                                                        )
                                                                else
                                                                        MaterialTheme.colorScheme
                                                                                .primary
                                                )
                                        } else {
                                                // On Time
                                                FormattedTimeText(
                                                        timezone = zoneId.id,
                                                        timeSeconds = rtTime,
                                                        showSeconds = effectiveShowSeconds,
                                                        style =
                                                                MaterialTheme.typography.bodyMedium
                                                                        .copy(
                                                                                fontWeight =
                                                                                        FontWeight
                                                                                                .Medium
                                                                        ),
                                                        color =
                                                                if (isPast)
                                                                        MaterialTheme.colorScheme
                                                                                .primary.copy(
                                                                                alpha = 0.7f
                                                                        )
                                                                else
                                                                        MaterialTheme.colorScheme
                                                                                .primary
                                                )
                                        }
                                } else if (schedTime != null) {
                                        FormattedTimeText(
                                                timezone = zoneId.id,
                                                timeSeconds = schedTime,
                                                showSeconds = effectiveShowSeconds,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color =
                                                        if (isPast)
                                                                MaterialTheme.colorScheme.onSurface
                                                                        .copy(alpha = 0.7f)
                                                        else MaterialTheme.colorScheme.onSurface
                                        )
                                }

                                // Countdown
                                val target = rtTime ?: schedTime
                                if (target != null) {
                                        SelfUpdatingDiffTimer(
                                                targetTimeSeconds = target,
                                                showBrackets = false,
                                                showSeconds = effectiveShowSeconds,
                                                showDays = false,
                                                numSize = 12.sp,
                                                showPlus = false
                                        )
                                }
                        }
                }

                // Middle: Info
            Column(modifier = Modifier
                .weight(1f)
                .padding(end = 4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                        text = event.headsign ?: "",
                                        style = MaterialTheme.typography.bodyMedium
                                )
                                if (!event.trip_short_name.isNullOrBlank()) {
                                        Text(
                                                text = " ${event.trip_short_name}",
                                                style =
                                                        MaterialTheme.typography.bodyMedium.copy(
                                                                fontWeight = FontWeight.Bold
                                                        ),
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
                                                color =
                                                        parseColor(
                                                                routeInfo.text_color,
                                                                Color.White
                                                        ),
                                                style =
                                                        MaterialTheme.typography.labelSmall.copy(
                                                                fontWeight = FontWeight.Bold
                                                        ),
                                                modifier =
                                                    Modifier
                                                        .clip(RoundedCornerShape(2.dp))
                                                        .background(
                                                            parseColor(
                                                                routeInfo.color,
                                                                Color.Gray
                                                            )
                                                        )
                                                        .padding(
                                                            horizontal = 4.dp,
                                                            vertical = 1.dp
                                                        )
                                        )
                                }

                                val agencyInfo =
                                        NationalRailUtils.getAgencyInfo(agencyId, agencyName)
                                val resolvedAgencyName = agencyInfo?.name ?: agencyName

                                if (resolvedAgencyName != null) {
                                        val iconUrl =
                                                NationalRailUtils.getAgencyIconUrl(
                                                        agencyId,
                                                        agencyName
                                                )
                                        if (iconUrl != null) {
                                                val context = LocalContext.current
                                                val imageLoader =
                                                        androidx.compose.runtime.remember(context) {
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
                                                                ImageRequest.Builder(context)
                                                                        .data(iconUrl)
                                                                        .crossfade(true)
                                                                        .build(),
                                                        imageLoader = imageLoader,
                                                        contentDescription = agencyName,
                                                        modifier = Modifier.size(16.dp),
                                                        colorFilter =
                                                                null // SVGs might have their own
                                                        // colors
                                                        )
                                        }

                                        Text(
                                                text = resolvedAgencyName,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                                text = "•",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                }

                                if (showRouteName &&
                                                routeInfo?.short_name == null &&
                                                routeInfo?.long_name != null
                                ) {
                                        Text(
                                                text = routeInfo.long_name,
                                                color =
                                                        parseColor(
                                                                routeInfo.text_color,
                                                                Color.White
                                                        ),
                                                style =
                                                        MaterialTheme.typography.labelSmall.copy(
                                                                fontWeight = FontWeight.Bold
                                                        ),
                                                modifier =
                                                    Modifier
                                                        .clip(RoundedCornerShape(2.dp))
                                                        .background(
                                                            parseColor(
                                                                routeInfo.color,
                                                                Color.Gray
                                                            )
                                                        )
                                                        .padding(
                                                            horizontal = 4.dp,
                                                            vertical = 1.dp
                                                        )
                                        )
                                }
                        }
                }

                // Right: Platform
                Column(modifier = Modifier.width(80.dp), horizontalAlignment = Alignment.End) {
                        if (!event.platform_string_realtime.isNullOrBlank()) {
                                Text(
                                        text =
                                                event.platform_string_realtime
                                                        .replace("Track", "")
                                                        .replace("platform", "", ignoreCase = true)
                                                        .replace("Platform", "")
                                                        .trim(),
                                        style =
                                                MaterialTheme.typography.labelMedium.copy(
                                                        fontWeight = FontWeight.Bold
                                                ),
                                        modifier =
                                            Modifier
                                                .background(
                                                    MaterialTheme.colorScheme
                                                        .surfaceVariant,
                                                    RoundedCornerShape(4.dp)
                                                )
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
        showArrivals: Boolean = false,
        useSymbolSign: Boolean = false,
        vertical: Boolean = false,
        modifier: Modifier = Modifier
) {
        val rtTime =
                if (showArrivals || event.last_stop == true) event.realtime_arrival
                else event.realtime_departure
        val schedTime =
                if (showArrivals || event.last_stop == true) event.scheduled_arrival
                else event.scheduled_departure

        val isPast = (rtTime ?: schedTime ?: 0) < (currentTime - 60)

        // We can use a simpler layout for "Bus" style
        Column(modifier = modifier.fillMaxWidth()) {
                if (event.trip_cancelled == true) {
                        Row(Modifier.fillMaxWidth()) {
                                Text(
                                        stringResource(R.string.cancelled),
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.SemiBold
                                )
                                Spacer(Modifier.weight(1f))
                                if (schedTime != null) {
                                        FormattedTimeText(
                                                timezone = zoneId.id,
                                                timeSeconds = schedTime,
                                                showSeconds = showSeconds,
                                                textDecoration = TextDecoration.LineThrough,
                                                color =
                                                        MaterialTheme.colorScheme.onSurfaceVariant
                                                                .copy(alpha = 0.7f)
                                        )
                                }
                        }
                } else if (event.trip_deleted == true) {
                        Row(Modifier.fillMaxWidth()) {
                                Text(
                                        stringResource(R.string.deleted),
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.SemiBold
                                )
                                Spacer(Modifier.weight(1f))
                                if (schedTime != null) {
                                        FormattedTimeText(
                                                timezone = zoneId.id,
                                                timeSeconds = schedTime,
                                                showSeconds = showSeconds,
                                                textDecoration = TextDecoration.LineThrough,
                                                color =
                                                        MaterialTheme.colorScheme.onSurfaceVariant
                                                                .copy(alpha = 0.7f)
                                        )
                                }
                        }
                } else if (event.stop_cancelled == true) {
                        Row(Modifier.fillMaxWidth()) {
                                Text(
                                        stringResource(R.string.stop_cancelled),
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.SemiBold
                                )
                                Spacer(Modifier.weight(1f))
                                if (schedTime != null) {
                                        FormattedTimeText(
                                                timezone = zoneId.id,
                                                timeSeconds = schedTime,
                                                showSeconds = showSeconds,
                                                textDecoration = TextDecoration.LineThrough,
                                                color =
                                                        MaterialTheme.colorScheme.onSurfaceVariant
                                                                .copy(alpha = 0.7f)
                                        )
                                }
                        }
                } else {
                        // Main Row
                        Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                                // Info
                                Column(Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                val isRatp =
                                                        RatpUtils.isIdfmChateau(event.chateau) &&
                                                                RatpUtils.isRatpRoute(
                                                                        routeInfo?.short_name
                                                                )
                                                val isMta =
                                                        MtaSubwayUtils.MTA_CHATEAU_ID ==
                                                                event.chateau &&
                                                                !routeInfo?.short_name
                                                                        .isNullOrEmpty() &&
                                                                MtaSubwayUtils.isSubwayRouteId(
                                                                        routeInfo?.short_name!!
                                                                )

                                                if (isRatp) {
                                                        val iconUrl =
                                                                RatpUtils.getRatpIconUrl(
                                                                        routeInfo?.short_name
                                                                )
                                                        if (iconUrl != null) {
                                                                val context = LocalContext.current
                                                                val imageLoader =
                                                                        androidx.compose.runtime
                                                                                .remember(context) {
                                                                                        coil.ImageLoader
                                                                                                .Builder(
                                                                                                        context
                                                                                                )
                                                                                                .components {
                                                                                                        add(
                                                                                                                coil.decode
                                                                                                                        .SvgDecoder
                                                                                                                        .Factory()
                                                                                                        )
                                                                                                }
                                                                                                .build()
                                                                                }
                                                                AsyncImage(
                                                                        model =
                                                                                ImageRequest
                                                                                        .Builder(
                                                                                                context
                                                                                        )
                                                                                        .data(
                                                                                                iconUrl
                                                                                        )
                                                                                        .crossfade(
                                                                                                true
                                                                                        )
                                                                                        .build(),
                                                                        imageLoader = imageLoader,
                                                                        contentDescription =
                                                                                routeInfo
                                                                                        ?.short_name,
                                                                        modifier =
                                                                            Modifier
                                                                                .height(
                                                                                    20.dp
                                                                                )
                                                                                .padding(
                                                                                    end =
                                                                                        4.dp
                                                                                )
                                                                )
                                                        }
                                                } else if (isMta) {
                                                        val mtaColor =
                                                                MtaSubwayUtils.getMtaSubwayColor(
                                                                        routeInfo?.short_name!!
                                                                )
                                                        val symbolShortName =
                                                                MtaSubwayUtils
                                                                        .getMtaSymbolShortName(
                                                                                routeInfo.short_name
                                                                        )
                                                        androidx.compose.foundation.layout.Box(
                                                                modifier =
                                                                    Modifier
                                                                        .size(20.dp)
                                                                        .clip(CircleShape)
                                                                        .background(
                                                                            mtaColor
                                                                        ),
                                                                contentAlignment = Alignment.Center
                                                        ) {
                                                                Text(
                                                                        text = symbolShortName,
                                                                        color = Color.White,
                                                                        style =
                                                                                MaterialTheme
                                                                                        .typography
                                                                                        .labelSmall
                                                                                        .copy(
                                                                                                fontWeight =
                                                                                                        FontWeight
                                                                                                                .Bold
                                                                                        ),
                                                                        textAlign =
                                                                                androidx.compose.ui
                                                                                        .text.style
                                                                                        .TextAlign
                                                                                        .Center
                                                                )
                                                        }
                                                        Spacer(Modifier.width(4.dp))
                                                } else if (routeInfo?.short_name != null) {
                                                        Text(
                                                                text = routeInfo.short_name,
                                                                color =
                                                                        parseColor(
                                                                                routeInfo
                                                                                        .text_color,
                                                                                Color.White
                                                                        ),
                                                                style =
                                                                        MaterialTheme.typography
                                                                                .labelSmall.copy(
                                                                                fontWeight =
                                                                                        FontWeight
                                                                                                .Bold
                                                                        ),
                                                                modifier =
                                                                    Modifier
                                                                        .clip(
                                                                            RoundedCornerShape(
                                                                                2.dp
                                                                            )
                                                                        )
                                                                        .background(
                                                                            parseColor(
                                                                                routeInfo
                                                                                    .color,
                                                                                Color.Gray
                                                                            )
                                                                        )
                                                                        .padding(
                                                                            horizontal =
                                                                                4.dp,
                                                                            vertical =
                                                                                1.dp
                                                                        )
                                                        )
                                                        Spacer(Modifier.width(4.dp))
                                                } else if (routeInfo?.long_name != null) {
                                                        Text(
                                                                text = routeInfo.long_name,
                                                                color =
                                                                        parseColor(
                                                                                routeInfo
                                                                                        .text_color,
                                                                                Color.White
                                                                        ),
                                                                style =
                                                                        MaterialTheme.typography
                                                                                .labelSmall.copy(
                                                                                fontWeight =
                                                                                        FontWeight
                                                                                                .SemiBold
                                                                        ),
                                                                modifier =
                                                                    Modifier.clip(
                                                                                        RoundedCornerShape(
                                                                                                2.dp
                                                                                        )
                                                                                )
                                                                                .background(
                                                                                        parseColor(
                                                                                                routeInfo
                                                                                                        .color,
                                                                                                Color.Gray
                                                                                        )
                                                                                )
                                                                                .padding(
                                                                                        horizontal =
                                                                                                4.dp,
                                                                                        vertical =
                                                                                                1.dp
                                                                                )
                                                        )
                                                        Spacer(Modifier.width(4.dp))
                                                }

                                                if (!event.trip_short_name.isNullOrBlank()) {
                                                        Text(
                                                                event.trip_short_name,
                                                                fontWeight = FontWeight.Bold,
                                                                style =
                                                                        MaterialTheme.typography
                                                                                .bodyMedium
                                                        )
                                                        Spacer(Modifier.width(4.dp))
                                                }
                                                Text(
                                                        event.headsign ?: "",
                                                        style = MaterialTheme.typography.bodyMedium
                                                )
                                        }
                                        if (event.last_stop == true) {
                                                Text(
                                                        stringResource(R.string.last_stop),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold
                                                )
                                        }
                                }

                                // Times
                                // !vertical implies TimeDiff + Diff?
                                // Svelte:
                                // if !vertical: TimeDiff (minutes) + DelayDiff
                                // AND Right side: Clock

                                Column(horizontalAlignment = Alignment.End) {
                                        // Countdown
                                        if ((rtTime ?: schedTime) != null) {
                                                SelfUpdatingDiffTimer(
                                                        targetTimeSeconds = rtTime
                                                                        ?: schedTime ?: 0,
                                                        showBrackets = false,
                                                        showSeconds = showSeconds,
                                                        showDays = false,
                                                        numSize = 14.sp,
                                                        showPlus = false
                                                )
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                if (rtTime != null &&
                                                                schedTime != null &&
                                                                rtTime != schedTime
                                                ) {
                                                        // Delay
                                                        DelayDiff(
                                                                diff = rtTime - schedTime,
                                                                show_seconds = showSeconds,
                                                                use_symbol_sign = useSymbolSign
                                                        )
                                                        Spacer(Modifier.width(4.dp))

                                                        // Strikethrough

                                                }

                                                // Real time / Scheduled
                                                if (rtTime != null) {
                                                        FormattedTimeText(
                                                                timezone = zoneId.id,
                                                                timeSeconds = rtTime,
                                                                showSeconds = showSeconds,
                                                                style =
                                                                        MaterialTheme.typography
                                                                                .bodyMedium.copy(
                                                                                fontWeight =
                                                                                        FontWeight
                                                                                                .Medium
                                                                        ),
                                                                color =
                                                                        if (isPast)
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .primary
                                                                                        .copy(
                                                                                                alpha =
                                                                                                        0.7f
                                                                                        )
                                                                        else
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .primary
                                                        )
                                                } else if (schedTime != null) {
                                                        FormattedTimeText(
                                                                timezone = zoneId.id,
                                                                timeSeconds = schedTime,
                                                                showSeconds = showSeconds,
                                                                style =
                                                                        MaterialTheme.typography
                                                                                .bodyMedium,
                                                                color =
                                                                        if (isPast)
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .onSurface
                                                                                        .copy(
                                                                                                alpha =
                                                                                                        0.7f
                                                                                        )
                                                                        else
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .onSurface
                                                        )
                                                }
                                        }
                                }
                        }

                        // Extra info line
                        Row {
                                if (!event.platform_string_realtime.isNullOrBlank()) {
                                        Text(
                                                "${stringResource(R.string.platform)} ${event.platform_string_realtime}",
                                                style = MaterialTheme.typography.bodySmall
                                        )
                                        Spacer(Modifier.width(8.dp))
                                }
                                if (!event.vehicle_number.isNullOrBlank()) {
                                        Text(
                                                "${stringResource(R.string.vehicle)}: ${event.vehicle_number}",
                                                style = MaterialTheme.typography.bodySmall
                                        )
                                }
                        }
                }
        }
}

@Composable
fun StationScreenTrainRowCompact(
    event: StopEvent,
    routeInfo: StopRouteInfo?,
    agencies: Map<String, AgencyInfo>?,
    currentTime: Long,
    zoneId: ZoneId,
    locale: Locale,
    showSeconds: Boolean = true,
    useSymbolSign: Boolean = false,
    showAgencyName: Boolean = true,
    showTimeDiff: Boolean = true,
    modifier: Modifier = Modifier,
    onTripClick: (StopEvent) -> Unit = {}
) {
    val rtTime =
        if (event.last_stop == true) event.realtime_arrival else event.realtime_departure
    val schedTime =
        if (event.last_stop == true) event.scheduled_arrival else event.scheduled_departure

    val effectiveShowSeconds = showSeconds && (schedTime?.rem(60) != 0L)
    val agencyId = routeInfo?.agency_id
    val agencyName = if (agencyId != null) agencies?.get(agencyId)?.agency_name else null
    val showRouteName =
        event.chateau != "nationalrailuk" ||
                listOf("TW", "ME", "LO", "XR", "HX").contains(agencyId)
    val isPast = (rtTime ?: schedTime ?: 0) < (currentTime - 60)

    Row(
        modifier =
            modifier.fillMaxWidth()
                .clickable { onTripClick(event) }
                .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Route Name Bubble (Leftmost)
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.width(40.dp).padding(horizontal = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            if (showRouteName && routeInfo?.short_name != null) {
                Text(
                    text = routeInfo.short_name.replace(" Line", ""),
                    color = parseColor(routeInfo.text_color, Color.White),
                    style =
                        MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        ),
                    modifier =
                        Modifier.clip(RoundedCornerShape(2.dp))
                            .background(
                                parseColor(
                                    routeInfo.color,
                                    Color.Gray
                                )
                            )
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
        }

        // 2. Time (Vertical Stack)
        Column(
            modifier = Modifier.width(70.dp).padding(horizontal = 2.dp),
            horizontalAlignment = Alignment.Start
        ) {
            if (event.trip_cancelled == true) {
                Text(
                    text = stringResource(R.string.cancelled),
                    color = MaterialTheme.colorScheme.error,
                    style =
                        MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                )
                if (schedTime != null) {
                    FormattedTimeText(
                        timezone = zoneId.id,
                        timeSeconds = schedTime,
                        showSeconds = effectiveShowSeconds,
                        style =
                            MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp
                            ),
                        textDecoration = TextDecoration.LineThrough,
                        color =
                            MaterialTheme.colorScheme.onSurfaceVariant
                                .copy(alpha = 0.7f)
                    )
                }
            } else if (event.trip_deleted == true) {
                Text(
                    text = stringResource(R.string.deleted),
                    color = MaterialTheme.colorScheme.error,
                    style =
                        MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp
                        )
                )
            } else if (event.stop_cancelled == true) {
                Text(
                    text = stringResource(R.string.stop_cancelled),
                    color = MaterialTheme.colorScheme.error,
                    style =
                        MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp
                        )
                )
            } else {
                if (rtTime != null && schedTime != null && rtTime != schedTime) {
                    // Sched (crossed)
                    FormattedTimeText(
                        timezone = zoneId.id,
                        timeSeconds = schedTime,
                        showSeconds = effectiveShowSeconds,
                        style =
                            MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp
                            ),
                        textDecoration = TextDecoration.LineThrough,
                        color =
                            MaterialTheme.colorScheme.onSurfaceVariant
                                .copy(alpha = 0.7f)
                    )
                    // Delay
                    DelayDiff(
                        diff = rtTime - schedTime,
                        show_seconds = effectiveShowSeconds,
                        use_symbol_sign = useSymbolSign
                    )
                    // Realtime
                    FormattedTimeText(
                        timezone = zoneId.id,
                        timeSeconds = rtTime,
                        showSeconds = effectiveShowSeconds,
                        style =
                            MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            ),
                        color =
                            if (isPast)
                                MaterialTheme.colorScheme.primary
                                    .copy(alpha = 0.7f)
                            else MaterialTheme.colorScheme.primary
                    )
                } else {
                    // On Time OR No Realtime Data
                    if (rtTime != null) {
                        // Realtime (On Time)
                        FormattedTimeText(
                            timezone = zoneId.id,
                            timeSeconds = rtTime,
                            showSeconds = effectiveShowSeconds,
                            style =
                                MaterialTheme.typography.labelSmall
                                    .copy(
                                        fontSize = 11.sp,
                                        fontWeight =
                                            FontWeight
                                                .Medium
                                    ),
                            color =
                                if (isPast)
                                    MaterialTheme.colorScheme
                                        .primary.copy(
                                            alpha = 0.7f
                                        )
                                else
                                    MaterialTheme.colorScheme
                                        .primary
                        )
                    } else if (schedTime != null) {
                        // Scheduled Only
                        FormattedTimeText(
                            timezone = zoneId.id,
                            timeSeconds = schedTime,
                            showSeconds = effectiveShowSeconds,
                            style =
                                MaterialTheme.typography.labelSmall
                                    .copy(
                                        fontSize = 11.sp,
                                        fontWeight =
                                            FontWeight
                                                .Medium
                                    ),
                            color =
                                if (isPast)
                                    MaterialTheme.colorScheme
                                        .onSurface.copy(
                                            alpha = 0.7f
                                        )
                                else
                                    MaterialTheme.colorScheme
                                        .onSurface
                        )
                    }
                }
            }
            // TimeDiff (Countdown)
            val diff = (rtTime ?: schedTime ?: 0) - currentTime
            if (showTimeDiff && diff < 3600 && (rtTime ?: schedTime) != null) {
                SelfUpdatingDiffTimer(
                    targetTimeSeconds = rtTime ?: schedTime ?: 0,
                    showBrackets = false,
                    showSeconds = effectiveShowSeconds && diff < 3600,
                    showDays = false,
                    numSize = 10.sp,
                    showPlus = false
                )
            }
        }

        // 3. Info (Middle)
        Column(
            modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = event.headsign ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1
                )
                if (!event.trip_short_name.isNullOrBlank()) {
                    Text(
                        text = " ${event.trip_short_name}",
                        style =
                            MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold
                            ),
                        modifier = Modifier.padding(start = 2.dp)
                    )
                }
            }
            // Agency Name / Long Name
            Row(
                modifier = Modifier.padding(top = 0.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val agencyInfo =
                    NationalRailUtils.getAgencyInfo(agencyId, agencyName)
                val resolvedAgencyName = agencyInfo?.name ?: agencyName
                if (showAgencyName && resolvedAgencyName != null) {
                    val iconUrl =
                        NationalRailUtils.getAgencyIconUrl(
                            agencyId,
                            agencyName
                        )
                    if (iconUrl != null) {
                        val context = LocalContext.current
                        val imageLoader =
                            androidx.compose.runtime.remember(context) {
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
                                ImageRequest.Builder(context)
                                    .data(iconUrl)
                                    .crossfade(true)
                                    .build(),
                            imageLoader = imageLoader,
                            contentDescription = agencyName,
                            modifier =
                                Modifier.size(12.dp)
                                    .padding(end = 2.dp),
                            colorFilter = null
                        )
                    }
                    Text(
                        text = resolvedAgencyName,
                        style =
                            MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp
                            ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (showRouteName &&
                    routeInfo?.short_name == null &&
                    routeInfo?.long_name != null
                ) {
                    if (showAgencyName && resolvedAgencyName != null) {
                        Text(
                            text = " • ",
                            style =
                                MaterialTheme.typography.labelSmall
                                    .copy(fontSize = 10.sp),
                            color =
                                MaterialTheme.colorScheme
                                    .onSurfaceVariant
                        )
                    }
                    Text(
                        text = routeInfo.long_name,
                        color =
                            parseColor(
                                routeInfo.text_color,
                                Color.White
                            ),
                        style =
                            MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                        modifier =
                            Modifier.clip(RoundedCornerShape(2.dp))
                                .background(
                                    parseColor(
                                        routeInfo.color,
                                        Color.Gray
                                    )
                                )
                                .padding(
                                    horizontal = 4.dp,
                                    vertical = 0.5.dp
                                )
                    )
                }
            }
        }

        // 4. Platform (Right)
        Column(modifier = Modifier.width(80.dp), horizontalAlignment = Alignment.End) {
            if (!event.platform_string_realtime.isNullOrBlank()) {
                Text(
                    text =
                        event.platform_string_realtime
                            .replace("Track", "")
                            .replace("platform", "", ignoreCase = true)
                            .replace("Platform", "")
                            .trim(),
                    style =
                        MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        ),
                    modifier =
                        Modifier.background(
                            MaterialTheme.colorScheme
                                .surfaceVariant,
                            RoundedCornerShape(4.dp)
                        )
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }
    }
}
