package com.catenarymaps.catenary

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.unit.Dp
import kotlin.math.abs

/**
 * Lightweight, reusable date+time selector used on multiple screens.
 *
 * This is a controlled component: callers own the epoch-second state and
 * pass the current value in via [epochSeconds]. The picker always interprets
 * and displays that instant in [timezoneId].
 *
 * [onTimeChange] is invoked with a unix timestamp (seconds) in UTC.
 * [onIsNowChange] toggles whether the caller should keep the clock locked to
 * the current instant ("Now"). When [isNow] is true, callers typically keep
 * updating [epochSeconds] to the real current time.
 */
@Composable
fun TimeSelectorButton(
    epochSeconds: Long,
    timezoneId: String,
    isNow: Boolean,
    onTimeChange: (Long) -> Unit,
    onIsNowChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    labelPrefix: String? = null
) {
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    val zone: ZoneId = remember(timezoneId) {
        try {
            ZoneId.of(timezoneId)
        } catch (e: Exception) {
            ZoneId.systemDefault()
        }
    }

    val zoned: ZonedDateTime = remember(epochSeconds, zone) {
        try {
            Instant.ofEpochSecond(epochSeconds).atZone(zone)
        } catch (e: Exception) {
            ZonedDateTime.now(zone)
        }
    }

    val today = remember(zone) { LocalDate.now(zone) }
    val dateFormatter = remember(locale, zone) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
            .withLocale(locale)
            .withZone(zone)
    }
    val timeFormatter = remember(locale, zone) {
        DateTimeFormatter.ofPattern("HH:mm")
            .withLocale(locale)
            .withZone(zone)
    }

    val dateLabel = remember(zoned, today, dateFormatter) {
        val d = zoned.toLocalDate()
        when {
            d.isEqual(today) -> "Today"
            d.isEqual(today.plusDays(1)) -> "Tomorrow"
            else -> dateFormatter.format(zoned)
        }
    }

    val timeLabel = remember(zoned, timeFormatter) { timeFormatter.format(zoned) }

    var showDialog by remember { mutableStateOf(false) }

    // Subtle text-style button (no background) that is still clickable
    Surface(
        modifier = modifier.clickable { showDialog = true },
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(999.dp),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Icon(
                imageVector = Icons.Filled.AccessTime,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = buildString {
                    if (labelPrefix != null) {
                        append(labelPrefix)
                        append(" ")
                    }
                    append(dateLabel)
                    append(" ")
                    append(timeLabel)
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )

            if (isNow) {
                Text(
                    text = "Now",
                    modifier = Modifier.padding(start = 2.dp),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }

    if (showDialog) {
        TimeSelectorDialog(
            epochSeconds = epochSeconds,
            timezoneId = zone.id,
            isNowInitial = isNow,
            onDismiss = { showDialog = false },
            onConfirm = { newEpoch, lockNow ->
                onIsNowChange(lockNow)
                onTimeChange(newEpoch)
                showDialog = false
            }
        )
    }
}

@Composable
private fun TimeSelectorDialog(
    epochSeconds: Long,
    timezoneId: String,
    isNowInitial: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Long, Boolean) -> Unit
) {
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    val zone: ZoneId = remember(timezoneId) {
        try {
            ZoneId.of(timezoneId)
        } catch (e: Exception) {
            ZoneId.systemDefault()
        }
    }

    val initialZoned: ZonedDateTime = remember(epochSeconds, zone, isNowInitial) {
        try {
            if (isNowInitial) {
                ZonedDateTime.now(zone)
            } else {
                Instant.ofEpochSecond(epochSeconds).atZone(zone)
            }
        } catch (e: Exception) {
            ZonedDateTime.now(zone)
        }
    }

    var selectedDate by remember { mutableStateOf(initialZoned.toLocalDate()) }
    var selectedHour by remember { mutableStateOf(initialZoned.hour) }
    var selectedMinute by remember { mutableStateOf(initialZoned.minute) }
    var lockToNow by remember { mutableStateOf(isNowInitial) }

    val today = remember(zone) { LocalDate.now(zone) }
    val startDate = remember(today, selectedDate) {
        minOf(today.minusDays(1), selectedDate)
    }
    val endDate = remember(today, selectedDate) {
        maxOf(today.plusDays(30), selectedDate)
    }
    val dateOptions = remember(startDate, endDate) {
        generateSequence(startDate) { current ->
            if (current.isBefore(endDate)) current.plusDays(1) else null
        }.toList()
    }

    val dateFormatter = remember(locale, zone) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
            .withLocale(locale)
            .withZone(zone)
    }

    val timeFormatter = remember(locale) {
        DateTimeFormatter.ofPattern("HH:mm").withLocale(locale)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
				.fillMaxWidth()
				.padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Choose time",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = zone.id,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Date wheel
                    WheelPicker(
                        items = dateOptions,
                        selectedIndex = dateOptions.indexOf(selectedDate).let { index ->
                            if (index >= 0) index else 0
                        },
                        onSelectedIndexChange = { idx ->
                            if (idx in dateOptions.indices) {
                                selectedDate = dateOptions[idx]
                                lockToNow = false
                            }
                        },
                        label = { date ->
                            when {
                                date.isEqual(today) -> "Today"
                                date.isEqual(today.plusDays(1)) -> "Tomorrow"
                                else -> dateFormatter.format(date)
                            }
                        },
                        modifier = Modifier.weight(1.4f)
                    )

                    // Time columns: hours and minutes
                    Column(
                        modifier = Modifier
                            .weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val hours = remember { (0..23).toList() }
                            WheelPicker(
                                items = hours,
                                selectedIndex = hours.indexOf(selectedHour).let { index ->
                                    if (index >= 0) index else 0
                                },
                                onSelectedIndexChange = { idx ->
                                    if (idx in hours.indices) {
                                        selectedHour = hours[idx]
                                        lockToNow = false
                                    }
                                },
                                label = { h -> String.format(locale, "%02d", h) },
                                modifier = Modifier.weight(1f)
                            )

                            val minutes = remember { (0..59).toList() }
                            WheelPicker(
                                items = minutes,
                                selectedIndex = minutes.indexOf(selectedMinute).let { index ->
                                    if (index >= 0) index else 0
                                },
                                onSelectedIndexChange = { idx ->
                                    if (idx in minutes.indices) {
                                        selectedMinute = minutes[idx]
                                        lockToNow = false
                                    }
                                },
                                label = { m -> String.format(locale, "%02d", m) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start
                    ) {
                        // "Now" acts as a one-way button: it can only
                        // turn the lock on, and is turned off implicitly
                        // when the user adjusts the wheels.
                        FilterChip(
                            selected = lockToNow,
                            onClick = {
                                if (!lockToNow) {
                                    lockToNow = true
                                    val now = ZonedDateTime.now(zone)
                                    selectedDate = now.toLocalDate()
                                    selectedHour = now.hour
                                    selectedMinute = now.minute
                                }
                            },
                            label = { Text("Now") }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = {
                                val finalInstant = if (lockToNow) {
                                    Instant.now()
                                } else {
                                    val localTime = LocalTime.of(selectedHour, selectedMinute)
                                    ZonedDateTime.of(selectedDate, localTime, zone).toInstant()
                                }
                                onConfirm(finalInstant.epochSecond, lockToNow)
                            }
                        ) {
                            val previewTime = if (lockToNow) {
                                timeFormatter.format(ZonedDateTime.now(zone))
                            } else {
                                val localTime = LocalTime.of(selectedHour, selectedMinute)
                                timeFormatter.format(localTime)
                            }
                            Text("Done • $previewTime")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun <T> WheelPicker(
    items: List<T>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    visibleCount: Int = 5,
    itemHeight: Dp = 40.dp
) {
    val listState = rememberLazyListState()
    val paddedCount = remember(visibleCount) { visibleCount / 2 }
    var programmaticScroll by remember { mutableStateOf(false) }

    // Keep the selected index centered when it changes from the caller.
    LaunchedEffect(items.size, selectedIndex) {
        if (items.isNotEmpty()) {
            val target = (selectedIndex + paddedCount).coerceIn(0, items.size + paddedCount * 2)
            programmaticScroll = true
            listState.scrollToItem(target)
            programmaticScroll = false
        }
    }

    // When user scrolls and it settles, snap selection to the item that is
    // visually closest to the center.
    LaunchedEffect(listState, items) {
        snapshotFlow { listState.isScrollInProgress }
            .collect { scrolling ->
                if (!scrolling && !programmaticScroll && items.isNotEmpty()) {
                    val layoutInfo = listState.layoutInfo
                    val visible = layoutInfo.visibleItemsInfo
                    if (visible.isEmpty()) return@collect

                    val viewportCenter =
                        (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2f

                    val closest =
                        visible.minByOrNull { info ->
                            abs((info.offset + info.size / 2f) - viewportCenter)
                        }
                    val absoluteIndex = closest?.index ?: return@collect
                    val idx = absoluteIndex - paddedCount
                    if (idx in items.indices && idx != selectedIndex) {
                        onSelectedIndexChange(idx)
                    }
                }
            }
    }

    Box(
        modifier = modifier
            .height(itemHeight * visibleCount)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize()
        ) {
            items(paddedCount) {
                Spacer(modifier = Modifier.height(itemHeight))
            }
            itemsIndexed(items) { index, item ->
                val isSelected = index == selectedIndex
                Row(
                    modifier = Modifier
						.fillMaxWidth()
						.height(itemHeight)
						.clip(RoundedCornerShape(8.dp))
						.clickable { onSelectedIndexChange(index) }
						.padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = label(item),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isSelected)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            items(paddedCount) {
                Spacer(modifier = Modifier.height(itemHeight))
            }
        }

        // Center highlight to create the "locked" row feeling.
        Box(
            modifier = Modifier
				.align(Alignment.Center)
				.fillMaxWidth()
				.height(itemHeight)
				.border(
					width = 1.dp,
					color = MaterialTheme.colorScheme.outlineVariant,
					shape = RoundedCornerShape(8.dp)
				)
        )
    }
}


