@file:Suppress("NOTHING_TO_INLINE")

package com.catenarymaps.catenary

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import org.maplibre.spatialk.geojson.*

/** Axis-aligned rectangle in lon/lat space (WGS84). */
data class Rect(
    val minX: Double,
    val minY: Double,
    val maxX: Double,
    val maxY: Double,
) {
    init {
        require(minX <= maxX && minY <= maxY) { "Invalid rect" }
    }

    inline fun width() = maxX - minX
    inline fun height() = maxY - minY

    fun expandToInclude(other: Rect): Rect = Rect(
        minOf(minX, other.minX),
        minOf(minY, other.minY),
        maxOf(maxX, other.maxX),
        maxOf(maxY, other.maxY)
    )

    fun intersects(other: Rect): Boolean =
        other.minX <= maxX && other.maxX >= minX &&
                other.minY <= maxY && other.maxY >= minY

    /**
     * True when rectangles "touch" but do not overlap in area (edge or corner touch).
     * For many GIS workflows this approximates ST_Touches for envelopes.
     */
    fun touches(other: Rect): Boolean {
        if (!intersects(other)) return false
        val overlapX = minOf(maxX, other.maxX) - maxOf(minX, other.minX)
        val overlapY = minOf(maxY, other.maxY) - maxOf(minY, other.minY)
        // If both overlaps are positive we have area overlap (not a pure touch)
        if (overlapX > 0.0 && overlapY > 0.0) return false
        // At least one dimension has zero overlap => edge/corner touch
        return true
    }

    fun toBoundingBox(): BoundingBox = BoundingBox(minX, minY, maxX, maxY)

    companion object {
        fun from(bbox: BoundingBox): Rect = Rect(
            bbox.west, bbox.south, bbox.east, bbox.north
        )
    }
}

/** Leaf entry carrying a payload. */
data class Entry<T>(val rect: Rect, val value: T)

/** Simple packed R-Tree (STR) suitable for read-mostly workloads. */
class RTree<T> private constructor(
    private val maxEntries: Int,
    private val root: Node<T>
) {
    sealed class Node<T>(open val mbr: Rect) {
        data class Leaf<T>(override val mbr: Rect, val entries: List<Entry<T>>) : Node<T>(mbr)
        data class Branch<T>(override val mbr: Rect, val children: List<Node<T>>) : Node<T>(mbr)
    }

    /** Query all values whose rectangles intersect [query]. */
    fun searchIntersect(query: Rect): List<T> {
        val out = ArrayList<T>()
        fun visit(n: Node<T>) {
            if (!n.mbr.intersects(query)) return
            when (n) {
                is Node.Leaf -> n.entries.forEach { if (it.rect.intersects(query)) out += it.value }
                is Node.Branch -> n.children.forEach(::visit)
            }
        }
        visit(root)
        return out
    }

    /** Query all values whose rectangles *touch* [query] without area overlap. */
    fun searchTouch(query: Rect): List<T> {
        val out = ArrayList<T>()
        fun visit(n: Node<T>) {
            if (!n.mbr.intersects(query)) return
            when (n) {
                is Node.Leaf -> n.entries.forEach { if (it.rect.touches(query)) out += it.value }
                is Node.Branch -> n.children.forEach(::visit)
            }
        }
        visit(root)
        return out
    }

    companion object {
        /** Build a packed STR R-Tree from [entries]. */
        fun <T> pack(entries: List<Entry<T>>, maxEntries: Int = 8): RTree<T> {
            require(maxEntries >= 4) { "maxEntries should be >= 4 for decent packing" }
            if (entries.isEmpty()) {
                val emptyLeaf: Node.Leaf<T> = Node.Leaf(Rect(0.0, 0.0, 0.0, 0.0), emptyList<Entry<T>>())
                return RTree(maxEntries, emptyLeaf)
            }
            fun buildLeafGroups(items: List<Entry<T>>): List<Node<T>> {
                val M = maxEntries
                val m = kotlin.math.ceil(items.size / M.toDouble()).toInt() // groups
                val s = kotlin.math.ceil(kotlin.math.sqrt(m.toDouble())).toInt().coerceAtLeast(1) // slices
                val sliceSize = s * M
                val byX = items.sortedBy { it.rect.minX }
                val nodes = ArrayList<Node<T>>()
                var i = 0
                while (i < byX.size) {
                    val slice = byX.subList(i, minOf(i + sliceSize, byX.size)).sortedBy { it.rect.minY }
                    var j = 0
                    while (j < slice.size) {
                        val group = slice.subList(j, minOf(j + M, slice.size))
                        var mbr = group.first().rect
                        for (k in 1 until group.size) mbr = mbr.expandToInclude(group[k].rect)
                        nodes += Node.Leaf(mbr, group)
                        j += M
                    }
                    i += sliceSize
                }
                return nodes
            }
            fun buildUpperLevel(children: List<Node<T>>): Node<T> {
                if (children.size <= maxEntries) {
                    var mbr = children.first().mbr
                    for (k in 1 until children.size) mbr = mbr.expandToInclude(children[k].mbr)
                    return Node.Branch(mbr, children)
                }
                // pack children just like leaves, using their MBRs
                val M = maxEntries
                val m = kotlin.math.ceil(children.size / M.toDouble()).toInt()
                val s = kotlin.math.ceil(kotlin.math.sqrt(m.toDouble())).toInt().coerceAtLeast(1)
                val sliceSize = s * M
                val byX = children.sortedBy { it.mbr.minX }
                val parentNodes = ArrayList<Node<T>>()
                var i = 0
                while (i < byX.size) {
                    val slice = byX.subList(i, minOf(i + sliceSize, byX.size)).sortedBy { it.mbr.minY }
                    var j = 0
                    while (j < slice.size) {
                        val group = slice.subList(j, minOf(j + M, slice.size))
                        var mbr = group.first().mbr
                        for (k in 1 until group.size) mbr = mbr.expandToInclude(group[k].mbr)
                        parentNodes += Node.Branch(mbr, group)
                        j += M
                    }
                    i += sliceSize
                }
                return buildUpperLevel(parentNodes)
            }

            val leaves = buildLeafGroups(entries)
            val root = if (leaves.size == 1) leaves.first() else buildUpperLevel(leaves)
            return RTree(maxEntries, root)
        }
    }
}

// ---------------------------- GeoJSON helpers ----------------------------

/** Compute a BoundingBox for any supported Geometry if absent. */
private fun computeBBox(geom: Geometry): BoundingBox {
    fun fromPositions(positions: Iterable<Position>): BoundingBox {
        var minX = Double.POSITIVE_INFINITY
        var minY = Double.POSITIVE_INFINITY
        var maxX = Double.NEGATIVE_INFINITY
        var maxY = Double.NEGATIVE_INFINITY
        positions.forEach { p ->
            val x = p.longitude
            val y = p.latitude
            if (x < minX) minX = x
            if (y < minY) minY = y
            if (x > maxX) maxX = x
            if (y > maxY) maxY = y
        }
        return BoundingBox(minX, minY, maxX, maxY)
    }
    return when (geom) {
        is Point -> geom.bbox ?: BoundingBox(geom.longitude, geom.latitude, geom.longitude, geom.latitude)
        is MultiPoint -> geom.bbox ?: fromPositions(geom.coordinates)
        is LineString -> geom.bbox ?: fromPositions(geom.coordinates)
        is MultiLineString -> geom.bbox ?: fromPositions(geom.coordinates.flatten())
        is Polygon -> geom.bbox ?: fromPositions(geom.coordinates.flatten())
        is MultiPolygon -> geom.bbox ?: fromPositions(geom.coordinates.flatten().flatten())
        is GeometryCollection<*> -> {
            val boxes = geom.geometries.map { g -> (g as Geometry).bbox ?: computeBBox(g as Geometry) }
            var rect = Rect.from(boxes.first())
            for (i in 1 until boxes.size) rect = rect.expandToInclude(Rect.from(boxes[i]))
            rect.toBoundingBox()
        }
        else -> error("Unsupported geometry type: $geom")
    }
}

/** Convert a Feature's geometry to an envelope Rect (falls back to Feature.bbox if present). */
private fun featureRect(f: Feature<Geometry?, *>): Rect {
    val bb: BoundingBox = f.geometry?.bbox ?: f.bbox ?: (f.geometry?.let { computeBBox(it) }
        ?: error("Feature has neither geometry nor bbox"))
    return Rect.from(bb)
}

// ---------------------------- Public API ----------------------------

/**
 * Build a packed RTree of GeoJSON Features from raw GeoJSON text.
 *
 * @param geoJson A FeatureCollection or any GeoJSON that decodes to a FeatureCollection.
 * @param propsDecoder Map raw JsonObject properties to your domain type [P]. Return null to skip a feature.
 * @param maxEntries Node capacity for the RTree (default 8).
 */
fun <P : Any> buildRTreeFromGeoJson(
    geoJson: String,
    propsDecoder: (JsonObject?) -> P?,
    maxEntries: Int = 8
): RTree<Feature<Geometry?, P>> {
    val parsed: GeoJsonObject = GeoJsonObject.Companion.fromJson(geoJson)
    val fc: FeatureCollection<Geometry?, JsonObject?> = when (parsed) {
        is FeatureCollection<*, *> -> parsed as FeatureCollection<Geometry?, JsonObject?>
        is Feature<*, *> -> FeatureCollection(listOf(parsed as Feature<Geometry?, JsonObject?>))
        is Geometry -> FeatureCollection(listOf(Feature(parsed, null)))
        else -> error("Unsupported GeoJSON input: ${parsed::class.simpleName}")
    }

    val typedFeatures = buildList {
        for (f in fc.features) {
            val p = propsDecoder(f.properties) ?: continue
            @Suppress("UNCHECKED_CAST")
            add(Feature(f.geometry, p, f.id, f.bbox) as Feature<Geometry?, P>)
        }
    }
    return buildRTree(FeatureCollection(typedFeatures), maxEntries)
}

/** Build an RTree directly from an in-memory [FeatureCollection]. */
fun <P : Any> buildRTree(
    features: FeatureCollection<out Geometry?, P>,
    maxEntries: Int = 8
): RTree<Feature<Geometry?, P>> {
    @Suppress("UNCHECKED_CAST")
    val entries = features.features.map { f ->
        val f2 = f as Feature<Geometry?, P>
        Entry(featureRect(f2 as Feature<Geometry?, *>), f2)
    }
    return RTree.pack(entries, maxEntries)
}

/** Query features that intersect (or just *touch*) a BoundingBox. */
fun <P : Any> queryTouching(
    index: RTree<Feature<Geometry?, P>>,
    bbox: BoundingBox,
    strictTouchOnly: Boolean = false
): List<Feature<Geometry?, P>> {
    val rect = Rect.from(bbox)
    return if (strictTouchOnly) index.searchTouch(rect) else index.searchIntersect(rect)
}

// ---------------------------- Small conveniences ----------------------------

/** Easier construction of BoundingBox from lon/lat pairs. */
fun bbox(west: Double, south: Double, east: Double, north: Double) = BoundingBox(west, south, east, north)

// PARALLEL

suspend fun <P : Any> buildRTreeParallel(
    features: FeatureCollection<out Geometry?, P>,
    maxEntries: Int = 8,
    chunkSize: Int = 2048
): RTree<Feature<Geometry?, P>> = withContext(Dispatchers.Default) {
    @Suppress("UNCHECKED_CAST")
    val fs = features.features as List<Feature<Geometry?, P>>

    val entries: List<Entry<Feature<Geometry?, P>>> =
        fs.asSequence()
            .chunked(chunkSize)            // coarse batches to reduce overhead
            .map { chunk ->
                async {
                    chunk.map { f -> Entry(rect = featureRect(f as Feature<Geometry?, *>), value = f) }
                }
            }
            .toList()
            .awaitAll()
            .flatten()

    RTree.pack(entries, maxEntries)
}