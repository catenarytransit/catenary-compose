package com.catenarymaps.catenary

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/** One horizontally scrolling action row shared by stops, stations, and places. */
@Composable
fun DirectionsActions(
    location: DirectionsLocation?,
    onDirections: (Boolean, DirectionsLocation) -> Unit,
    onNearbyDepartures: ((DirectionsLocation) -> Unit)? = null
) {
    val enabled = location?.isValid() == true
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(
            enabled = enabled,
            onClick = { location?.let { onDirections(true, it) } }
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_directions_origin),
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize)
            )
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.directions_as_origin), maxLines = 1)
        }
        OutlinedButton(
            enabled = enabled,
            onClick = { location?.let { onDirections(false, it) } }
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_directions_destination),
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize)
            )
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.directions_as_destination), maxLines = 1)
        }
        if (onNearbyDepartures != null) {
            OutlinedButton(
                enabled = enabled,
                onClick = { location?.let { onNearbyDepartures(it) } }
            ) {
                Text(
                    stringResource(if (enabled) R.string.osm_nearby else R.string.osm_nearby_unavailable),
                    maxLines = 1
                )
            }
        }
    }
}
