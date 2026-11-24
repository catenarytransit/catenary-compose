package com.catenarymaps.catenary

import kotlinx.serialization.json.*

fun DirectionsItinerary.identity(): String = id?.takeIf { it.isNotEmpty() }
    ?: buildJsonArray {
        add(startTime); add(endTime)
        add(buildJsonArray {
            legs.forEach { leg ->
                add(buildJsonArray {
                    add(leg.mode); add(leg.startTime); add(leg.endTime); add(leg.routeId)
                    add(leg.from?.name); add(leg.to?.name)
                })
            }
        })
    }.toString()
