package com.catenarymaps.catenary

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.ktor.client.HttpClient
import kotlinx.coroutines.*
import kotlinx.serialization.json.JsonObject
import java.util.UUID
import java.time.Instant

class TripPlannerViewModel : ViewModel() {
    var origin by mutableStateOf<DirectionsLocation?>(null)
        private set
    var destination by mutableStateOf<DirectionsLocation?>(null)
        private set
    var originText by mutableStateOf("")
        private set
    var destinationText by mutableStateOf("")
        private set
    // true = origin, false = destination, null = no endpoint has focus.
    var editing by mutableStateOf<Boolean?>(null)
    // Endpoint being selected with the fixed centre pin, separate from text search.
    var mapPicking by mutableStateOf<Boolean?>(null)
    var options by mutableStateOf(DirectionsOptions())
    var arriveBy by mutableStateOf(false)
    var now by mutableStateOf(true)
    var time by mutableLongStateOf(Instant.now().epochSecond)
    var itineraries by mutableStateOf<List<DirectionsItinerary>>(emptyList())
        private set
    var selected by mutableIntStateOf(0)
    var details by mutableStateOf(false)
    var detailsCollapse by mutableFloatStateOf(0f)
    var detailsCollapseRangePx by mutableFloatStateOf(1f)
    private var awaitingCurrentOrigin = false
    var mapMode by mutableStateOf(false)
    val selectedJourney get() = itineraries.getOrNull(selected)
    val streetOnly get() = selectedJourney?.let { it.legs.isNotEmpty() && it.legs.all { leg -> leg.street } } == true
    val usingMap get() = details && (mapMode || streetOnly)

    fun selectJourney(journey: DirectionsItinerary) {
        selected = itineraries.indexOfFirst { it.identity() == journey.identity() }.coerceAtLeast(0)
        mapMode = false
        detailsCollapse = 0f
        showTransfers = true
        details = true
        editing = null
    }

    var showTransfers by mutableStateOf(false)
    var loading by mutableStateOf(false)
        private set
    var searched by mutableStateOf(false)
        private set
    var error by mutableStateOf<Int?>(null)
        private set
    private var request: Job? = null
    private var generation = 0
    var earlierLoading by mutableStateOf(false)
        private set
    var laterLoading by mutableStateOf(false)
        private set
    var previousPageCursor by mutableStateOf<String?>(null)
        private set
    var nextPageCursor by mutableStateOf<String?>(null)
        private set
    var earlierInsertedIds by mutableStateOf<Set<String>>(emptySet())
        private set
    private var pageRequest: Job? = null
    private var baseQuery: JsonObject? = null
    private var initialPage: DirectionsPlan? = null
    private val earlierPages = linkedMapOf<String, DirectionsPlan>()
    private val laterPages = linkedMapOf<String, DirectionsPlan>()
    val canLoadEarlier get() = previousPageCursor != null && baseQuery != null &&
        !earlierLoading && !laterLoading && (!loading || itineraries.isNotEmpty())
    val canLoadLater get() = nextPageCursor != null && baseQuery != null &&
        !earlierLoading && !laterLoading && !loading

    private fun cancelRequests() {
        generation++; request?.cancel(); pageRequest?.cancel()
        loading = false; earlierLoading = false; laterLoading = false
    }

    private fun publishPages() {
        val selectedId = itineraries.getOrNull(selected)?.identity()
        val merged = mergeDirectionsPages(earlierPages.values.toList() + listOfNotNull(initialPage) + laterPages.values)
        itineraries = merged
        selected = itineraries.indexOfFirst { it.identity() == selectedId }.coerceAtLeast(0)
        if (details && selectedId != null && itineraries.none { it.identity() == selectedId }) details = false
    }
    private var sessionInitialized = false

    /** A retained ViewModel already owns the latest state during Activity recreation. */
    internal fun restoreSession(session: TripPlannerSession?): Boolean {
        if (sessionInitialized) return false
        sessionInitialized = true
        val saved = session?.validated() ?: return true
        origin = saved.origin; originText = saved.origin.name
        destination = saved.destination; destinationText = saved.destination.name
        options = saved.options; arriveBy = saved.arriveBy; now = saved.now; time = saved.time
        itineraries = saved.itineraries; selected = saved.selected
        details = saved.details; showTransfers = saved.showTransfers
        baseQuery = saved.requestQuery
        initialPage = DirectionsPlan(itineraries = saved.itineraries)
        previousPageCursor = saved.previousPageCursor
        nextPageCursor = saved.nextPageCursor
        searched = true
        return true
    }

    /** Empty, invalidated and unfinished searches must never reopen an empty planner. */
    internal fun session(visible: Boolean): TripPlannerSession? {
        if (!visible || loading || earlierLoading || laterLoading || !searched || error != null || itineraries.isEmpty()) return null
        return TripPlannerSession(
            origin = origin ?: return null, destination = destination ?: return null,
            options = options, arriveBy = arriveBy, now = now, time = time,
            itineraries = itineraries, selected = selected, details = details,
            showTransfers = showTransfers,
            requestQuery = baseQuery, previousPageCursor = previousPageCursor, nextPageCursor = nextPageCursor
        ).validated()
    }

    fun invalidate() {
        cancelRequests(); searched = false
        baseQuery = null; initialPage = null; earlierPages.clear(); laterPages.clear()
        previousPageCursor = null; nextPageCursor = null; earlierInsertedIds = emptySet()
        error = null; itineraries = emptyList(); selected = 0; details = false; mapMode = false; showTransfers = false
    }
    /** Coordinate-based selections become an empty search when the field is focused again. */
    fun beginEditing(isOrigin: Boolean) {
        val location = if (isOrigin) origin else destination
        if (location?.isCurrentLocation == true || location?.isMapLocation == true) {
            edit(isOrigin, "")
        } else {
            editing = isOrigin
        }
    }

    fun edit(isOrigin: Boolean, text: String) {
        invalidate()
        if (isOrigin) awaitingCurrentOrigin = false
        if (isOrigin) { origin = null; originText = text }
        else { destination = null; destinationText = text }
        editing = isOrigin
    }
    fun choose(isOrigin: Boolean, location: DirectionsLocation) {
        if (!location.isValid()) return
        invalidate()
        if (isOrigin) awaitingCurrentOrigin = false
        if (isOrigin) { origin = location; originText = location.name }
        else { destination = location; destinationText = location.name }
        editing = if (isOrigin && destination == null) false else if (!isOrigin && origin == null) true else null
        mapPicking = null
    }
    /** Prepare a fresh planner without replacing an existing endpoint or session. */
    fun open(currentLocation: DirectionsLocation?) {
        awaitingCurrentOrigin = origin == null && destination == null && originText.isBlank() && destinationText.isBlank()
        resolveCurrentOrigin(currentLocation)
        editing = when {
            destination == null -> false
            origin == null -> true
            else -> null
        }
    }

    /** A delayed location fix may complete the default origin until the user edits it. */
    fun resolveCurrentOrigin(location: DirectionsLocation?) {
        if (awaitingCurrentOrigin && location?.isValid() == true) choose(true, location)
    }

    /** Location-card shortcuts focus the opposite endpoint. */
    fun chooseFromLocationAction(
        isOrigin: Boolean,
        location: DirectionsLocation,
        currentLocation: DirectionsLocation?
    ) {
        if (!location.isValid()) return
        awaitingCurrentOrigin = false
        choose(isOrigin, location)
        editing = !isOrigin
    }

    fun swap() {
        invalidate()
        val old = origin; origin = destination; destination = old
        val text = originText; originText = destinationText; destinationText = text
        editing = null
    }
    fun leave() {
        awaitingCurrentOrigin = false
        mapPicking = null
        if (loading && itineraries.isEmpty()) searched = false
        cancelRequests()
        editing = null
    }

    fun search(client: HttpClient) {
        val from = origin ?: return
        val to = destination ?: return
        invalidate(); searched = true; loading = true; editing = null
        val token = generation
        val base = directionsRequest(from, to, if (now) Instant.now().epochSecond else time, arriveBy, options)
        baseQuery = base
        request = viewModelScope.launch {
            try {
                DirectionsStreaming.load(client, directionsPageQuery(base,
                    if (arriveBy) ConnectionDirection.BEFORE else ConnectionDirection.AFTER),
                    "catenary-android-${UUID.randomUUID()}") { page ->
                    if (token == generation) {
                        initialPage = page
                        if (earlierPages.isEmpty()) previousPageCursor = page.previousPageCursor?.takeIf { it.isNotBlank() }
                        if (laterPages.isEmpty()) nextPageCursor = page.nextPageCursor?.takeIf { it.isNotBlank() }
                        publishPages()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("TripPlanner", "Unable to plan journey", e)
                if (token == generation) error = R.string.directions_error
            } finally {
                if (token == generation) loading = false
            }
        }
    }

    fun loadConnections(client: HttpClient, earlier: Boolean) {
        if (if (earlier) !canLoadEarlier else !canLoadLater) return
        val cursor = (if (earlier) previousPageCursor else nextPageCursor) ?: return
        val base = baseQuery ?: return
        val token = generation
        val id = "catenary-android-${UUID.randomUUID()}"
        if (earlier) { earlierLoading = true; earlierInsertedIds = emptySet() } else laterLoading = true
        error = null
        pageRequest = viewModelScope.launch {
            try {
                var completed: DirectionsPlan? = null
                DirectionsStreaming.load(client, directionsPageQuery(base,
                    if (earlier) ConnectionDirection.BEFORE else ConnectionDirection.AFTER, cursor), id) { page ->
                    if (token == generation) {
                        completed = page // Publish either direction as one completed batch.
                    }
                }
                if (token == generation) {
                    val page = checkNotNull(completed)
                    val existing = itineraries.map { it.identity() }.toSet()
                    if (earlier) {
                        earlierInsertedIds = page.itineraries.map { it.identity() }.filter { it !in existing }.toSet()
                        earlierPages[id] = page
                        previousPageCursor = page.previousPageCursor?.takeIf { it.isNotBlank() && it != cursor }
                    } else {
                        laterPages[id] = page
                        nextPageCursor = page.nextPageCursor?.takeIf { it.isNotBlank() && it != cursor }
                    }
                    publishPages()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("TripPlanner", "Unable to load connections", e)
                if (token == generation) error = R.string.directions_error
            } finally {
                if (token == generation) {
                    if (earlier) earlierLoading = false else laterLoading = false
                }
            }
        }
    }
}
