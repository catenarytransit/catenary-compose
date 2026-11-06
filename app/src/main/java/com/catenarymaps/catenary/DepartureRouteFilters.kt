package com.catenarymaps.catenary

import java.util.Locale

internal data class DepartureRouteFilterKey(val chateau: String, val name: String, val color: String)

internal data class DepartureRouteFilter(
    val key: DepartureRouteFilterKey,
    val routeIds: Set<String>,
    val route: StopRouteInfo
)

// Compare digit runs by length and then text, avoiding integer overflow and pair-dependent
// numeric/lexical comparisons (which can violate the sorting comparator contract).
private val routeNameRuns = Regex("[0-9]+|[^0-9]+")

internal val departureRouteNameComparator = Comparator<String> { left, right ->
    val a = routeNameRuns.findAll(left).map { it.value }.toList()
    val b = routeNameRuns.findAll(right).map { it.value }.toList()
    var result = 0
    for (i in 0 until minOf(a.size, b.size)) {
        val x = a[i]
        val y = b[i]
        val xn = x.first() in '0'..'9'
        val yn = y.first() in '0'..'9'
        result = when {
            xn && yn -> {
                val nx = x.trimStart('0').ifEmpty { "0" }
                val ny = y.trimStart('0').ifEmpty { "0" }
                nx.length.compareTo(ny.length).takeIf { it != 0 } ?: nx.compareTo(ny)
            }
            xn != yn -> if (xn) -1 else 1
            else -> x.compareTo(y, ignoreCase = true)
        }
        if (result != 0) break
    }
    if (result == 0) result = a.size.compareTo(b.size)
    if (result == 0) result = left.compareTo(right)
    result
}

internal fun departureRouteFilters(routes: Map<String, Map<String, StopRouteInfo>>): List<DepartureRouteFilter> =
    routes.flatMap { (chateau, definitions) ->
        definitions.entries.groupBy { (id, route) ->
            DepartureRouteFilterKey(
                chateau,
                route.short_name?.takeIf { it.isNotBlank() }
                    ?: route.long_name?.takeIf { it.isNotBlank() } ?: id,
                route.color.trim().removePrefix("#").uppercase(Locale.ROOT)
            )
        }.map { (key, entries) ->
            DepartureRouteFilter(key, entries.map { it.key }.toSet(), entries.first().value)
        }
    }.sortedWith(compareBy(departureRouteNameComparator) { filter: DepartureRouteFilter -> filter.key.name }
        .thenBy { it.key.chateau }.thenBy { it.key.color })

internal fun matchesDepartureRouteFilters(
    chateau: String,
    routeId: String,
    selected: Set<DepartureRouteFilterKey>,
    filters: List<DepartureRouteFilter>
): Boolean = selected.isEmpty() || filters.any {
    it.key in selected && it.key.chateau == chateau && routeId in it.routeIds
}

internal fun departureRouteMode(routeType: Int): String = when (routeType) {
    3, 11, 700 -> "bus"
    0, 1, 5, 7, 12, 900 -> "metro"
    2, 106, 107, 101, 100, 102, 103 -> "rail"
    else -> "other"
}
