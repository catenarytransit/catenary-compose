package com.catenarymaps.catenary

import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.SvgDecoder

/** Shared between map search and the trip planner's endpoint search. */
internal object SearchTypeIcons {
    const val Train = "train-right-fill-color"
    const val Metro = "metro-right-fill-color"
    const val Tram = "tram-right-fill-color"
    const val Bus = "bus-right-fill-color"
    const val Hotel = "roof-bed-small"
    const val Restaurant = "cutlery-small"
    const val Park = "tree-small"
    const val Government = "parliament-small"
    const val Shopping = "shopping-bag-small"
    const val Recent = "arrow-circle-time-small"
    const val Location = "location-pin-small"

    fun route(type: Int?): String = when (type) {
        0 -> Tram
        1 -> Metro
        2 -> Train
        3 -> Bus
        in 100..399 -> Train
        in 400..699 -> Metro
        in 700..899 -> Bus
        in 900..999 -> Tram
        else -> Location
    }

    fun mode(value: String?): String {
        val mode = value?.lowercase() ?: return Location
        return when {
            "tram" in mode || "streetcar" in mode || "light_rail" in mode -> Tram
            "metro" in mode || "subway" in mode || "underground" in mode -> Metro
            "train" in mode || "rail" in mode || "station" in mode -> Train
            "bus" in mode || "coach" in mode || "trolleybus" in mode -> Bus
            else -> Location
        }
    }

    fun place(categories: List<String>, layer: String? = null, type: String? = null): String {
        val terms = (categories + listOfNotNull(layer, type)).map { it.lowercase() }
        fun has(regex: Regex) = terms.any { regex.containsMatchIn(it) }
        return when {
            has(Regex("(^|[:_])(railway|train_station|station)($|[:_])|railway:station")) -> Train
            has(Regex("metro|subway")) -> Metro
            has(Regex("tram|streetcar")) -> Tram
            has(Regex("bus_station|bus_stop|highway:bus_stop")) -> Bus
            has(Regex("tourism:(hotel|motel|hostel|guest_house|apartment|chalet)|accommodation|lodging")) -> Hotel
            has(Regex("amenity:(restaurant|cafe|fast_food|food_court|pub|bar)|restaurant|eatery")) -> Restaurant
            has(Regex("leisure:(park|garden|nature_reserve)|landuse:(forest|recreation_ground)|natural:(wood|forest)|park")) -> Park
            has(Regex("amenity:(townhall|courthouse|public_building)|office:government|government|townhall")) -> Government
            has(Regex("^(shop(:|$)|shopping($|[_:])|retail($|[_:])|mall($|[_:]))|amenity:(marketplace|supermarket|convenience)|building:retail|landuse:retail")) -> Shopping
            else -> Location
        }
    }
}

/** SVG assets are tinted after decoding, so fixed black SVG fills also become white in dark mode. */
@Composable
internal fun SearchTypeIcon(name: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val loader = remember(context) {
        ImageLoader.Builder(context).components { add(SvgDecoder.Factory()) }.build()
    }
    val tint = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) Color.White else Color.Black
    AsyncImage(
        model = "file:///android_asset/search/$name.svg",
        imageLoader = loader,
        contentDescription = null,
        colorFilter = ColorFilter.tint(tint),
        modifier = modifier.size(24.dp)
    )
}
