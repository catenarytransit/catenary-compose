package com.catenarymaps.catenary

// Wrapper matching the TS `StackInterface`
data class StackInterface(val data: CatenaryStackEnum)

// All stack variants the wrapper can hold
sealed interface CatenaryStackEnum {
    data class SingleTrip(
        val chateau_id: String,
        val trip_id: String?,
        val route_id: String?,
        val start_time: String?,
        val start_date: String?,
        val vehicle_id: String?,
        val route_type: Int?
    ) : CatenaryStackEnum

    data class VehicleSelectedStack(
        val chateau_id: String,
        val vehicle_id: String?,
        val gtfs_id: String
    ) : CatenaryStackEnum

    data class RouteStack(
        val chateau_id: String,
        val route_id: String
    ) : CatenaryStackEnum

    data class StopStack(
        val chateau_id: String,
        val stop_id: String
    ) : CatenaryStackEnum

    data class NearbyDeparturesStack(
        val chateau_id: String,
        val lat: Double,
        val lon: Double
    ) : CatenaryStackEnum

    data class MapSelectionScreen(
        val arrayofoptions: List<MapSelectionOption>
    ) : CatenaryStackEnum

    data class SettingsStack(
        val hi: Boolean? = true
    ) : CatenaryStackEnum

    data class BlockStack(
        val chateau_id: String,
        val block_id: String,
        val service_date: String
    ) : CatenaryStackEnum

    data class OsmItemStack(
        val osm_id: String,
        val osm_class: String,
        val osm_type: String?
    ) : CatenaryStackEnum
}

// Map selection option + its union of selector types
data class MapSelectionOption(val data: MapSelectionSelector)

sealed interface MapSelectionSelector {
    data class StopMapSelector(
        val chateau_id: String,
        val stop_id: String,
        val stop_name: String
    ) : MapSelectionSelector

    data class RouteMapSelector(
        val chateau_id: String,
        val route_id: String,
        val colour: String,
        val name: String?
    ) : MapSelectionSelector

    data class VehicleMapSelector(
        val chateau_id: String,
        val vehicle_id: String?,
        val route_id: String?,
        val headsign: String,
        val triplabel: String,
        val colour: String,
        val route_short_name: String?,
        val route_long_name: String?,
        val route_type: Int,
        val trip_short_name: String?,
        val text_colour: String,
        val gtfs_id: String,
        val trip_id: String?,
        val start_time: String?,
        val start_date: String?
    ) : MapSelectionSelector
}