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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
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

// ---------- Self-Updating Timer Component ----------
/**
 * A self-updating countdown timer that automatically refreshes every second.
 * Use this for countdown displays that need to update in real-time.
 *
 * @param targetTimeSeconds The target Unix timestamp in seconds to count down to (or from if in the past)
 */
@Composable
fun SelfUpdatingDiffTimer(
    targetTimeSeconds: Long,
    showBrackets: Boolean = true,
    showSeconds: Boolean = false,
    showDays: Boolean = false,
    showPlus: Boolean = false,
    numSize: TextUnit = 14.sp,
    unitSize: TextUnit = 12.sp,
    bracketSize: TextUnit = 14.sp,
    numberFontWeight: FontWeight = FontWeight.Normal,
    unitFontWeight: FontWeight = FontWeight.Normal,
    color: Color = Color.Unspecified
) {
    var currentTimeSeconds by remember { mutableStateOf(java.time.Instant.now().epochSecond) }
    
    LaunchedEffect(Unit) {
        while (true) {
            currentTimeSeconds = java.time.Instant.now().epochSecond
            kotlinx.coroutines.delay(1000L)
        }
    }
    
    val diff = (targetTimeSeconds - currentTimeSeconds).toDouble()
    
    DiffTimer(
        diff = diff,
        showBrackets = showBrackets,
        showSeconds = showSeconds,
        showDays = showDays,
        showPlus = showPlus,
        numSize = numSize,
        unitSize = unitSize,
        bracketSize = bracketSize,
        numberFontWeight = numberFontWeight,
        unitFontWeight = unitFontWeight,
        color = color
    )
}

// ---------- Compose Timer Component ----------
@Composable
fun DiffTimer(
    diff: Double,                 // seconds (can be negative)
    showBrackets: Boolean = true,
    showSeconds: Boolean = false,
    showDays: Boolean = false,
    showPlus: Boolean = false,
    numSize: TextUnit = 14.sp,
    unitSize: TextUnit = 12.sp,
    bracketSize: TextUnit = 14.sp,
    numberFontWeight: FontWeight = FontWeight.Normal,
    unitFontWeight: FontWeight = FontWeight.Normal,
    color: Color = Color.Unspecified
) {
    val context = LocalContext.current
    val locale = remember { currentLocale(context) }

    // Fix: Unified types to Int to avoid "intersection of Number & Comparable" warning
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

        // Explicitly convert to Int here so the array is Array<Int>
        val secs = remainder.toInt()
        arrayOf(days, hours, mins, secs)
    }

    val signText = when {
        diff < 0 -> "-"
        diff > 0 && showPlus -> "+"
        else -> ""
    }

    Row(modifier = Modifier, verticalAlignment = Alignment.Bottom) {
        if (showBrackets) {
            Text("[", fontSize = bracketSize, modifier = Modifier.alignByBaseline(), color = color)
        }

        if (signText.isNotEmpty()) {
            Text(
                signText, fontSize = numSize, fontWeight = FontWeight.Bold,
                modifier = Modifier.alignByBaseline(), color = color
            )
        }

        // Note: usage of 'as Int' is no longer required as d, h, m, s are inferred as Int
        if (d > 0) {
            Text(
                "$d", fontSize = numSize, fontWeight = numberFontWeight,
                modifier = Modifier.alignByBaseline(), color = color
            )
            Text(
                dayMarking(locale),
                fontSize = unitSize,
                fontWeight = unitFontWeight,
                modifier = Modifier.alignByBaseline(),
                color = color
            )
        }

        if (h > 0) {
            Text(
                "$h", modifier = Modifier.alignByBaseline(),
                fontSize = numSize, fontWeight = numberFontWeight, color = color
            )
            Text(
                hourMarking(locale),
                fontSize = unitSize,
                fontWeight = unitFontWeight,
                modifier = Modifier.alignByBaseline(),
                color = color
            )
        }

        val showM =
            h > 0 || (m > 0 || (!showSeconds && m >= 0 && diff != 0.0))
        if (showM) {
            Text(
                "$m",
                modifier = Modifier.alignByBaseline(),
                fontSize = numSize,
                fontWeight = numberFontWeight,
                color = color
            )
            Text(
                minMarking(locale),
                fontSize = unitSize,
                fontWeight = unitFontWeight,
                modifier = Modifier.alignByBaseline(),
                color = color
            )
        }

        if (showSeconds) {
            Text(
                s.toString(), // Removed unsafe cast (s as Double)
                modifier = Modifier.alignByBaseline(),
                fontSize = numSize,
                fontWeight = numberFontWeight,
                color = color
            )
            Text(
                secMarking(locale),
                fontSize = unitSize,
                fontWeight = unitFontWeight,
                modifier = Modifier.alignByBaseline(),
                color = color
            )
        }

        if (showBrackets) {
            Text("]", fontSize = bracketSize, modifier = Modifier.alignByBaseline(), color = color)
        }
    }
}