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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
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
private data class OsmApiElement(
    val type: String,
    val id: Long,
    val tags: Map<String, String> = emptyMap()
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

private fun humanTagValue(value: String): String = when (value.lowercase()) {
    "yes" -> "Yes"
    "no" -> "No"
    "limited" -> "Limited"
    "designated" -> "Designated"
    "only" -> "Only"
    else -> value.replace('_', ' ').replaceFirstChar { it.titlecase() }
}

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
    onHome: () -> Unit
) {
    val context = LocalContext.current
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
                error = "This search result has no public OSM object identifier."
                return@LaunchedEffect
            }
            // The unsuffixed OSM API URL returns XML; the .json suffix requests JSON.
            val data = ktorClient.get(
                "https://www.openstreetmap.org/api/0.6/${item.osm_class}/${item.osm_id}.json"
            ).body<OsmApiResponse>()
            response = data.elements.firstOrNull {
                it.type == item.osm_class && it.id.toString() == item.osm_id
            } ?: throw IllegalStateException("OpenStreetMap did not return this object")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (exception: Exception) {
            error = exception.message ?: "Could not load OpenStreetMap details"
        } finally {
            loading = false
        }
    }

    val tags = response?.tags.orEmpty()
    val title = tags["name"] ?: item.name?.takeIf { it.isNotBlank() }
    ?: "${item.osm_class.replaceFirstChar { it.titlecase() }} ${item.osm_id}"
    val tagAddress = listOfNotNull(
        listOfNotNull(tags["addr:street"], tags["addr:housenumber"])
            .joinToString(" ").takeIf { it.isNotBlank() },
        tags["addr:postcode"], tags["addr:city"], tags["addr:state"], tags["addr:country"]
    ).joinToString(", ")
    val address = tagAddress.ifBlank { item.address.orEmpty() }
    val kind = tags["amenity"] ?: tags["shop"] ?: tags["tourism"] ?: tags["leisure"]
    ?: item.osm_type ?: "OpenStreetMap feature"

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

            if (loading) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator()
                    Text(
                        "Loading OpenStreetMap details…",
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
                            OutlinedButton(onClick = { retries++ }) { Text("Retry") }
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
                                    text = "Other names (${alternateNames.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = if (namesExpanded) Icons.Filled.KeyboardArrowUp
                                    else Icons.Filled.KeyboardArrowDown,
                                    contentDescription = if (namesExpanded) "Collapse other names"
                                    else "Expand other names"
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
                            label = "Website",
                            value = websiteDomain(website),
                            onClick = { openUrl(website) }
                        )
                    }
                    if (phone != null) {
                        OsmContactCard(
                            icon = Icons.Outlined.Call,
                            label = "Phone number",
                            value = phone,
                            onClick = { dialPhone(phone) }
                        )
                    }
                    if (openingHours != null) {
                        // OSM opening_hours is a rule expression; do not guess 'Open now'.
                        OsmContactCard(
                            icon = Icons.Outlined.Schedule,
                            label = "Opening hours",
                            value = openingHours
                        )
                    }
                }
            }

            if (response != null) {
                val features = listOf(
                    "wheelchair" to "Wheelchair access",
                    "toilets" to "Toilets",
                    "toilets:wheelchair" to "Accessible toilets",
                    "indoor_seating" to "Indoor seating",
                    "outdoor_seating" to "Outdoor seating",
                    "takeaway" to "Takeaway",
                    "drive_through" to "Drive-through",
                    "air_conditioning" to "Air conditioning",
                    "internet_access" to "Internet access",
                    "diet:vegan" to "Vegan options",
                    "diet:vegetarian" to "Vegetarian options",
                    "payment:cash" to "Cash",
                    "payment:credit_cards" to "Credit cards",
                    "payment:debit_cards" to "Debit cards",
                    "smoking" to "Smoking",
                    "level" to "Level"
                ).mapNotNull { (key, label) -> tags[key]?.let { label to humanTagValue(it) } }
                if (features.isNotEmpty()) {
                    Text("Amenities & accessibility", style = MaterialTheme.typography.titleMedium)
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
                    "operator" to "Operator", "brand" to "Brand", "network" to "Network",
                    "cuisine" to "Cuisine", "email" to "Email", "contact:email" to "Contact email"
                ).mapNotNull { (key, label) -> tags[key]?.let { label to it } }
                if (extraDetails.isNotEmpty()) {
                    HorizontalDivider()
                    Text("Details", style = MaterialTheme.typography.titleMedium)
                    extraDetails.forEach { (label, value) ->
                        Text("$label: $value", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            val wikidata = tags["wikidata"]?.takeIf(wikidataPattern::matches)
            if (wikidata != null) {
                TextButton(onClick = { openUrl("https://www.wikidata.org/wiki/$wikidata") }) {
                    Text("Wikidata ($wikidata) ↗")
                }
            }
            if (item.osm_class in setOf(
                    "node",
                    "way",
                    "relation"
                ) && osmIdPattern.matches(item.osm_id)
            ) {
                TextButton(onClick = {
                    openUrl("https://www.openstreetmap.org/${item.osm_class}/${item.osm_id}")
                }) { Text("View on OpenStreetMap ↗") }
            }

            if (tags.isNotEmpty()) {
                TextButton(onClick = { tagsExpanded = !tagsExpanded }) {
                    Text(if (tagsExpanded) "Hide all OSM tags" else "Show all OSM tags")
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
