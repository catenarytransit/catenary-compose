package com.catenarymaps.catenary

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guard against English fallbacks in supported non-English journey-planner locales. */
class TripPlannerLocalizationTest {
    private fun stringKeys(file: File): List<String> {
        val strings = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder().parse(file).getElementsByTagName("string")
        return (0 until strings.length).mapNotNull { index ->
            strings.item(index).attributes?.getNamedItem("name")?.nodeValue
        }
    }

    @Test
    fun everySupportedLocaleProvidesAllPlannerLabels() {
        // Gradle runs JVM tests from the module directory; the second path supports root invocations.
        val resources = listOf(File("src/main/res"), File("app/src/main/res"))
            .firstOrNull { it.isDirectory } ?: error("Cannot find Android resources")
        val expected = stringKeys(File(resources, "values/directions.xml")).toSet()
        val errors = mutableListOf<String>()
        resources.listFiles().orEmpty().filter {
            it.isDirectory && it.name.startsWith("values-") && it.name != "values-en"
        }.forEach { locale ->
            val names = listOf("directions.xml", "advanced_search.xml")
                .map { File(locale, it) }.filter { it.isFile }
                .flatMap(::stringKeys)
            val missing = expected - names.toSet()
            val duplicates = names.groupingBy { it }.eachCount()
                .filterValues { it > 1 }.keys
            if (missing.isNotEmpty()) {
                errors.add(locale.name + " missing: " + missing.sorted().joinToString(", "))
            }
            if (duplicates.isNotEmpty()) {
                errors.add(locale.name + " duplicates: " + duplicates.sorted().joinToString(", "))
            }
        }
        assertTrue(errors.joinToString("\n"), errors.isEmpty())
    }
}
