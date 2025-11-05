package com.catenarymaps.catenary

import android.content.Context
import android.os.Build
import androidx.compose.runtime.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor

// ---------- Locale helpers ----------
fun currentLocale(context: Context): Locale {
    val cfg = context.resources.configuration
    return if (Build.VERSION.SDK_INT >= 24) cfg.locales[0]
    else @Suppress("DEPRECATION") cfg.locale
}

private fun hourMarking(locale: Locale): String {
    val tag = locale.toLanguageTag()
    return when {
        tag.equals("zh", true) || tag.equals("zh-CN", true) || tag.equals("zh_CN", true) -> "小时"
        tag.equals("zh-TW", true) || tag.equals("zh_TW", true) -> "小時"
        tag.startsWith("ko", true) -> "시간"
        tag.startsWith("ja", true) -> "時間"
        else -> "h"
    }
}

private fun dayMarking(locale: Locale): String {
    val tag = locale.toLanguageTag()
    return when {
        tag.equals("zh", true) || tag.equals("zh-CN", true) || tag.equals("zh_CN", true) -> "天"
        tag.equals("zh-TW", true) || tag.equals("zh_TW", true) -> "天"
        tag.startsWith("ko", true) -> "일"
        tag.startsWith("ja", true) -> "日"
        else -> "d"
    }
}

private fun minMarking(locale: Locale): String {
    val tag = locale.toLanguageTag()
    return when {
        tag.equals("zh", true) || tag.equals("zh-CN", true) || tag.equals("zh_CN", true) -> "分"
        tag.equals("zh-TW", true) || tag.equals("zh_TW", true) -> "分"
        tag.startsWith("ko", true) -> "분"
        tag.startsWith("ja", true) -> "分"
        else -> "min"
    }
}

private fun secMarking(locale: Locale): String {
    val tag = locale.toLanguageTag()
    return when {
        tag.equals("zh", true) || tag.equals("zh-CN", true) || tag.equals("zh_CN", true) -> "秒"
        tag.equals("zh-TW", true) || tag.equals("zh_TW", true) -> "秒"
        tag.startsWith("ko", true) -> "초"
        tag.startsWith("ja", true) -> "秒"
        else -> "s"
    }
}

// ---------- Compose Timer Component ----------
@Composable
fun DiffTimer(
    diff: Double,                 // seconds (can be negative)
    showBrackets: Boolean = true,
    showSeconds: Boolean = false,
    large: Boolean = false,
    showDays: Boolean = false,
    showPlus: Boolean = false,
) {
    val context = LocalContext.current
    val locale = remember { currentLocale(context) }

    // Compute d/h/m/s
    val (d, h, m, s) = remember(diff, showDays) {
        var remainder = floor(abs(diff))
        var days = 0
        if (showDays) {
            days = floor(remainder / 86400).toInt()
            remainder -= days * 86400
        }
        val hours = floor(remainder / 3600).toInt()
        remainder -= hours * 3600
        val mins = floor(remainder / 60).toInt()
        remainder -= mins * 60
        val secs = remainder
        arrayOf(days, hours, mins, secs)
    }

    val signText = when {
        diff < 0 -> "-"
        diff > 0 && showPlus -> "+"
        else -> ""
    }

    val numSize = if (large) 18.sp else 14.sp
    val unitSize = if (large) 14.sp else 12.sp
    val bracketSize = if (large) 18.sp else 14.sp

    Row(modifier = Modifier, verticalAlignment = Alignment.Bottom) {
        if (showBrackets) {
            Text("[", fontSize = bracketSize)
        }

        if (signText.isNotEmpty()) {
            Text(signText, fontSize = numSize, fontWeight = FontWeight.Bold)
        }

        if ((d as Int) > 0) {
            Text("$d", fontSize = numSize)
            Text(dayMarking(locale), fontSize = unitSize)
        }

        if ((h as Int) > 0) {
            Text("$h", fontSize = numSize)
            Text(hourMarking(locale), fontSize = unitSize)
        }

        val showM =
            (h as Int) > 0 || ((m as Int) > 0 || (!showSeconds && (m as Int) >= 0 && diff != 0.0))
        if (showM) {
            Text("$m", fontSize = numSize)
            Text(minMarking(locale), fontSize = unitSize)
        }

        if (showSeconds) {
            Text((s as Double).toInt().toString(), fontSize = numSize)
            Text(secMarking(locale), fontSize = unitSize)
        }

        if (showBrackets) {
            Text("]", fontSize = bracketSize)
        }
    }
}
