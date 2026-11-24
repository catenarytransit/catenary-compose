package com.catenarymaps.catenary

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveJourneyNavigationTest {
    @Test
    fun opensFromExploreAndBackReturnsHome() {
        val stack = pushActiveJourneyDetails(ArrayDeque(), "journey-1")
        assertEquals(
            listOf(CatenaryStackEnum.ActiveJourneyDetailsStack("journey-1")),
            stack.toList()
        )
        stack.removeLast()
        assertTrue(stack.isEmpty())
    }

    @Test
    fun preservesTheUnderlyingPlannerResultsAndOtherScreens() {
        val plannerStack =
            ArrayDeque<CatenaryStackEnum>().apply { add(CatenaryStackEnum.DirectionsStack) }
        val opened = pushActiveJourneyDetails(plannerStack, "journey-1")
        assertEquals(listOf(CatenaryStackEnum.DirectionsStack), plannerStack.toList())
        opened.removeLast()
        assertEquals(plannerStack.toList(), opened.toList())
        val stop = CatenaryStackEnum.StopStack("feed", "stop")
        val fromStop = pushActiveJourneyDetails(
            ArrayDeque<CatenaryStackEnum>().apply { add(stop) },
            "journey-1"
        )
        fromStop.removeLast()
        assertEquals(listOf(stop), fromStop.toList())
    }

    @Test
    fun reopeningGuidanceDoesNotPileUpDetailsScreens() {
        val opened = pushActiveJourneyDetails(ArrayDeque(), "old")
        val updated = pushActiveJourneyDetails(opened, "new")
        assertEquals(listOf(CatenaryStackEnum.ActiveJourneyDetailsStack("new")), updated.toList())
    }

    @Test
    fun activeDestinationCanBeRestoredWithTheStackSaver() {
        val stack = listOf<CatenaryStackEnum>(
            CatenaryStackEnum.DirectionsStack,
            CatenaryStackEnum.ActiveJourneyDetailsStack("journey-1")
        )
        val encoded = Json.encodeToString(stack)
        val restored = Json.decodeFromString<List<CatenaryStackEnum>>(encoded)
        assertEquals(stack, restored)
    }
}
