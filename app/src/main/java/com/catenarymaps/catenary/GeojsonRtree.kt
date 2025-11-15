@file:Suppress("NOTHING_TO_INLINE")

package com.catenarymaps.catenary

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer
import org.maplibre.spatialk.geojson.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.descriptors.listSerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure
import kotlinx.serialization.serializer
import kotlinx.serialization.descriptors.PrimitiveKind

// ---------------------------- Core geometry types ----------------------------

/** Axis-aligned rectangle in lon/lat space (WGS84). */
@Serializable
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

    /** Edge/corner touch (no positive-area overlap). */
    fun touches(other: Rect): Boolean {
        if (!intersects(other)) return false
        val overlapX = minOf(maxX, other.maxX) - maxOf(minX, other.minX)
        val overlapY = minOf(maxY, other.maxY) - maxOf(minY, other.minY)
        if (overlapX > 0.0 && overlapY > 0.0) return false
        return true
    }

    fun toBoundingBox(): BoundingBox = BoundingBox(minX, minY, maxX, maxY)

    companion object {
        fun from(bbox: BoundingBox): Rect = Rect(bbox.west, bbox.south, bbox.east, bbox.north)
    }
}

/** Leaf entry carrying a payload. */
@Serializable
data class Entry<T>(
    val rect: Rect,
    val value: T
)

// ---------------------------- RTree ----------------------------

/** Simple packed R-Tree (STR) suitable for read-mostly workloads. */
class RTree<T> internal constructor(
    internal val maxEntries: Int,
    internal val root: Node<T>
) {
    @Serializable
    sealed class Node<T> {
        abstract val mbr: Rect

        @Serializable
        @SerialName("leaf")
        data class Leaf<T>(
            override val mbr: Rect,
            val entries: List<Entry<T>>
        ) : Node<T>()

        @Serializable
        @SerialName("branch")
        data class Branch<T>(
            override val mbr: Rect,
            val children: List<Node<T>>
        ) : Node<T>()
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
                val emptyLeaf: Node.Leaf<T> = Node.Leaf(Rect(0.0, 0.0, 0.0, 0.0), emptyList())
                return RTree(maxEntries, emptyLeaf)
            }
            fun buildLeafGroups(items: List<Entry<T>>): List<Node<T>> {
                val M = maxEntries
                val m = kotlin.math.ceil(items.size / M.toDouble()).toInt()
                val s = kotlin.math.ceil(kotlin.math.sqrt(m.toDouble())).toInt().coerceAtLeast(1)
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

// ---------------------------- Serialization helpers for RTree (manual, no polymorphic surprises) ----------------------------


/** Serializer for Entry<T> that uses the concrete T serializer explicitly. */
fun <T> entrySerializer(tSer: KSerializer<T>): KSerializer<Entry<T>> =
    object : KSerializer<Entry<T>> {
        override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Entry") {
            element("rect", Rect.serializer().descriptor)
            element("value", tSer.descriptor)
        }

        override fun serialize(encoder: Encoder, value: Entry<T>) {
            encoder.encodeStructure(descriptor) {
                encodeSerializableElement(descriptor, 0, Rect.serializer(), value.rect)
                encodeSerializableElement(descriptor, 1, tSer, value.value)
            }
        }

        override fun deserialize(decoder: Decoder): Entry<T> =
            decoder.decodeStructure(descriptor) {
                var rect: Rect? = null
                var v: T? = null
                loop@ while (true) {
                    when (val i = decodeElementIndex(descriptor)) {
                        0 -> rect = decodeSerializableElement(descriptor, 0, Rect.serializer())
                        1 -> v = decodeSerializableElement(descriptor, 1, tSer)
                        CompositeDecoder.DECODE_DONE -> break@loop
                        else -> throw SerializationException("Unexpected index: $i")
                    }
                }
                Entry(rect ?: error("rect missing"), v ?: error("value missing"))
            }
    }

/** Serializer for RTree.Node<T> (recursive, tagged with "kind":"leaf"|"branch"). */
@OptIn(InternalSerializationApi::class)
fun <T> nodeSerializer(tSer: KSerializer<T>): KSerializer<RTree.Node<T>> {
    val entrySer = entrySerializer(tSer)

    // Descriptor cannot reference self-serializer; use a neutral list descriptor for "children".
    val nodeDesc: SerialDescriptor = buildClassSerialDescriptor("RTree.Node") {
        element("kind", String.serializer().descriptor)           // <-- simple & version-safe
        element("mbr", Rect.serializer().descriptor)
        element("entries", listSerialDescriptor(entrySer.descriptor), isOptional = true)
        element(
            "children",
            buildSerialDescriptor("kotlin.collections.List", StructureKind.LIST) { },
            isOptional = true
        )
    }

    // Now define the actual serializer, wiring recursion only at encode/decode time.
    lateinit var selfSer: KSerializer<RTree.Node<T>>
    val childrenSer by lazy { ListSerializer(selfSer) }

    selfSer = object : KSerializer<RTree.Node<T>> {
        override val descriptor: SerialDescriptor = nodeDesc

        override fun serialize(encoder: Encoder, value: RTree.Node<T>) {
            encoder.encodeStructure(descriptor) {
                when (value) {
                    is RTree.Node.Leaf -> {
                        encodeStringElement(descriptor, 0, "leaf")
                        encodeSerializableElement(descriptor, 1, Rect.serializer(), value.mbr)
                        encodeSerializableElement(descriptor, 2, ListSerializer(entrySer), value.entries)
                    }
                    is RTree.Node.Branch -> {
                        encodeStringElement(descriptor, 0, "branch")
                        encodeSerializableElement(descriptor, 1, Rect.serializer(), value.mbr)
                        encodeSerializableElement(descriptor, 3, childrenSer, value.children)
                    }
                }
            }
        }

        override fun deserialize(decoder: Decoder): RTree.Node<T> =
            decoder.decodeStructure(descriptor) {
                var kind: String? = null
                var mbr: Rect? = null
                var entries: List<Entry<T>>? = null
                var children: List<RTree.Node<T>>? = null
                loop@ while (true) {
                    when (val i = decodeElementIndex(descriptor)) {
                        0 -> kind = decodeStringElement(descriptor, 0)
                        1 -> mbr = decodeSerializableElement(descriptor, 1, Rect.serializer())
                        2 -> entries = decodeSerializableElement(descriptor, 2, ListSerializer(entrySer))
                        3 -> children = decodeSerializableElement(descriptor, 3, childrenSer)
                        CompositeDecoder.DECODE_DONE -> break@loop
                        else -> throw SerializationException("Unexpected index: $i")
                    }
                }
                when (kind) {
                    "leaf" -> RTree.Node.Leaf(
                        mbr ?: error("mbr missing"),
                        entries ?: error("entries missing")
                    )
                    "branch" -> RTree.Node.Branch(
                        mbr ?: error("mbr missing"),
                        children ?: error("children missing")
                    )
                    else -> error("Unknown kind: $kind")
                }
            }
    }
    return selfSer
}

/** Custom serializer for RTree<T> that uses the explicit Node/Entry serializers above. */
fun <T> rtreeSerializer(tSer: KSerializer<T>): KSerializer<RTree<T>> =
    object : KSerializer<RTree<T>> {
        private val nodeSer = nodeSerializer(tSer)
        override val descriptor: SerialDescriptor = buildClassSerialDescriptor("RTree") {
            element("maxEntries", Int.serializer().descriptor)
            element("root", nodeSer.descriptor)
        }

        override fun serialize(encoder: Encoder, value: RTree<T>) {
            encoder.encodeStructure(descriptor) {
                encodeIntElement(descriptor, 0, value.maxEntries)
                encodeSerializableElement(descriptor, 1, nodeSer, value.root)
            }
        }

        override fun deserialize(decoder: Decoder): RTree<T> =
            decoder.decodeStructure(descriptor) {
                var maxEntries: Int? = null
                var root: RTree.Node<T>? = null
                loop@ while (true) {
                    when (val i = decodeElementIndex(descriptor)) {
                        0 -> maxEntries = decodeIntElement(descriptor, 0)
                        1 -> root = decodeSerializableElement(descriptor, 1, nodeSer)
                        CompositeDecoder.DECODE_DONE -> break@loop
                        else -> throw SerializationException("Unexpected index: $i")
                    }
                }
                RTree(maxEntries ?: error("maxEntries missing"), root ?: error("root missing"))
            }
    }

/** Encode an RTree<T> to JSON using the Json's module to resolve T. */
inline fun <reified T> Json.encodeToStringRTree(tree: RTree<T>): String {
    val tSer = serializersModule.serializer<T>() // works for Int, String, or any @Serializable T
    return encodeToString(rtreeSerializer(tSer), tree)
}

/** Decode an RTree<T> from JSON using the Json's module to resolve T. */
inline fun <reified T> Json.decodeFromStringRTree(text: String): RTree<T> {
    val tSer = serializersModule.serializer<T>()
    return decodeFromString(rtreeSerializer(tSer), text)
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

// ---------------------------- Parallel build ----------------------------

suspend fun <P : Any> buildRTreeParallel(
    features: FeatureCollection<out Geometry?, P>,
    maxEntries: Int = 8,
    chunkSize: Int = 2048
): RTree<Feature<Geometry?, P>> = withContext(Dispatchers.Default) {
    @Suppress("UNCHECKED_CAST")
    val fs = features.features as List<Feature<Geometry?, P>>

    val entries: List<Entry<Feature<Geometry?, P>>> =
        fs.asSequence()
            .chunked(chunkSize)
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

/** Build an ID-based RTree: payloads are indices into the features list. */
fun <P : Any> buildRTreeIds(
    features: FeatureCollection<out Geometry?, P>,
    maxEntries: Int = 8
): Pair<RTree<Int>, List<Feature<Geometry?, P>>> {
    @Suppress("UNCHECKED_CAST")
    val fs = features.features as List<Feature<Geometry?, P>>
    val entries = fs.mapIndexed { idx, f ->
        Entry(
            rect = featureRect(f as Feature<Geometry?, *>),
            value = idx
        )
    }
    val tree = RTree.pack(entries, maxEntries)
    return tree to fs
}

/** Query returning matching feature indices. */
fun queryTouchingIds(
    index: RTree<Int>,
    bbox: BoundingBox,
    strictTouchOnly: Boolean = false
): List<Int> {
    val rect = Rect.from(bbox)
    return if (strictTouchOnly) index.searchTouch(rect) else index.searchIntersect(rect)
}