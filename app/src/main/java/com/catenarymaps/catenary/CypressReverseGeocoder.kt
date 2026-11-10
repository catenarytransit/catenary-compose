package com.catenarymaps.catenary

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

/** Naming a pin must not prevent selecting it when geocoding is unavailable. */
internal suspend fun reverseMapLocationName(
    client: HttpClient, latitude: Double, longitude: Double, language: String
): String? = withTimeoutOrNull(3_000L) {
    try {
        val response = client.get("https://cypress.catenarymaps.org/v2/reverse") {
            url {
                parameters.append("lat", latitude.toString())
                parameters.append("lon", longitude.toString())
                parameters.append("lang", language)
                parameters.append("size", "1")
            }
        }
        if (!response.status.isSuccess()) return@withTimeoutOrNull null
        response.body<CypressResponse>().features.firstOrNull()?.properties?.name
            ?.trim()?.takeIf { it.isNotEmpty() }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }
}
