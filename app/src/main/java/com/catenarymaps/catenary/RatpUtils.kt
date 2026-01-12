package com.catenarymaps.catenary

object RatpUtils {
    // Île-de-France Mobilités (IDFM) chateau ID
    const val IDFM_CHATEAU_ID = "île~de~france~mobilités"

    // Map of route short names to icon file names
    // Keys are lowercase versions of route short names
    private val RATP_ICON_MAP =
            mapOf(
                    // Metro lines
                    "1" to "metro_1",
                    "2" to "metro_2",
                    "3" to "metro_3",
                    "3b" to "metro_3bis",
                    "3bis" to "metro_3bis",
                    "4" to "metro_4",
                    "5" to "metro_5",
                    "6" to "metro_6",
                    "7" to "metro_7",
                    "7b" to "metro_7bis",
                    "7bis" to "metro_7bis",
                    "8" to "metro_8",
                    "9" to "metro_9",
                    "10" to "metro_10",
                    "11" to "metro_11",
                    "12" to "metro_12",
                    "13" to "metro_13",
                    "14" to "metro_14",
                    "15" to "metro_15",
                    "16" to "metro_16",
                    "17" to "metro_17",
                    "18" to "metro_18",
                    "19" to "metro_19",

                    // RER lines
                    "a" to "rer_a",
                    "b" to "rer_b",
                    "c" to "rer_c",
                    "d" to "rer_d",
                    "e" to "rer_e",

                    // Transilien train lines
                    "h" to "train_h",
                    "j" to "train_j",
                    "k" to "train_k",
                    "l" to "train_l",
                    "n" to "train_n",
                    "p" to "train_p",
                    "r" to "train_r",
                    "u" to "train_u",
                    "v" to "train_v",

                    // Tram lines
                    "t1" to "tram_1",
                    "t2" to "tram_2",
                    "t3a" to "tram_3a",
                    "t3b" to "tram_3b",
                    "t4" to "tram_4",
                    "t5" to "tram_5",
                    "t6" to "tram_6",
                    "t7" to "tram_7",
                    "t8" to "tram_8",
                    "t9" to "tram_9",
                    "t10" to "tram_10",
                    "t11" to "tram_11",
                    "t12" to "tram_12",
                    "t13" to "tram_13",
                    "t14" to "tram_14"
            )

    /** Check if a route has a RATP icon available */
    fun isRatpRoute(routeShortName: String?): Boolean {
        if (routeShortName == null) return false
        val normalized = routeShortName.lowercase().trim()
        return RATP_ICON_MAP.containsKey(normalized)
    }

    /**
     * Get the icon path for a RATP route
     * @returns The path to the SVG icon, or null if not found
     */
    fun getRatpIconUrl(routeShortName: String?): String? {
        if (routeShortName == null) return null
        val normalized = routeShortName.lowercase().trim()
        val iconName = RATP_ICON_MAP[normalized] ?: return null
        // Assuming the icons are hosted on the same server or can be loaded via generic URL
        // From TS file: return `/ratp/${iconName}.svg`;
        // Android needs full URL or local resource. Assuming web URL for now.
        return "https://maps.catenarymaps.org/ratp/$iconName.svg"
    }

    /** Check if this is the IDFM chateau */
    fun isIdfmChateau(chateauId: String?): Boolean {
        return chateauId == IDFM_CHATEAU_ID
    }
}
