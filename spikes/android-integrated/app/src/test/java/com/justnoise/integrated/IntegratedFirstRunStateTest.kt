package com.justnoise.integrated

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

class IntegratedFirstRunStateTest {
    private val fresh = IntegratedFirstRunFacts(false, false, false, false, false)

    @Test fun `onboarding explains custom modes and independent schedules without implying pairing is consent`() {
        val appRoot = generateSequence(File(requireNotNull(System.getProperty("user.dir"))).canonicalFile) { it.parentFile }
            .map { File(it, "spikes/android-integrated/app") }
            .first { File(it, "build.gradle.kts").isFile }
        val document = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }.newDocumentBuilder().parse(File(appRoot, "src/main/res/values/first_run.xml"))
        val nodes = document.getElementsByTagName("string")
        val strings = (0 until nodes.length).associate { index ->
            val node = nodes.item(index) as Element
            node.getAttribute("name") to node.textContent
        }
        assertEquals("Create a Mode", strings["first_run_select_apps"])
        assertTrue(strings.getValue("first_run_select_apps_body").contains("choose the apps it blocks"))
        val sessions = strings.getValue("first_run_stay_focused_body")
        assertTrue(sessions.contains("last selected mode"))
        assertTrue(sessions.contains("tap again to stop"))
        assertTrue(sessions.contains("Schedules start automatically with their saved mode"))
        assertTrue(strings.getValue("first_run_connected_body").contains("Pairing alone does not enable blocking"))
    }

    @Test fun `fresh installation starts with two page tutorial`() {
        assertEquals(IntegratedFirstRunRoute.ONBOARDING, IntegratedFirstRunRules.route(fresh))
        assertEquals(0, IntegratedFirstRunRules.page(-100))
        assertEquals(1, IntegratedFirstRunRules.page(100))
    }

    @Test fun `durable tutorial completion advances only to pairing`() {
        assertEquals(IntegratedFirstRunRoute.ACTIVATION,
            IntegratedFirstRunRules.route(fresh.copy(tutorialCompleted = true)))
    }

    @Test fun `paired confirmation survives cold launch before explicit setup`() {
        assertEquals(IntegratedFirstRunRoute.ACTIVATION,
            IntegratedFirstRunRules.route(fresh.copy(tutorialCompleted = true, zapPaired = true)))
    }

    @Test fun `legacy pairing bypasses new tutorial without pretending permissions are ready`() {
        assertEquals(IntegratedFirstRunRoute.MAIN,
            IntegratedFirstRunRules.route(fresh.copy(zapPaired = true)))
    }

    @Test fun `existing selection or disclosure setup is not reset by new onboarding`() {
        assertEquals(IntegratedFirstRunRoute.MAIN,
            IntegratedFirstRunRules.route(fresh.copy(existingSetup = true), IntegratedFirstRunRoute.ONBOARDING))
    }

    @Test fun `active and recovery routes override every restored introduction`() {
        for (route in IntegratedFirstRunRoute.entries) {
            for (facts in listOf(fresh.copy(sessionRequested = true), fresh.copy(recoveryRequired = true))) {
                assertEquals(IntegratedFirstRunRoute.MAIN, IntegratedFirstRunRules.route(facts, route))
            }
        }
    }

    @Test fun `rotation after skip preserves normal app route without setting pairing or consent`() {
        assertEquals(IntegratedFirstRunRoute.MAIN,
            IntegratedFirstRunRules.route(fresh.copy(tutorialCompleted = true), IntegratedFirstRunRoute.MAIN))
        assertEquals(IntegratedFirstRunRoute.ACTIVATION,
            IntegratedFirstRunRules.route(fresh.copy(tutorialCompleted = true)))
    }

    @Test fun `stale saved page cannot undo committed tutorial or manufacture completion`() {
        assertEquals(IntegratedFirstRunRoute.ACTIVATION,
            IntegratedFirstRunRules.route(fresh.copy(tutorialCompleted = true), IntegratedFirstRunRoute.ONBOARDING))
        assertEquals(IntegratedFirstRunRoute.ONBOARDING,
            IntegratedFirstRunRules.route(fresh, IntegratedFirstRunRoute.ACTIVATION))
    }
}
