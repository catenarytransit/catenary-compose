package com.catenarymaps.catenary

/**
 * Routing search prefers an OSM station over individual GTFS stops assigned to it.
 * Only an exact Birch osm_station_id match is safe: nearby stops may be distinct
 * entrances, platforms or services, even when their names and coordinates match.
 * The general search overlay deliberately retains the individual GTFS stops.
 */
internal fun filterRoutingSearchRows(rows: List<SearchRow>): List<SearchRow> {
    val visibleStationIds = rows.asSequence()
        .filterIsInstance<SearchRow.OsmStationRow>()
        .map { it.result.osmId.toString() }
        .toSet()
    if (visibleStationIds.isEmpty()) return rows
    return rows.filterNot { row ->
        row is SearchRow.StopRow &&
                row.stopInfo.osmStationId in visibleStationIds
    }
}
