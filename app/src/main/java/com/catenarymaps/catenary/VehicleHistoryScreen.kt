package com.catenarymaps.catenary

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import java.net.URLEncoder
import java.time.DateTimeException
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.LinkedHashMap
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import com.catenarymaps.catenary.hourglass_arrow_up
import com.catenarymaps.catenary.hourglass_arrow_down

private const val VEHICLE_HISTORY_ENDPOINT =
        "https://birch.catenarymaps.org/vehicle_history_lookup"

class VehicleHistoryViewModel : ViewModel() {
        private val client = HttpClient(Android)
        private val json = Json { ignoreUnknownKeys = true }
        private var fetchJob: Job? = null
        private var currentLookupKey: String? = null
        private var requestSequence = 0

        private val _isLoading = MutableStateFlow(true)
        val isLoading = _isLoading.asStateFlow()

        private val _data = MutableStateFlow<VehicleHistoryLookupResponse?>(null)
        val data = _data.asStateFlow()

        private val _error = MutableStateFlow<String?>(null)
        val error = _error.asStateFlow()

        fun fetchData(chateau: String, vehicle: String, routeId: String?) {
                val lookupKey = "$chateau\u0000$vehicle\u0000${routeId.orEmpty()}"
                if (lookupKey == currentLookupKey && (_data.value != null || _error.value != null)) {
                        return
                }

                currentLookupKey = lookupKey
                val requestId = ++requestSequence
                fetchJob?.cancel()
                fetchJob =
                        viewModelScope.launch {
                                _isLoading.value = true
                                _error.value = null
                                _data.value = null

                                try {
                                        val encodedChateau = URLEncoder.encode(chateau, "UTF-8")
                                        val encodedVehicle = URLEncoder.encode(vehicle, "UTF-8")
                                        val url =
                                                buildString {
                                                        append(VEHICLE_HISTORY_ENDPOINT)
                                                        append("?vehicle=")
                                                        append(encodedVehicle)
                                                        append("&chateau=")
                                                        append(encodedChateau)
                                                        if (!routeId.isNullOrBlank()) {
                                                                append("&route_id=")
                                                                append(
                                                                        URLEncoder.encode(
                                                                                routeId,
                                                                                "UTF-8"
                                                                        )
                                                                )
                                                        }
                                                }

                                        val response = client.get(url)
                                        val responseText = response.bodyAsText()
                                        if (requestId != requestSequence) return@launch

                                        when {
                                                response.status == HttpStatusCode.NotFound -> {
                                                        _data.value =
                                                                VehicleHistoryLookupResponse(
                                                                        trip_history = emptyList(),
                                                                        routes = emptyMap(),
                                                                        agency_timezone = "UTC",
                                                                        agency_name = ""
                                                                )
                                                }
                                                response.status.value !in 200..299 -> {
                                                        val serverMessage =
                                                                runCatching {
                                                                                json.decodeFromString<
                                                                                        VehicleHistoryLookupErrorResponse>(
                                                                                        responseText
                                                                                )
                                                                        }
                                                                        .getOrNull()
                                                                        ?.error
                                                                        ?.message
                                                        _error.value =
                                                                serverMessage
                                                                        ?: "Vehicle history request failed (${response.status.value})"
                                                }
                                                else -> {
                                                        _data.value =
                                                                json.decodeFromString<
                                                                        VehicleHistoryLookupResponse>(
                                                                        responseText
                                                                )
                                                }
                                        }
                                } catch (cancellation: CancellationException) {
                                        throw cancellation
                                } catch (exception: Exception) {
                                        if (requestId == requestSequence) {
                                                _error.value =
                                                        exception.message
                                                                ?: "Vehicle history request failed"
                                        }
                                } finally {
                                        if (requestId == requestSequence) {
                                                _isLoading.value = false
                                        }
                                }
                        }
        }

        override fun onCleared() {
                fetchJob?.cancel()
                client.close()
                super.onCleared()
        }
}

private fun sortedVehicleHistory(
        rows: List<RouteHistoryRow>,
        descending: Boolean
): LinkedHashMap<String, List<RouteHistoryRow>> {
        val sortedRows =
                rows.sortedWith { left, right ->
                        val dateComparison = left.operation_date.compareTo(right.operation_date)
                        if (dateComparison != 0) {
                                return@sortedWith if (descending) -dateComparison else dateComparison
                        }

                        val leftTime = left.unix_start_time
                        val rightTime = right.unix_start_time
                        if (leftTime == null && rightTime != null) return@sortedWith 1
                        if (leftTime != null && rightTime == null) return@sortedWith -1

                        if (leftTime != null && rightTime != null) {
                                val timeComparison = leftTime.compareTo(rightTime)
                                if (timeComparison != 0) {
                                        return@sortedWith if (descending) -timeComparison else timeComparison
                                }
                        }

                        left.trip_id.compareTo(right.trip_id)
                }

        val grouped = LinkedHashMap<String, MutableList<RouteHistoryRow>>()
        for (row in sortedRows) {
                grouped.getOrPut(row.operation_date) { mutableListOf() }.add(row)
        }
        return LinkedHashMap(grouped.mapValues { it.value.toList() })
}

private fun vehicleHistoryDateLabel(operationDate: String, locale: Locale): String {
        return try {
                LocalDate.parse(operationDate)
                        .format(
                                DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)
                                        .withLocale(locale)
                        )
        } catch (_: DateTimeException) {
                operationDate
        }
}

private fun gtfsStartTimeFromUnix(
        unixStartTime: Long?,
        operationDate: String,
        timezone: String
): String? {
        if (unixStartTime == null) return null

        return try {
                val serviceDate = LocalDate.parse(operationDate)
                val localNoonEpoch =
                        serviceDate.atTime(12, 0).atZone(ZoneId.of(timezone)).toEpochSecond()
                val referenceMidnightEpoch = localNoonEpoch - 12L * 60L * 60L
                val gtfsSeconds = unixStartTime - referenceMidnightEpoch
                if (gtfsSeconds < 0) return null

                val hours = gtfsSeconds / 3600L
                val minutes = (gtfsSeconds % 3600L) / 60L
                val seconds = gtfsSeconds % 60L
                String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, seconds)
        } catch (_: DateTimeException) {
                null
        }
}

private fun vehicleHistoryRouteName(
        route: VehicleHistoryRoute?,
        routeId: String
): String =
        route?.short_name?.takeIf { it.isNotBlank() }
                ?: route?.long_name?.takeIf { it.isNotBlank() }
                ?: routeId

@Composable
fun VehicleHistoryScreen(
        screenData: CatenaryStackEnum.VehicleHistoryStack,
        showSeconds: Boolean,
        onTripClick: (CatenaryStackEnum.SingleTrip) -> Unit,
        onBlockClick: (CatenaryStackEnum.BlockStack) -> Unit,
        onBack: () -> Unit,
        onHome: () -> Unit,
        viewModel: VehicleHistoryViewModel = viewModel()
) {
        val isLoading by viewModel.isLoading.collectAsState()
        val historyData by viewModel.data.collectAsState()
        val error by viewModel.error.collectAsState()
        var sortDescending by rememberSaveable { mutableStateOf(true) }
        val locale = LocalConfiguration.current.locales[0]

        LaunchedEffect(screenData) {
                viewModel.fetchData(
                        chateau = screenData.chateau_id,
                        vehicle = screenData.vehicle_id,
                        routeId = screenData.route_id
                )
        }

        val groupedHistory =
                remember(historyData, sortDescending) {
                        sortedVehicleHistory(
                                rows = historyData?.trip_history.orEmpty(),
                                descending = sortDescending
                        )
                }

        Column(
                modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.Top
        ) {
                Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                ) {
                        NavigationControls(onBack = onBack, onHome = onHome)

                        Surface(
                                onClick = { sortDescending = !sortDescending },
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                                Icon(
                                        imageVector = if (sortDescending) {
                                                hourglass_arrow_down
                                        } else {
                                                hourglass_arrow_up
                                        },
                                        contentDescription =
                                                if (sortDescending) {
                                                        stringResource(
                                                                R.string.vehicle_history_sort_oldest_first
                                                        )
                                                } else {
                                                        stringResource(
                                                                R.string.vehicle_history_sort_newest_first
                                                        )
                                                },
                                        modifier = Modifier
                                                .padding(8.dp)
                                                .size(24.dp)
                                )
                        }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                        text = stringResource(R.string.vehicle_history_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                )
                if (!historyData?.agency_name.isNullOrBlank()) {
                        Text(
                                text = historyData?.agency_name.orEmpty(),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                        )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                                text = "${stringResource(R.string.vehicle)}: ",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                                text = screenData.vehicle_id,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                        )
                }
                Spacer(modifier = Modifier.height(4.dp))
                VehicleInfo(
                        label = screenData.vehicle_id,
                        chateau = screenData.chateau_id,
                        routeId = screenData.route_id
                )
                DonationSupportCard(
                        titleRes = R.string.vehicle_history_support_title,
                        messageRes = R.string.vehicle_history_support_message,
                        dismissible = false,
                        modifier = Modifier.padding(top = 12.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))

                when {
                        isLoading -> {
                                Box(
                                        modifier = Modifier
                                                .fillMaxWidth()
                                                .weight(1f),
                                        contentAlignment = Alignment.Center
                                ) {
                                        CircularProgressIndicator()
                                }
                        }
                        error != null -> {
                                Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.errorContainer
                                ) {
                                        Text(
                                                text = error.orEmpty(),
                                                color = MaterialTheme.colorScheme.onErrorContainer,
                                                modifier = Modifier.padding(12.dp)
                                        )
                                }
                        }
                        historyData == null || historyData?.trip_history.isNullOrEmpty() -> {
                                Box(
                                        modifier = Modifier
                                                .fillMaxWidth()
                                                .weight(1f),
                                        contentAlignment = Alignment.Center
                                ) {
                                        Text(
                                                text = stringResource(R.string.vehicle_history_no_history),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                }
                        }
                        else -> {
                                LazyColumn(modifier = Modifier.fillMaxSize()) {
                                        groupedHistory.forEach { (operationDate, trips) ->
                                                item(key = "date-$operationDate") {
                                                        Text(
                                                                text =
                                                                        vehicleHistoryDateLabel(
                                                                                operationDate,
                                                                                locale
                                                                        ),
                                                                style =
                                                                        MaterialTheme.typography
                                                                                .titleSmall,
                                                                fontWeight = FontWeight.SemiBold,
                                                                modifier =
                                                                        Modifier.padding(
                                                                                start = 4.dp,
                                                                                top = 12.dp,
                                                                                bottom = 4.dp
                                                                        )
                                                        )
                                                        VehicleHistoryColumnHeadings()
                                                        HorizontalDivider(
                                                                color =
                                                                        MaterialTheme.colorScheme
                                                                                .outlineVariant
                                                        )
                                                }

                                                items(
                                                        items = trips,
                                                        key = { row ->
                                                                "$operationDate-${row.trip_id}-${row.route_id}-${row.block_id.orEmpty()}"
                                                        }
                                                ) { row ->
                                                        val route =
                                                                historyData?.routes?.get(row.route_id)
                                                        VehicleHistoryTripRow(
                                                                row = row,
                                                                route = route,
                                                                timezone =
                                                                        historyData?.agency_timezone
                                                                                ?: "UTC",
                                                                showSeconds = showSeconds,
                                                                onTripClick = {
                                                                        onTripClick(
                                                                                CatenaryStackEnum
                                                                                        .SingleTrip(
                                                                                                chateau_id =
                                                                                                        screenData
                                                                                                                .chateau_id,
                                                                                                trip_id =
                                                                                                        row.trip_id,
                                                                                                route_id =
                                                                                                        row.route_id,
                                                                                                start_time =
                                                                                                        gtfsStartTimeFromUnix(
                                                                                                                row.unix_start_time,
                                                                                                                row.operation_date,
                                                                                                                historyData
                                                                                                                        ?.agency_timezone
                                                                                                                        ?: "UTC"
                                                                                                        ),
                                                                                                start_date =
                                                                                                        row.operation_date
                                                                                                                .replace(
                                                                                                                        "-",
                                                                                                                        ""
                                                                                                                ),
                                                                                                vehicle_id =
                                                                                                        screenData
                                                                                                                .vehicle_id,
                                                                                                route_type =
                                                                                                        route?.route_type
                                                                                        )
                                                                        )
                                                                },
                                                                onBlockClick = {
                                                                        row.block_id?.let { blockId ->
                                                                                onBlockClick(
                                                                                        CatenaryStackEnum
                                                                                                .BlockStack(
                                                                                                        chateau_id =
                                                                                                                screenData
                                                                                                                        .chateau_id,
                                                                                                        block_id =
                                                                                                                blockId,
                                                                                                        service_date =
                                                                                                                row.operation_date
                                                                                                )
                                                                                )
                                                                        }
                                                                }
                                                        )
                                                }
                                        }

                                        item(key = "history-bottom-spacer") {
                                                Spacer(modifier = Modifier.height(24.dp))
                                        }
                                }
                        }
                }
        }
}

@Composable
private fun VehicleHistoryColumnHeadings() {
        Row(
                modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
        ) {
                Text(
                        text = stringResource(R.string.vehicle_history_time),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.width(64.dp)
                )
                Text(
                        text = stringResource(R.string.route),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.width(58.dp)
                )
                Text(
                        text = stringResource(R.string.headsign),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                )
                Text(
                        text = stringResource(R.string.block),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(72.dp)
                )
        }
}

@Composable
private fun VehicleHistoryTripRow(
        row: RouteHistoryRow,
        route: VehicleHistoryRoute?,
        timezone: String,
        showSeconds: Boolean,
        onTripClick: () -> Unit,
        onBlockClick: () -> Unit
) {
        Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                        modifier =
                                Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 4.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                        Box(modifier = Modifier.width(64.dp)) {
                                if (row.unix_start_time != null) {
                                        FormattedTimeText(
                                                timezone = timezone,
                                                timeSeconds = row.unix_start_time,
                                                showSeconds = showSeconds,
                                                style = MaterialTheme.typography.labelMedium
                                        )
                                } else {
                                        Text("—", style = MaterialTheme.typography.labelMedium)
                                }
                        }

                        Box(modifier = Modifier.width(58.dp)) {
                                Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color =
                                                parseColor(
                                                        route?.color,
                                                        MaterialTheme.colorScheme.surfaceVariant
                                                ),
                                        modifier = Modifier.widthIn(max = 54.dp)
                                ) {
                                        Text(
                                                text = vehicleHistoryRouteName(route, row.route_id),
                                                color =
                                                        parseColor(
                                                                route?.text_color,
                                                                MaterialTheme.colorScheme
                                                                        .onSurfaceVariant
                                                        ),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier =
                                                        Modifier.padding(
                                                                horizontal = 6.dp,
                                                                vertical = 2.dp
                                                        )
                                        )
                                }
                        }

                        Text(
                                text =
                                        row.direction_headsign?.takeIf { it.isNotBlank() }
                                                ?: row.trip_short_name?.takeIf { it.isNotBlank() }
                                                ?: row.trip_id,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                textDecoration = TextDecoration.Underline,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                        .weight(1f)
                                        .clickable(onClick = onTripClick)
                        )

                        if (row.block_id != null) {
                                Text(
                                        text = row.block_id,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        textAlign = TextAlign.End,
                                        textDecoration = TextDecoration.Underline,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier =
                                                Modifier
                                                        .width(72.dp)
                                                        .clickable(onClick = onBlockClick)
                                )
                        } else {
                                Text(
                                        text = "—",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        textAlign = TextAlign.End,
                                        modifier = Modifier.width(72.dp)
                                )
                        }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
}
