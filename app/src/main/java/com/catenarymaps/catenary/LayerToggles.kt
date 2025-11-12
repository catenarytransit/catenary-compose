package com.catenarymaps.catenary

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp


@Composable
fun LayerToggleButton(
    name: String,
    icon: @Composable () -> Unit,
    isActive: Boolean,
    onToggle: () -> Unit,
    padding: Dp = 4.dp,
    activeBorderWidth: Dp = 2.dp // Parameter for "thick" border
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(4.dp) // This provides spacing between the box and the text
                .border( // --- CHANGE 1: Added border ---
                    width = activeBorderWidth,
                    color = if (isActive) MaterialTheme.colorScheme.primary else Color.Transparent,
                    shape = RoundedCornerShape(8.dp)
                )
                .clip(RoundedCornerShape(8.dp))
                .background(
                    // --- CHANGE 2: Removed active state from background ---
                    MaterialTheme.colorScheme.surfaceVariant
                )
                .clickable { onToggle() }
                .padding(padding) // This is the inner padding for the icon
        ) {
            icon()
        }

        Text(
            text = name,
            style = MaterialTheme.typography.bodySmall,
            // --- CHANGE 3: Removed active state from text color ---
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun VehicleLabelToggleButton(
    name: String,
    icon: ImageVector,
    isActive: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.width(IntrinsicSize.Min) // Ensure column shrinks to text
    ) {
        FilledIconToggleButton(
            checked = isActive,
            onCheckedChange = { onToggle() },
            colors = IconButtonDefaults.filledIconToggleButtonColors(
                containerColor = MaterialTheme.colorScheme.surface,
                checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        ) {
            Icon(icon, contentDescription = name)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
