package com.catenarymaps.catenary

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

// --- Cypress API Response ---

@Serializable data class CypressResponse(val features: List<CypressFeature>)

@Serializable
data class CypressFeature(
        val type: String,
        val geometry: CypressGeometry,
        val properties: CypressPropertiesV2
)

@Serializable data class CypressGeometry(val type: String, val coordinates: List<Double>)

@Serializable
data class CypressPropertiesV2(
        val id: String,
        val layer: String,
        val name: String,
        val names: Map<String, String> = emptyMap(),
        val housenumber: String? = null,
        val street: String? = null,
        val postcode: String? = null,
        val country: String? = null,
        val country_names: Map<String, String>? = null,
        val region: String? = null,
        val region_names: Map<String, String>? = null,
        val county: String? = null,
        val county_names: Map<String, String>? = null,
        val locality: String? = null,
        val locality_names: Map<String, String>? = null,
        val neighbourhood: String? = null,
        val neighbourhood_names: Map<String, String>? = null,
        val categories: List<String> = emptyList(),
        val confidence: Double
)

// --- Catenary Text Search API Response ---

@Serializable
data class CatenarySearchResponse(
        @SerialName("stops_section") val stopsSection: StopsSection,
        @SerialName("routes_section") val routesSection: RoutesSection
)

@Serializable
data class StopsSection(
        val stops: Map<String, Map<String, StopInfo>>,
        val routes: Map<String, Map<String, RouteInfo>>,
        val agencies: Map<String, Map<String, Agency>>,
        val ranking: List<StopRanking>
)

@Serializable
data class StopRanking(
        @SerialName("gtfs_id") val gtfsId: String,
        val chateau: String,
        val score: Double
)

@Serializable
data class RoutesSection(
        val routes: Map<String, Map<String, RouteInfo>>,
        val agencies: Map<String, Map<String, Agency>>,
        val ranking: List<RouteRanking>
)

@Serializable
data class RouteRanking(
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
        @SerialName("parent_station") val parentStation: String? = null,
)

@Serializable
data class StopPoint(
        val x: Double, // Longitude
        val y: Double // Latitude
)

@Serializable
data class RouteInfo(
        @SerialName("short_name") val shortName: String? = null,
        @SerialName("long_name") val longName: String? = null,
        val color: String,
        @SerialName("text_color") val textColor: String,
        @SerialName("agency_id") val agencyId: String? = null,
        @SerialName("route_id") val routeId: String,
        @SerialName("route_type") val routeType: Int,
        @SerialName("chateau") val chateau: String,
)

@Serializable
data class Agency(
        @SerialName("static_onestop_id") val staticOnestopId: String,
        @SerialName("agency_id") val agencyId: String,
        @SerialName("attempt_id") val attemptId: String,
        @SerialName("agency_name") val agencyName: String,
        @SerialName("agency_name_translations")
        val agencyNameTranslations: Map<String, String>? = null,
        @SerialName("agency_url") val agencyUrl: String,
        @SerialName("agency_url_translations")
        val agencyUrlTranslations: Map<String, String>? = null,
        @SerialName("agency_timezone") val agencyTimezone: String,
        @SerialName("agency_lang") val agencyLang: String? = null,
        @SerialName("agency_phone") val agencyPhone: String? = null,
        @SerialName("agency_fare_url") val agencyFareUrl: String? = null,
        @SerialName("agency_fare_url_translations")
        val agencyFareUrlTranslations: Map<String, String>? = null,
        val chateau: String
)

// --- OSM Station Search API Response ---

@Serializable
data class OsmStationSearchResponse(val results: List<OsmStationSearchResult>)

@Serializable
data class OsmStationSearchResult(
        @SerialName("osm_id") val osmId: Long,
        val name: String? = null,
        val point: OsmStationPoint? = null,
        @SerialName("mode_type") val modeType: String,
        val operator: String? = null,
        val network: String? = null,
        @SerialName("admin_hierarchy") val adminHierarchy: AdminHierarchy? = null,
        val routes: List<RouteInfo> = emptyList(),
        val confidence: Double
)

@Serializable
data class OsmStationPoint(val x: Double, val y: Double)

@Serializable
data class AdminHierarchy(
        val country: AdminArea? = null,
        val neighbourhood: AdminArea? = null,
        val county: AdminArea? = null,
        val region: AdminArea? = null,
        // Keep room for additional admin levels without strict typing
        val raw: JsonObject? = null
)

@Serializable
data class AdminArea(val name: String? = null)

