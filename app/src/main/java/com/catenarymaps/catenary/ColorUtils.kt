// ColorTransforms.kt
package com.catenarymaps.catenary

import androidx.annotation.ColorInt
import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class Rgb(val r: Int, val g: Int, val b: Int)
data class Hsl(var h: Double, var s: Double, var l: Double) // h: 0–360, s/l: 0–100

// ---------- Common helpers (same math as your JS) ----------
private fun clampInt(v: Int, lo: Int, hi: Int) = max(lo, min(hi, v))
private fun clamp(v: Double, lo: Double, hi: Double) = max(lo, min(hi, v))

fun componentToHex(c: Int): String = clampInt(c, 0, 255).toString(16).padStart(2, '0')

fun hexToRgb(input: String): Rgb? {
    val hex = input.removePrefix("#").trim()
    if (hex.length != 6 || !hex.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
    return Rgb(
        hex.substring(0, 2).toInt(16),
        hex.substring(2, 4).toInt(16),
        hex.substring(4, 6).toInt(16)
    )
}

fun rgbToHsl(r: Int, g: Int, b: Int): Hsl {
    val rf = r / 255.0
    val gf = g / 255.0
    val bf = b / 255.0

    val maxC = max(rf, max(gf, bf))
    val minC = min(rf, min(gf, bf))
    val delta = maxC - minC

    var h = 0.0
    val l = (maxC + minC) / 2.0
    val s = if (delta == 0.0) 0.0 else delta / (1.0 - abs(2.0 * l - 1.0))

    if (delta != 0.0) {
        h = when (maxC) {
            rf -> ((gf - bf) / delta) % 6.0
            gf -> ((bf - rf) / delta) + 2.0
            else -> ((rf - gf) / delta) + 4.0
        }
        h *= 60.0
        if (h < 0) h += 360.0
    }
    return Hsl(h, s * 100.0, l * 100.0)
}

fun hslToRgb(h: Double, s: Double, l: Double): Rgb {
    val hh = ((h % 360.0) + 360.0) % 360.0
    val ss = clamp(s, 0.0, 100.0) / 100.0
    val ll = clamp(l, 0.0, 100.0) / 100.0

    if (ss == 0.0) {
        val gray = (ll * 255.0).roundToInt()
        return Rgb(gray, gray, gray)
    }

    val c = (1.0 - abs(2.0 * ll - 1.0)) * ss
    val x = c * (1.0 - abs((hh / 60.0) % 2.0 - 1.0))
    val m = ll - c / 2.0

    val (rf, gf, bf) = when {
        hh < 60 -> Triple(c, x, 0.0)
        hh < 120 -> Triple(x, c, 0.0)
        hh < 180 -> Triple(0.0, c, x)
        hh < 240 -> Triple(0.0, x, c)
        hh < 300 -> Triple(x, 0.0, c)
        else -> Triple(c, 0.0, x)
    }

    val r = ((rf + m) * 255.0).roundToInt()
    val g = ((gf + m) * 255.0).roundToInt()
    val b = ((bf + m) * 255.0).roundToInt()
    return Rgb(clampInt(r, 0, 255), clampInt(g, 0, 255), clampInt(b, 0, 255))
}

// ---------- Original String <-> #RRGGBB API (unchanged behavior) ----------
fun lightenColour(inputString: String): String {
    var out = inputString
    val rgb = hexToRgb(inputString) ?: return out

    val hsl = rgbToHsl(rgb.r, rgb.g, rgb.b)
    val newDarkHsl = Hsl(hsl.h, hsl.s, hsl.l)

    var blueOffset = 0.0
    if (rgb.b > 40) blueOffset = 30.0 * (rgb.b / 255.0)

    if (hsl.l < 60.0) {
        newDarkHsl.l = hsl.l + 10.0 + (25.0 * ((100.0 - hsl.s) / 100.0) + blueOffset)
        if (hsl.l > 60.0) {
            hsl.l = if (blueOffset == 0.0) 60.0 else 60.0 + blueOffset
        }
    }
    if (hsl.l < 60.0) {
        hsl.l = hsl.l + ((100.0 - hsl.l) * 0.4)
    }

    val newRgb = hslToRgb(newDarkHsl.h, newDarkHsl.s, newDarkHsl.l)
    out = "#${componentToHex(newRgb.r)}${componentToHex(newRgb.g)}${componentToHex(newRgb.b)}"
    return out
}

fun darkenColour(inputString: String): String {
    var out = inputString
    val rgb = hexToRgb(inputString) ?: return out

    val new_rgb = darkenColour(rgb)

    val hsl = rgbToHsl(new_rgb.r, new_rgb.g, new_rgb.b)

    val newRgb = hslToRgb(hsl.h, hsl.s, hsl.l)
    out = "#${componentToHex(newRgb.r)}${componentToHex(newRgb.g)}${componentToHex(newRgb.b)}"
    return out
}

/** Luminance of a white background. */
private const val LUMINANCE_WHITE = 1.0

/** Target contrast ratio for AA-level readability. */
fun srgbToLinear(u8: Int): Double {
    val s = (u8.coerceIn(0, 255)) / 255.0
    return if (s <= 0.04045) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4)
}

fun linearToSrgb(lin: Double): Int {
    val s = if (lin <= 0.0031308) 12.92 * lin else 1.055 * Math.pow(lin, 1.0 / 2.4) - 0.055
    return (s * 255.0).roundToInt().coerceIn(0, 255)
}

fun darkenColour(rgb: Rgb, minContrast: Double = 4.5): Rgb {
    // Convert to linear sRGB
    val rLin = srgbToLinear(rgb.r)
    val gLin = srgbToLinear(rgb.g)
    val bLin = srgbToLinear(rgb.b)

    // Relative luminance per WCAG
    val y = 0.2126 * rLin + 0.7152 * gLin + 0.0722 * bLin

    // Contrast vs white (white luminance = 1.0)
    val contrast = (1.0 + 0.05) / (y + 0.05)
    if (contrast >= minContrast || y == 0.0) {
        return rgb // already dark enough or black
    }

    // Find scalar k so new luminance y' = k * y meets contrast:
    // (1.05) / (y' + 0.05) >= minContrast  =>  y' <= (1.05 / minContrast) - 0.05
    val yTargetMax = (1.05 / minContrast) - 0.05
    val k = (yTargetMax / y).coerceIn(0.0, 1.0)

    // Scale linear channels uniformly, then convert back to sRGB
    val rOut = linearToSrgb(rLin * k)
    val gOut = linearToSrgb(gLin * k)
    val bOut = linearToSrgb(bLin * k)
    return Rgb(rOut, gOut, bOut)
}

// ---------- Compose Color API (new) ----------
fun lightenColour(color: Color): Color {
    val rgb = Rgb(
        (color.red * 255).roundToInt(),
        (color.green * 255).roundToInt(),
        (color.blue * 255).roundToInt()
    )
    val hsl = rgbToHsl(rgb.r, rgb.g, rgb.b)

    var blueOffset = 0.0
    if (rgb.b > 40) blueOffset = 30.0 * (rgb.b / 255.0)

    if (hsl.l < 60.0) {
        hsl.l = hsl.l + 10.0 + (25.0 * ((100.0 - hsl.s) / 100.0) + blueOffset)
        if (hsl.l > 60.0) {
            hsl.l = if (blueOffset == 0.0) 60.0 else 60.0 + blueOffset
        }
    }

    val newRgb = hslToRgb(hsl.h, hsl.s, hsl.l)
    return Color(newRgb.r, newRgb.g, newRgb.b)
}

fun darkenColour(color: Color, minContrast: Double = 4.5): Color {
    val newRgb = darkenColour(
        Rgb(
            (color.red * 255).roundToInt(),
            (color.green * 255).roundToInt(),
            (color.blue * 255).roundToInt()
        ), minContrast
    )
    return Color(newRgb.r, newRgb.g, newRgb.b)
}


fun processColor(hexColor: String, isDark: Boolean): Pair<String, String> {
    val (r, g, b) = hexToRgb(hexColor) ?: return Pair(hexColor, hexColor)

    if (isDark) {
        val (h, s, l) = rgbToHsl(r, g, b)
        var newL = l
        val blueOffset = if (b > 40) 30 * (b / 255.0) else 0.0
        if (l < 60) {
            newL = l + 10 + (25 * ((100 - s) / 100) + blueOffset)
            if (newL > 60) {
                newL = 60.0 + blueOffset
            }
        }
        if (l < 60) {
            newL = minOf(sqrt(l * 25.0) + 40, 100.0)
            // s = minOf(100.0, s + 20) // This was in JS, but newL logic seems to override
        }

        val (newR, newG, newB) = hslToRgb(h, s, newL)
        val (bearR, bearG, bearB) = hslToRgb(h, s, (newL + l) / 2.0)

        val contrastColor = "#%02x%02x%02x".format(newR, newG, newB)
        val bearingColor = "#%02x%02x%02x".format(bearR, bearG, bearB)
        return Pair(contrastColor, bearingColor)
    } else {
        // Light mode processing
        val gamma = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
        if (gamma > 0.55) {
            val (adjR, adjG, adjB) = adjustGamma(r, g, b, 0.55)
            val contrastColor = "#%02x%02x%02x".format(adjR, adjG, adjB)
            return Pair(contrastColor, contrastColor)
        }
        return Pair(hexColor, hexColor)
    }
}

fun adjustGamma(r: Int, g: Int, b: Int, targetGamma: Double): Triple<Int, Int, Int> {
    // Simplified version of adjustGamma
    val factor = targetGamma / ((0.299 * r + 0.587 * g + 0.114 * b) / 255.0)
    return Triple(
        (r * factor).roundToInt().coerceIn(0, 255),
        (g * factor).roundToInt().coerceIn(0, 255),
        (b * factor).roundToInt().coerceIn(0, 255)
    )
}

fun hueToRgb(p: Double, q: Double, t: Double): Double {
    var tN = t
    if (tN < 0.0) tN += 1.0
    if (tN > 1.0) tN -= 1.0
    if (tN < 1.0 / 6.0) return p + (q - p) * 6.0 * tN
    if (tN < 1.0 / 2.0) return q
    if (tN < 2.0 / 3.0) return p + (q - p) * (2.0 / 3.0 - tN) * 6.0
    return p
}
