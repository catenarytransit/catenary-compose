package com.catenarymaps.catenary

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.*

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.input.pointer.pointerInput
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest

// ... (Data classes AlertTranslation, AlertText, AlertActivePeriod, and Alert remain the same)

@Composable
fun AlertsBox(
    alerts: Map<String, Alert>,
    expanded: Boolean,
    onExpandedChange: () -> Unit,
    default_tz: String? = null,
    chateau: String? = null, // Chateau ID from the selection screen
    isScrollable: Boolean = false
) {
    if (alerts.isEmpty()) return
    val alertColor = Color(0xFFF99C24)
    val locale = LocalConfiguration.current.locales[0]

    val languageListToUse = remember(alerts) {
        val allLanguages = alerts.values.flatMap { alert ->
            val headerLangs =
                alert.header_text?.translation?.mapNotNull { it.language } ?: emptyList()
            val descLangs =
                alert.description_text?.translation?.mapNotNull { it.language } ?: emptyList()
            headerLangs + descLangs
        }.distinct()

        val htmlLangs = allLanguages.filter { it.endsWith("-html") }
        val basesToHide = htmlLangs.map { it.substringBefore("-html") }
        allLanguages.filter { it !in basesToHide }
    }

    Column(
        modifier = Modifier
            .border(1.dp, alertColor, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Warning, "Service Alert", Modifier.size(20.dp), alertColor)
            Text(
                getAlertsTitle(alerts.size),
                color = alertColor,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(start = 8.dp)
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onExpandedChange) {
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    if (expanded) "Collapse" else "Expand"
                )
            }
        }

        AnimatedVisibility(visible = expanded) {
            val configuration = LocalConfiguration.current
            val maxAlertHeight =
                remember(configuration) { (configuration.screenHeightDp * 0.8f).dp }
            val scrollableModifier = if (isScrollable) Modifier
                .heightIn(max = maxAlertHeight)
                .verticalScroll(rememberScrollState()) else Modifier

            Column(modifier = scrollableModifier) {
                Spacer(modifier = Modifier.height(4.dp))
                alerts.values.forEachIndexed { index, alert ->
                    if (index > 0) HorizontalDivider(
                        Modifier.padding(vertical = 4.dp),
                        color = alertColor.copy(alpha = 0.5f)
                    )
                    // Propagation of chateau ID to the Item view
                    AlertItem(alert, languageListToUse, locale, default_tz, alertColor, chateau)
                }
            }
        }
    }
}

@Composable
private fun AlertItem(
    alert: Alert,
    languageListToUse: List<String>,
    locale: Locale,
    default_tz: String?,
    alertColor: Color,
    chateau: String?
) {
    Column(modifier = Modifier.padding(top = 2.dp)) {
        Row {
            Text(
                causeIdToStr(alert.cause),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = alertColor
            )
            Text(
                " // ",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = alertColor
            )
            Text(
                effectIdToStr(alert.effect),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = alertColor
            )
        }
        alert.url?.let { AlertUrl(it) }
        languageListToUse.forEach { lang ->
            alert.header_text?.translation?.find { it.language == lang }?.let {
                FormattedText(it.text, MaterialTheme.typography.bodySmall, chateau)
            }
            alert.description_text?.translation?.find { it.language == lang }?.let {
                FormattedText(it.text, MaterialTheme.typography.bodySmall, chateau)
            }
        }
        alert.active_period.forEach { AlertActivePeriodRow(it, locale, default_tz) }
    }
}

@Composable
private fun FormattedText(text: String, style: TextStyle, chateau: String?) {
    val uriHandler = LocalUriHandler.current
    val tagRegex = remember { Regex("""<(/?[a-zA-Z0-9]+)(\s[^>]*)?>|([^<]+)""") }
    val hrefRegex = remember { Regex("""href="([^"]+)"""") }
    val mtaIconRegex = remember { Regex("""\[([A-Z0-9]+|shuttle bus icon|accessibility icon)\]""") }

    val inlineContentMap = mutableMapOf<String, InlineTextContent>()

    val annotatedString = buildAnnotatedString {
        val styleStack = mutableListOf<SpanStyle>()
        var justAddedBullet = false

        fun ensureNewlines(count: Int) {
            val currentText = this.toAnnotatedString().text
            if (currentText.isEmpty()) return
            val existing = currentText.takeLastWhile { it == '\n' }.length
            repeat(maxOf(0, count - existing)) { append("\n") }
        }

        tagRegex.findAll(text).forEach { match ->
            val tagName = match.groups[1]?.value?.lowercase()
            val attrs = match.groups[2]?.value ?: ""
            val content = match.groups[3]?.value ?: ""

            if (content.isNotEmpty()) {
                justAddedBullet = false
                val currentStyle = styleStack.fold(SpanStyle()) { acc, s -> acc.merge(s) }
                withStyle(currentStyle) {
                    // Strict gate: only perform bracket replacement if the agency is MTA (nyct)
                    if (chateau == MtaSubwayUtils.MTA_CHATEAU_ID) {
                        var lastIdx = 0
                        mtaIconRegex.findAll(content).forEach { mtaMatch ->
                            append(content.substring(lastIdx, mtaMatch.range.first))
                            val rawId = mtaMatch.groupValues[1]
                            val routeId = when (rawId) {
                                "shuttle bus icon" -> "GS"
                                "accessibility icon" -> "ADA"
                                else -> rawId
                            }

                            if (MtaSubwayUtils.isSubwayRouteId(routeId) || routeId == "ADA") {
                                val inlineId = "mta_$routeId"
                                appendInlineContent(inlineId, "[$rawId]")
                                inlineContentMap[inlineId] = InlineTextContent(
                                    Placeholder(
                                        14.sp,
                                        14.sp,
                                        PlaceholderVerticalAlign.TextCenter
                                    )
                                ) { MtaIcon(routeId) }
                            } else append(mtaMatch.value)
                            lastIdx = mtaMatch.range.last + 1
                        }
                        append(content.substring(lastIdx))
                    } else {
                        // For non-MTA agencies, append content exactly as provided
                        append(content)
                    }
                }
            } else if (tagName != null) {
                // Ignore MTA-specific spacing tags
                if (attrs.contains("min-height")) return@forEach

                when (tagName) {
                    "a" -> {
                        val url = hrefRegex.find(attrs)?.groupValues?.get(1)
                        if (!url.isNullOrEmpty()) {
                            pushStringAnnotation("URL", url)
                            styleStack.add(
                                SpanStyle(
                                    color = Color(0xff2b7fff),
                                    textDecoration = TextDecoration.Underline
                                )
                            )
                        }
                    }

                    "/a" -> {
                        pop(); styleStack.removeLastOrNull()
                    }

                    "b", "strong" -> styleStack.add(SpanStyle(fontWeight = FontWeight.Bold))
                    "/b", "/strong" -> styleStack.removeLastOrNull()
                    "br" -> {
                        ensureNewlines(1); justAddedBullet = false
                    }

                    "p" -> {
                        if (!justAddedBullet) ensureNewlines(2)
                        justAddedBullet = false
                    }

                    "/p" -> {
                        ensureNewlines(1); justAddedBullet = false
                    }

                    "ul", "/ul" -> {
                        ensureNewlines(1); justAddedBullet = false
                    }

                    "li" -> {
                        ensureNewlines(1)
                        append("  • ")
                        justAddedBullet = true // Suppresses extra newline if a <p> follows <li>
                    }
                }
            }
        }
    }

    val layoutResult = remember { mutableStateOf<TextLayoutResult?>(null) }
    BasicText(
        text = annotatedString,
        style = style.copy(color = MaterialTheme.colorScheme.onSurface),
        inlineContent = inlineContentMap,
        onTextLayout = { layoutResult.value = it },
        modifier = Modifier.pointerInput(annotatedString) {
            detectTapGestures { offset ->
                layoutResult.value?.let { res ->
                    val pos = res.getOffsetForPosition(offset)
                    annotatedString.getStringAnnotations("URL", pos, pos).firstOrNull()
                        ?.let { uriHandler.openUri(it.item) }
                }
            }
        }
    )
}

@Composable
private fun getAlertsTitle(alertCount: Int): String =
    pluralStringResource(
        R.plurals.service_alerts,
        alertCount,
        alertCount
    ).takeIf { it.isNotBlank() } ?: "Service Alerts ($alertCount)"

@Composable
fun causeIdToStr(cause: Int?): String = stringResource(
    id = when (cause) {
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
)

@Composable
fun effectIdToStr(effect: Int?): String = stringResource(
    id = when (effect) {
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
)