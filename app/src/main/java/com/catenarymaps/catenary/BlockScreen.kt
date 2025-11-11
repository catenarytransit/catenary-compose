package com.catenarymaps.catenary

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.logEvent
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data class BlockData(
    val routes: Map<String, RouteInfoForBlock>,
    val trips: List<Trip>
)

@Serializable
data class RouteInfoForBlock(
    val short_name: String?,
    val long_name: String?,
    val color: String?
)

@Serializable
data class Trip(
    val trip_id: String,
    val route_id: String,
    val trip_headsign: String,
    val start_time: Long,
    val end_time: Long,
    val first_stop_name: String,
    val last_stop_name: String,
    val timezone_start: String,
    val timezone_end: String
)

class BlockScreenViewModel : ViewModel() {
    private val _blockData = MutableStateFlow<BlockData?>(null)
    val blockData = _blockData.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    private val client = HttpClient(Android) {
        install(ContentNegotiation) {
            json(kotlinx.serialization.json.Json {
                ignoreUnknownKeys = true
            })
        }
    }

    fun fetchData(chateau: String, blockId: String, serviceDate: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response: BlockData =
                    client.get("https://birch.catenarymaps.org/get_block?chateau=$chateau&block_id=$blockId&service_date=$serviceDate")
                        .body()
                _blockData.value = response
            } catch (e: Exception) {
                // Handle error
            } finally {
                _isLoading.value = false
            }
        }
    }
}

@Composable
fun BlockScreen(
    chateau: String,
    blockId: String,
    serviceDate: String,
    catenaryStack: ArrayDeque<CatenaryStackEnum>,
    onStackChange: (ArrayDeque<CatenaryStackEnum>) -> Unit,
    viewModel: BlockScreenViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val blockData by viewModel.blockData.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    val context = LocalContext.current
    LaunchedEffect(Unit) {
        try {
            val firebaseAnalytics = FirebaseAnalytics.getInstance(context)
            firebaseAnalytics.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW) {
                param(FirebaseAnalytics.Param.SCREEN_NAME, "BlockScreen")
                //param(FirebaseAnalytics.Param.SCREEN_CLASS, "HomeCompose")
                param("chateau_id", chateau)
                param("block_id", blockId)
            }
        } catch (e: Exception) {
            // Log the error or handle it gracefully
            android.util.Log.e("GA", "Failed to log screen view", e)
        }
    }

    var currentTime by remember { mutableStateOf(System.currentTimeMillis() / 1000) }

    LaunchedEffect(Unit) {
        while (true) {
            currentTime = System.currentTimeMillis() / 1000
            delay(1000)
        }
    }

    LaunchedEffect(blockId, serviceDate) {
        viewModel.fetchData(chateau, blockId, serviceDate)
    }

    Column(modifier = Modifier.padding(16.dp)) {
        if (isLoading) {
            CircularProgressIndicator()
        } else if (blockData != null) {
            val data = blockData!!
            val singleRoute = data.routes.size == 1
            val tripDurationSeconds = if (data.trips.isNotEmpty()) {
                data.trips.last().end_time - data.trips.first().start_time
            } else 0

            if (singleRoute) {
                val route = data.routes.values.first()
                Row {
                    route.short_name?.let {
                        Text(
                            text = it,
                            fontWeight = FontWeight.SemiBold,
                            color = parseColor(route.color)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    route.long_name?.let {
                        Text(
                            text = it,
                            color = parseColor(route.color)
                        )
                    }
                }
            }

            Text(text = stringResource(id = R.string.block_screen_block_id, blockId), fontWeight = FontWeight.SemiBold)
            Row {
                Text(text = stringResource(id = R.string.block_screen_duration))
                DiffTimer(diff = tripDurationSeconds.toDouble(), showBrackets = false)
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(data.trips.size) { index ->
                    val trip = data.trips[index]
                    val route = data.routes[trip.route_id]
                    val isTripActive = currentTime in trip.start_time..trip.end_time

                    Column(
                        modifier = Modifier.fillMaxWidth()
                            .let {
                                if (currentTime > trip.end_time) it.then(Modifier.padding(0.dp)) // TODO: figure out text dimming
                                else it
                            }
                    ) {
                        if (!singleRoute && route != null) {
                            Row {
                                route.short_name?.let {
                                    Text(
                                        text = it,
                                        fontWeight = FontWeight.SemiBold,
                                        color = parseColor(route.color)
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                route.long_name?.let {
                                    Text(
                                        text = it,
                                        color = parseColor(route.color)
                                    )
                                }
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.ArrowForward,
                                contentDescription = "Trip direction",
                                modifier = Modifier.size(16.dp)
                            )
                            Text(text = trip.trip_headsign)

                            if (isTripActive) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(Color.Blue)
                                )
                            }

                            Spacer(modifier = Modifier.weight(1f))

                            Icon(
                                imageVector = Icons.Default.OpenInNew,
                                contentDescription = "Open Trip",
                                modifier = Modifier
                                    .clickable {
                                        val newStack = ArrayDeque(catenaryStack)
                                        newStack.addLast(
                                            CatenaryStackEnum.SingleTrip(
                                                chateau_id = chateau,
                                                trip_id = trip.trip_id,
                                                route_id = trip.route_id,
                                                start_time = null,
                                                start_date = serviceDate.replace("-", ""),
                                                vehicle_id = null,
                                                route_type = null
                                            )
                                        )
                                        onStackChange(newStack)
                                    }
                                    .padding(8.dp)
                            )
                        }

                        Row {
                            Text(text = stringResource(id = R.string.block_screen_duration))
                            DiffTimer(
                                diff = (trip.end_time - trip.start_time).toDouble(),
                                showBrackets = false
                            )
                        }

                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(text = trip.first_stop_name)
                            Spacer(modifier = Modifier.weight(1f))
                            FormattedTimeText(
                                timeSeconds = trip.start_time,
                                timezone = trip.timezone_start
                            )
                        }
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(text = trip.last_stop_name)
                            Spacer(modifier = Modifier.weight(1f))
                            FormattedTimeText(
                                timeSeconds = trip.end_time,
                                timezone = trip.timezone_end
                            )
                        }

                        if (index < data.trips.size - 1) {
                            val nextTrip = data.trips[index + 1]
                            val layover = nextTrip.start_time - trip.end_time
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Icon for layover, e.g., self_improvement from material symbols
                                // For now, just text
                                Text(text = stringResource(id = R.string.block_screen_layover))
                                DiffTimer(diff = layover.toDouble(), showBrackets = false)
                            }
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
                }
            }
        } else {
            Text(text = stringResource(id = R.string.block_screen_error))
        }
    }
}