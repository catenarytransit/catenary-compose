package com.catenarymaps.catenary

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@Composable
fun OccupancyStatusText(occupancyStatus: Int?) {
    if (occupancyStatus == null) return

    val occupancyText = when (occupancyStatus) {
        0 -> stringResource(R.string.occupancy_status_empty)
        1 -> stringResource(R.string.occupancy_status_many_seats_available)
        2 -> stringResource(R.string.occupancy_status_few_seats_available)
        3 -> stringResource(R.string.occupancy_status_standing_room_only)
        4 -> stringResource(R.string.occupancy_status_crushed_standing_room_only)
        5 -> stringResource(R.string.occupancy_status_full)
        6 -> stringResource(R.string.occupancy_status_not_accepting_passengers)
        7 -> stringResource(R.string.occupancy_status_no_data)
        8 -> stringResource(R.string.occupancy_status_not_boardable)
        else -> ""
    }

    val occupancyColor = when (occupancyStatus) {
        3 -> Color(0xFFD97706) // Amber 600
        in 4..6, 8 -> MaterialTheme.colorScheme.error
        else -> Color.Unspecified
    }

    if (occupancyText.isNotEmpty()) {
        Row {
            Text(text = "${stringResource(R.string.occupancy_status)}:")
            Spacer(Modifier.width(4.dp))
            Text(text = occupancyText, color = occupancyColor)
        }
    }
}
