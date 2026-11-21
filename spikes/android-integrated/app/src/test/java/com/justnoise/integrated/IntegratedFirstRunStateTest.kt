package com.justnoise.integrated

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

class IntegratedFirstRunStateTest {
    private val fresh = IntegratedFirstRunFacts(false, false, false, false, false)

    @Test fun `onboarding copy matches iOS welcome and first-mode structure`() {
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
        assertEquals("Make space for you.", strings["first_run_select_apps"])
        assertEquals("Do more for what matters.", strings["first_run_select_apps_body"])
        assertEquals("Setup your Zap", strings["first_run_get_started"])
        assertEquals("Create your first mode", strings["first_run_mode_title"])
        assertEquals("What do you want to focus on?", strings["first_run_mode_headline"])
        assertEquals("A mode is a set of apps you block during focus time.", strings["first_run_mode_body"])
        assertEquals("Mode name", strings["first_run_mode_name"])
        assertEquals("Choose apps to block", strings["first_run_mode_choose_apps"])
        assertEquals("Save mode & continue", strings["first_run_mode_save"])
        assertEquals("Connect your Zap", strings["first_run_activate_zap"])
        assertEquals("Tap the Zap on your screen to start.", strings["first_run_activation_title"])
        assertTrue(strings.getValue("first_run_activation_body").contains("back of your phone"))
        assertEquals("Watch how it works", strings["first_run_watch_how_it_works"])
        assertEquals("How it works", strings["first_session_guide_title"])
        assertEquals("Tap", strings["first_session_step_choose"])
        assertEquals("Tap the Zap button in the app.", strings["first_session_guide_choose"])
        assertEquals("Hold the back of your phone against Zap.", strings["first_session_step_hold_body"])
        assertEquals("Wait until Justnoise confirms. You’re all set.", strings["first_session_step_done_body"])
        assertEquals("Got it – start scan", strings["first_session_guide_done"])
        assertEquals("Continue", strings["first_session_guide_continue"])
        assertEquals("Next, allow Justnoise to block distractions.", strings["first_run_connected_body"])
        assertEquals("One last step", strings["first_run_blocking_title"])
        assertEquals("Allow Justnoise to block distractions.", strings["first_run_blocking_intro"])
        assertEquals(null, strings["first_run_blocking_body"])
        assertTrue(strings.getValue("first_run_accessibility_disclosure").contains("detect opened apps and window changes"))
        assertTrue(strings.getValue("first_run_accessibility_disclosure").contains("stays on your phone"))
        assertTrue(strings.getValue("first_run_accessibility_disclosure").contains("We don’t read your screen"))
        assertEquals("Agree & open settings", strings["first_run_agree_open_settings"])
        assertEquals("Open settings", strings["first_run_open_android_settings"])
        assertTrue(strings.getValue("first_run_accessibility_disclosure").split(Regex("\\s+")).size <= 45)
        listOf("Android Accessibility", "Zap sessions", "passwords", "typing", "notifications", "Turn access off").forEach {
            assertTrue(strings.getValue("first_run_accessibility_disclosure").contains(it))
        }
        assertEquals("Switch on Justnoise", strings["first_run_accessibility_turn_on"])
        assertEquals("Turn on · Tap Allow", strings["first_run_accessibility_allow"])
        assertTrue(strings.getValue("first_run_accessibility_path").startsWith("Return to Justnoise"))
        val branding = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }.newDocumentBuilder().parse(File(appRoot, "../../../android-app/app/src/release/res/values/branding.xml").canonicalFile)
        val labels = branding.getElementsByTagName("string")
        val serviceLabel = (0 until labels.length).map { labels.item(it) as Element }
            .single { it.getAttribute("name") == "integrated_accessibility_service_label" }.textContent
        assertEquals("Justnoise", serviceLabel)
        assertEquals("Scanning for your Zap", strings["first_run_scan_overlay_title"])
        assertEquals("Not now", strings["first_run_blocking_not_now"])
    }

    @Test fun `fresh installation starts on the welcome screen`() {
        assertEquals(IntegratedFirstRunRoute.ONBOARDING, IntegratedFirstRunRules.route(fresh))
    }

    @Test fun `welcome can return to first mode without finishing onboarding`() {
        assertEquals(IntegratedFirstRunRoute.FIRST_MODE,
            IntegratedFirstRunRules.route(fresh, IntegratedFirstRunRoute.FIRST_MODE))
        assertEquals(IntegratedFirstRunRoute.ONBOARDING, IntegratedFirstRunRules.route(fresh))
    }

    @Test fun `saving the first mode advances only to pairing`() {
        assertEquals(IntegratedFirstRunRoute.ACTIVATION,
            IntegratedFirstRunRules.route(fresh.copy(tutorialCompleted = true)))
        assertEquals(IntegratedFirstRunRoute.ACTIVATION,
            IntegratedFirstRunRules.route(fresh.copy(tutorialCompleted = true, existingSetup = true)))
    }

    @Test fun `paired confirmation stays during pairing but does not replay on cold launch`() {
        val paired = fresh.copy(tutorialCompleted = true, zapPaired = true)
        assertEquals(IntegratedFirstRunRoute.ACTIVATION,
            IntegratedFirstRunRules.route(paired, IntegratedFirstRunRoute.ACTIVATION))
        assertEquals(IntegratedFirstRunRoute.MAIN, IntegratedFirstRunRules.route(paired))
    }

    @Test fun `blocking setup is required before the first inactive product route`() {
        val paired = fresh.copy(tutorialCompleted = true, zapPaired = true)
        assertTrue(IntegratedFirstRunRules.needsBlockingSetup(paired, false, false))
        assertTrue(IntegratedFirstRunRules.needsBlockingSetup(paired, true, false))
        assertEquals(false, IntegratedFirstRunRules.needsBlockingSetup(paired, true, true))
        assertEquals(false, IntegratedFirstRunRules.needsBlockingSetup(paired.copy(sessionRequested = true), false, false))
        assertEquals(false, IntegratedFirstRunRules.needsBlockingSetup(paired.copy(recoveryRequired = true), false, false))
        assertTrue(IntegratedFirstRunRules.needsBlockingSetup(paired.copy(tutorialCompleted = false), false, false))
    }

    @Test fun `required sign-in waits while Android permission setup is visible`() {
        val hold = IntegratedFirstRunRules.shouldHoldRequiredAccount(
            IntegratedFirstRunRoute.MAIN, false, false, true)
        assertTrue(hold)
        assertTrue(IntegratedFirstRunRules.shouldHoldRequiredAccount(
            IntegratedFirstRunRoute.MAIN, true, false, false))
        assertTrue(IntegratedFirstRunRules.shouldHoldRequiredAccount(
            IntegratedFirstRunRoute.MAIN, false, true, false))
        assertFalse(IntegratedFirstRunRules.shouldHoldRequiredAccount(
            IntegratedFirstRunRoute.MAIN, false, false, false))
        assertFalse(IntegratedFirstRunRules.shouldHoldRequiredAccount(
            IntegratedFirstRunRoute.ACTIVATION, false, false, false))
    }

    @Test fun `legacy pairing bypasses new tutorial without pretending permissions are ready`() {
        assertEquals(IntegratedFirstRunRoute.MAIN,
            IntegratedFirstRunRules.route(fresh.copy(zapPaired = true)))
    }

    @Test fun `existing selection or disclosure setup is not reset by new onboarding`() {
        assertEquals(IntegratedFirstRunRoute.MAIN,
            IntegratedFirstRunRules.route(fresh.copy(existingSetup = true), IntegratedFirstRunRoute.ONBOARDING))
        assertEquals(IntegratedFirstRunRoute.MAIN,
            IntegratedFirstRunRules.route(fresh.copy(existingSetup = true), IntegratedFirstRunRoute.FIRST_MODE))
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
        assertEquals(IntegratedFirstRunRoute.ACTIVATION,
            IntegratedFirstRunRules.route(fresh.copy(tutorialCompleted = true), IntegratedFirstRunRoute.FIRST_MODE))
        assertEquals(IntegratedFirstRunRoute.ONBOARDING,
            IntegratedFirstRunRules.route(fresh, IntegratedFirstRunRoute.ACTIVATION))
    }
}
