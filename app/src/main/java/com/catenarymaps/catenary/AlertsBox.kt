package com.catenarymaps.catenary

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.serialization.Serializable
import java.util.Locale

@Serializable
data class AlertTranslation(
    val text: String,
    val language: String
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
    val cause: Int,
    val effect: Int,
    val url: AlertText? = null,
    val header_text: AlertText? = null,
    val description_text: AlertText? = null,
    val active_period: List<AlertActivePeriod> = emptyList()
)

@Composable
fun AlertsBox(
    alerts: Map<String, Alert>,
    default_tz: String? = null,
    chateau: String? = null
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
            Text(
                text = pluralStringResource(R.plurals.service_alerts, alerts.size, alerts.size),
                color = alertColor,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleLarge,
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
            Column {
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
    languageListToUse: List<String>,
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

        // TODO: Implement URL, Header, Description, and Active Period rendering
        // This would involve creating composables for:
        // - Displaying translated URLs
        // - Parsing and displaying HTML/Markdown-like text for headers and descriptions
        // - Formatting and displaying active periods with TimeDiff

        // Placeholder for description
        languageListToUse.forEach { lang ->
            alert.description_text?.translation?.find { it.language == lang }?.let {
                Text(
                    text = it.text.replace(Regex("<.*?>"), "").replace("\\n", "\n"),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
fun causeIdToStr(cause: Int): String {
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
fun effectIdToStr(effect: Int): String {
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

// You will need a way to handle plural strings.
// This is a simplified example. You might use a library or a more robust implementation.
@Composable
fun pluralStringResource(id: Int, quantity: Int, vararg formatArgs: Any): String {
    return LocalContext.current.resources.getQuantityString(id, quantity, *formatArgs)
}
