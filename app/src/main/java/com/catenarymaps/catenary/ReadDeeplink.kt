package com.catenarymaps.catenary

import android.net.Uri
import androidx.compose.runtime.MutableState
import org.maplibre.compose.camera.CameraState

fun readDeeplink(
    uri: Uri?,
    catenaryStack: ArrayDeque<CatenaryStackEnum>,
    camera: CameraState,
    reassigncatenarystack: (newChateauStack: ArrayDeque<CatenaryStackEnum>) -> Unit
) {
    if (uri != null) {
        val screen = uri.getQueryParameter("screen")

        if (screen != null) {
            when (screen) {
                "route" -> {
                    val chateau = uri.getQueryParameter("chateau")

                    val route_id = uri.getQueryParameter("route_id")

                    if (chateau != null && route_id != null) {

                        val newStack = ArrayDeque(catenaryStack)

                        newStack.addLast(
                            CatenaryStackEnum.RouteStack(
                                chateau_id = chateau,
                                route_id = route_id,
                            )
                        )

                        reassigncatenarystack(newStack)
                    }
                }

                else -> {}
            }
        }
    }
}
