package com.catenarymaps.catenary

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Subway
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.filled.Tram
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.catenarymaps.catenary.CatenaryStackEnum
import com.catenarymaps.catenary.darkenColour
import com.catenarymaps.catenary.lightenColour
import com.catenarymaps.catenary.parseColor
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth

@Composable
fun RouteHeading(
    color: String,
    textColor: String,
    routeType: Int?,
    agencyName: String?,
    shortName: String?,
    longName: String?,
    description: String? = null,
    tripShortName: String? = null,
    isCompact: Boolean,
    routeClickable: Boolean = false,
    headsign: String? = null,
    onRouteClick: (() -> Unit)? = null,
    controls: @Composable () -> Unit = {}
) {
    if (isCompact) {
        // Compact version not implemented based on svelte file
        // but can be added here if needed.
        return
    }

    println("route heading, ${shortName} ${longName}")

    val routeColor = parseColor(color, MaterialTheme.colorScheme.primary)
    val routeTextColor = parseColor(textColor, MaterialTheme.colorScheme.onPrimary)

    val displayColor = if (androidx.compose.foundation.isSystemInDarkTheme()) {
        lightenColour(routeColor, minContrast = 7.0)
    } else {
        darkenColour(routeColor)
    }

    val clickableModifier = if (routeClickable && onRouteClick != null) {
        Modifier.clickable(onClick = onRouteClick)
    } else {
        Modifier
    }

    val textStyle = if (routeClickable) {
        MaterialTheme.typography.titleLarge.copy(
            textDecoration = TextDecoration.Underline,
            lineHeight = MaterialTheme.typography.titleLarge.fontSize * 0.8
        )
    } else {
        MaterialTheme.typography.titleLarge.copy(
            lineHeight = MaterialTheme.typography.titleLarge.fontSize * 0.8
        )
    }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    modifier = clickableModifier,
                    text = buildAnnotatedString {
                        if (!shortName.isNullOrBlank()) {
                            withStyle(
                                style = textStyle.toSpanStyle().copy(fontWeight = FontWeight.Bold)
                            ) {
                                append(shortName)
                            }
                            if (!longName.isNullOrBlank()) {
                                append(" ")
                            }
                        }
                        if (!longName.isNullOrBlank()) {
                            append(longName)
                        }
                    },
                    color = displayColor,
                    style = textStyle,
                )

                if (headsign != null || tripShortName != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (tripShortName != null) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(routeColor)
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = tripShortName,
                                    color = routeTextColor
                                )
                            }

                            Spacer(Modifier.size(4.dp))
                        }

                        if (headsign != null) {
                            Text(
                                text = headsign
                            )
                        }
                    }


                }

                if (!agencyName.isNullOrBlank()) {
                    Text(text = agencyName, style = MaterialTheme.typography.titleMedium)
                }

                if (!description.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = description, style = MaterialTheme.typography.bodyMedium)
                }
            }


            controls()
        }


    }
}