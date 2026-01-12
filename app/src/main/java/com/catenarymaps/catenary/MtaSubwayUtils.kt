package com.catenarymaps.catenary

import androidx.compose.ui.graphics.Color

object MtaSubwayUtils {
    const val MTA_CHATEAU_ID = "nyct"

    // Colors
    val MTA_RED = Color(0xFFEE352E)
    val MTA_GREEN = Color(0xFF00933C)
    val MTA_BLUE = Color(0xFF0039A6)
    val MTA_ORANGE = Color(0xFFFF6319)
    val MTA_BROWN = Color(0xFF996633)
    val MTA_GRAY = Color(0xFFA7A9AC)
    val MTA_YELLOW = Color(0xFFFCCC0A)
    val MTA_PURPLE = Color(0xFFB933AD)
    val MTA_GREEN_2 = Color(0xFF6CBE45) // G train

    fun getMtaSubwayColor(shortName: String): Color {
        val s = shortName.uppercase()
        return when (s) {
            "1", "2", "3" -> MTA_RED
            "4", "5", "6", "6X" -> MTA_GREEN
            "A", "C", "E" -> MTA_BLUE
            "B", "D", "F", "FX", "M" -> MTA_ORANGE
            "G" -> MTA_GREEN_2
            "J", "Z" -> MTA_BROWN
            "L", "GS", "FS", "H" -> MTA_GRAY
            "N", "Q", "R", "W" -> MTA_YELLOW
            "7", "7X" -> MTA_PURPLE
            else -> Color.Gray
        }
    }

    fun getMtaSymbolShortName(shortName: String): String {
        val s = shortName.uppercase()
        return when (s) {
            "6X" -> "6"
            "7X" -> "7"
            "FX" -> "F"
            "GS", "FS", "H" -> "S"
            else -> shortName
        }
    }

    private val SUBWAY_ROUTE_IDS =
            setOf(
                    "A",
                    "C",
                    "E",
                    "B",
                    "D",
                    "F",
                    "FX",
                    "M",
                    "G",
                    "J",
                    "Z",
                    "L",
                    "N",
                    "Q",
                    "R",
                    "W",
                    "GS",
                    "FS",
                    "H",
                    "1",
                    "2",
                    "3",
                    "4",
                    "5",
                    "6",
                    "6X",
                    "7",
                    "7X"
            )

    fun isSubwayRouteId(routeId: String): Boolean {
        return SUBWAY_ROUTE_IDS.contains(routeId.uppercase())
    }

    fun isExpress(routeId: String): Boolean {
        return routeId.uppercase().endsWith("X")
    }

    fun getMtaIconUrl(routeId: String): String? {
        val s = routeId.uppercase()
        val iconName =
                when {
                    s == "6X" -> "6d"
                    s == "7X" -> "7d"
                    s == "FX" -> "fd"
                    s.endsWith("X") -> "${s.dropLast(1).lowercase()}d"
                    s == "GS" || s == "FS" || s == "H" -> "s"
                    s == "SIR" -> "sir"
                    isSubwayRouteId(s) -> s.lowercase()
                    else -> return null
                }
        return "https://maps.catenarymaps.org/mtaicons/$iconName.svg"
    }
}
