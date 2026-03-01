const fs = require('fs');
const euro = fs.readFileSync('euro_points.txt', 'utf8');
const swiss = fs.readFileSync('swiss_points.txt', 'utf8');

const code = `package com.catenarymaps.catenary

import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.PolyUtil

object EurostyleZone {

${euro}
${swiss}
    fun isInside(lat: Double, lon: Double): Boolean {
        val point = LatLng(lat, lon)
        return PolyUtil.containsLocation(point, P1, false) ||
                PolyUtil.containsLocation(point, P2, false)
    }

    fun isSwitzerland(lat: Double, lon: Double): Boolean {
        val point = LatLng(lat, lon)
        return PolyUtil.containsLocation(point, P1_SWISS, false) ||
                PolyUtil.containsLocation(point, P2_SWISS, false) ||
                PolyUtil.containsLocation(point, P3_SWISS, false)
    }
}
`;

fs.writeFileSync('app/src/main/java/com/catenarymaps/catenary/EurostyleZone.kt', code);
