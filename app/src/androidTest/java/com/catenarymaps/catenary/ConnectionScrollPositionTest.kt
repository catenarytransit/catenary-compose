package com.catenarymaps.catenary

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ConnectionScrollPositionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun prependAndAppendKeepTheVisibleCardAtTheSamePixel() = checkAnchor(5, 23)
    @Test fun prependKeepsCardsInPlaceWhenEarlierSpinnerIsVisible() = checkAnchor(1, 12)

    private fun checkAnchor(firstIndex: Int, scrollOffset: Int) {
        var identities by mutableStateOf((0..11).map { "connection-$it" })
        lateinit var state: LazyListState
        compose.setContent {
            state = rememberLazyListState()
            PreserveConnectionScrollPosition(state, identities)
            LazyColumn(Modifier.width(300.dp).height(400.dp), state = state,
                contentPadding = PaddingValues(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item(key = "status") { Spacer(Modifier.height(1.dp)) }
                item(key = "earlier") { Spacer(Modifier.height(48.dp)) }
                items(identities, key = { it }) { Spacer(Modifier.fillMaxWidth().height(100.dp)) }
                item(key = "later") { Spacer(Modifier.height(48.dp)) }
            }
        }
        compose.runOnIdle { state.requestScrollToItem(firstIndex, scrollOffset) }
        compose.waitForIdle()
        var anchorKey: Any = ""
        var anchorOffset = 0
        compose.runOnIdle {
            val anchor = state.layoutInfo.visibleItemsInfo.first { it.key.toString().startsWith("connection-") }
            anchorKey = anchor.key; anchorOffset = anchor.offset
            identities = (-4..-1).map { "connection-$it" } + identities
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(anchorOffset, state.layoutInfo.visibleItemsInfo.first { it.key == anchorKey }.offset)
            identities = identities + (12..16).map { "connection-$it" }
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(anchorOffset, state.layoutInfo.visibleItemsInfo.first { it.key == anchorKey }.offset)
        }
    }
}
