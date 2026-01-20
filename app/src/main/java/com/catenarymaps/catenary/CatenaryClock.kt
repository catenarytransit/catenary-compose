package com.catenarymaps.catenary

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import java.time.DateTimeException
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
public fun FormattedTimeText(
    timezone: String,
    timeSeconds: Long,
    showSeconds: Boolean = false,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    textDecoration: TextDecoration? = null,
    style: TextStyle = LocalTextStyle.current,
    secondsStyle: TextStyle? = null
) {
    // It's generally better to perform calculations like this outside of `remember`
    // if they are complex, but for this case, we can compute the formatted time directly.
    // The key is to handle potential exceptions during formatting.
    val (mainTime, secondsTime) =
        try {
            // 1. Create an Instant from epoch seconds
            val instant = Instant.ofEpochSecond(timeSeconds)

            // 2. Get the ZoneId from the IANA string
            val zoneId = ZoneId.of(timezone)

            // 3. Apply the timezone to the instant
            val zonedDateTime: ZonedDateTime = instant.atZone(zoneId)

            // 4. Create a formatter for the main part
            val mainFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.UK)
            val mainPart = zonedDateTime.format(mainFormatter)

            // 5. Create a formatter for the seconds part if needed
            val secondsPart =
                if (showSeconds) {
                    val secondsFormatter = DateTimeFormatter.ofPattern(":ss", Locale.UK)
                    zonedDateTime.format(secondsFormatter)
                } else {
                    ""
                }

            mainPart to secondsPart
        } catch (e: DateTimeException) {
            // Handle cases where the timezone string is invalid
            "Invalid Timezone" to ""
        } catch (e: Exception) {
            // Catch other potential errors
            "Error" to ""
        }

    val finalSecondsStyle = secondsStyle ?: style

    val dateText = buildAnnotatedString {
        withStyle(style.toSpanStyle()) { append(mainTime) }
        if (showSeconds) {
            withStyle(finalSecondsStyle.toSpanStyle()) { append(secondsTime) }
        }
    }

    // Display the formatted time string in a Text composable
    Text(
        text = dateText,
        modifier = modifier,
        color = color,
        textDecoration = textDecoration,
        style = style
    )
}
