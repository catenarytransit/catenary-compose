package com.catenarymaps.catenary

fun occupancy_to_symbol(status: Int?): String {
    return when (status) {
        0 -> "∅"
        1 -> "▢"
        2 -> "▣"
        3 -> "╬"
        4 -> "╬☹╬"
        5 -> "■"
        6 -> "✗"
        8 -> "✗"
        else -> ""
    }
}