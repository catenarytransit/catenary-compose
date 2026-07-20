package com.catenarymaps.catenary

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

data class CondensedAlertSchedule(
    val isCondensed: Boolean,
    val baseRule: String,
    val weekdayRules: String,
    val exceptions: String,
    val fallbackPeriods: List<AlertActivePeriod>
)

fun condenseActivePeriods(
    periods: List<AlertActivePeriod>,
    locale: Locale = Locale.CANADA,
    defaultTz: String? = null
): CondensedAlertSchedule {
    if (periods.isEmpty()) return fallbackSchedule(emptyList())

    val completePeriods = periods.mapNotNull { period ->
        val start = period.start
        val end = period.end
        if (start == null || end == null) null else CompletePeriod(start, end)
    }

    if (completePeriods.size != periods.size || completePeriods.size <= 3) {
        return fallbackSchedule(periods)
    }

    val zoneId = resolveZoneId(defaultTz)
    val language = locale.language.lowercase(Locale.ROOT)
    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.UK)

    val nights = completePeriods.map { period ->
        val start = Instant.ofEpochSecond(period.start).atZone(zoneId)
        val end = Instant.ofEpochSecond(period.end).atZone(zoneId)
        val startWeekday = formatWeekday(start, locale)
        val endWeekday = formatWeekday(end, locale)

        Night(
            originalStart = start,
            startEpochSecond = period.start,
            startTime = timeFormatter.format(start),
            endTime = timeFormatter.format(end),
            weekdayPair = if (start.toLocalDate() != end.toLocalDate()) {
                "$startWeekday/$endWeekday"
            } else {
                startWeekday
            },
            label = formatNightLabel(start, end, locale, language)
        )
    }.sortedBy { it.startEpochSecond }

    val patternCounts = linkedMapOf<String, Int>()
    nights.forEach { night ->
        val key = "${night.startTime}|${night.endTime}"
        patternCounts[key] = (patternCounts[key] ?: 0) + 1
    }

    val bestPattern = patternCounts.maxByOrNull { it.value }!!.key
    val baseStart = bestPattern.substringBefore('|')
    val baseEnd = bestPattern.substringAfter('|')
    val deviations = nights.filterNot { it.startTime == baseStart && it.endTime == baseEnd }

    val groupedDeviations = linkedMapOf<String, MutableList<Night>>()
    deviations.forEach { night ->
        val key = "${night.weekdayPair}|${night.startTime}|${night.endTime}"
        groupedDeviations.getOrPut(key) { mutableListOf() }.add(night)
    }

    val weekdayRules = mutableListOf<String>()
    val exceptions = mutableListOf<Night>()

    groupedDeviations.values.forEach { group ->
        if (group.size >= 2) {
            val sample = group.first()
            val start = replaceTimeSeparator(sample.startTime, language)
            val end = replaceTimeSeparator(sample.endTime, language)

            weekdayRules += if (sample.startTime == baseStart) {
                when (language) {
                    "de" -> "${sample.weekdayPair} bis $end Uhr"
                    "fr" -> "${sample.weekdayPair} jusqu’à $end"
                    "it" -> "${sample.weekdayPair} fino alle $end"
                    else -> "${sample.weekdayPair} until $end"
                }
            } else {
                when (language) {
                    "de" -> "${sample.weekdayPair} $start–$end Uhr"
                    "fr" -> "${sample.weekdayPair} de $start à $end"
                    else -> "${sample.weekdayPair} $start–$end"
                }
            }
        } else {
            exceptions += group.first()
        }
    }

    val exceptionText = exceptions.map { exception ->
        val start = replaceTimeSeparator(exception.startTime, language)
        val end = replaceTimeSeparator(exception.endTime, language)
        when (language) {
            "fr" -> "${exception.label}, de $start à $end"
            "de" -> "${exception.label}, $start–$end Uhr"
            else -> "${exception.label}, $start–$end"
        }
    }

    val rulesSeparator = if (language == "de" || language == "fr" || language == "it") {
        ", "
    } else {
        "; "
    }
    val rules = weekdayRules.joinToString(rulesSeparator)

    val firstLabel = nights.first().label
    val lastLabel = nights.last().label
    val start = replaceTimeSeparator(baseStart, language)
    val end = replaceTimeSeparator(baseEnd, language)
    val firstYear = nights.first().originalStart.year
    val lastYear = nights.last().originalStart.year
    val currentYear = ZonedDateTime.now(zoneId).year
    val showYear = firstYear != lastYear || lastYear != currentYear
    val yearSuffix = if (showYear) " $lastYear" else ""
    val commaYearSuffix = if (showYear) ", $lastYear" else ""

    val baseRule: String
    val exceptionsRule: String
    when (language) {
        "de" -> {
            baseRule = "Nächte $firstLabel–$lastLabel$yearSuffix, jeweils $start–$end Uhr"
            exceptionsRule = if (exceptionText.isEmpty()) "" else "Ausnahmen: ${exceptionText.joinToString("; ")}"
        }
        "fr" -> {
            baseRule = "Nuits du $firstLabel au $lastLabel$yearSuffix, de $start à $end"
            exceptionsRule = if (exceptionText.isEmpty()) "" else "Exceptions : ${exceptionText.joinToString("; ")}"
        }
        "it" -> {
            baseRule = "Notti dal $firstLabel all’$lastLabel$yearSuffix, $start–$end"
            exceptionsRule = if (exceptionText.isEmpty()) "" else "Eccezioni: ${exceptionText.joinToString("; ")}"
        }
        else -> {
            baseRule = "Nights $firstLabel–$lastLabel$commaYearSuffix, $start–$end"
            exceptionsRule = if (exceptionText.isEmpty()) "" else "Exceptions: ${exceptionText.joinToString("; ")}"
        }
    }

    return CondensedAlertSchedule(
        isCondensed = true,
        baseRule = baseRule,
        weekdayRules = rules,
        exceptions = exceptionsRule,
        fallbackPeriods = emptyList()
    )
}

private data class CompletePeriod(
    val start: Long,
    val end: Long
)

private data class Night(
    val originalStart: ZonedDateTime,
    val startEpochSecond: Long,
    val startTime: String,
    val endTime: String,
    val weekdayPair: String,
    val label: String
)

private fun fallbackSchedule(periods: List<AlertActivePeriod>) = CondensedAlertSchedule(
    isCondensed = false,
    baseRule = "",
    weekdayRules = "",
    exceptions = "",
    fallbackPeriods = periods
)

private fun resolveZoneId(defaultTz: String?): ZoneId {
    if (defaultTz.isNullOrBlank()) return ZoneId.systemDefault()
    return runCatching { ZoneId.of(defaultTz) }.getOrElse { ZoneId.systemDefault() }
}

private fun formatWeekday(date: ZonedDateTime, locale: Locale): String {
    val weekday = date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale).removeSuffix(".")
    return weekday.take(1).uppercase(locale) + weekday.drop(1)
}

private fun replaceTimeSeparator(value: String, language: String): String = when (language) {
    "de", "it" -> value.replaceFirst(":", ".")
    "fr" -> value.replaceFirst(":", "h")
    else -> value
}

private fun formatNightLabel(
    start: ZonedDateTime,
    end: ZonedDateTime,
    locale: Locale,
    language: String
): String {
    val sameDay = start.toLocalDate() == end.toLocalDate()
    return when (language) {
        "de" -> {
            val endDate = "%02d.%02d.".format(locale, end.dayOfMonth, end.monthValue)
            if (sameDay) {
                endDate
            } else {
                "%02d./%s".format(locale, start.dayOfMonth, endDate)
            }
        }
        "fr", "it" -> {
            val month = start.month.getDisplayName(TextStyle.FULL, locale).lowercase(locale)
            if (sameDay) {
                "${start.dayOfMonth} $month"
            } else {
                "${start.dayOfMonth}/${end.dayOfMonth} $month"
            }
        }
        else -> {
            val month = start.month.getDisplayName(TextStyle.SHORT, locale)
            if (sameDay) {
                "$month ${start.dayOfMonth}"
            } else {
                "$month ${start.dayOfMonth}/${end.dayOfMonth}"
            }
        }
    }
}
