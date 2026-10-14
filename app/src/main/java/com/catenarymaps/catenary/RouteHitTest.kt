package com.catenarymaps.catenary

import kotlinx.serialization.Serializable

/** Parameters for the server-side shape hit test. Persisted with the selection stack. */
@Serializable
data class RouteClickLookup(
    val latitude: Double,
    val longitude: Double,
    val zoom: Double,
    val bus: Boolean,
    val localRail: Boolean,
    val intercityRail: Boolean,
    val other: Boolean
)

@Serializable
data class ServerRouteHit(
    val chateau: String,
    val route_id: String,
    val colour: String,
    val name: String,
    val route_type: Int
) {
    fun toSelectionOption() = MapSelectionOption(
        MapSelectionSelector.RouteMapSelector(
            chateau_id = chateau,
            route_id = route_id,
            colour = "#" + colour.removePrefix("#"),
            name = name,
            route_type = route_type
        )
    )
}
