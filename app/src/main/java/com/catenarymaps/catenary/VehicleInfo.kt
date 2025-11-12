package com.catenarymaps.catenary

import androidx.compose.foundation.layout.Column

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class VehicleData(
    val manufacturer: String? = null,
    val model: String? = null,
    val years: List<String>? = null,
    val engine: String? = null,
    val transmission: String? = null,
    val notes: String? = null
)

@Serializable
data class VehicleDetailsResponse(
    val found_data: Boolean,
    val vehicle: VehicleData?
)

@Composable
fun VehicleInfo(label: String, chateau: String, routeId: String?) {
    var vehicleData by remember { mutableStateOf<VehicleData?>(null) }
    var loading by remember { mutableStateOf(true) }

    

    LaunchedEffect(label, chateau, routeId) {
        loading = true
        val client = HttpClient() {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                })
            }
        }

        val url = if (routeId == null) {
            "https://birch.catenarymaps.org/get_vehicle?chateau=$chateau&label=$label"
        } else {
            "https://birch.catenarymaps.org/get_vehicle?chateau=$chateau&label=$label&route_id=$routeId"
        }

        try {
            val response = client.get(url).body<VehicleDetailsResponse>()
            if (response.found_data) {
                println("Found vehicle data")
                vehicleData = response.vehicle
            }
        } catch (e: Exception) {
            // Handle error
        } finally {
            loading = false
            client.close()
        }
    }

    if (loading) {
        //Text("...", modifier = Modifier) // TODO: Add loading animation
    } else {
        vehicleData?.let {
            Column(
                verticalArrangement = Arrangement.spacedBy((-6).dp)
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        it.manufacturer?.let { manufacturer ->
                            Text(
                                text = manufacturer,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                        it.model?.let { model ->
                            Text(text = " $model", fontStyle = FontStyle.Italic, fontSize = 14.sp)
                        }
                        it.years?.let { years ->
                            Text(text = " ${years.joinToString(",")}", fontSize = 14.sp)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        it.engine?.let { engine ->
                            Icon(
                                imageVector = ImageVector.vectorResource(id = R.drawable.ic_engine),
                                contentDescription = "Engine",
                                modifier = Modifier.size(14.dp)
                            )
                            Text(text = " $engine", fontSize = 12.sp)
                        }

                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    it.transmission?.let { transmission ->
                        Icon(
                            imageVector = ImageVector.vectorResource(id = R.drawable.ic_transmission),
                            contentDescription = "Transmission",
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = " $transmission",
                            // fontWeight = FontWeight.Light,
                            fontSize = 12.sp
                        )
                    }
                }
                it.notes?.let { notes ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = notes, fontSize = 12.sp)
                }
            }
        }
    }
}