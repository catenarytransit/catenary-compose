package com.catenarymaps.catenary

import kotlinx.serialization.Serializable

@Serializable
data class RustCoord(var x: Float, var y: Float)

@Serializable
data class RustRect(
    var min: RustCoord,
    var max: RustCoord
)
