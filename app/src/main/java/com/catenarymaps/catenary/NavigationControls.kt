package com.catenarymaps.catenary

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreTime
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentEnforcement
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavigationControls(
        onBack: () -> Unit,
        onHome: () -> Unit,
        onPageInfo: (() -> Unit)? = null,
        isPageInfoPulse: Boolean = false
) {
        CompositionLocalProvider(LocalMinimumInteractiveComponentEnforcement provides false) {
                Row {
                        IconButton(
                                onClick = onBack,
                                modifier = Modifier.size(32.dp) // shrink the button hitbox
                        ) {
                                Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Go back",
                                        modifier =
                                                Modifier
                                                        .background(
                                                                color =
                                                                        MaterialTheme.colorScheme
                                                                                .surfaceVariant,
                                                                shape = CircleShape
                                                        )
                                                        .padding(8.dp)
                                                        .size(24.dp) // circle + icon size
                                )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        IconButton(onClick = onHome, modifier = Modifier.size(32.dp)) {
                                Icon(
                                        Icons.Filled.MoreVert,
                                        contentDescription = "Home screen",
                                        modifier =
                                                Modifier
                                                        .background(
                                                                color =
                                                                        MaterialTheme.colorScheme
                                                                                .surfaceVariant,
                                                                shape = CircleShape
                                                        )
                                                        .padding(8.dp)
                                                        .size(24.dp)
                                )
                        }

                        if (onPageInfo != null) {
                                Spacer(modifier = Modifier.width(8.dp))

                                val scale =
                                        if (isPageInfoPulse) {
                                                val infiniteTransition =
                                                        rememberInfiniteTransition(label = "pulse")
                                                infiniteTransition.animateFloat(
                                                        initialValue = 1f,
                                                        targetValue = 1.2f,
                                                        animationSpec =
                                                                infiniteRepeatable(
                                                                        animation =
                                                                                tween(500),
                                                                        repeatMode =
                                                                                RepeatMode
                                                                                        .Reverse
                                                                ),
                                                        label = "scale"
                                                )
                                                        .value
                                        } else {
                                                1f
                                        }

                                IconButton(
                                        onClick = onPageInfo,
                                        modifier = Modifier
                                                .size(32.dp)
                                                .scale(scale)
                                ) {
                                        Icon(
                                                Icons.Filled
                                                        .MoreTime, // Using Info as PageInfo replacement
                                                // if not found,
                                                // usually PageInfo is not standard.
                                                contentDescription = "Page Info",
                                                modifier =
                                                        Modifier
                                                                .background(
                                                                        color =
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .surfaceVariant,
                                                                        shape = CircleShape
                                                                )
                                                                .padding(8.dp)
                                                                .size(24.dp)
                                        )
                                }
                        }
                }
        }
}
