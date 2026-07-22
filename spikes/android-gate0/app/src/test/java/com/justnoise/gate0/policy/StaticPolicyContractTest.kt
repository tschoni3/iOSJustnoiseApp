package com.justnoise.gate0.policy

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StaticPolicyContractTest {
    private val repositoryRoot = File(
        requireNotNull(System.getProperty("justnoise.repositoryRoot")) {
            "Gradle must provide justnoise.repositoryRoot"
        },
    )
    private val spikeRoot = File(repositoryRoot, "spikes/android-gate0")

    @Test
    fun `manifest has only scoped NFC permission and launcher visibility`() {
        val manifest = file("app/src/main/AndroidManifest.xml").readText()

        assertTrue(manifest.contains("android.permission.NFC"))
        assertTrue(manifest.contains("android.intent.category.LAUNCHER"))
        assertFalse(manifest.contains("android.permission.INTERNET"))
        assertFalse(manifest.contains("android.permission.QUERY_ALL_PACKAGES"))
        assertFalse(manifest.contains("android.permission.SYSTEM_ALERT_WINDOW"))
        assertFalse(manifest.contains("android.permission.PACKAGE_USAGE_STATS"))
        assertTrue(manifest.contains("android:allowBackup=\"false\""))
        assertTrue(manifest.contains("android:fullBackupContent=\"false\""))
        assertTrue(manifest.contains("android:dataExtractionRules=\"@xml/data_extraction_rules\""))
    }

    @Test
    fun `accessibility metadata exposes the minimal declared capability`() {
        val metadata = file("app/src/main/res/xml/gate0_accessibility_service.xml").readText()

        assertTrue(metadata.contains("android:accessibilityEventTypes=\"typeWindowStateChanged\""))
        assertTrue(metadata.contains("android:canRetrieveWindowContent=\"false\""))
        assertTrue(metadata.contains("android:isAccessibilityTool=\"false\""))
        assertTrue(metadata.contains("android:notificationTimeout=\"0\""))
        assertFalse(metadata.contains("android:accessibilityFlags"))
    }

    @Test
    fun `overlay uses shared copy substitution and white primary action`() {
        val layout = file("app/src/main/res/layout/overlay_blocked.xml").readText()
        val colors = file("app/src/main/res/values/colors.xml").readText()
        val resolver = file(
            "app/src/main/java/com/justnoise/gate0/platform/discovery/" +
                "ProtectedPackageResolver.kt",
        ).readText()
        val strings = file("app/src/main/res/values/strings.xml").readText()
        val extractionRules = file("app/src/main/res/xml/data_extraction_rules.xml").readText()

        assertTrue(layout.contains("android:id=\"@+id/overlayMessage\""))
        assertTrue(layout.contains("android:backgroundTint=\"@color/action_background\""))
        assertTrue(colors.contains("<color name=\"action_background\">#FFFFFF</color>"))
        assertTrue(strings.contains("<string name=\"shield_open_justnoise\">Keep going</string>"))
        assertTrue(extractionRules.contains("<cloud-backup>"))
        assertTrue(extractionRules.contains("<device-transfer>"))
        assertTrue(extractionRules.contains("domain=\"sharedpref\" path=\".\""))
        assertTrue(resolver.contains("\"com.android.vending\""))
        assertTrue(resolver.contains("\"com.sec.android.app.samsungapps\""))
    }

    @Test
    fun `service reads package name without content inspection APIs`() {
        val source = file(
            "app/src/main/java/com/justnoise/gate0/platform/restriction/" +
                "Gate0AccessibilityService.kt",
        ).readText()

        assertTrue(source.contains("event.packageName"))
        listOf(
            "event.source",
            "event.text",
            "rootInActiveWindow",
            "findAccessibilityNodeInfos",
            "FLAG_RETRIEVE_INTERACTIVE_WINDOWS",
            "AccessibilityNodeInfo",
        ).forEach { prohibited ->
            assertFalse("Found prohibited content access: $prohibited", source.contains(prohibited))
        }
    }

    @Test
    fun `spike stays one Views module without cross-platform or network runtimes`() {
        val settings = file("settings.gradle.kts").readText()
        val appBuild = file("app/build.gradle.kts").readText()

        assertTrue(settings.contains("include(\":app\")"))
        assertFalse(settings.contains("include(\":app\","))
        assertFalse(appBuild.contains("org.jetbrains.kotlin.android"))
        assertFalse(appBuild.contains("compose"))
        assertFalse(appBuild.contains("retrofit"))
        assertFalse(appBuild.contains("okhttp"))
        assertFalse(appBuild.contains("firebase"))
    }

    private fun file(relativePath: String): File = File(spikeRoot, relativePath).also {
        require(it.isFile) { "Required Gate 0 file is missing: ${it.path}" }
    }
}
