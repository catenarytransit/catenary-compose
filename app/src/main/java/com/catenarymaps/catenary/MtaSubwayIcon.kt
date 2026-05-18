package com.catenarymaps.catenary

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest

@Composable
internal fun MtaSubwayIcon(
        routeId: String,
        modifier: Modifier = Modifier,
        size: Dp = 24.dp,
        contentDescription: String? = routeId
) {
        val iconUrl = MtaSubwayUtils.getMtaIconUrl(routeId)

        if (iconUrl != null) {
                val context = LocalContext.current
                val imageLoader =
                        remember(context) {
                                ImageLoader.Builder(context)
                                        .components { add(SvgDecoder.Factory()) }
                                        .build()
                        }

                AsyncImage(
                        model =
                                ImageRequest.Builder(context)
                                        .data(iconUrl)
                                        .crossfade(true)
                                        .build(),
                        imageLoader = imageLoader,
                        contentDescription = contentDescription,
                        modifier = modifier.size(size),
                        colorFilter = null
                )
        } else {
                MtaSubwayFallbackIcon(routeId = routeId, modifier = modifier, size = size)
        }
}

@Composable
private fun MtaSubwayFallbackIcon(routeId: String, modifier: Modifier, size: Dp) {
        Box(
                modifier =
                        modifier
                                .size(size)
                                .clip(CircleShape)
                                .background(MtaSubwayUtils.getMtaSubwayColor(routeId)),
                contentAlignment = Alignment.Center
        ) {
                Text(
                        text = MtaSubwayUtils.getMtaSymbolShortName(routeId),
                        color = Color.White,
                        style =
                                MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold
                                ),
                        textAlign = TextAlign.Center
                )
        }
}
