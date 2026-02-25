package com.catenarymaps.catenary

import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.PolyUtil

object EurostyleZone {

    private val P1 =
        listOf(
            LatLng(36.265397486051356, 35.05156915572351),
            LatLng(36.265397486051356, 31.51620103147289),
            LatLng(34.03747988838202, 31.51620103147289),
            LatLng(34.03747988838202, 35.05156915572351),
            LatLng(36.265397486051356, 35.05156915572351)
        )

    private val P2 =
        listOf(
            LatLng(35.9120098, -6.3870015),
            LatLng(36.0169147, -4.2307423),
            LatLng(37.8970836, 4.1309351),
            LatLng(38.0715542, 10.8581061),
            LatLng(34.2127088, 16.1252774),
            LatLng(34.3589922, 29.0497651),
            LatLng(39.7544833, 24.7080723),
            LatLng(40.7356351, 26.0844406),
            LatLng(40.9181495, 26.3236653),
            LatLng(41.0807905, 26.4047249),
            LatLng(41.3644062, 26.6985377),
            LatLng(41.7490575, 26.4039552),
            LatLng(41.9434746, 27.2226981),
            LatLng(41.9434746, 28.2882417),
            LatLng(45.0644799, 30.3515152),
            LatLng(44.6279267, 36.6775107),
            LatLng(47.155096, 38.1313784),
            LatLng(47.8392695, 39.994693),
            LatLng(49.5940133, 40.2917431),
            LatLng(52.569923, 33.7604953),
            LatLng(52.2019573, 31.725737),
            LatLng(51.9269669, 24.4561162),
            LatLng(52.0726432, 23.6583495),
            LatLng(52.2899562, 23.1611515),
            LatLng(52.617074, 24.1349089),
            LatLng(54.1814797, 25.8087735),
            LatLng(56.0741175, 28.2635078),
            LatLng(59.8837398, 28.3776815),
            LatLng(61.008257, 28.5203979),
            LatLng(62.8084654, 32.5737724),
            LatLng(71.5588343, 31.1926753),
            LatLng(71.0307763, 13.8758433),
            LatLng(62.343639, 1.924042),
            LatLng(51.8380854, 2.4267964),
            LatLng(50.6209964, 0.9204065),
            LatLng(49.6707394, -4.4719831),
            LatLng(47.1774178, -10.2348455),
            LatLng(35.5054177, -10.976326),
            LatLng(35.9120098, -6.3870015)
        )

    fun isInside(lat: Double, lon: Double): Boolean {
        val point = LatLng(lat, lon)
        return PolyUtil.containsLocation(point, P1, false) ||
                PolyUtil.containsLocation(point, P2, false)
    }
}
