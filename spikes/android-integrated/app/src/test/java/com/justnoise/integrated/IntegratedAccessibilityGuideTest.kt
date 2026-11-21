package com.justnoise.integrated

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class IntegratedAccessibilityGuideTest {
    private val app = generateSequence(File(requireNotNull(System.getProperty("user.dir"))).canonicalFile) {
        it.parentFile
    }.map { File(it, "spikes/android-integrated/app") }
        .first { File(it, "src/main/res/values/first_run.xml").isFile }

    @Test fun guideProvidesTheFallbackRouteAndDirectEntryWithoutClaimingToGrantAccess() {
        val nodes = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }.newDocumentBuilder().parse(File(app, "src/main/res/values/first_run.xml"))
            .getElementsByTagName("string")
        val copy = (0 until nodes.length).associate {
            val element = nodes.item(it) as Element
            element.getAttribute("name") to element.textContent
        }
        val guide = copy.getValue("first_run_settings_guide_description")
        listOf("Installed apps", "Justnoise", "Switch it on", "tap Allow", "opens directly").forEach {
            assertTrue("Guide must explain $it", guide.contains(it))
        }
        assertTrue(copy.getValue("first_run_agree_open_settings").startsWith("Agree"))
        assertTrue(copy.getValue("first_run_accessibility_path").startsWith("Return to Justnoise"))
        assertTrue(copy.getValue("first_run_accessibility_disclosure").contains("Android Accessibility"))
    }

    @Test fun illustrationIsStaticAndNotAnInteractivePermissionControl() {
        val source = File(app, "src/main/java/com/justnoise/integrated/IntegratedFirstRunSurface.kt").readText()
        val guide = source.substringAfter("private fun AccessibilitySettingsGuide(")
            .substringBefore("fun IntegratedRecoverySurface(")
        assertTrue(guide.contains("clearAndSetSemantics { contentDescription = description }"))
        assertFalse(guide.contains("onClick"))
        assertFalse(guide.contains("onCheckedChange"))
        assertFalse(guide.contains("infiniteTransition"))
        val disclosure = source.substringAfter("fun IntegratedAccessibilitySetupSurface(")
            .substringBefore("private fun AccessibilitySettingsGuide(")
        assertTrue(disclosure.contains("AccessibilitySettingsGuide("))
        assertTrue(disclosure.contains("verticalScroll(rememberScrollState())"))
    }

    @Test fun returningFromSettingsRechecksCurrentFactsWithoutAnotherContinueButton() {
        val activity = File(app, "src/main/java/com/justnoise/integrated/MainActivity.kt").readText()
        val resume = activity.substringAfter("override fun onResume()")
            .substringBefore("override fun onPause()")
        assertTrue(resume.contains("AccessibilityServiceStatus.isEnabled(this)"))
        assertTrue(resume.contains("if (showingAccessibilitySetup || showingScheduleSetup) advancePermissionSetup()"))
        val advance = activity.substringAfter("private fun advancePermissionSetup()")
            .substringBefore("private fun finishPermissionSetup()")
        assertTrue(advance.contains("controller.hasAcceptedCurrentDisclosure()"))
        assertTrue(advance.contains("AccessibilityServiceStatus.isEnabled(this)"))
        assertTrue(advance.contains("IntegratedPermissionStep.COMPLETE -> finishPermissionSetup()"))
    }

    @Test fun compactCopyKeepsDisclosureVisibleAndCancellationQuiet() {
        val source = File(app, "src/main/java/com/justnoise/integrated/IntegratedFirstRunSurface.kt").readText()
        val setup = source.substringAfter("fun IntegratedAccessibilitySetupSurface(")
            .substringBefore("private fun AccessibilitySettingsGuide(")
        assertFalse(setup.contains("first_run_accessibility_reason"))
        assertTrue(setup.contains("first_run_accessibility_disclosure"))
        assertTrue(setup.contains("style = IntegratedGuidanceTypography.disclosure"))
        val activation = source.substringAfter("fun IntegratedActivationSurface(")
            .substringBefore("fun IntegratedNfcScanDialog(")
        assertFalse(activation.contains("first_run_activation_body"))
        assertTrue(activation.contains("first_run_activation_title"))
        assertTrue(activation.contains("FirstRunStatus(status")) // Keep real errors actionable.
        val activity = File(app, "src/main/java/com/justnoise/integrated/MainActivity.kt").readText()
        val cancel = activity.substringAfter("private fun cancelInitialScan() {")
            .substringBefore("private fun openZapStore()")
        assertTrue(cancel.contains("cancelReaderMode()"))
        assertFalse(cancel.contains("showTransientStatus"))
        val resources = File(app, "src/main/res/values/first_run.xml").readText()
        val disclosure = resources.substringAfter("name=\"first_run_accessibility_disclosure\">")
            .substringBefore("</string>")
        assertFalse(disclosure.contains("\\n"))
    }
}
