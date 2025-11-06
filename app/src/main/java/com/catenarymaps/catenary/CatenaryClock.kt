package com.catenarymaps.catenary

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

@Composable
public fun FormattedTimeText(
    timezone: String,
    timeSeconds: Long,
    showSeconds: Boolean = false,
    modifier: Modifier = Modifier
) {
    // Use remember to recalculate the formatted time only when
    // the inputs (timezone, timeSeconds, showSeconds) change.
    val formattedTime = remember(timezone, timeSeconds, showSeconds) {
        try {
            // 1. Create an Instant from epoch seconds
            val instant = Instant.ofEpochSecond(timeSeconds)

            // 2. Get the ZoneId from the IANA string
            val zoneId = ZoneId.of(timezone)

            // 3. Apply the timezone to the instant
            val zonedDateTime: ZonedDateTime = instant.atZone(zoneId)

            // 4. Define the pattern based on showSeconds
            val pattern = if (showSeconds) "HH:mm:ss" else "HH:mm"

            // 5. Create a formatter, using Locale.UK to match 'en-UK'/'en-GB'
            val formatter = DateTimeFormatter.ofPattern(pattern, Locale.UK)

            // 6. Format the date-time
            zonedDateTime.format(formatter)

        } catch (e: DateTimeParseException) {
            // Handle cases where the timezone string is invalid
            "Invalid Timezone"
        } catch (e: Exception) {
            // Catch other potential errors
            "Error"
        }
    }

    // Display the formatted time string in a Text composable
    Text(
        text = formattedTime,
        modifier = modifier,
    )
}
