package com.catenarymaps.catenary

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// --- Nominatim API Response ---

@Serializable
data class NominatimResult(
    val name: String,
    @SerialName("addresstype") val addressType: String,
    @SerialName("display_name") val displayName: String,
    val lat: String,
    val lon: String,
    @SerialName("osm_id") val osmId: Long,
    @SerialName("osm_type") val osmType: String,
    //@SerialName("category") val category: String,
    @SerialName("place_id") val placeId: Long,
    @SerialName("boundingbox") val boundingBox: List<String>? = null
)

// --- Catenary Text Search API Response ---

@Serializable
data class CatenarySearchResponse(
    @SerialName("stops_section") val stopsSection: StopsSection
)

@Serializable
data class StopsSection(
    val stops: Map<String, Map<String, StopInfo>>,
    val routes: Map<String, Map<String, RouteInfo>>,
    val ranking: List<StopRanking>
)

@Serializable
data class StopRanking(
    @SerialName("gtfs_id") val gtfsId: String,
    val chateau: String,
    val score: Double
)

@Serializable
data class StopInfo(
    val name: String,
    val code: String? = null,
    val point: StopPoint,
    val routes: List<String>,
    @SerialName("parent_station") val parentStation: String? = null
)

@Serializable
data class StopPoint(
    val x: Double, // Longitude
    val y: Double  // Latitude
)

@Serializable
data class RouteInfo(
    @SerialName("short_name") val shortName: String? = null,
    @SerialName("long_name") val longName: String? = null,
    val color: String,
    @SerialName("text_color") val textColor: String
)