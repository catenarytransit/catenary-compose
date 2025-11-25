package com.catenarymaps.catenary

import org.maplibre.compose.expressions.dsl.Feature.get
import org.maplibre.compose.expressions.dsl.any
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.eq
import org.maplibre.compose.expressions.value.EquatableValue
import org.maplibre.compose.expressions.value.NumberValue

fun rtEq(v: Int) =
    get("route_type").cast<NumberValue<EquatableValue>>().eq(const(v))

fun isMetro() =
    any(rtEq(1), rtEq(12))

fun isTram() =
    any(rtEq(0), rtEq(5))

fun isIntercity() =
    rtEq(2)
