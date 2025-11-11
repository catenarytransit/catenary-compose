package com.catenarymaps.catenary

import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp


@Composable
fun SettingsScreen(
    datadogConsent: Boolean,
    onDatadogConsentChanged: (Boolean) -> Unit,
    gaConsent: Boolean,
    onGaConsentChanged: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = stringResource(id = R.string.settings),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // --- Datadog Section ---
        Text(
            text = stringResource(id = R.string.settings_datadog_analytics_title),
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = stringResource(id = R.string.settings_datadog_analytics_description),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(vertical = 4.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (datadogConsent) {
            // Show Disable button if consent is GRANTED
            Button(
                onClick = { onDatadogConsentChanged(false) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(id = R.string.settings_disable_datadog_tracking))
            }
        } else {
            // Show Enable button if consent is NOT_GRANTED
            OutlinedButton(
                onClick = { onDatadogConsentChanged(true) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(id = R.string.settings_enable_datadog_tracking))
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

        // --- Google Analytics Section ---
        Text(
            text = stringResource(id = R.string.settings_google_analytics_title),
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = stringResource(id = R.string.settings_google_analytics_description),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(vertical = 4.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (gaConsent) {
            // Show Disable button if consent is GRANTED (default)
            Button(
                onClick = { onGaConsentChanged(false) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(id = R.string.settings_disable_google_analytics))
            }
        } else {
            // Show Enable button if consent is NOT_GRANTED
            OutlinedButton(
                onClick = { onGaConsentChanged(true) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(id = R.string.settings_enable_google_analytics))
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

        // --- Test Crash Button ---
        Button(
            onClick = {
                throw RuntimeException("Test Crash") // Force a crash
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text(text = stringResource(id = R.string.settings_test_crash)) }
    }
}