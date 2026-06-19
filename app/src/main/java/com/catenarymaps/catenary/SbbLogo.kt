package com.catenarymaps.catenary

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest

@Composable
fun SbbLogo(
    text: String,
    modifier: Modifier = Modifier,
    height: Dp = 16.dp,
    color: Color? = null
) {
    val isSbbIcOrIr = text.startsWith("IR") || text.startsWith("IC")
    val isEc = text == "EC"

    if (!isSbbIcOrIr && !isEc) {
        Text(
            text = text,
            fontWeight = FontWeight.Bold,
            modifier = modifier
        )
        return
    }

    val sbbLogoUrl = when {
        isEc -> "https://maps.catenarymaps.org/icons/sbb/SBB_EC_Logo.svg"
        text.startsWith("IR") -> "https://maps.catenarymaps.org/icons/sbb/SBB_IR_Logo.svg"
        else -> "https://maps.catenarymaps.org/icons/sbb/SBB_IC_Logo.svg"
    }

    val logoWidth = if (isEc) height * 2.78f else height * 2.5f
    val remainingText = if (isSbbIcOrIr) text.substring(2).trim() else ""
    val tintColor = color ?: LocalContentColor.current

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val context = LocalContext.current
        val imageLoader = remember(context) {
            ImageLoader.Builder(context)
                .components { add(SvgDecoder.Factory()) }
                .build()
        }

        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(sbbLogoUrl)
                .crossfade(true)
                .build(),
            imageLoader = imageLoader,
            contentDescription = text,
            colorFilter = ColorFilter.tint(tintColor),
            modifier = Modifier
                .height(height)
                .width(logoWidth)
        )

        if (remainingText.isNotEmpty()) {
            Spacer(modifier = Modifier.width(height * 0.2f))
            Text(
                text = remainingText,
                color = tintColor,
                fontWeight = FontWeight.Bold,
                fontSize = (height.value * 0.8).sp,
                lineHeight = (height.value * 0.8).sp
            )
        }
    }
}
