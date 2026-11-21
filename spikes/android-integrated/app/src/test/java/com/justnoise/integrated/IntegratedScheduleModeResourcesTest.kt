package com.justnoise.integrated

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/** Native copy/source guards; these do not claim rendered or alarm-delivery evidence. */
class IntegratedScheduleModeResourcesTest {
    private val appRoot = generateSequence(File(requireNotNull(System.getProperty("user.dir"))).canonicalFile) { it.parentFile }
        .map { File(it, "spikes/android-integrated/app") }
        .first { File(it, "build.gradle.kts").isFile }

    @Test
    fun modeChoiceAndRecoveryCopyLivesInResourcesAndNamesTheScheduleScope() {
        val document = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }.newDocumentBuilder().parse(File(appRoot, "src/main/res/values/schedule_modes.xml"))
        val nodes = document.getElementsByTagName("string")
        val strings = (0 until nodes.length).associate { index ->
            val node = nodes.item(index) as Element
            node.getAttribute("name") to node.textContent
        }
        assertEquals("Choose a mode for this schedule.", strings["integrated_schedule_mode_required"])
        assertTrue(strings.getValue("integrated_schedule_mode_unavailable").contains("Choose another mode"))
        assertTrue(strings.getValue("integrated_schedule_mode_empty").contains("no apps selected"))
        assertTrue(strings.getValue("integrated_schedule_mode_start_help").contains("this schedule’s mode"))
        assertTrue(strings.getValue("integrated_schedule_mode_explanation").contains("Each schedule uses its own selected mode"))
        val plurals = document.getElementsByTagName("plurals").item(0) as Element
        assertEquals("integrated_schedule_mode_app_count", plurals.getAttribute("name"))
        val items = plurals.getElementsByTagName("item")
        assertEquals(setOf("one", "other"), (0 until items.length).map {
            (items.item(it) as Element).getAttribute("quantity")
        }.toSet())
        assertTrue((0 until items.length).all { items.item(it).textContent.contains("%1\$d") })
    }

    @Test
    fun surfaceUsesExplicitModeChoiceAndPerScheduleReadinessNotTheGlobalSelection() {
        val surface = File(appRoot, "src/main/java/com/justnoise/integrated/IntegratedScheduleSurface.kt").readText()
        assertTrue(surface.contains("schedule-mode-choice:"))
        assertTrue(surface.contains("onChange(editor.copy(modeReference = mode.id))"))
        assertTrue(surface.contains("snapshot.modeForReference(schedule.modeReference)?.selectedPackages.isNullOrEmpty()"))
        assertFalse(surface.contains("snapshot.selectedAppCount"))
        assertFalse(surface.contains("DEFAULT_SCHEDULE_MODE_REFERENCE"))
    }
}
