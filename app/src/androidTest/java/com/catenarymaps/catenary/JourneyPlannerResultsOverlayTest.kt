package com.catenarymaps.catenary

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class JourneyPlannerResultsOverlayTest {
    @get:Rule val compose = createComposeRule()

    @Test fun switchingFromListToMapReleasesMapPaneForTouch() {
        var usingMap by mutableStateOf(false)
        var mapTaps = 0
        var resultsTaps = 0
        var drawerTaps = 0
        compose.setContent {
            MaterialTheme {
                Box(Modifier.fillMaxSize().testTag("viewport")) {
                    Box(Modifier.fillMaxSize().clickable { mapTaps++ })
                    JourneyPlannerResultsOverlay(usingMap) {
                        // Surfaces and scrollable content consume touch within the results pane.
                        Surface(Modifier.fillMaxSize().clickable { resultsTaps++ }) { }
                    }
                    if (usingMap) Surface(
                        Modifier.align(Alignment.BottomStart).fillMaxWidth(0.5f).height(100.dp)
                            .clickable { drawerTaps++ }
                    ) { }
                }
            }
        }
        fun tapAt(fraction: Float) {
            compose.onNodeWithTag("viewport").performTouchInput {
                click(Offset(size.width * fraction, size.height * 0.5f))
            }
        }
        tapAt(0.75f)
        compose.runOnIdle { assertEquals(0, mapTaps); assertEquals(1, resultsTaps); usingMap = true }
        compose.waitForIdle()
        tapAt(0.75f)
        compose.runOnIdle { assertEquals(1, mapTaps); assertEquals(1, resultsTaps) }
        tapAt(0.25f)
        compose.runOnIdle { assertEquals(2, mapTaps); assertEquals(1, resultsTaps) }
        compose.onNodeWithTag("viewport").performTouchInput {
            click(Offset(size.width * 0.25f, size.height - 10f))
        }
        compose.runOnIdle { assertEquals(1, drawerTaps); usingMap = false }
        compose.waitForIdle()
        tapAt(0.75f)
        compose.runOnIdle { assertEquals(2, mapTaps); assertEquals(2, resultsTaps) }
    }
}
