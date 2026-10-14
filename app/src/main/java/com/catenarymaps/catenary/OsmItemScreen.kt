package com.catenarymaps.catenary

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable

@Serializable
private data class OsmApiResponse(val elements: List<OsmApiElement> = emptyList())

@Serializable
private data class OsmApiCenter(val lat: Double, val lon: Double)

@Serializable
private data class OsmApiElement(
    val type: String,
    val id: Long,
    val tags: Map<String, String> = emptyMap(),
    val lat: Double? = null,
    val lon: Double? = null,
    val center: OsmApiCenter? = null
)

private val osmIdPattern = Regex("[1-9][0-9]*")
private val wikidataPattern = Regex("Q[1-9][0-9]*")

/** External websites must be valid HTTP(S) URLs. */
private fun websiteUrl(raw: String?): String? {
    val input = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val url = if ("://" in input) input else "https://$input"
    val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return null
    if (uri.scheme?.lowercase() !in setOf("http", "https") || uri.host.isNullOrBlank()) return null
    return url
}

/** Display only the host, not the potentially long URL/path. */
private fun websiteDomain(url: String): String =
    Uri.parse(url).host?.removePrefix("www.") ?: url

private fun humanTagValue(value: String, translations: Map<String, String>): String =
    translations[value.lowercase()]
        ?: value.replace('_', ' ').replaceFirstChar { it.titlecase() }

private fun titleCaseTag(key: String): String = key.replace(':', ' ').replace('_', ' ')
    .replaceFirstChar { it.titlecase() }

/** One Google Maps-style info card: icon on the left, label and value on the right. */
@Composable
private fun OsmContactCard(
    icon: ImageVector,
    label: String,
    value: String,
    onClick: (() -> Unit)? = null
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(onClick?.let { Modifier.clickable(onClick = it) } ?: Modifier)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(text = value, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OsmItemScreen(
    item: CatenaryStackEnum.OsmItemStack,
    ktorClient: HttpClient,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onShowNearbyDepartures: (lat: Double, lon: Double) -> Unit
) {
    val context = LocalContext.current
    val language = LocalConfiguration.current.locales[0]?.language
    val invalidIdError = stringResource(R.string.osm_invalid_id)
    val missingObjectError = stringResource(R.string.osm_object_missing)
    val loadError = stringResource(R.string.osm_load_failed)
    var response by remember(item.osm_class, item.osm_id) { mutableStateOf<OsmApiElement?>(null) }
    var loading by remember(item.osm_class, item.osm_id) { mutableStateOf(true) }
    var error by remember(item.osm_class, item.osm_id) { mutableStateOf<String?>(null) }
    var retries by remember(item.osm_class, item.osm_id) { mutableIntStateOf(0) }
    var namesExpanded by remember(item.osm_class, item.osm_id) { mutableStateOf(false) }
    var tagsExpanded by remember(item.osm_class, item.osm_id) { mutableStateOf(false) }
    val canFetch = item.osm_class in setOf("node", "way", "relation") &&
            osmIdPattern.matches(item.osm_id)

    LaunchedEffect(item.osm_class, item.osm_id, retries) {
        response = null
        error = null
        loading = true
        try {
            if (!canFetch) {
                error = invalidIdError
                return@LaunchedEffect
            }
            // The unsuffixed OSM API URL returns XML; the .json suffix requests JSON.
            val data = ktorClient.get(
                "https://www.openstreetmap.org/api/0.6/${item.osm_class}/${item.osm_id}.json"
            ).body<OsmApiResponse>()
            response = data.elements.firstOrNull {
                it.type == item.osm_class && it.id.toString() == item.osm_id
            } ?: throw IllegalStateException(missingObjectError)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (exception: Exception) {
            // Do not expose unlocalized Ktor/HTTP exception messages as screen text.
            error = if (exception.message == missingObjectError) missingObjectError else loadError
        } finally {
            loading = false
        }
    }

    val tags = response?.tags.orEmpty()
    val objectType = when (item.osm_class) {
        "node" -> stringResource(R.string.osm_node)
        "way" -> stringResource(R.string.osm_way)
        "relation" -> stringResource(R.string.osm_relation)
        else -> stringResource(R.string.osm_feature)
    }
    val title = language?.let { tags["name:$it"] }?.takeIf { it.isNotBlank() }
        ?: tags["name"]?.takeIf { it.isNotBlank() }
        ?: item.name?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.osm_unnamed, objectType, item.osm_id)
    val tagAddress = listOfNotNull(
        listOfNotNull(tags["addr:street"], tags["addr:housenumber"])
            .joinToString(" ").takeIf { it.isNotBlank() },
        tags["addr:postcode"], tags["addr:city"], tags["addr:state"], tags["addr:country"]
    ).joinToString(", ")
    val address = tagAddress.ifBlank { item.address.orEmpty() }
    val kind = tags["amenity"] ?: tags["shop"] ?: tags["tourism"] ?: tags["leisure"]
    ?: item.osm_type ?: stringResource(R.string.osm_feature)

    // Point searches and map clicks have coordinates already. For OSM nodes we can
    // also use the API response. Ways/relations need a coordinate from search or
    // the map: the standard OSM /api/0.6 endpoint does not include their centroid.
    val nearbyOrigin = sequenceOf(
        item.lat to item.lon,
        response?.lat to response?.lon,
        response?.center?.lat to response?.center?.lon
    ).firstNotNullOfOrNull { (lat, lon) ->
        if (lat != null && lon != null && lat.isFinite() && lon.isFinite() &&
            lat in -90.0..90.0 && lon in -180.0..180.0
        ) lat to lon else null
    }

    val localizedTagValues = mapOf(
        "yes" to stringResource(R.string.osm_yes),
        "no" to stringResource(R.string.osm_no),
        "limited" to stringResource(R.string.osm_limited),
        "designated" to stringResource(R.string.osm_designated),
        "only" to stringResource(R.string.osm_only)
    )

    // Search results provide alternate names before OSM finishes loading.
    val alternateNames = buildMap {
        item.names.filterKeys { it != "default" }.forEach { (key, value) ->
            put(key.removePrefix("name:"), value)
        }
        tags.filterKeys { it.startsWith("name:") }.forEach { (key, value) ->
            put(key.removePrefix("name:"), value)
        }
    }.filterValues { it.isNotBlank() && it != title }.toSortedMap()

    val website = websiteUrl(tags["website"] ?: tags["contact:website"])
    val phone = (tags["phone"] ?: tags["contact:phone"])?.trim()?.takeIf { it.isNotEmpty() }
    val openingHours = tags["opening_hours"]?.takeIf { it.isNotBlank() }

    fun openUrl(url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    fun dialPhone(number: String) {
        // OSM phone tags may contain multiple numbers separated by ';'.
        val firstNumber = number.substringBefore(';').trim()
        if (firstNumber.isNotEmpty()) {
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", firstNumber, null))
                )
            }
        }
    }

    // Match StopScreen and OsmStationStack: the shared Back / Home controls stay
    // visible above the independently scrolling details section.
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp)
            )
            NavigationControls(onBack = onBack, onHome = onHome)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = kind.replace('_', ' ').replaceFirstChar { it.titlecase() },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge
            )
            if (address.isNotBlank()) {
                Text(address, style = MaterialTheme.typography.bodyMedium)
            }

            Button(
                onClick = {
                    nearbyOrigin?.let { (lat, lon) -> onShowNearbyDepartures(lat, lon) }
                },
                enabled = nearbyOrigin != null,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Text(stringResource(if (nearbyOrigin == null) R.string.osm_nearby_unavailable else R.string.osm_nearby))
            }

            if (loading) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator()
                    Text(
                        stringResource(R.string.osm_loading),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            if (error != null) {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(error!!, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(8.dp))
                        if (canFetch) {
                            FilledTonalButton(onClick = { retries++ }) {
                                Text(stringResource(R.string.osm_retry))
                            }
                        }
                    }
                }
            }

            // Keep related details grouped, each in its own Material card.
            if (alternateNames.isNotEmpty() || website != null || phone != null || openingHours != null) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (alternateNames.isNotEmpty()) {
                        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { namesExpanded = !namesExpanded }
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.Translate,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = stringResource(
                                        R.string.osm_other_names,
                                        alternateNames.size
                                    ),
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = if (namesExpanded) Icons.Filled.KeyboardArrowUp
                                    else Icons.Filled.KeyboardArrowDown,
                                    contentDescription = stringResource(
                                        if (namesExpanded)
                                            R.string.osm_collapse_names else R.string.osm_expand_names
                                    )
                                )
                            }
                            AnimatedVisibility(visible = namesExpanded) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 56.dp, end = 16.dp, bottom = 16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    alternateNames.forEach { (language, name) ->
                                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            Text(
                                                text = language,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = name,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (website != null) {
                        OsmContactCard(
                            icon = Icons.Outlined.Language,
                            label = stringResource(R.string.osm_website),
                            value = websiteDomain(website),
                            onClick = { openUrl(website) }
                        )
                    }
                    if (phone != null) {
                        OsmContactCard(
                            icon = Icons.Outlined.Call,
                            label = stringResource(R.string.osm_phone),
                            value = phone,
                            onClick = { dialPhone(phone) }
                        )
                    }
                    if (openingHours != null) {
                        // OSM opening_hours is a rule expression; do not guess 'Open now'.
                        OsmContactCard(
                            icon = Icons.Outlined.Schedule,
                            label = stringResource(R.string.osm_hours),
                            value = openingHours
                        )
                    }
                }
            }

            if (response != null) {
                val features = listOf(
                    "wheelchair" to stringResource(R.string.osm_wheelchair),
                    "toilets" to stringResource(R.string.osm_toilets),
                    "toilets:wheelchair" to stringResource(R.string.osm_accessible_toilets),
                    "indoor_seating" to stringResource(R.string.osm_indoor_seating),
                    "outdoor_seating" to stringResource(R.string.osm_outdoor_seating),
                    "takeaway" to stringResource(R.string.osm_takeaway),
                    "drive_through" to stringResource(R.string.osm_drive_through),
                    "air_conditioning" to stringResource(R.string.osm_air_conditioning),
                    "internet_access" to stringResource(R.string.osm_internet_access),
                    "diet:vegan" to stringResource(R.string.osm_vegan),
                    "diet:vegetarian" to stringResource(R.string.osm_vegetarian),
                    "payment:cash" to stringResource(R.string.osm_cash),
                    "payment:credit_cards" to stringResource(R.string.osm_credit_cards),
                    "payment:debit_cards" to stringResource(R.string.osm_debit_cards),
                    "smoking" to stringResource(R.string.osm_smoking),
                    "level" to stringResource(R.string.osm_level)
                ).mapNotNull { (key, label) ->
                    tags[key]?.let {
                        label to humanTagValue(
                            it,
                            localizedTagValues
                        )
                    }
                }
                if (features.isNotEmpty()) {
                    Text(
                        stringResource(R.string.osm_amenities),
                        style = MaterialTheme.typography.titleMedium
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        features.forEach { (label, value) ->
                            Surface(shape = MaterialTheme.shapes.small, tonalElevation = 2.dp) {
                                Text(
                                    "$label: $value", modifier = Modifier.padding(8.dp),
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                }

                val extraDetails = listOf(
                    "operator" to stringResource(R.string.osm_operator),
                    "brand" to stringResource(R.string.osm_brand),
                    "network" to stringResource(R.string.osm_network),
                    "cuisine" to stringResource(R.string.osm_cuisine),
                    "email" to stringResource(R.string.osm_email),
                    "contact:email" to stringResource(R.string.osm_contact_email)
                ).mapNotNull { (key, label) -> tags[key]?.let { label to it } }
                if (extraDetails.isNotEmpty()) {
                    HorizontalDivider()
                    Text(
                        stringResource(R.string.osm_details),
                        style = MaterialTheme.typography.titleMedium
                    )
                    extraDetails.forEach { (label, value) ->
                        Text("$label: $value", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            val wikidata = tags["wikidata"]?.takeIf(wikidataPattern::matches)
            if (wikidata != null) {
                FilledTonalButton(onClick = { openUrl("https://www.wikidata.org/wiki/$wikidata") }) {
                    Text("Wikidata ($wikidata) ↗")
                }
            }
            if (item.osm_class in setOf(
                    "node",
                    "way",
                    "relation"
                ) && osmIdPattern.matches(item.osm_id)
            ) {
                FilledTonalButton(onClick = {
                    openUrl("https://www.openstreetmap.org/${item.osm_class}/${item.osm_id}")
                }) { Text(stringResource(R.string.osm_view_osm)) }
            }

            if (tags.isNotEmpty()) {
                FilledTonalButton(onClick = { tagsExpanded = !tagsExpanded }) {
                    Text(stringResource(if (tagsExpanded) R.string.osm_hide_tags else R.string.osm_show_tags))
                }
                AnimatedVisibility(tagsExpanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        tags.toSortedMap().forEach { (key, value) ->
                            Text(
                                "${titleCaseTag(key)}: $value",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
