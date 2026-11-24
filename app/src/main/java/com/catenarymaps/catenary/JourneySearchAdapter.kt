package com.catenarymaps.catenary

fun SearchRow.directionsLocation(): DirectionsLocation? = when (this) {
    is SearchRow.CypressRow -> item.geometry.coordinates.takeIf { it.size >= 2 }?.let {
        DirectionsLocation(item.properties.name, it[1], it[0])
    }

    is SearchRow.StopRow -> DirectionsLocation(stopInfo.name, stopInfo.point.y, stopInfo.point.x)
    is SearchRow.OsmStationRow -> result.point?.let {
        DirectionsLocation(
            result.name ?: "Station",
            it.y,
            it.x
        )
    }

    is SearchRow.RouteRow -> null
}?.takeIf { it.isValid() }
