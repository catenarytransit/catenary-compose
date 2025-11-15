package com.catenarymaps.catenary

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun NavigationControls(
    onBack: () -> Unit,
    onHome: () -> Unit
) {
    Row(modifier = Modifier) {
        IconButton(
            onClick = onBack,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack, "Go back", modifier = Modifier
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = CircleShape
                    )
                    .padding(8.dp)
                    .size(16.dp)
            )
        }

        //Spacer(Modifier.width(1.dp))

        IconButton(
            onClick = onHome
        ) {
            Icon(
                Icons.Filled.Home, "Home screen", modifier = Modifier
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = CircleShape
                    )
                    .padding(8.dp)
                    .size(16.dp)
            )
        }
    }
}
