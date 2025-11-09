package com.catenarymaps.catenary

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import java.time.DateTimeException
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
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    textDecoration: TextDecoration? = null,
    style: TextStyle = LocalTextStyle.current
) {
    // It's generally better to perform calculations like this outside of `remember`
    // if they are complex, but for this case, we can compute the formatted time directly.
    // The key is to handle potential exceptions during formatting.
    val formattedTime = try {
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
    } catch (e: DateTimeException) {
            // Handle cases where the timezone string is invalid
            "Invalid Timezone"
        } catch (e: Exception) {
            // Catch other potential errors
            "Error"
        }

    // Display the formatted time string in a Text composable
    Text(
        text = formattedTime,
        modifier = modifier,
        color = color,
        textDecoration = textDecoration,
        style = style
    )
}
