package com.catenarymaps.catenary

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Icon
import androidx.compose.ui.res.stringResource
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Serializable
data class AlertTranslation(
    val text: String,
    val language: String?
)

@Serializable
data class AlertText(
    val translation: List<AlertTranslation>
)

@Serializable
data class AlertActivePeriod(
    val start: Long? = null,
    val end: Long? = null
)

@Serializable
data class Alert(
    val cause: Int?,
    val effect: Int?,
    val url: AlertText? = null,
    val header_text: AlertText? = null,
    val description_text: AlertText? = null,
    val active_period: List<AlertActivePeriod> = emptyList()
)

@Composable
fun AlertsBox(
    alerts: Map<String, Alert>,
    default_tz: String? = null,
    chateau: String? = null,
    isScrollable: Boolean = false
) {
    if (alerts.isEmpty()) return

    var expanded by remember { mutableStateOf(true) }
    val alertColor = Color(0xFFF99C24)

    val locale = LocalConfiguration.current.locales[0]
    val localeCode = remember(locale) {
        if (locale.language.startsWith("en")) "en-CA" else locale.toLanguageTag()
    }

    val languageListToUse = remember(alerts) {
        val allLanguages = alerts.values.flatMap { alert ->
            val headerLangs = alert.header_text?.translation?.map { it.language } ?: emptyList()
            val descLangs = alert.description_text?.translation?.map { it.language } ?: emptyList()
            headerLangs + descLangs
        }.distinct()

        if (allLanguages.contains("en-html")) {
            allLanguages.filter { it != "en" }
        } else {
            allLanguages
        }
    }

    Column(
        modifier = Modifier
            .border(1.dp, alertColor, RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Service Alert",
                modifier = Modifier.size(24.dp),
                tint = alertColor
            )
            val alertText = getAlertsTitle(alerts.size)
            Text(
                text = alertText,
                color = alertColor,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(start = 8.dp)
            )
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = { expanded = !expanded }) {
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand"
                )
            }
        }

        AnimatedVisibility(visible = expanded) {
            val scrollState = rememberScrollState()
            val scrollableModifier = if (isScrollable) {
                Modifier
                    .fillMaxHeight(0.8f)
                    .verticalScroll(scrollState)
            } else {
                Modifier
            }
            Column(modifier = scrollableModifier) {
                Spacer(modifier = Modifier.height(4.dp))
                alerts.values.forEachIndexed { index, alert ->
                    if (index > 0) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            color = alertColor.copy(alpha = 0.5f)
                        )
                    }
                    AlertItem(
                        alert = alert,
                        languageListToUse = languageListToUse,
                        locale = locale,
                        default_tz = default_tz,
                        alertColor = alertColor
                    )
                }
            }
        }
    }
}

@Composable
private fun AlertItem(
    alert: Alert,
    languageListToUse: List<String?>,
    locale: Locale,
    default_tz: String?,
    alertColor: Color
) {
    Column(modifier = Modifier.padding(top = 4.dp)) {
        Row {
            Text(
                text = causeIdToStr(alert.cause),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = alertColor
            )
            Text(
                text = " // ",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = alertColor
            )
            Text(
                text = effectIdToStr(alert.effect),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = alertColor
            )
        }

        alert.url?.let { AlertUrl(it) }

        languageListToUse.forEach { lang ->
            alert.header_text?.translation?.find { it.language == lang }?.let {
                FormattedText(
                    text = it.text,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            alert.description_text?.translation?.find { it.language == lang }?.let {
                FormattedText(
                    text = it.text,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        alert.active_period.forEach {
            AlertActivePeriod(
                activePeriod = it,
                locale = locale,
                default_tz = default_tz
            )
        }
    }
}

@Composable
private fun AlertUrl(url: AlertText) {
    val uriHandler = LocalUriHandler.current
    url.translation.forEach { urlTranslation ->
        Row {
            Text(
                text = "${urlTranslation.language}: ",
                style = MaterialTheme.typography.bodySmall
            )
            ClickableText(
                text = AnnotatedString(urlTranslation.text),
                style = MaterialTheme.typography.bodySmall.copy(
                    color = if (isSystemInDarkTheme()) Color(
                        0xff2b7fff
                    ) else Color.Blue
                ),
                onClick = { uriHandler.openUri(urlTranslation.text) }
            )
        }
    }
}

@Composable
private fun FormattedText(text: String, style: TextStyle) {
    val uriHandler = LocalUriHandler.current
    val defaultColor = MaterialTheme.colorScheme.onSurface

    // Regex for various tags. Added <p>, </p>, <br>, <strong>, <ul>, <li>, </ul>
    val tagRegex = remember {
        Regex("""<(/?[a-zA-Z0-9]+)(?:\s+href="([^"]+)")?>|([^<]+)""")
    }

    val annotatedString = buildAnnotatedString {
        val styleStack = mutableListOf<SpanStyle>()
        var inList = false

        tagRegex.findAll(text)
            .forEachIndexed { i, match ->
                val (tag, url, content) = match.destructured

                if (content.isNotEmpty()) {
                    // It's plain text content
                    val currentStyle = styleStack.fold(SpanStyle()) { acc, s -> acc.merge(s) }
                    withStyle(currentStyle) {
                        append(content)
                    }
                } else {
                    // It's a tag
                    when (tag.lowercase()) {
                        "a" -> {
                            if (url.isNotEmpty()) {
                                pushStringAnnotation(tag = "URL", annotation = url)
                                styleStack.add(
                                    SpanStyle(
                                        color = Color(0xff2b7fff),
                                        textDecoration = TextDecoration.Underline
                                    )
                                )
                            }
                        }

                        "/a" -> {
                            if (styleStack.isNotEmpty()) { // Prevent popping from an empty stack
                                pop()
                            }
                            styleStack.removeLastOrNull()
                        }

                        "b", "strong" -> {
                            styleStack.add(SpanStyle(fontWeight = FontWeight.Bold))
                        }

                        "/b", "/strong" -> {
                            styleStack.removeLastOrNull()
                        }

                        "br" -> {
                            append("\n")
                        }

                        "p" -> {

                            append("\n\n")

                        }

                        "/p" -> {

                            append("\n\n")
                        }

                        "ul" -> {

                            append("\n")

                            inList = true
                        }

                        "/ul" -> {

                            append("\n")

                            inList = false
                        }

                        "li" -> {

                            append("\n")

                            append("  • ")
                        }

                        "/li" -> {
                            // No action needed on closing li, newline is handled by next li or /ul
                        }
                        // You can add more tag handlers here
                    }
                }
        }
    }

    ClickableText(
        // Replace multiple newlines with a maximum of two to prevent excessive spacing
        text = AnnotatedString(annotatedString.toString().replace(Regex("\n{3,}"), "\n\n")),
        style = style.copy(color = defaultColor),
        onClick = { offset ->
            annotatedString.getStringAnnotations(tag = "URL", start = offset, end = offset)
                .firstOrNull()?.let { annotation ->
                    uriHandler.openUri(annotation.item)
                }
        }
    )
}


@Composable
private fun AlertActivePeriod(
    activePeriod: AlertActivePeriod,
    locale: Locale,
    default_tz: String?
) {
    val context = LocalContext.current
    val dateFormat = remember(locale, default_tz) {
        SimpleDateFormat("yyyy-MM-dd HH:mm", locale).apply {
            default_tz?.let { timeZone = TimeZone.getTimeZone(it) }
        }
    }

    activePeriod.start?.let { start ->
        val startDate = Date(start * 1000)
        val diff = (start * 1000 - System.currentTimeMillis()) / 1000.0
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${stringResource(R.string.starting_time)}: ${dateFormat.format(startDate)}",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.width(4.dp))
            DiffTimer(
                diff = diff,
                showBrackets = true,
                showPlus = true,
                showDays = true,
                numSize = MaterialTheme.typography.bodySmall.fontSize,
                unitSize = MaterialTheme.typography.bodySmall.fontSize * 0.8,
                bracketSize = MaterialTheme.typography.bodySmall.fontSize
            )
        }
    }

    activePeriod.end?.let { end ->
        val endDate = Date(end * 1000)
        val diff = (end * 1000 - System.currentTimeMillis()) / 1000.0
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${stringResource(R.string.ending_time)}: ${dateFormat.format(endDate)}",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.width(4.dp))
            DiffTimer(
                diff = diff,
                showBrackets = true,
                showPlus = true,
                showDays = true,
                numSize = MaterialTheme.typography.bodySmall.fontSize,
                unitSize = MaterialTheme.typography.bodySmall.fontSize * 0.8,
                bracketSize = MaterialTheme.typography.bodySmall.fontSize
            )
        }
    }
}

@Composable
private fun getAlertsTitle(alertCount: Int): String =
    pluralStringResource(
        R.plurals.service_alerts,
        alertCount,
        alertCount
    ).takeIf { it.isNotBlank() }
        ?: "Service Alerts ($alertCount)"


@Composable
fun causeIdToStr(cause: Int?): String {
    val resourceId = when (cause) {
        1 -> R.string.alert_cause_unknown_cause
        2 -> R.string.alert_cause_other_cause
        3 -> R.string.alert_cause_technical_problem
        4 -> R.string.alert_cause_labour_strike
        5 -> R.string.alert_cause_demonstration_street_blockage
        6 -> R.string.alert_cause_accident
        7 -> R.string.alert_cause_holiday
        8 -> R.string.alert_cause_weather
        9 -> R.string.alert_cause_maintenance
        10 -> R.string.alert_cause_construction
        11 -> R.string.alert_cause_police_activity
        12 -> R.string.alert_cause_medical_emergency
        else -> R.string.alert_cause_unknown_cause
    }
    return stringResource(id = resourceId)
}

@Composable
fun effectIdToStr(effect: Int?): String {
    val resourceId = when (effect) {
        1 -> R.string.alert_effect_no_service
        2 -> R.string.alert_effect_reduced_service
        3 -> R.string.alert_effect_significant_delays
        4 -> R.string.alert_effect_detour
        5 -> R.string.alert_effect_additional_service
        6 -> R.string.alert_effect_modified_service
        7 -> R.string.alert_effect_other_effect
        8 -> R.string.alert_effect_unknown_effect
        9 -> R.string.alert_effect_stop_moved
        10 -> R.string.alert_effect_no_effect
        11 -> R.string.alert_effect_accessibility_issue
        else -> R.string.alert_effect_unknown_effect
    }
    return stringResource(id = resourceId)
}