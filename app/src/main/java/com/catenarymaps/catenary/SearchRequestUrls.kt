package com.catenarymaps.catenary

import io.ktor.http.URLBuilder

// Build query parameters through Ktor so user-entered text cannot become URL syntax.
internal fun buildCypressSearchUrl(
    query: String,
    focusLatitude: Double,
    focusLongitude: Double,
): String = URLBuilder("https://cypress.catenarymaps.org/v2/search").apply {
    parameters.append("text", query)
    parameters.append("focus.point.lat", focusLatitude.toString())
    parameters.append("focus.point.lon", focusLongitude.toString())
    parameters.append("focus.point.weight", "4")
}.buildString()

internal fun buildCatenarySearchUrl(
    query: String,
    mapLatitude: Double,
    mapLongitude: Double,
    userLocation: Pair<Double, Double>?,
): String = URLBuilder("https://birch.catenarymaps.org/text_search_v1").apply {
    parameters.append("text", query)
    parameters.append("map_lat", mapLatitude.toString())
    parameters.append("map_lon", mapLongitude.toString())
    parameters.append("map_z", "10")
    userLocation?.let { (latitude, longitude) ->
        parameters.append("user_lat", latitude.toString())
        parameters.append("user_lon", longitude.toString())
    }
}.buildString()
