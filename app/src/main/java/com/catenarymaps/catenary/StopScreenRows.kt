package com.catenarymaps.catenary

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import java.time.ZoneId
import java.util.Locale

private fun getRouteShape(chateau: String, shortName: String?): androidx.compose.ui.graphics.Shape {
        val isSBahn =
                (chateau == "dbregioag" || chateau == "deutschland") &&
                        shortName?.matches(Regex("^S\\d+")) == true
        return if (isSBahn) RoundedCornerShape(30.dp) else RoundedCornerShape(2.dp)
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
        eurostyle: Boolean = false,
        swiss: Boolean = false,
        modifier: Modifier = Modifier
) {
        val rtTime =
                if (event.last_stop == true) event.realtime_arrival else event.realtime_departure
        val schedTime =
                if (event.last_stop == true) event.scheduled_arrival else event.scheduled_departure

        // Only show seconds if the scheduled time isn't on an exact minute
        val effectiveShowSeconds = showSeconds

        val agencyId = routeInfo?.agency_id
        val agencyName = if (agencyId != null) agencies?.get(agencyId)?.agency_name else null
        val showRouteName =
                event.chateau != "nationalrailuk" ||
                        listOf("TW", "ME", "LO", "XR", "HX").contains(agencyId)

        val isPast = (rtTime ?: schedTime ?: 0) < (currentTime - 60)

        Row(
                modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
        ) {
                // Determine order
                // Standard: Time - Headsign (with Route under Headsign)
                // Eurostyle: Time - Route - Headsign
                // Swiss: Route - Time - Headsign

                val routeBubbleParams =
                        @Composable
                        {
                                androidx.compose.foundation.layout.Box(
                                        modifier =
                                                Modifier.width(if (swiss) 50.dp else 40.dp)
                                                        .padding(horizontal = 2.dp),
                                        contentAlignment =
                                                if (swiss) Alignment.CenterStart
                                                else Alignment.Center
                                ) {
                                        if (showRouteName && routeInfo?.short_name != null) {
                                                Text(
                                                        text =
                                                                routeInfo.short_name.replace(
                                                                        " Line",
                                                                        ""
                                                                ),
                                                        color =
                                                                parseColor(
                                                                        routeInfo.text_color,
                                                                        Color.White
                                                                ),
                                                        style =
                                                                MaterialTheme.typography.labelSmall
                                                                        .copy(
                                                                                fontSize =
                                                                                        if (swiss)
                                                                                                12.sp
                                                                                        else 10.sp,
                                                                                fontWeight =
                                                                                        FontWeight
                                                                                                .Bold
                                                                        ),
                                                        modifier =
                                                                Modifier.clip(
                                                                                getRouteShape(
                                                                                        event.chateau,
                                                                                        routeInfo
                                                                                                ?.short_name
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
                                                                                horizontal = 4.dp,
                                                                                vertical = 1.dp
                                                                        )
                                                )
                                        }
                                }
                        }

                if (swiss) {
                        routeBubbleParams()
                } else if (eurostyle) {
                        // Eurostyle route bubble is between Time and Headsign, it's rendered below.
                }
                // Left (or Middle if Swiss): Time (Vertical Stack)
                Column(
                        modifier = Modifier.width(70.dp).padding(horizontal = 4.dp),
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

                if (eurostyle && !swiss) {
                        routeBubbleParams()
                }

                // Middle (or Right if Swiss): Info
                Column(
                        modifier =
                                Modifier.weight(1f)
                                        .padding(start = if (swiss) 4.dp else 0.dp, end = 4.dp)
                ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                                // ---------------------------------------------------------
                                // ROW 1: Headsign + Trip Name (Merged)
                                // ---------------------------------------------------------
                                val headsignText = buildAnnotatedString {
                                        withStyle(SpanStyle(fontWeight = FontWeight.Medium)) {
                                                append(event.headsign ?: "")
                                        }

                                        if (!event.trip_short_name.isNullOrBlank()) {
                                                // Add a space before the trip name if headsign
                                                // exists
                                                if (length > 0) append(" ")

                                                withStyle(
                                                        SpanStyle(fontWeight = FontWeight.Light)
                                                ) { append(event.trip_short_name) }
                                        }
                                }

                                Text(
                                        text = headsignText,
                                        style = MaterialTheme.typography.bodyMedium,
                                        // Modifier.fillMaxWidth() allows wrapping; removing
                                        // weight/width constraints
                                        // helps it behave like a natural paragraph.
                                        modifier = Modifier.padding(bottom = 2.dp)
                                )
                        }
                        FlowRow(
                                modifier = Modifier.padding(top = 2.dp),
                                // verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
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
                                }

                                if (showRouteName &&
                                                !(eurostyle || swiss) &&
                                                routeInfo?.short_name != null
                                ) {
                                        if (routeInfo?.short_name != null) {
                                                Text(
                                                        text =
                                                                routeInfo.short_name.replace(
                                                                        " Line",
                                                                        ""
                                                                ),
                                                        color =
                                                                parseColor(
                                                                        routeInfo.text_color,
                                                                        Color.White
                                                                ),
                                                        style =
                                                                MaterialTheme.typography.labelSmall
                                                                        .copy(
                                                                                fontSize = 10.sp,
                                                                                fontWeight =
                                                                                        FontWeight
                                                                                                .Bold
                                                                        ),
                                                        modifier =
                                                                Modifier.clip(
                                                                                getRouteShape(
                                                                                        event.chateau,
                                                                                        routeInfo
                                                                                                ?.short_name
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
                                                                                horizontal = 4.dp,
                                                                                vertical = 1.dp
                                                                        )
                                                )
                                                Spacer(Modifier.width(4.dp))
                                        } else if (routeInfo?.long_name != null) {
                                                Text(
                                                        text = routeInfo.long_name,
                                                        color =
                                                                parseColor(
                                                                        routeInfo.text_color,
                                                                        Color.White
                                                                ),
                                                        style =
                                                                MaterialTheme.typography.labelSmall
                                                                        .copy(
                                                                                fontWeight =
                                                                                        FontWeight
                                                                                                .Bold
                                                                        ),
                                                        modifier =
                                                                Modifier.clip(
                                                                                getRouteShape(
                                                                                        event.chateau,
                                                                                        routeInfo
                                                                                                ?.short_name
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
                                                                                horizontal = 4.dp,
                                                                                vertical = 1.dp
                                                                        )
                                                )
                                        }
                                }
                        }
                }

                // Right: Platform
                Column(modifier = Modifier.width(40.dp), horizontalAlignment = Alignment.End) {
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
                                                Modifier.background(
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
                                                                                Modifier.height(
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
                                                                        Modifier.size(20.dp)
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
                                                                        Modifier.clip(
                                                                                        getRouteShape(
                                                                                                event.chateau,
                                                                                                routeInfo
                                                                                                        ?.short_name
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
                                                                                        getRouteShape(
                                                                                                event.chateau,
                                                                                                routeInfo
                                                                                                        ?.short_name
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
        eurostyle: Boolean = false,
        swiss: Boolean = false,
        modifier: Modifier = Modifier,
        onTripClick: (StopEvent) -> Unit = {}
) {
        val rtTime =
                if (event.last_stop == true) event.realtime_arrival else event.realtime_departure
        val schedTime =
                if (event.last_stop == true) event.scheduled_arrival else event.scheduled_departure

        val effectiveShowSeconds = showSeconds
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
                // Determine order
                // Standard: Time - Headsign (with Route under Headsign)
                // Eurostyle: Time - Route - Headsign
                // Swiss: Route - Time - Headsign

                val routeBubbleParams =
                        @Composable
                        {
                                androidx.compose.foundation.layout.Box(
                                        modifier =
                                                Modifier.width(if (swiss) 50.dp else 40.dp)
                                                        .padding(horizontal = 2.dp),
                                        contentAlignment =
                                                if (swiss) Alignment.CenterStart
                                                else Alignment.Center
                                ) {
                                        if (showRouteName && routeInfo?.short_name != null) {
                                                Text(
                                                        text =
                                                                routeInfo.short_name.replace(
                                                                        " Line",
                                                                        ""
                                                                ),
                                                        color =
                                                                parseColor(
                                                                        routeInfo.text_color,
                                                                        Color.White
                                                                ),
                                                        style =
                                                                MaterialTheme.typography.labelSmall
                                                                        .copy(
                                                                                fontSize =
                                                                                        if (swiss)
                                                                                                12.sp
                                                                                        else 10.sp,
                                                                                fontWeight =
                                                                                        FontWeight
                                                                                                .Bold
                                                                        ),
                                                        modifier =
                                                                Modifier.clip(
                                                                                getRouteShape(
                                                                                        event.chateau,
                                                                                        routeInfo
                                                                                                ?.short_name
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
                                                                                horizontal = 4.dp,
                                                                                vertical = 1.dp
                                                                        )
                                                )
                                        }
                                }
                        }

                if (swiss) {
                        routeBubbleParams()
                } else if (eurostyle) {
                        // Eurostyle route bubble is between Time and Headsign, it's rendered below.
                }

                // Left (or Middle if Swiss): Time (Vertical Stack)
                Column(
                        modifier = Modifier.width(70.dp).padding(horizontal = 4.dp),
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

                if (eurostyle && !swiss) {
                        routeBubbleParams()
                }

                // Middle (or Right if Swiss): Info
                Column(
                        modifier =
                                Modifier.weight(1f)
                                        .padding(start = if (swiss) 4.dp else 0.dp, end = 4.dp),
                        verticalArrangement = Arrangement.Center
                ) {
                        // ---------------------------------------------------------
                        // ROW 1: Headsign + Trip Name (Merged)
                        // ---------------------------------------------------------
                        val headsignText = buildAnnotatedString {
                                withStyle(SpanStyle(fontWeight = FontWeight.Medium)) {
                                        append(event.headsign ?: "")
                                }

                                if (!event.trip_short_name.isNullOrBlank()) {
                                        // Add a space before the trip name if headsign exists
                                        if (length > 0) append(" ")

                                        append(event.trip_short_name)
                                }
                        }

                        Text(
                                text = headsignText,
                                style = MaterialTheme.typography.bodyMedium,
                                // Modifier.fillMaxWidth() allows wrapping; removing weight/width
                                // constraints
                                // helps it behave like a natural paragraph.
                                modifier = Modifier.padding(bottom = 2.dp)
                        )

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
                                                !(eurostyle || swiss) &&
                                                routeInfo?.short_name != null
                                ) {
                                        if (showAgencyName &&
                                                        resolvedAgencyName != null &&
                                                        (routeInfo?.short_name != null ||
                                                                routeInfo?.long_name != null)
                                        ) {}
                                        if (routeInfo?.short_name != null) {
                                                Text(
                                                        text =
                                                                routeInfo.short_name.replace(
                                                                        " Line",
                                                                        ""
                                                                ),
                                                        color =
                                                                parseColor(
                                                                        routeInfo.text_color,
                                                                        Color.White
                                                                ),
                                                        style =
                                                                MaterialTheme.typography.labelSmall
                                                                        .copy(
                                                                                fontSize = 10.sp,
                                                                                fontWeight =
                                                                                        FontWeight
                                                                                                .Bold
                                                                        ),
                                                        modifier =
                                                                Modifier.clip(
                                                                                getRouteShape(
                                                                                        event.chateau,
                                                                                        routeInfo
                                                                                                ?.short_name
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
                                                                                horizontal = 4.dp,
                                                                                vertical = 0.5.dp
                                                                        )
                                                )
                                        } else if (routeInfo?.long_name != null) {
                                                Text(
                                                        text = routeInfo.long_name,
                                                        color =
                                                                parseColor(
                                                                        routeInfo.text_color,
                                                                        Color.White
                                                                ),
                                                        style =
                                                                MaterialTheme.typography.labelSmall
                                                                        .copy(
                                                                                fontSize = 10.sp,
                                                                                fontWeight =
                                                                                        FontWeight
                                                                                                .SemiBold
                                                                        ),
                                                        modifier =
                                                                Modifier.clip(
                                                                                getRouteShape(
                                                                                        event.chateau,
                                                                                        routeInfo
                                                                                                ?.short_name
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
                                                                                horizontal = 4.dp,
                                                                                vertical = 0.5.dp
                                                                        )
                                                )
                                        }
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
                                                MaterialTheme.typography.labelMedium.copy(
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 10.sp
                                                ),
                                )
                        }
                }
        }
}

@Composable
fun StopScreenRowV2(
        event: StopEvent,
        routeInfo: StopRouteInfo?,
        currentTime: Long,
        zoneId: ZoneId,
        locale: Locale,
        showSeconds: Boolean = true,
        showArrivals: Boolean = false,
        useSymbolSign: Boolean = false,
        vertical: Boolean = false,
        eurostyle: Boolean = false,
        swiss: Boolean = false,
        modifier: Modifier = Modifier,
        onTripClick: (StopEvent) -> Unit = {}
) {
        val rtTime =
                if (showArrivals || event.last_stop == true) event.realtime_arrival
                else event.realtime_departure
        val schedTime =
                if (showArrivals || event.last_stop == true) event.scheduled_arrival
                else event.scheduled_departure

        val isPast = (rtTime ?: schedTime ?: 0) < (currentTime - 60)
        val effectiveShowSeconds = showSeconds

        // Pre-calculate conditions for route display
        val isRatp =
                RatpUtils.isIdfmChateau(event.chateau) &&
                        RatpUtils.isRatpRoute(routeInfo?.short_name)
        val isMta =
                MtaSubwayUtils.MTA_CHATEAU_ID == event.chateau &&
                        !routeInfo?.short_name.isNullOrEmpty() &&
                        MtaSubwayUtils.isSubwayRouteId(routeInfo?.short_name!!)

        val routeName = routeInfo?.short_name ?: routeInfo?.long_name
        val isLongName = !isRatp && !isMta && (routeName?.length ?: 0) > 8
        val isBusOrMetro = routeInfo?.route_type in listOf(3, 11, 700, 0, 1, 5, 7, 12, 900)

        Column(
                modifier =
                        modifier.fillMaxWidth()
                                .clickable { onTripClick(event) }
                                .defaultMinSize(minHeight = 48.dp)
                                .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.Center
        ) {
                Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                        // 1. Name Column - Mode Specific Indicators
                        val nameColumn: @Composable () -> Unit = {
                                if (isRatp || isMta || (!isLongName && routeName != null)) {
                                        androidx.compose.foundation.layout.Box(
                                                modifier =
                                                        Modifier.width(if (swiss) 50.dp else 40.dp)
                                                                .padding(horizontal = 2.dp),
                                                contentAlignment =
                                                        if (swiss) Alignment.CenterStart
                                                        else Alignment.Center
                                        ) {
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
                                                                                        ImageLoader
                                                                                                .Builder(
                                                                                                        context
                                                                                                )
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
                                                                                Modifier.height(
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
                                                                        Modifier.size(20.dp)
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
                                                                                                                .Bold,
                                                                                                fontSize =
                                                                                                        10.sp
                                                                                        ),
                                                                        textAlign =
                                                                                androidx.compose.ui
                                                                                        .text.style
                                                                                        .TextAlign
                                                                                        .Center
                                                                )
                                                        }
                                                } else if (!isLongName) {
                                                        if (routeInfo?.short_name != null) {
                                                                Text(
                                                                        text = routeInfo.short_name,
                                                                        color =
                                                                                parseColor(
                                                                                        routeInfo
                                                                                                .text_color,
                                                                                        Color.White
                                                                                ),
                                                                        style =
                                                                                MaterialTheme
                                                                                        .typography
                                                                                        .labelSmall
                                                                                        .copy(
                                                                                                fontWeight =
                                                                                                        FontWeight
                                                                                                                .Bold,
                                                                                                fontSize =
                                                                                                        if (swiss
                                                                                                        )
                                                                                                                12.sp
                                                                                                        else
                                                                                                                10.sp
                                                                                        ),
                                                                        modifier =
                                                                                Modifier.clip(
                                                                                                getRouteShape(
                                                                                                        event.chateau,
                                                                                                        routeInfo
                                                                                                                ?.short_name
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
                                                                                MaterialTheme
                                                                                        .typography
                                                                                        .labelSmall
                                                                                        .copy(
                                                                                                fontWeight =
                                                                                                        FontWeight
                                                                                                                .SemiBold,
                                                                                                fontSize =
                                                                                                        if (swiss
                                                                                                        )
                                                                                                                12.sp
                                                                                                        else
                                                                                                                10.sp
                                                                                        ),
                                                                        modifier =
                                                                                Modifier.clip(
                                                                                                getRouteShape(
                                                                                                        event.chateau,
                                                                                                        routeInfo
                                                                                                                ?.short_name
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
                                                        }
                                                }
                                        }
                                }
                        }

                        // 2. Time Column (Vertical Stack) - Reused from
                        // StationScreenTrainRowCompact logic
                        val timeColumn: @Composable () -> Unit = {
                                Column(
                                        modifier = Modifier.padding(horizontal = 2.dp),
                                        horizontalAlignment = Alignment.Start,
                                        verticalArrangement = Arrangement.Center
                                ) {
                                        if (event.trip_cancelled == true) {
                                                Text(
                                                        text = stringResource(R.string.cancelled),
                                                        color = MaterialTheme.colorScheme.error,
                                                        style =
                                                                MaterialTheme.typography.labelSmall
                                                                        .copy(
                                                                                fontSize = 13.sp,
                                                                                fontWeight =
                                                                                        FontWeight
                                                                                                .Bold
                                                                        )
                                                )
                                                if (schedTime != null) {
                                                        FormattedTimeText(
                                                                timezone = zoneId.id,
                                                                timeSeconds = schedTime,
                                                                showSeconds = effectiveShowSeconds,
                                                                style =
                                                                        MaterialTheme.typography
                                                                                .labelSmall.copy(
                                                                                fontSize = 13.sp
                                                                        ),
                                                                color =
                                                                        MaterialTheme.colorScheme
                                                                                .onSurfaceVariant
                                                                                .copy(alpha = 0.7f)
                                                        )
                                                }
                                        } else if (event.trip_deleted == true) {
                                                Text(
                                                        text = stringResource(R.string.deleted),
                                                        color = MaterialTheme.colorScheme.error,
                                                        style =
                                                                MaterialTheme.typography.labelSmall
                                                                        .copy(fontSize = 10.sp)
                                                )
                                        } else if (event.stop_cancelled == true) {
                                                Text(
                                                        text =
                                                                stringResource(
                                                                        R.string.stop_cancelled
                                                                ),
                                                        color = MaterialTheme.colorScheme.error,
                                                        style =
                                                                MaterialTheme.typography.labelSmall
                                                                        .copy(fontSize = 10.sp)
                                                )
                                        } else {
                                                if (rtTime != null &&
                                                                schedTime != null &&
                                                                rtTime != schedTime
                                                ) {
                                                        // Sched (crossed)
                                                        FormattedTimeText(
                                                                timezone = zoneId.id,
                                                                timeSeconds = schedTime,
                                                                showSeconds = effectiveShowSeconds,
                                                                style =
                                                                        MaterialTheme.typography
                                                                                .labelSmall.copy(
                                                                                fontSize = 13.sp
                                                                        ),
                                                                color =
                                                                        MaterialTheme.colorScheme
                                                                                .onSurfaceVariant
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
                                                                        MaterialTheme.typography
                                                                                .labelSmall.copy(
                                                                                fontSize = 14.sp,
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
                                                } else {
                                                        // On Time OR No Realtime Data
                                                        if (rtTime != null) {
                                                                FormattedTimeText(
                                                                        timezone = zoneId.id,
                                                                        timeSeconds = rtTime,
                                                                        showSeconds =
                                                                                effectiveShowSeconds,
                                                                        style =
                                                                                MaterialTheme
                                                                                        .typography
                                                                                        .labelSmall
                                                                                        .copy(
                                                                                                fontSize =
                                                                                                        14.sp,
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
                                                                        showSeconds =
                                                                                effectiveShowSeconds,
                                                                        style =
                                                                                MaterialTheme
                                                                                        .typography
                                                                                        .labelSmall
                                                                                        .copy(
                                                                                                fontSize =
                                                                                                        14.sp,
                                                                                                fontWeight =
                                                                                                        FontWeight
                                                                                                                .Medium
                                                                                        ),
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
                                        // TimeDiff (Countdown)
                                        val diff = (rtTime ?: schedTime ?: 0) - currentTime
                                        if (diff < 3600 && (rtTime ?: schedTime) != null) {
                                                SelfUpdatingDiffTimer(
                                                        targetTimeSeconds = rtTime
                                                                        ?: schedTime ?: 0,
                                                        showBrackets = false,
                                                        showSeconds =
                                                                effectiveShowSeconds && diff < 3600,
                                                        showDays = false,
                                                        numSize = 13.sp,
                                                        showPlus = false
                                                )
                                        }
                                }
                        }

                        // 3. Info (Middle) - Headsign
                        val headsignColumn: @Composable () -> Unit = {
                                Column(
                                        modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                                        verticalArrangement = Arrangement.Center
                                ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                if (isLongName && routeName != null) {
                                                        Text(
                                                                text = routeName,
                                                                color =
                                                                        parseColor(
                                                                                routeInfo
                                                                                        ?.text_color,
                                                                                Color.White
                                                                        ),
                                                                style =
                                                                        MaterialTheme.typography
                                                                                .labelSmall.copy(
                                                                                fontSize = 10.sp,
                                                                                fontWeight =
                                                                                        FontWeight
                                                                                                .Bold
                                                                        ),
                                                                modifier =
                                                                        Modifier.padding(end = 4.dp)
                                                                                .clip(
                                                                                        getRouteShape(
                                                                                                event.chateau,
                                                                                                routeInfo
                                                                                                        ?.short_name
                                                                                        )
                                                                                )
                                                                                .background(
                                                                                        parseColor(
                                                                                                routeInfo
                                                                                                        ?.color,
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
                                                }
                                                Text(
                                                        text = event.headsign ?: "",
                                                        style =
                                                                MaterialTheme.typography.bodyMedium
                                                                        .copy(
                                                                                fontWeight =
                                                                                        FontWeight
                                                                                                .Bold
                                                                        ),
                                                        maxLines = 1,
                                                        overflow =
                                                                androidx.compose.ui.text.style
                                                                        .TextOverflow.Ellipsis
                                                )
                                                if (!event.trip_short_name.isNullOrBlank()) {
                                                        Text(
                                                                text = " ${event.trip_short_name}",
                                                                style =
                                                                        MaterialTheme.typography
                                                                                .labelSmall,
                                                                modifier =
                                                                        Modifier.padding(
                                                                                start = 2.dp
                                                                        )
                                                        )
                                                }
                                        }
                                        if (event.last_stop == true) {
                                                Text(
                                                        text = stringResource(R.string.last_stop),
                                                        style =
                                                                MaterialTheme.typography.labelSmall
                                                                        .copy(fontSize = 10.sp),
                                                        fontWeight = FontWeight.Bold,
                                                        color =
                                                                MaterialTheme.colorScheme
                                                                        .onSurfaceVariant
                                                )
                                        }
                                        // Display vehicle number if available
                                        if (!event.vehicle_number.isNullOrBlank()) {
                                                Text(
                                                        text =
                                                                "${stringResource(R.string.vehicle)}: ${event.vehicle_number}",
                                                        style =
                                                                MaterialTheme.typography.labelSmall
                                                                        .copy(fontSize = 10.sp),
                                                        color =
                                                                MaterialTheme.colorScheme
                                                                        .onSurfaceVariant
                                                )
                                        }
                                }
                        }

                        // 4. Platform (Right)
                        val platformColumn: @Composable () -> Unit = {
                                if (!event.platform_string_realtime.isNullOrBlank()) {
                                        Column(
                                                modifier = Modifier.width(80.dp),
                                                horizontalAlignment = Alignment.End
                                        ) {
                                                Text(
                                                        text =
                                                                event.platform_string_realtime
                                                                        .replace("Track", "")
                                                                        .replace(
                                                                                "platform",
                                                                                "",
                                                                                ignoreCase = true
                                                                        )
                                                                        .replace("Platform", "")
                                                                        .trim(),
                                                        style =
                                                                MaterialTheme.typography.labelSmall
                                                                        .copy(
                                                                                fontWeight =
                                                                                        FontWeight
                                                                                                .Bold,
                                                                                fontSize = 10.sp
                                                                        ),
                                                        modifier =
                                                                Modifier.background(
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .surfaceVariant,
                                                                                RoundedCornerShape(
                                                                                        4.dp
                                                                                )
                                                                        )
                                                                        .padding(
                                                                                horizontal = 4.dp,
                                                                                vertical = 2.dp
                                                                        )
                                                )
                                        }
                                }
                        }

                        if (swiss) {
                                nameColumn()
                                timeColumn()
                                headsignColumn()
                        } else if (eurostyle) {
                                timeColumn()
                                nameColumn()
                                headsignColumn()
                        } else {
                                // Default for Bus/Metro (matches Eurostyle order originally)
                                timeColumn()
                                nameColumn()
                                headsignColumn()
                        }
                        platformColumn()
                }
        }
}
