package com.catenarymaps.catenary

object NationalRailUtils {
    data class AgencyInfo(val name: String, val icon: String?)

    private val AGENCY_MAP =
        mapOf(
            "GW" to AgencyInfo("Great Western Railway", "GreaterWesternRailway.svg"),
            "GWR" to AgencyInfo("Great Western Railway", "GreaterWesternRailway.svg"),
            "SW" to AgencyInfo("South Western Railway", "SouthWesternRailway.svg"),
            "SN" to AgencyInfo("Southern", "SouthernIcon.svg"),
            "CC" to AgencyInfo("c2c", "c2c_logo.svg"),
            "LE" to AgencyInfo("Greater Anglia", null),
            "CH" to AgencyInfo("Chiltern Railways", null),
            "VT" to AgencyInfo("Avanti West Coast", null),
            "HT" to AgencyInfo("Hull Trains", null),
            "GN" to AgencyInfo("Great Northern", null),
            "TL" to AgencyInfo("Thameslink", null),
            "LO" to AgencyInfo("London Overground", "uk-london-overground.svg"),
            "AW" to AgencyInfo("Transport for Wales", null),
            "SR" to AgencyInfo("ScotRail", null),
            "GR" to AgencyInfo("London North Eastern Railway", null),
            "EM" to AgencyInfo("East Midlands Railway", null),
            "LM" to AgencyInfo("West Midlands Railway", null),
            "SE" to AgencyInfo("Southeastern", null),
            "XC" to AgencyInfo("CrossCountry", null),
            "XR" to
                    AgencyInfo(
                        "Elizabeth Line",
                        "Elizabeth_line_roundel.svg"
                    ) // Elizabeth Line usually XR or EL
        )

    private val NAME_MAP =
        mapOf(
            "gwr" to AgencyInfo("Great Western Railway", "GreaterWesternRailway.svg"),
            "london overground" to
                    AgencyInfo("London Overground", "uk-london-overground.svg"),
            "c2c" to AgencyInfo("c2c", "c2c_logo.svg"),
            "elizabeth line" to AgencyInfo("Elizabeth Line", "Elizabeth_line_roundel.svg")
        )

    fun getAgencyInfo(agencyId: String?, agencyName: String?): AgencyInfo? {
        if (agencyId != null) {
            AGENCY_MAP[agencyId]?.let {
                return it
            }
        }
        if (agencyName != null) {
            NAME_MAP[agencyName.trim().lowercase()]?.let {
                return it
            }
        }
        return null
    }

    fun getAgencyIconUrl(agencyId: String?, agencyName: String?): String? {
        val info = getAgencyInfo(agencyId, agencyName)
        return info?.icon?.let { "https://maps.catenarymaps.org/agencyicons/$it" }
    }

    // List of agencies that should be consolidated/handled
    val KNOWN_AGENCIES = AGENCY_MAP.keys
}
