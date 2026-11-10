package com.catenarymaps.catenary

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*

/** Two fixed rows (status and earlier-page progress) precede the connections. */
@Composable
internal fun PreserveConnectionScrollPosition(state: LazyListState, identities: List<String>) {
    var previous by remember { mutableStateOf(identities) }
    SideEffect {
        if (previous != identities) {
            // Composition has the new keys, while layout still describes the visible old
            // cards. Anchor the first surviving card even when a sentinel is first visible.
            val indices = identities.mapIndexed { index, identity -> identity to index + 2 }.toMap()
            val anchor = state.layoutInfo.visibleItemsInfo.firstOrNull {
                it.key is String && indices.containsKey(it.key as String)
            }
            if (anchor != null) state.requestScrollToItem(indices.getValue(anchor.key as String), -anchor.offset)
            previous = identities
        }
    }
}
