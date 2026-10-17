package com.catenarymaps.catenary

import io.ktor.http.Url
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchRequestUrlsTest {
    @Test
    fun cypressEncodesAmpersandInSearchText() {
        val urlString = buildCypressSearchUrl("T&T", 34.05, -118.25)
        val url = Url(urlString)

        assertTrue(urlString.contains("text=T%26T"))
        assertEquals("T&T", url.parameters["text"])
        assertEquals(1, url.parameters.getAll("text")?.size)
        assertEquals("34.05", url.parameters["focus.point.lat"])
        assertEquals("-118.25", url.parameters["focus.point.lon"])
        assertEquals("4", url.parameters["focus.point.weight"])
    }

    @Test
    fun cypressEncodesOtherReservedCharactersAndUnicode() {
        val search = "A+B / #50% 東京"
        val url = Url(buildCypressSearchUrl(search, 0.0, 0.0))

        assertEquals(search, url.parameters["text"])
        assertEquals(1, url.parameters.getAll("text")?.size)
    }

    @Test
    fun cypressSearchTextCannotOverrideOtherParameters() {
        val search = "T&T&focus.point.weight=0"
        val url = Url(buildCypressSearchUrl(search, 34.05, -118.25))

        assertEquals(search, url.parameters["text"])
        assertEquals("4", url.parameters["focus.point.weight"])
        assertEquals(1, url.parameters.getAll("focus.point.weight")?.size)
    }

    @Test
    fun birchEncodesTextAndPreservesOptionalLocation() {
        val url = Url(buildCatenarySearchUrl("T&T", 34.05, -118.25, 33.0 to -117.0))

        assertEquals("T&T", url.parameters["text"])
        assertEquals("34.05", url.parameters["map_lat"])
        assertEquals("-118.25", url.parameters["map_lon"])
        assertEquals("10", url.parameters["map_z"])
        assertEquals("33.0", url.parameters["user_lat"])
        assertEquals("-117.0", url.parameters["user_lon"])
    }

    @Test
    fun birchOmitsAbsentUserLocation() {
        val urlString = buildCatenarySearchUrl("T&T", 34.05, -118.25, null)
        val url = Url(urlString)

        assertTrue(urlString.contains("text=T%26T"))
        assertFalse(urlString.contains("user_lat="))
        assertNull(url.parameters["user_lat"])
        assertNull(url.parameters["user_lon"])
    }
}
