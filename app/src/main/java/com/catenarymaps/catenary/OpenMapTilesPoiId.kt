package com.catenarymaps.catenary

internal data class OsmPoiRef(val osmClass: String, val osmId: String)

/**
 * Decode the vector tile feature ID from Catenary's `openmaptiles` / `poi` layer.
 *
 * This is NOT the numeric OSM object ID. The IDs in our current POI tiles are
 * 10 * osmId + type digit, where 1 = node, 2 = way, 3 = relation. For example:
 *
 *  35134799741 -> node/3513479974 (Blaze Pizza)
 *  67762119591 -> node/6776211959 (Dunkin')
 *
 * The upstream OpenMapTiles SQL generator may use a different type-digit
 * convention; this decoder is intentionally specific to our deployed tiles.
 * Never guess an OSM ID by removing a digit from an arbitrary vector feature.
 * Use integer arithmetic, not Double (feature IDs are 64-bit integers).
 */
internal fun decodeOpenMapTilesPoiId(tileFeatureId: String?): OsmPoiRef? {
    val encoded = tileFeatureId?.toULongOrNull() ?: return null
    val osmId = encoded / 10UL
    if (osmId == 0UL) return null

    val osmClass = when (encoded % 10UL) {
        1UL -> "node"
        2UL -> "way"
        3UL -> "relation"
        else -> return null
    }

    return OsmPoiRef(osmClass = osmClass, osmId = osmId.toString())
}
