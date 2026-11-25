package com.catenarymaps.catenary

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Prepare immutable presentation data without blocking composition. Key changes cancel old work. */
@Composable
internal fun <K, T> rememberBackgroundValue(
    input: K,
    initialValue: T,
    calculate: (K) -> T
): T = key(input) {
    produceState(initialValue, input) {
        value = withContext(Dispatchers.Default) { calculate(input) }
    }.value
}
