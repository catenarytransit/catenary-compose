package com.catenarymaps.catenary

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Accessible
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.ChildFriendly
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PedalBike
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.VoiceOverOff
import androidx.compose.material.icons.filled.Wc
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlin.math.max
import kotlin.math.min

private const val SBB_ICON_ROOT = "https://maps.catenarymaps.org/icons/sbb"
private const val SBB_VEHICLE_GAP_DP = 6f
private const val SBB_NO_PASSAGE_GAP_DP = 22f

@Composable
fun CoachSequencePage(
    coachSequence: UnifiedConsist? = null,
    sbbFormation: SbbFormationData? = null,
    modifier: Modifier = Modifier
) {
    when {
        !sbbFormation?.formations.isNullOrEmpty() ->
            SbbFormationPage(data = sbbFormation!!, modifier = modifier)
        coachSequence != null ->
            UnifiedFormationPage(coachSequence = coachSequence, modifier = modifier)
        else ->
            EmptyFormationMessage(modifier)
    }
}

private data class SbbStationOption(
    val key: String,
    val name: String,
    val uic: Long?,
    val track: String?,
    val stopTime: SbbStopTime?,
    val destination: String?,
    val formationShortString: String?
)

private data class SbbVehicleView(
    val vehicle: SbbFormationVehicle,
    val stationData: SbbFormationVehicleAtScheduledStop?,
    val widthDp: Float,
    val startDp: Float,
    val endDp: Float,
    val gapAfterDp: Float
)

private data class SbbSectorSegment(
    val label: String,
    val leftDp: Float,
    val widthDp: Float,
    val compact: Boolean
)

private data class SbbAmenityDefinition(
    val key: String,
    val iconUrl: String,
    val label: String,
    val shortLabel: String
)

private val sbbAmenityCatalog = mapOf(
    "wheelchair" to SbbAmenityDefinition(
        "wheelchair",
        "$SBB_ICON_ROOT/wheelchair.svg",
        "Wheelchair space",
        "Wheelchair"
    ),
    "wheelchair_toilet" to SbbAmenityDefinition(
        "wheelchair_toilet",
        "$SBB_ICON_ROOT/wheelchair.svg",
        "Wheelchair space with wheelchair-accessible toilet",
        "Wheelchair WC"
    ),
    "bicycle" to SbbAmenityDefinition(
        "bicycle",
        "$SBB_ICON_ROOT/bicycle.svg",
        "Bicycle space",
        "Bicycle"
    ),
    "business_zone" to SbbAmenityDefinition(
        "business_zone",
        "$SBB_ICON_ROOT/laptop.svg",
        "Business zone in 1st class",
        "Business"
    ),
    "family_zone" to SbbAmenityDefinition(
        "family_zone",
        "$SBB_ICON_ROOT/family-zone.svg",
        "Family coach with play area",
        "Family"
    ),
    "stroller" to SbbAmenityDefinition(
        "stroller",
        "$SBB_ICON_ROOT/stroller.svg",
        "Stroller space",
        "Stroller"
    ),
    "restaurant" to SbbAmenityDefinition(
        "restaurant",
        "$SBB_ICON_ROOT/restaurant.svg",
        "Restaurant / Catering",
        "Restaurant"
    ),
    "low_floor" to SbbAmenityDefinition(
        "low_floor",
        "$SBB_ICON_ROOT/niederflureinstieg.svg",
        "Low-floor access",
        "Low floor"
    ),
    "sleeping" to SbbAmenityDefinition(
        "sleeping",
        "$SBB_ICON_ROOT/sleeping-car.svg",
        "Sleeping car",
        "Sleeping car"
    ),
    "emergency_call" to SbbAmenityDefinition(
        "emergency_call",
        "$SBB_ICON_ROOT/emergency-call.svg",
        "Emergency call system",
        "Emergency call"
    ),
    "closed" to SbbAmenityDefinition(
        "closed",
        "$SBB_ICON_ROOT/closed.svg",
        "Coach closed",
        "Closed"
    )
)

@Composable
private fun SbbFormationPage(data: SbbFormationData, modifier: Modifier = Modifier) {
    val stations = remember(data) { getSbbStations(data) }
    var selectedStationKey by remember(data) { mutableStateOf(stations.firstOrNull()?.key.orEmpty()) }
    var showVehicleNumber by remember { mutableStateOf(false) }

    LaunchedEffect(stations) {
        if (stations.none { it.key == selectedStationKey }) {
            selectedStationKey = stations.firstOrNull()?.key.orEmpty()
        }
    }

    val selectedStation = remember(stations, selectedStationKey) {
        stations.firstOrNull { it.key == selectedStationKey }
    }
    val formation = remember(data, selectedStation, stations) {
        selectSbbFormation(data, selectedStation, stations)
    }
    val vehicleViews = remember(formation, selectedStation) {
        buildSbbVehicleViews(formation, selectedStation)
    }
    val trainWidth = vehicleViews.lastOrNull()?.endDp ?: 0f
    val sectorSegments = remember(vehicleViews, trainWidth) {
        buildSbbSectorSegments(vehicleViews, trainWidth)
    }
    val legendItems = remember(vehicleViews, selectedStation) {
        collectSbbLegendItems(vehicleViews, selectedStation)
    }
    val classes = remember(vehicleViews) { collectSbbClasses(vehicleViews) }
    val direction = selectedStation?.destination
        ?: stations.lastOrNull()?.takeIf { it.key != selectedStationKey }?.name.orEmpty()

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Train formation",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )

        if (stations.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(stations, key = { it.key }) { station ->
                    FilterChip(
                        selected = station.key == selectedStationKey,
                        onClick = { selectedStationKey = station.key },
                        label = { Text(station.name, maxLines = 1) }
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        if (selectedStation == null || vehicleViews.isEmpty()) {
            EmptyFormationMessage(Modifier.fillMaxWidth())
            return@Column
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (isSystemInDarkTheme()) Color(0xFF1A1C1E)
                    else Color(0xFFF9FAFB)
                )
                .padding(vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("‹", fontSize = 20.sp)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Direction of travel${if (direction.isBlank()) "" else " $direction"}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
                selectedStation.track?.let { track ->
                    Text(
                        text = "Platform $track",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            SbbFormationMetadata(
                formation = formation,
                showVehicleNumber = showVehicleNumber,
                onShowVehicleNumberChange = { showVehicleNumber = it }
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(trainWidth.dp)
                        .height(28.dp)
                ) {
                    sectorSegments.forEach { segment ->
                        Row(
                            modifier = Modifier
                                .offset(x = segment.leftDp.dp)
                                .width(segment.widthDp.dp)
                                .height(28.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            HorizontalDivider(modifier = Modifier.weight(1f))
                            Text(
                                text = if (segment.compact) "Sec. ${segment.label}" else "Sector ${segment.label}",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 4.dp),
                                maxLines = 1
                            )
                            HorizontalDivider(modifier = Modifier.weight(1f))
                        }
                    }
                }

                Row(verticalAlignment = Alignment.Top) {
                    vehicleViews.forEachIndexed { index, view ->
                        SbbVehicle(
                            view = view,
                            fallbackIndex = index,
                            station = selectedStation,
                            showVehicleNumber = showVehicleNumber
                        )
                        if (view.gapAfterDp > 0f) {
                            Box(
                                modifier = Modifier
                                    .width(view.gapAfterDp.dp)
                                    .height(78.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (vehicleViews.getOrNull(index + 1)
                                        ?.stationData
                                        ?.accessToPreviousVehicle == false
                                ) {
                                    NoPassageIcon()
                                }
                            }
                        }
                    }
                }
            }
        }

        FormationLegend(
            classes = classes,
            amenities = legendItems,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp)
        )
    }
}

@Composable
private fun SbbFormationMetadata(
    formation: SbbFormation?,
    showVehicleNumber: Boolean,
    onShowVehicleNumberChange: (Boolean) -> Unit
) {
    val meta = formation?.metaInformation
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            meta?.length?.let { MetadataText("Length", "$it m") }
            meta?.numberVehicles?.let { MetadataText("Vehicles", it.toString()) }
            meta?.numberSeats?.let { MetadataText("Seats", it.toString()) }
            meta?.numberAxis?.let { MetadataText("Axis", it.toString()) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Vehicle number",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(6.dp))
            Switch(
                checked = showVehicleNumber,
                onCheckedChange = onShowVehicleNumberChange,
                modifier = Modifier.size(width = 42.dp, height = 28.dp)
            )
        }
    }
}

@Composable
private fun MetadataText(label: String, value: String) {
    Text(
        text = "$label: $value",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1
    )
}

@Composable
private fun SbbVehicle(
    view: SbbVehicleView,
    fallbackIndex: Int,
    station: SbbStationOption,
    showVehicleNumber: Boolean
) {
    val vehicle = view.vehicle
    val vehicleClass = getSbbVehicleClass(vehicle)
    val amenities = getSbbVehicleAmenities(vehicle, station)
    val typeCode = getSbbVehicleTypeCode(vehicle)
    val identifier = vehicle.vehicleIdentifier
    val iconTint = ColorFilter.tint(MaterialTheme.colorScheme.onSurface)

    Column(
        modifier = Modifier.width(view.widthDp.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = getSbbVehicleLabel(vehicle, fallbackIndex),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.height(18.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .border(
                    width = 1.5.dp,
                    color = MaterialTheme.colorScheme.onSurface,
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            if (vehicle.vehicleProperties?.closed == true) {
                AsyncImage(
                    model = "$SBB_ICON_ROOT/closed.svg",
                    contentDescription = "Coach closed",
                    colorFilter = iconTint,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .size(16.dp)
                )
            }
            if (vehicleClass.isNotBlank()) {
                Text(vehicleClass, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
        Row(
            modifier = Modifier
                .height(28.dp)
                .widthIn(max = view.widthDp.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            amenities.forEach { amenity ->
                AsyncImage(
                    model = amenity.iconUrl,
                    contentDescription = amenity.label,
                    colorFilter = iconTint,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
        Text(
            text = typeCode,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .height(16.dp)
                .padding(horizontal = 2.dp)
        )
        if (showVehicleNumber) {
            Text(
                text = identifier?.vehicleNumber ?: "—",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(16.dp)
                    .padding(horizontal = 2.dp)
            )
        }
    }
}

@Composable
private fun NoPassageIcon() {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier = Modifier.size(15.dp)) {
        drawCircle(color = color, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx()))
        drawLine(
            color = color,
            start = Offset(size.width * 0.2f, size.height * 0.8f),
            end = Offset(size.width * 0.8f, size.height * 0.2f),
            strokeWidth = 1.5.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun FormationLegend(
    classes: Set<String>,
    amenities: List<SbbAmenityDefinition>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text("Legend", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(Modifier.height(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if ("1" in classes) LegendItem(textIcon = "1", text = "1st class coach", box = true)
            if ("2" in classes) LegendItem(textIcon = "2", text = "2nd class coach", box = true)
            amenities.forEach { amenity ->
                LegendItem(iconUrl = amenity.iconUrl, text = amenity.label)
            }
        }
        Text(
            "All information without guarantee.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 24.dp)
        )
    }
}

private fun stopPointMatches(a: SbbStopPoint?, b: SbbStopPoint?): Boolean {
    if (a == null || b == null) return false
    if (a.uic != null && b.uic != null) return a.uic == b.uic
    return !a.name.isNullOrBlank() && a.name == b.name
}

private fun makeSbbStationKey(
    stopPoint: SbbStopPoint,
    stopTime: SbbStopTime?,
    fallbackIndex: Int
): String {
    val stopKey = stopPoint.uic?.toString() ?: stopPoint.name ?: fallbackIndex.toString()
    val timeKey = stopTime?.departureTime ?: stopTime?.arrivalTime ?: fallbackIndex.toString()
    return "$stopKey|$timeKey"
}

private fun getSbbStations(data: SbbFormationData): List<SbbStationOption> {
    val stations = mutableListOf<SbbStationOption>()
    val seen = mutableSetOf<String>()

    data.formationsAtScheduledStops.forEachIndexed { index, entry ->
        val scheduledStop = entry.scheduledStop ?: return@forEachIndexed
        val stopPoint = scheduledStop.stopPoint
        if (stopPoint?.name.isNullOrBlank()) return@forEachIndexed

        val hasVehicleData = data.formations.any { formation ->
            formation.formationVehicles.any { vehicle ->
                vehicle.formationVehicleAtScheduledStops.any { vehicleStop ->
                    stopPointMatches(vehicleStop.stopPoint, stopPoint)
                }
            }
        }
        if (!hasVehicleData) return@forEachIndexed

        val key = makeSbbStationKey(stopPoint!!, scheduledStop.stopTime, index)
        if (!seen.add(key)) return@forEachIndexed
        stations += SbbStationOption(
            key = key,
            name = stopPoint.name!!,
            uic = stopPoint.uic,
            track = scheduledStop.track,
            stopTime = scheduledStop.stopTime,
            destination = entry.formationShort?.vehicleGoals?.firstOrNull()?.destinationStopPoint?.name,
            formationShortString = entry.formationShort?.formationShortString
        )
    }

    if (stations.isNotEmpty()) return stations

    val fallbackVehicle = data.formations
        .asSequence()
        .flatMap { it.formationVehicles.asSequence() }
        .firstOrNull { it.formationVehicleAtScheduledStops.isNotEmpty() }

    fallbackVehicle?.formationVehicleAtScheduledStops?.forEachIndexed { index, entry ->
        val stopPoint = entry.stopPoint
        if (stopPoint?.name.isNullOrBlank()) return@forEachIndexed
        val key = makeSbbStationKey(stopPoint!!, entry.stopTime, index)
        if (!seen.add(key)) return@forEachIndexed
        stations += SbbStationOption(
            key = key,
            name = stopPoint.name!!,
            uic = stopPoint.uic,
            track = entry.track,
            stopTime = entry.stopTime,
            destination = null,
            formationShortString = null
        )
    }

    return stations
}

private fun stationAsStopPoint(station: SbbStationOption?): SbbStopPoint? =
    station?.let { SbbStopPoint(name = it.name, uic = it.uic) }

private fun findSbbVehicleStop(
    vehicle: SbbFormationVehicle,
    station: SbbStationOption?
): SbbFormationVehicleAtScheduledStop? {
    if (station == null) return null
    val stationPoint = stationAsStopPoint(station)
    val matches = vehicle.formationVehicleAtScheduledStops.filter {
        stopPointMatches(it.stopPoint, stationPoint)
    }
    if (matches.size <= 1) return matches.firstOrNull()
    return matches.firstOrNull {
        it.stopTime?.departureTime == station.stopTime?.departureTime &&
            it.stopTime?.arrivalTime == station.stopTime?.arrivalTime
    } ?: matches.first()
}

private fun getFormationEndpoints(formation: SbbFormation): Pair<SbbStopPoint?, SbbStopPoint?> {
    val properties = formation.formationVehicles
        .firstOrNull { it.vehicleProperties?.fromStop != null || it.vehicleProperties?.toStop != null }
        ?.vehicleProperties
    return properties?.fromStop to properties?.toStop
}

private fun findStationIndex(stations: List<SbbStationOption>, stopPoint: SbbStopPoint?): Int =
    stations.indexOfFirst { stopPointMatches(stationAsStopPoint(it), stopPoint) }

private fun selectSbbFormation(
    data: SbbFormationData,
    station: SbbStationOption?,
    stations: List<SbbStationOption>
): SbbFormation? {
    if (station == null) return null
    val selectedIndex = stations.indexOfFirst { it.key == station.key }
    var bestFormation: SbbFormation? = null
    var bestScore = Int.MIN_VALUE

    data.formations.forEach { formation ->
        if (formation.formationVehicles.none { findSbbVehicleStop(it, station) != null }) {
            return@forEach
        }
        val (fromStop, toStop) = getFormationEndpoints(formation)
        val fromIndex = findStationIndex(stations, fromStop)
        val toIndex = findStationIndex(stations, toStop)
        val low = min(fromIndex, toIndex)
        val high = max(fromIndex, toIndex)
        val inRange = fromIndex >= 0 && toIndex >= 0 && selectedIndex in low..high
        var score = if (inRange) 1000 else 0
        if (stopPointMatches(fromStop, stationAsStopPoint(station))) score += 100
        if (stopPointMatches(toStop, stationAsStopPoint(station))) score += 10
        if (inRange) score -= high - low
        if (score > bestScore) {
            bestScore = score
            bestFormation = formation
        }
    }

    return bestFormation ?: data.formations.firstOrNull()
}

private fun buildSbbVehicleViews(
    formation: SbbFormation?,
    station: SbbStationOption?
): List<SbbVehicleView> {
    if (formation == null || station == null) return emptyList()
    val vehicles = formation.formationVehicles.sortedBy { it.position ?: Int.MAX_VALUE }
    val stationData = vehicles.map { findSbbVehicleStop(it, station) }
    var cursor = 0f

    return vehicles.mapIndexed { index, vehicle ->
        val width = ((vehicle.vehicleProperties?.length ?: 24.0) * 3.0)
            .coerceIn(58.0, 94.0)
            .toFloat()
        val hasNext = index < vehicles.lastIndex
        val gap = if (!hasNext) {
            0f
        } else if (stationData[index + 1]?.accessToPreviousVehicle == false) {
            SBB_NO_PASSAGE_GAP_DP
        } else {
            SBB_VEHICLE_GAP_DP
        }
        SbbVehicleView(
            vehicle = vehicle,
            stationData = stationData[index],
            widthDp = width,
            startDp = cursor,
            endDp = cursor + width,
            gapAfterDp = gap
        ).also { cursor = it.endDp + gap }
    }
}

private fun parseSectors(sectors: String?): List<String> =
    sectors.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }

private fun buildSbbSectorSegments(
    vehicles: List<SbbVehicleView>,
    totalWidth: Float
): List<SbbSectorSegment> {
    if (totalWidth <= 0f) return emptyList()
    val ranges = linkedMapOf<String, Pair<Float, Float>>()
    vehicles.forEach { view ->
        val sectors = parseSectors(view.stationData?.sectors)
        sectors.forEachIndexed { index, sector ->
            val start = view.startDp + (view.widthDp * index) / sectors.size
            val end = view.startDp + (view.widthDp * (index + 1)) / sectors.size
            val current = ranges[sector]
            ranges[sector] = min(current?.first ?: start, start) to max(current?.second ?: end, end)
        }
    }
    val ordered = ranges.map { (label, range) -> Triple(label, range.first, range.second) }
        .sortedWith(compareBy<Triple<String, Float, Float>> { it.second }.thenBy { it.third })

    return ordered.mapIndexed { index, sector ->
        val previous = ordered.getOrNull(index - 1)
        val next = ordered.getOrNull(index + 1)
        val left = if (index == 0) 0f else ((previous!!.third + sector.second) / 2f)
            .coerceIn(0f, totalWidth)
        val right = if (index == ordered.lastIndex) totalWidth else
            ((sector.third + next!!.second) / 2f).coerceIn(left, totalWidth)
        val width = max(0f, right - left)
        SbbSectorSegment(sector.first, left, width, width < 104f)
    }
}

private fun getSbbVehicleClass(vehicle: SbbFormationVehicle): String {
    val first = vehicle.vehicleProperties?.number1class ?: 0
    val second = vehicle.vehicleProperties?.number2class ?: 0
    return when {
        first > 0 && second > 0 -> "1 / 2"
        first > 0 -> "1"
        second > 0 -> "2"
        else -> ""
    }
}

private fun getSbbVehicleLabel(vehicle: SbbFormationVehicle, fallbackIndex: Int): String =
    when {
        (vehicle.number ?: 0) > 0 -> vehicle.number!!.toString()
        (vehicle.position ?: 0) > 0 -> vehicle.position!!.toString()
        else -> (fallbackIndex + 1).toString()
    }

private fun getSbbVehicleTypeCode(vehicle: SbbFormationVehicle): String =
    vehicle.vehicleIdentifier?.typeCode?.toString()
        ?: vehicle.vehicleIdentifier?.typeCodeName?.trim().orEmpty()

private fun getSbbFormationShortAmenityKeys(
    station: SbbStationOption?,
    vehicle: SbbFormationVehicle
): Set<String> {
    val keys = linkedSetOf<String>()
    val short = station?.formationShortString ?: return keys
    val number = vehicle.number ?: return keys
    val escapedNumber = Regex.escape(number.toString())
    val classAndFeatures = Regex(":$escapedNumber(?:#([^,@)\\]]+))?").find(short)
    val featureCodes = classAndFeatures?.groupValues?.getOrNull(1)
        .orEmpty()
        .split(';')
        .map { it.trim().uppercase() }
        .filter { it.isNotEmpty() }
        .toSet()

    if ("NF" in featureCodes) keys += "low_floor"
    if ("BZ" in featureCodes) keys += "business_zone"
    if (Regex("%W[12]:$escapedNumber(?:#|[,)\\]])").containsMatchIn(short)) {
        keys += "restaurant"
    }
    return keys
}

private fun getSbbVehicleAmenityKeys(
    vehicle: SbbFormationVehicle,
    station: SbbStationOption?
): List<String> {
    val keys = getSbbFormationShortAmenityKeys(station, vehicle).toMutableSet()
    val properties = vehicle.vehicleProperties
    val accessibility = properties?.accessibilityProperties
    val pictos = properties?.pictoProperties

    if (accessibility?.disabledCompartment == true ||
        (accessibility?.numberWheelchairSpaces ?: 0) > 0 ||
        (accessibility?.numberWheelchairSpaces1class ?: 0) > 0 ||
        (accessibility?.numberWheelchairSpaces2class ?: 0) > 0 ||
        pictos?.wheelchairPicto == true
    ) keys += "wheelchair"
    if (accessibility?.wheelchairToilet == true) keys += "wheelchair_toilet"
    if (properties?.bikePlatform == true ||
        (properties?.numberBikeHooks ?: 0) > 0 ||
        pictos?.bikePicto == true
    ) keys += "bicycle"
    if (pictos?.businessZonePicto == true) keys += "business_zone"
    if (pictos?.familyZonePicto == true ||
        vehicle.vehicleIdentifier?.typeCodeName.orEmpty().contains("fam", ignoreCase = true)
    ) keys += "family_zone"
    if (pictos?.strollerPicto == true) keys += "stroller"
    if ((!properties?.trolleyStatus.isNullOrBlank() && properties?.trolleyStatus != "Normal") ||
        (properties?.numberRestaurantSpace ?: 0) > 0 ||
        accessibility?.wheelchairAccessibleRestaurant == true
    ) keys += "restaurant"
    if (properties?.lowFloorTrolley == true) keys += "low_floor"
    if ((properties?.numberBeds ?: 0) > 0) keys += "sleeping"
    if (properties?.emergencyCallSystem == true) keys += "emergency_call"
    if (properties?.closed == true) keys += "closed"

    return keys.toList()
}

private fun getSbbVehicleAmenities(
    vehicle: SbbFormationVehicle,
    station: SbbStationOption?
): List<SbbAmenityDefinition> =
    getSbbVehicleAmenityKeys(vehicle, station).mapNotNull(sbbAmenityCatalog::get)

private fun collectSbbLegendItems(
    vehicles: List<SbbVehicleView>,
    station: SbbStationOption?
): List<SbbAmenityDefinition> {
    val keys = linkedSetOf<String>()
    vehicles.forEach { view -> keys += getSbbVehicleAmenityKeys(view.vehicle, station) }
    return keys.mapNotNull(sbbAmenityCatalog::get)
}

private fun collectSbbClasses(vehicles: List<SbbVehicleView>): Set<String> {
    val classes = linkedSetOf<String>()
    vehicles.forEach { view ->
        if ((view.vehicle.vehicleProperties?.number1class ?: 0) > 0) classes += "1"
        if ((view.vehicle.vehicleProperties?.number2class ?: 0) > 0) classes += "2"
    }
    return classes
}

@Composable
private fun UnifiedFormationPage(
    coachSequence: UnifiedConsist,
    modifier: Modifier = Modifier
) {
    val vehicles = coachSequence.groups.firstOrNull()?.vehicles.orEmpty()
    val presentAmenities = remember(coachSequence) {
        vehicles.flatMap { it.facilities }.mapNotNull { it.amenity_type }.toSet()
    }
    val presentClasses = remember(coachSequence) {
        vehicles.mapNotNull { it.passenger_class }.toSet()
    }
    val presentOccupancies = remember(coachSequence) {
        vehicles.mapNotNull { it.occupancy }.toSet()
    }

    if (vehicles.isEmpty()) {
        EmptyFormationMessage(modifier)
        return
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (isSystemInDarkTheme()) Color(0xFF1A1C1E)
                    else Color(0xFFF3F4F6)
                )
        ) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(vehicles.size) { index ->
                    val vehicle = vehicles[index]
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(IntrinsicSize.Min)
                    ) {
                        Text(
                            text = vehicle.label ?: (vehicle.order + 1).toString(),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        Box(
                            modifier = Modifier
                                .height(40.dp)
                                .width(64.dp)
                                .border(
                                    width = 1.5.dp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    shape = when (index) {
                                        0 -> RoundedCornerShape(
                                            topStart = 20.dp,
                                            bottomStart = 20.dp,
                                            topEnd = 8.dp,
                                            bottomEnd = 8.dp
                                        )
                                        vehicles.lastIndex -> RoundedCornerShape(
                                            topStart = 8.dp,
                                            bottomStart = 8.dp,
                                            topEnd = 20.dp,
                                            bottomEnd = 20.dp
                                        )
                                        else -> RoundedCornerShape(8.dp)
                                    }
                                )
                                .padding(horizontal = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = when (vehicle.occupancy) {
                                        SiriOccupancy.High -> Icons.Filled.Group
                                        SiriOccupancy.VeryHigh -> Icons.Filled.Groups
                                        else -> Icons.Filled.Person
                                    },
                                    contentDescription = "Occupancy",
                                    modifier = Modifier.size(14.dp),
                                    tint = if (vehicle.occupancy == SiriOccupancy.VeryHigh) {
                                        Color.Red
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )
                                Text(
                                    text = when (vehicle.passenger_class) {
                                        PassengerClass.First -> "1"
                                        PassengerClass.Second -> "2"
                                        else -> ""
                                    },
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .height(16.dp)
                        ) {
                            vehicle.facilities.forEach { facility ->
                                val amenity = facility.amenity_type
                                if (amenity == AmenityType.LowFloor) {
                                    Text("NF", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                } else if (amenity != null) {
                                    Icon(
                                        imageVector = getAmenityIcon(amenity),
                                        contentDescription = amenity.name,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }
                    }
                    if (index < vehicles.lastIndex) {
                        Box(
                            modifier = Modifier
                                .width(8.dp)
                                .height(2.dp)
                                .background(MaterialTheme.colorScheme.onSurface)
                        )
                    }
                }
            }
        }

        Column(modifier = Modifier.padding(16.dp)) {
            Text("Legend", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (presentOccupancies.isNotEmpty()) {
                    LegendItem(vectorIcon = Icons.Filled.Person, text = "Low to average occupancy expected")
                    LegendItem(vectorIcon = Icons.Filled.Group, text = "High occupancy expected")
                    LegendItem(
                        vectorIcon = Icons.Filled.Groups,
                        text = "Very high occupancy expected",
                        isRed = true
                    )
                }
                if (PassengerClass.First in presentClasses) {
                    LegendItem(textIcon = "1", text = "1st class coach", box = true)
                }
                if (PassengerClass.Second in presentClasses) {
                    LegendItem(textIcon = "2", text = "2nd class coach", box = true)
                }
                presentAmenities.forEach { amenity ->
                    if (amenity == AmenityType.LowFloor) {
                        LegendItem(textIcon = "NF", text = "Low-floor access", italic = true)
                    } else {
                        LegendItem(
                            vectorIcon = getAmenityIcon(amenity),
                            text = amenity.name.replace("([A-Z])".toRegex(), " $1").trim()
                        )
                    }
                }
            }
            Text(
                "All information without guarantee.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 24.dp)
            )
        }
    }
}

@Composable
private fun EmptyFormationMessage(modifier: Modifier = Modifier) {
    Text(
        "No train formation data available.",
        modifier = modifier.padding(32.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun LegendItem(
    text: String,
    textIcon: String? = null,
    vectorIcon: ImageVector? = null,
    iconUrl: String? = null,
    isRed: Boolean = false,
    box: Boolean = false,
    italic: Boolean = false
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.width(24.dp)) {
            when {
                box && textIcon != null -> Text(
                    text = textIcon,
                    modifier = Modifier
                        .border(1.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(2.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                iconUrl != null -> AsyncImage(
                    model = iconUrl,
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(18.dp)
                )
                vectorIcon != null -> Icon(
                    imageVector = vectorIcon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (isRed) Color.Red else MaterialTheme.colorScheme.onSurface
                )
                textIcon != null -> Text(
                    text = textIcon,
                    color = if (isRed) Color.Red else MaterialTheme.colorScheme.onSurface,
                    fontSize = if (italic) 12.sp else 14.sp,
                    fontWeight = if (italic) FontWeight.Bold else FontWeight.Normal,
                    fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(text, fontSize = 14.sp)
    }
}

private fun getAmenityIcon(amenity: AmenityType): ImageVector = when (amenity) {
    AmenityType.AirCondition -> Icons.Filled.AcUnit
    AmenityType.WheelchairSpace -> Icons.AutoMirrored.Filled.Accessible
    AmenityType.BikeSpace -> Icons.Filled.PedalBike
    AmenityType.QuietZone -> Icons.Filled.VoiceOverOff
    AmenityType.FamilyZone -> Icons.Filled.ChildFriendly
    AmenityType.InfoPoint -> Icons.Filled.Info
    AmenityType.DiningCar -> Icons.Filled.Restaurant
    AmenityType.Toilet -> Icons.Filled.Wc
    AmenityType.LowFloor -> Icons.Filled.QuestionMark
}
