package com.catenarymaps.catenary

fun occupancy_to_symbol(status: String?): String {
    return when (status) {
        "EMPTY" -> " " // "🚶"
        "MANY_SEATS_AVAILABLE" -> " " // "🚶"
        "FEW_SEATS_AVAILABLE" -> "👥"
        "STANDING_ROOM_ONLY" -> "👨‍👩‍👧‍👦"
        "CRUSHED_STANDING_ROOM_ONLY" -> " crammed "
        "FULL" -> " full "
        "NOT_ACCEPTING_PASSENGERS" -> "❌"
        else -> ""
    }
}