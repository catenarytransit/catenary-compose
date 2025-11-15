package com.catenarymaps.catenary

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Log
import org.maplibre.spatialk.geojson.*


/** Domain properties for a chateau feature in the provided dataset. */
@Serializable
data class ChateauProps(
    val chateau: String = "",
    @SerialName("languages_avaliable") val languagesAvailable: List<String> = emptyList(),
    @SerialName("realtime_feeds") val realtimeFeeds: List<String> = emptyList(),
    @SerialName("schedule_feeds") val scheduleFeeds: List<String> = emptyList(),
)


private val jsonLoose = Json { ignoreUnknownKeys = true }


/** Parse the JSON text into a typed FeatureCollection with [ChateauProps]. */
fun parseChateaux(json: String): FeatureCollection<Geometry?, ChateauProps> {
    val parsed: GeoJsonObject = GeoJsonObject.Companion.fromJson(json)
    val fcRaw: FeatureCollection<Geometry?, JsonObject?> = when (parsed) {
        is FeatureCollection<*, *> -> parsed as FeatureCollection<Geometry?, JsonObject?>
        is Feature<*, *> -> FeatureCollection(listOf(parsed as Feature<Geometry?, JsonObject?>))
        is Geometry -> FeatureCollection(listOf(Feature(parsed, null)))
        else -> error("Unsupported GeoJSON root: ${parsed::class.simpleName}")
    }


    val typed = fcRaw.features.map { f ->
        val props: ChateauProps = f.properties?.let { jsonLoose.decodeFromJsonElement(it) } ?: ChateauProps()
        Feature(f.geometry, props, f.id, f.bbox)
    }
    return FeatureCollection(typed, fcRaw.bbox)
}

/** Convert a chateaux FeatureCollection into an ID-based R-tree index. */
suspend fun chateauxToRTree(
    chateaux: FeatureCollection<out Geometry?, ChateauProps>,
    maxEntries: Int = 8
): Pair<RTree<Int>, List<Feature<Geometry?, ChateauProps>>> {
    @Suppress("UNCHECKED_CAST")
    val fc: FeatureCollection<Geometry?, ChateauProps> =
        FeatureCollection(chateaux.features as List<Feature<Geometry?, ChateauProps>>, chateaux.bbox)
    // parallel isn’t necessary here since we only need rects + IDs; but keeping it simple/serial:
    return buildRTreeIds(fc, maxEntries)
}

/** Lookup chateaux that intersect (or just touch) a [bbox]. */
fun lookupChateaux(
    index: RTree<Int>,
    features: List<Feature<Geometry?, ChateauProps>>,
    bbox: BoundingBox,
    strictTouchOnly: Boolean = false
): List<Feature<Geometry?, ChateauProps>> =
    queryTouchingIds(index, bbox, strictTouchOnly).map { features[it] }


// Build everything from raw JSON
suspend fun buildChateauxIndexFromJson(
    json: String,
    maxEntries: Int = 8
): Pair<RTree<Int>, List<Feature<Geometry?, ChateauProps>>> {
    val chateaux = parseChateaux(json)
    return chateauxToRTree(chateaux, maxEntries)
}

// Network + build
suspend fun fetchAndBuildChateauxIndex(
    client: HttpClient,
    maxEntries: Int = 8
): Pair<RTree<Int>, List<Feature<Geometry?, ChateauProps>>>? = withContext(Dispatchers.IO) {
    return@withContext try {
        val jsonString: String = client
            .get("https://raw.githubusercontent.com/catenarytransit/betula-celtiberica-cdn/refs/heads/main/data/chateaus_simp.json")
            .body()

        val t0 = System.currentTimeMillis()
        val result = buildChateauxIndexFromJson(jsonString, maxEntries) // (rtree, features)
        val t1 = System.currentTimeMillis()
        Log.d("ChateauxIndex", "Built chateaux index in ${t1 - t0} ms")
        result
    } catch (e: Exception) {
        null
    }
}