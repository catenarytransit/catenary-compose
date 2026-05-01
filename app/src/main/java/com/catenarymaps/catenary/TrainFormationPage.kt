package com.catenarymaps.catenary

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.serialization.json.*
import androidx.compose.foundation.lazy.items

@Composable
fun CoachSequencePage(
    coachSequence: UnifiedConsist,
    modifier: Modifier = Modifier
) {
    val groups = coachSequence.groups
    val groupZero = groups.getOrNull(0)
    val vehicles = groupZero?.vehicles

    val presentAmenities = remember(coachSequence) { mutableSetOf<AmenityType>() }
    val presentClasses = remember(coachSequence) { mutableSetOf<PassengerClass>() }
    val presentOccupancies = remember(coachSequence) { mutableSetOf<SiriOccupancy>() }

    vehicles?.forEach { vehicle ->
        vehicle.passenger_class?.let { presentClasses.add(it) }
        vehicle.occupancy?.let { presentOccupancies.add(it) }
        vehicle.facilities.forEach { facility ->
            facility.amenity_type?.let { presentAmenities.add(it) }
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        if (vehicles.isNullOrEmpty()) {
            Text(
                "No train formation data available.",
                modifier = Modifier
                    .padding(32.dp)
                    .align(Alignment.CenterHorizontally),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@Column
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (isSystemInDarkTheme()) Color(0xFF1A1C1E) else Color(0xFFF3F4F6))
        ) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(vehicles.size) { i ->
                    val vehicle = vehicles[i]
                    val label = vehicle.label ?: (vehicle.order + 1).toString()
                    val occupancy = vehicle.occupancy
                    val pClass = vehicle.passenger_class
                    val facilities = vehicle.facilities

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(IntrinsicSize.Min)
                    ) {
                        Text(
                            text = label,
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
                                    shape = when (i) {
                                        0 -> RoundedCornerShape(
                                            topStart = 20.dp,
                                            bottomStart = 20.dp,
                                            topEnd = 8.dp,
                                            bottomEnd = 8.dp
                                        )

                                        vehicles.size - 1 -> RoundedCornerShape(
                                            topStart = 8.dp,
                                            bottomStart = 8.dp,
                                            topEnd = 20.dp,
                                            bottomEnd = 20.dp
                                        )

                                        else -> java.util.Optional.of(RoundedCornerShape(8.dp))
                                            .get()
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
                                Text(
                                    text = if (occupancy == SiriOccupancy.High || occupancy == SiriOccupancy.VeryHigh) "👥" else "👤",
                                    fontSize = 12.sp,
                                    color = if (occupancy == SiriOccupancy.VeryHigh) Color.Red else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = when (pClass) {
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
                            facilities.forEach { facility ->
                                val amenity = facility.amenity_type
                                if (amenity == AmenityType.LowFloor) {
                                    Text("NF", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                } else if (amenity != null) {
                                    Text("•", fontSize = 10.sp) // Fallback for icons
                                }
                            }
                        }
                    }
                    if (i < vehicles.size - 1) {
                        Box(
                            modifier = Modifier
                                .width(8.dp)
                                .height(2.dp)
                                .padding(bottom = 32.dp)
                                .background(MaterialTheme.colorScheme.onSurface)
                        )
                    }
                }
            }
        }

        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Legend",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (presentOccupancies.isNotEmpty()) {
                    LegendItem("👤", "Low to average occupancy expected")
                    LegendItem("👥", "High occupancy expected")
                    LegendItem("👥", "Very high occupancy expected", isRed = true)
                }

                if (presentClasses.contains(PassengerClass.First)) {
                    LegendItem("1", "1st class coach", box = true)
                }
                if (presentClasses.contains(PassengerClass.Second)) {
                    LegendItem("2", "2nd class coach", box = true)
                }

                presentAmenities.forEach { amenity ->
                    if (amenity == AmenityType.LowFloor) {
                        LegendItem("NF", "Low-floor access", italic = true)
                    } else {
                        LegendItem("•", amenity.name.replace("([A-Z])".toRegex(), " $1").trim())
                    }
                }
            }
            Text(
                "All information without guarantee.",
                color = Color.Gray,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 24.dp)
            )
        }
    }
}

@Composable
private fun LegendItem(
    icon: String,
    text: String,
    isRed: Boolean = false,
    box: Boolean = false,
    italic: Boolean = false
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.width(24.dp)
        ) {
            if (box) {
                Text(
                    text = icon,
                    modifier = Modifier
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.onSurface,
                            RoundedCornerShape(2.dp)
                        )
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            } else {
                Text(
                    text = icon,
                    color = if (isRed) Color.Red else MaterialTheme.colorScheme.onSurface,
                    fontSize = if (italic) 12.sp else 14.sp,
                    fontWeight = if (italic) FontWeight.Bold else FontWeight.Normal,
                    fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(text, fontSize = 14.sp)
    }
}
