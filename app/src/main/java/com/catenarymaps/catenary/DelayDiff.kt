package com.catenarymaps.catenary

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor

@Composable
fun DelayDiff(diff: Long, show_seconds: Boolean = false, fontSizeOfPolarity: TextUnit = 12.sp) {
    val isDark = isSystemInDarkTheme()

    val textColor by remember(diff) {
        mutableStateOf(
            if (isDark) {
                when {
                    diff <= -300 -> Color(0xFFE53935) // Red 600
                    diff <= -60 -> Color(0xFFFDD835) // Yellow 600
                    diff >= 3600 -> Color(0xFFff6467) // Pink 600
                    diff >= 300 -> Color(0xFFE53935) // Red 700
                    diff >= 180 -> Color(0xFFFDD835) // Yellow 600
                    else -> Color(0xFF58A738)
                }
            } else {
                when {
                    diff <= -300 -> Color(0xFFE53935) // Red 600
                    diff <= -60 -> Color(0xFFe17100) // Amber 600
                    diff >= 3600 -> Color(0xFFD81B60) // Pink 600
                    diff >= 300 -> Color(0xFFE53935) // Red 700
                    diff >= 180 -> Color(0xFFe17100) // Amber 600
                    else -> Color(0xFF58A738)
                }

            }
        )
    }

    val remainder = abs(diff)
    val h = floor(remainder / 3600.0).toLong()
    val m = floor((remainder - h * 3600) / 60.0).toLong()
    val s = remainder - h * 3600 - m * 60

    Row(
        verticalAlignment = Alignment.Bottom
    ) {
        if (diff < 0) {
            Text(text = stringResource(id = R.string.early), fontSize = fontSizeOfPolarity, color = textColor)
        } else if (diff > 0) {
            Text(text = stringResource(id = R.string.late), fontSize = fontSizeOfPolarity, color = textColor)
        } else {
            Text(
                text = stringResource(id = R.string.ontime),
                fontSize = fontSizeOfPolarity,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF58A738)
            )
        }

        Text(text = " ", fontSize = fontSizeOfPolarity)

        if (diff != 0L) {
            if (h > 0) {
                Text(text = h.toString(), fontSize = 14.sp, color = textColor)
                Text(text = locale_hour_marking(), fontSize = 10.sp, color = textColor)
            }
            if (h > 0 || m > 0 || (!show_seconds && m >= 0 && diff != 0L)) {
                val minuteText = if (!show_seconds && abs(diff) < 60) "<1" else m.toString()
                Text(text = minuteText, fontSize = 14.sp, color = textColor)
                Text(text = locale_min_marking(show_seconds), fontSize = 10.sp, color = textColor)
            }
            if (show_seconds) {
                if (abs(diff) > 0) {
                    Text(text = s.toString(), fontSize = 14.sp, color = textColor)
                    Text(text = locale_s_marking(), fontSize = 10.sp, color = textColor)
                }
            }
        }
    }
}

fun locale_hour_marking(): String {
    val l = Locale.getDefault().language
    return when {
        l == "zh" -> "小时"
        l == "ko" -> "시간"
        l == "ja" -> "時間"
        else -> "h"
    }
}

fun locale_min_marking(show_seconds: Boolean): String {
    val l = Locale.getDefault().language
    return when {
        l == "zh" || l == "ko" || l == "ja" -> "分"
        show_seconds -> "m"
        else -> "min"
    }
}

fun locale_s_marking(): String {
    val l = Locale.getDefault().language
    return when {
        l == "zh" || l == "ko" || l == "ja" -> "秒"
        else -> "s"
    }
}