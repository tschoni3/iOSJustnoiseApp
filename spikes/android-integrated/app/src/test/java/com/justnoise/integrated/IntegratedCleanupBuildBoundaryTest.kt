package com.justnoise.integrated

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/** Source guards complement (and do not replace) inspection of the built APK. */
class IntegratedCleanupBuildBoundaryTest {
    private val appRoot = generateSequence(File(System.getProperty("user.dir")).canonicalFile) {
        it.parentFile
    }.map { File(it, "spikes/android-integrated/app") }
        .first { File(it, "build.gradle.kts").isFile }

    @Test
    fun cleanupIdentityAndDebugSigningAreScopedToTheNewBuildType() {
        val source = File(appRoot, "build.gradle.kts").readText()
        val cleanup = block(source, "create(\"cleanupCheck\")")
        assertTrue(cleanup.contains("initWith(getByName(\"release\"))"))
        assertTrue(cleanup.contains("applicationIdSuffix = \".cleanupcheck\""))
        assertTrue(cleanup.contains("versionNameSuffix = \"-cleanup-check\""))
        assertTrue(cleanup.contains("isDebuggable = true"))
        assertTrue(cleanup.contains("signingConfig = signingConfigs.getByName(\"debug\")"))
        assertTrue(cleanup.contains("matchingFallbacks += listOf(\"release\")"))
        val defaults = block(source, "defaultConfig")
        assertTrue(defaults.contains("applicationId = \"com.justnoise.androiddev\""))
        assertTrue(defaults.contains("versionName = \"0.1-integrated-dev\""))
        val release = block(source, "release")
        assertTrue(release.contains("isDebuggable = false"))
        assertFalse(release.contains("signingConfig"))
        assertFalse(release.contains("applicationIdSuffix"))
        assertEquals(1, Regex("applicationIdSuffix\\s*=").findAll(source).count())
    }

    @Test
    fun cleanupIsTestOnlyAndCannotCompeteForDirectNfcDispatch() {
        val manifest = xml("src/cleanupCheck/AndroidManifest.xml")
        assertEquals(0, manifest.getElementsByTagName("uses-permission").length)
        assertEquals(0, manifest.getElementsByTagName("service").length)
        assertEquals(0, manifest.getElementsByTagName("receiver").length)
        val app = manifest.getElementsByTagName("application").item(0) as Element
        assertEquals("true", app.android("testOnly"))
        assertEquals("@string/cleanup_check_app_label", app.android("label"))
        val activities = app.getElementsByTagName("activity")
        assertEquals(1, activities.length)
        val direct = activities.item(0) as Element
        assertEquals("com.justnoise.gate0.app.DirectNfcActivity", direct.android("name"))
        assertEquals("false", direct.android("enabled"))
        val main = xml("src/main/AndroidManifest.xml")
        val mainApp = main.getElementsByTagName("application").item(0) as Element
        assertEquals("Justnoise Android Dev", mainApp.android("label"))
        assertEquals("", mainApp.android("testOnly"))
        val mainActivities = main.getElementsByTagName("activity")
        val mainDirect = (0 until mainActivities.length)
            .map { mainActivities.item(it) as Element }
            .single { it.android("name") == "com.justnoise.gate0.app.DirectNfcActivity" }
        assertTrue(mainDirect.android("enabled") in setOf("", "true"))
    }

    @Test
    fun cleanupOnlyOverridesIdentityResourcesAndHasNoAlternateRuntime() {
        val resourceNodes = xml("src/cleanupCheck/res/values/strings.xml")
            .getElementsByTagName("string")
        val strings = (0 until resourceNodes.length).associate { index ->
            val node = resourceNodes.item(index) as Element
            node.getAttribute("name") to node.textContent
        }
        assertEquals(
            mapOf(
                "cleanup_check_app_label" to "Justnoise Cleanup Check",
                "app_name" to "Justnoise Cleanup Check",
                "integrated_accessibility_service_label" to
                    "Justnoise Cleanup Check — disposable app blocking",
            ),
            strings,
        )
        assertEquals(
            setOf("AndroidManifest.xml", "res/values/strings.xml"),
            File(appRoot, "src/cleanupCheck").walkTopDown().filter { it.isFile }
                .map { it.relativeTo(File(appRoot, "src/cleanupCheck")).invariantSeparatorsPath }
                .toSet(),
        )
    }

    private fun block(source: String, name: String): String =
        Regex(Regex.escape(name) + "\\s*\\{([^}]*)}")
            .find(source)?.groupValues?.get(1) ?: error("Missing Gradle block: $name")

    private fun xml(path: String) = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
    }.newDocumentBuilder().parse(File(appRoot, path))

    private fun Element.android(name: String): String =
        getAttributeNS("http://schemas.android.com/apk/res/android", name)
}
