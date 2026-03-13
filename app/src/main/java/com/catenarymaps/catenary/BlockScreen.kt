package com.catenarymaps.catenary

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.FreeBreakfast
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import java.net.URLEncoder

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
                val encodedBlockId = URLEncoder.encode(blockId, "UTF-8")
                val encodedServiceDate = URLEncoder.encode(serviceDate, "UTF-8")
                val encodedChateau = URLEncoder.encode(chateau, "UTF-8")
                val response: BlockData =
                    client.get("https://birch.catenarymaps.org/get_block?chateau=$encodedChateau&block_id=$encodedBlockId&service_date=$encodedServiceDate")
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
    viewModel: BlockScreenViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onBack: () -> Unit,
    onHome: () -> Unit
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(id = R.string.block_screen_block_id, blockId),
                    fontWeight = FontWeight.SemiBold
                )
                NavigationControls(onBack = onBack, onHome = onHome)
            }

            Row {
                Text(text = stringResource(id = R.string.block_screen_duration))
                DiffTimer(diff = tripDurationSeconds.toDouble(), showBrackets = false)
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.windowInsetsPadding(
                    WindowInsets(
                        bottom = WindowInsets.safeDrawing.getBottom(
                            density = LocalDensity.current
                        )
                    )
                )
            ) {
                items(data.trips.size) { index ->
                    val trip = data.trips[index]
                    val isTripActive = currentTime in trip.start_time..trip.end_time
                    val isPast = currentTime > trip.end_time

                    // Main trip row: times on the left, timeline in the middle, content on the right
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min)
                            .alpha(if (isPast) 0.6f else 1f),
                        verticalAlignment = Alignment.Top
                    ) {
                        // Left: start and end times
                        Column(
                            modifier = Modifier
                                .width(72.dp)
                                .padding(vertical = 12.dp),
                            verticalArrangement = Arrangement.SpaceBetween,
                            horizontalAlignment = Alignment.End
                        ) {
                            FormattedTimeText(
                                timeSeconds = trip.start_time,
                                timezone = trip.timezone_start
                            )
                            FormattedTimeText(
                                timeSeconds = trip.end_time,
                                timezone = trip.timezone_end
                            )
                        }

                        // Middle: vertical timeline with start/end dots and active indicator
                        BlockTripTimeline(
                            startTime = trip.start_time,
                            endTime = trip.end_time,
                            currentTime = currentTime
                        )

                        // Right: content card
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                )
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
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = trip.first_stop_name,
                                    fontWeight = FontWeight.Bold
                                )

                                // Route info inner box
                                Column(
                                    modifier = Modifier
                                        .padding(vertical = 6.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                                        )
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    val route = data.routes[trip.route_id]
                                    if (!singleRoute && route != null) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
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

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(top = 4.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.ArrowForward,
                                                contentDescription = "Trip direction",
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = trip.trip_headsign,
                                                fontSize = 14.sp
                                            )
                                        }

                                        Spacer(modifier = Modifier.weight(1f))

                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(
                                                    MaterialTheme.colorScheme.surfaceVariant.copy(
                                                        alpha = 0.9f
                                                    )
                                                )
                                                .clickable {
                                                    val newStack = ArrayDeque(catenaryStack)
                                                    newStack.addLast(
                                                        CatenaryStackEnum.SingleTrip(
                                                            chateau_id = chateau,
                                                            trip_id = trip.trip_id,
                                                            route_id = trip.route_id,
                                                            start_time = null,
                                                            start_date = serviceDate.replace(
                                                                "-",
                                                                ""
                                                            ),
                                                            vehicle_id = null,
                                                            route_type = null
                                                        )
                                                    )
                                                    onStackChange(newStack)
                                                }
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.OpenInNew,
                                                contentDescription = "Open trip",
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                                ) {
                                    Text(
                                        text = stringResource(id = R.string.block_screen_duration),
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    DiffTimer(
                                        diff = (trip.end_time - trip.start_time).toDouble(),
                                        showBrackets = false
                                    )
                                }

                                Text(
                                    text = trip.last_stop_name,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Layover row between trips
                    if (index < data.trips.size - 1) {
                        val nextTrip = data.trips[index + 1]
                        val layover = nextTrip.start_time - trip.end_time
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .alpha(if (isPast) 0.6f else 1f)
                                .padding(bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Left spacer to align with time column
                            Spacer(modifier = Modifier.width(72.dp))

                            // Middle: short connector line
                            Box(
                                modifier = Modifier
                                    .width(24.dp)
                                    .height(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .fillMaxHeight()
                                        .alpha(0.7f)
                                        .background(
                                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                        )
                                )
                            }

                            // Right: layover info
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FreeBreakfast,
                                    contentDescription = stringResource(id = R.string.block_screen_layover),
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                DiffTimer(
                                    diff = layover.toDouble(),
                                    showBrackets = false
                                )
                            }
                        }
                    }
                }
            }
        } else {
            Text(text = stringResource(id = R.string.block_screen_error))
        }
    }
}

@Composable
fun BlockTripTimeline(
    startTime: Long,
    endTime: Long,
    currentTime: Long
) {
    val primary = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    val surface = MaterialTheme.colorScheme.surface

    Canvas(
        modifier = Modifier
            .width(24.dp)
            .fillMaxHeight()
    ) {
        val lineTopPaddingPx = 16.dp.toPx()
        val lineBottomPaddingPx = 16.dp.toPx()

        val centerX = size.width / 2f
        val lineTop = lineTopPaddingPx
        val lineBottom = size.height - lineBottomPaddingPx

        // Vertical line
        val lineWidth = 2.dp.toPx()
        drawRect(
            color = onSurface,
            topLeft = Offset(centerX - lineWidth / 2f, lineTop),
            size = Size(lineWidth, lineBottom - lineTop)
        )

        // Start and end dots
        val outerRadius = 4.dp.toPx()
        val borderWidth = 2.dp.toPx()
        val innerRadius = (outerRadius - borderWidth).coerceAtLeast(0f)

        // Start dot (top)
        val startCenter = Offset(centerX, lineTop)
        drawCircle(color = onSurface, radius = outerRadius, center = startCenter)
        if (innerRadius > 0f) {
            drawCircle(
                color = surface,
                radius = innerRadius,
                center = startCenter
            )
        }

        // End dot (bottom)
        val endCenter = Offset(centerX, lineBottom)
        drawCircle(color = onSurface, radius = outerRadius, center = endCenter)
        if (innerRadius > 0f) {
            drawCircle(
                color = surface,
                radius = innerRadius,
                center = endCenter
            )
        }

        // Active position indicator
        val fraction = if (endTime > startTime) {
            ((currentTime - startTime).toFloat() / (endTime - startTime).toFloat())
                .coerceIn(0f, 1f)
        } else {
            0f
        }

        if (fraction > 0f && fraction < 1f) {
            val indicatorY = lineTop + (lineBottom - lineTop) * fraction
            val indicatorRadius = 5.dp.toPx()
            drawCircle(
                color = primary,
                radius = indicatorRadius,
                center = Offset(centerX, indicatorY)
            )
        }
    }
}