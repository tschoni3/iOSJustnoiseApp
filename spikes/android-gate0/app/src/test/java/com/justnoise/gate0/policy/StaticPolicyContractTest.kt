package com.justnoise.gate0.policy

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element

class StaticPolicyContractTest {
    private val repositoryRoot = File(
        requireNotNull(System.getProperty("justnoise.repositoryRoot")) {
            "Gradle must provide justnoise.repositoryRoot"
        },
    )
    private val spikeRoot = File(repositoryRoot, "spikes/android-gate0")

    @Test
    fun `manifests keep NFC visibility and only explicit scheduling permissions and receiver`() {
        for (path in sourceManifests()) {
            val manifest = path.readText()
            assertScheduleManifestBoundary(parseManifest(path))
            assertTrue(manifest.contains("android.permission.NFC"))
            assertTrue(manifest.contains("android.intent.category.LAUNCHER"))
            assertTrue(manifest.contains("android.nfc.action.NDEF_DISCOVERED"))
            assertTrue(manifest.contains("android:mimeType=\"text/plain\""))
            assertFalse(manifest.contains("android.permission.INTERNET"))
            assertFalse(manifest.contains("android.permission.USE_EXACT_ALARM"))
            assertFalse(manifest.contains("android.permission.QUERY_ALL_PACKAGES"))
            assertFalse(manifest.contains("android.permission.SYSTEM_ALERT_WINDOW"))
            assertFalse(manifest.contains("android.permission.PACKAGE_USAGE_STATS"))
            assertTrue(manifest.contains("android:allowBackup=\"false\""))
            assertTrue(manifest.contains("android:fullBackupContent=\"false\""))
            assertTrue(manifest.contains("android:dataExtractionRules=\"@xml/data_extraction_rules\""))
        }
    }

    @Test
    fun `schedule manifest boundary rejects extra permissions and exported receivers`() {
        for (permission in listOf("android.permission.INTERNET", "android.permission.USE_EXACT_ALARM")) {
            val manifest = parseManifest(sourceManifests().first())
            val extra = manifest.createElement("uses-permission").apply {
                setAttributeNS(ANDROID_NAMESPACE, "android:name", permission)
            }
            manifest.documentElement.appendChild(extra)
            assertThrows(AssertionError::class.java) { assertScheduleManifestBoundary(manifest) }
        }
        for ((attribute, value) in listOf("exported" to "true", "directBootAware" to "true")) {
            val manifest = parseManifest(sourceManifests().first())
            val receiver = manifest.getElementsByTagName("receiver").item(0) as Element
            receiver.setAttributeNS(ANDROID_NAMESPACE, "android:$attribute", value)
            assertThrows(AssertionError::class.java) { assertScheduleManifestBoundary(manifest) }
        }
        val manifest = parseManifest(sourceManifests().first())
        val application = manifest.getElementsByTagName("application").item(0) as Element
        application.appendChild(manifest.createElement("receiver"))
        assertThrows(AssertionError::class.java) { assertScheduleManifestBoundary(manifest) }
    }

    @Test
    fun `direct NFC dispatch requires a live tag and ignores copied messages`() {
        val source = file(
            "app/src/main/java/com/justnoise/gate0/app/DirectNfcActivity.kt",
        ).readText()
        val rules = file(
            "app/src/main/java/com/justnoise/gate0/platform/nfc/DirectNfcDispatchRules.kt",
        ).readText()

        assertTrue(source.contains("candidate.liveNfcTag()"))
        assertTrue(source.contains("NdefTagReader.readSingleStrictTextRecord"))
        assertTrue(source.contains("ProcessDirectNfcMutationGate.tryClaim"))
        assertFalse(source.contains("EXTRA_NDEF_MESSAGES"))
        assertTrue(rules.contains("hasLiveTag"))
    }

    @Test
    fun `direct NFC entry stays out of the application task in both APKs`() {
        val androidNamespace = "http://schemas.android.com/apk/res/android"
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }
        val manifests = listOf(
            file("app/src/main/AndroidManifest.xml"),
            File(repositoryRoot, "spikes/android-integrated/app/src/main/AndroidManifest.xml"),
        )
        for (manifest in manifests) {
            val activities = factory.newDocumentBuilder().parse(manifest)
                .getElementsByTagName("activity")
            val entry = (0 until activities.length)
                .map { activities.item(it) as Element }
                .single {
                    it.getAttributeNS(androidNamespace, "name").endsWith(".app.DirectNfcActivity")
                }
            // A missing attribute also reads as an empty string; require an explicit opt-out.
            assertTrue(manifest.path, entry.hasAttributeNS(androidNamespace, "taskAffinity"))
            assertEquals(manifest.path, "", entry.getAttributeNS(androidNamespace, "taskAffinity"))
            assertEquals("singleTop", entry.getAttributeNS(androidNamespace, "launchMode"))
            assertEquals("true", entry.getAttributeNS(androidNamespace, "noHistory"))
            assertEquals("true", entry.getAttributeNS(androidNamespace, "excludeFromRecents"))
        }
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
        assertTrue(layout.contains("android:clickable=\"true\""))
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
    fun `overlay wires display metrics and keeps content touch blocking enabled`() {
        val source = file(
            "app/src/main/java/com/justnoise/gate0/platform/restriction/OverlayController.kt",
        ).readText()
        assertTrue(source.contains("currentWindowMetrics"))
        assertTrue(source.contains("getInsets(WindowInsets.Type.systemBars())"))
        assertTrue(source.contains("getInsetsIgnoringVisibility(WindowInsets.Type.displayCutout())"))
        assertTrue(source.contains("OverlayWindowBounds.calculate"))
        assertTrue(source.contains("setFitInsetsTypes(0)"))
        assertTrue(source.contains("FLAG_NOT_FOCUSABLE"))
        assertFalse(source.contains("FLAG_NOT_TOUCHABLE"))
        assertFalse(source.contains("WindowManager.LayoutParams.MATCH_PARENT"))
    }

    @Test
    fun `service reads package name without content inspection APIs`() {
        val source = file(
            "app/src/main/java/com/justnoise/gate0/platform/restriction/" +
                "Gate0AccessibilityService.kt",
        ).readText()

        assertTrue(source.contains("event?.packageName"))
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
    fun `feedback interruption suspends enforcement without disconnecting recovery channels`() {
        val source = file(
            "app/src/main/java/com/justnoise/gate0/platform/restriction/" +
                "Gate0AccessibilityService.kt",
        ).readText()
        val interruption = source.substringAfter("override fun onInterrupt() {", "")
            .substringBefore("\n    }")

        assertTrue(
            "Interrupt must dismiss the blocker",
            interruption.contains("overlayController.hide()"),
        )
        assertTrue(
            "Interrupt must prevent re-enforcement",
            interruption.contains("preferences.markRecoveryRequired()"),
        )
        listOf(
            "shutdownServiceState()",
            "markServiceDisconnected()",
            "removeCallbacks(",
            "unregisterReceiver(",
            "disableSelf()",
        ).forEach { teardown ->
            assertFalse("Interrupt is not unbind: $teardown", interruption.contains(teardown))
        }

        val eventHandler = source.substringAfter("override fun onAccessibilityEvent(", "")
            .substringBefore("private fun resolveAppLabel")
        assertTrue(
            "Recovery must suppress later target events",
            eventHandler.contains("!stored.recoveryRequired"),
        )
        assertTrue(
            source.contains(
                "override fun onUnbind(intent: Intent?): Boolean {\n        shutdownServiceState()",
            ),
        )
        assertTrue(source.contains("override fun onDestroy() {\n        shutdownServiceState()"))
    }

    @Test
    fun `withdrawal feedback uses the cleanup result`() {
        val source = file(
            "app/src/main/java/com/justnoise/gate0/app/MainActivity.kt",
        ).readText()

        assertTrue(source.contains("val cleared = controller.withdrawAndClear()"))
        assertTrue(source.contains("withdrawalStatusMessage(cleared)"))
        assertFalse(source.contains("showTransientStatus(R.string.status_withdrawn)"))
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

    private fun sourceManifests(): List<File> = listOf(
        file("app/src/main/AndroidManifest.xml"),
        File(repositoryRoot, "spikes/android-integrated/app/src/main/AndroidManifest.xml"),
    )

    private fun parseManifest(path: File): Document = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
    }.newDocumentBuilder().parse(path)

    private fun assertScheduleManifestBoundary(document: Document) {
        val root = document.documentElement
        assertEquals("manifest", root.tagName)
        val permissions = root.childElements().filter { it.tagName.startsWith("uses-permission") }
        val expectedPermissions = setOf(
            "android.permission.NFC",
            "android.permission.SCHEDULE_EXACT_ALARM",
            "android.permission.RECEIVE_BOOT_COMPLETED",
            "android.permission.WAKE_LOCK",
        )
        assertEquals(expectedPermissions.size, permissions.size)
        assertEquals(expectedPermissions, permissions.map { it.getAttributeNS(ANDROID_NAMESPACE, "name") }.toSet())
        for (permission in permissions) {
            assertEquals("uses-permission", permission.tagName)
            assertEquals(1, permission.attributes.length)
            assertTrue(permission.childElements().isEmpty())
        }
        val applications = root.childElements().filter { it.tagName == "application" }
        assertEquals(1, applications.size)
        val application = applications.single()
        val receivers = application.childElements().filter { it.tagName == "receiver" }
        assertEquals(1, receivers.size)
        val receiver = receivers.single()
        assertTrue(receiver.getAttributeNS(ANDROID_NAMESPACE, "name") in setOf(
            ".platform.schedule.ScheduleAlarmReceiver",
            "com.justnoise.gate0.platform.schedule.ScheduleAlarmReceiver",
        ))
        for (index in 0 until receiver.attributes.length) {
            val attribute = receiver.attributes.item(index)
            assertEquals(ANDROID_NAMESPACE, attribute.namespaceURI)
            assertTrue(attribute.localName in setOf("name", "exported", "directBootAware", "enabled"))
        }
        assertEquals("false", receiver.getAttributeNS(ANDROID_NAMESPACE, "exported"))
        assertTrue(receiver.getAttributeNS(ANDROID_NAMESPACE, "directBootAware") in setOf("", "false"))
        assertTrue(application.getAttributeNS(ANDROID_NAMESPACE, "directBootAware") in setOf("", "false"))
        assertTrue(receiver.getAttributeNS(ANDROID_NAMESPACE, "enabled") in setOf("", "true"))
        val filters = receiver.childElements()
        assertEquals(1, filters.size)
        val filter = filters.single()
        assertEquals("intent-filter", filter.tagName)
        assertEquals(0, filter.attributes.length)
        val actions = filter.childElements()
        val expectedActions = setOf(
            "android.intent.action.BOOT_COMPLETED",
            "android.intent.action.TIME_SET",
            "android.intent.action.TIMEZONE_CHANGED",
            "android.intent.action.MY_PACKAGE_REPLACED",
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
        )
        assertEquals(expectedActions.size, actions.size)
        assertEquals(expectedActions, actions.map { it.getAttributeNS(ANDROID_NAMESPACE, "name") }.toSet())
        for (action in actions) {
            assertEquals("action", action.tagName)
            assertEquals(1, action.attributes.length)
            assertTrue(action.childElements().isEmpty())
        }
    }

    private fun Element.childElements(): List<Element> = (0 until childNodes.length)
        .mapNotNull { childNodes.item(it) as? Element }

    private companion object {
        const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
    }
}
