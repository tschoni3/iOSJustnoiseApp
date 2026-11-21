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
    fun `manifests keep NFC and only reviewed scheduling and optional notification boundaries`() {
        for (path in sourceManifests()) {
            val manifest = path.readText()
            assertScheduleManifestBoundary(parseManifest(path))
            assertTrue(manifest.contains("android.permission.NFC"))
            assertTrue(manifest.contains("android.permission.POST_NOTIFICATIONS"))
            assertTrue(manifest.contains("android.intent.category.LAUNCHER"))
            assertTrue(manifest.contains("android.nfc.action.NDEF_DISCOVERED"))
            assertTrue(manifest.contains("android:mimeType=\"text/plain\""))
            assertFalse(manifest.contains("android.permission.INTERNET"))
            assertFalse(manifest.contains("android.permission.USE_EXACT_ALARM"))
            assertFalse(manifest.contains("android.permission.QUERY_ALL_PACKAGES"))
            assertFalse(manifest.contains("android.permission.SYSTEM_ALERT_WINDOW"))
            assertFalse(manifest.contains("android.permission.PACKAGE_USAGE_STATS"))
            assertFalse(manifest.contains("android.permission.POST_PROMOTED_NOTIFICATIONS"))
            assertFalse(manifest.contains("android.permission.FOREGROUND_SERVICE"))
            assertTrue(manifest.contains("android:allowBackup=\"false\""))
            assertTrue(manifest.contains("android:fullBackupContent=\"false\""))
            assertTrue(manifest.contains("android:dataExtractionRules=\"@xml/data_extraction_rules\""))
        }
    }

    @Test
    fun `schedule manifest boundary rejects extra permissions and exported receivers`() {
        for (permission in listOf(
            "android.permission.INTERNET",
            "android.permission.USE_EXACT_ALARM",
            "android.permission.POST_PROMOTED_NOTIFICATIONS",
            "android.permission.FOREGROUND_SERVICE",
            "android.permission.FOREGROUND_SERVICE_SPECIAL_USE",
        )) {
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
    fun `notification dismissal receiver permits only explicit private enabled delivery`() {
        for ((attribute, value) in listOf(
            "exported" to "true",
            "enabled" to "false",
            "enabled" to "",
            "permission" to "android.permission.DUMP",
            "process" to ":notification",
            "directBootAware" to "true",
            "directBootAware" to "false",
            "name" to "com.justnoise.OtherReceiver",
        )) {
            val manifest = parseManifest(sourceManifests().first())
            notificationDismissalReceiver(manifest).setAttributeNS(ANDROID_NAMESPACE, "android:$attribute", value)
            assertThrows(AssertionError::class.java) { assertScheduleManifestBoundary(manifest) }
        }
        for (mutation in listOf("missing", "duplicate", "implicit_export", "intent-filter", "meta-data")) {
            val manifest = parseManifest(sourceManifests().first())
            val receiver = notificationDismissalReceiver(manifest)
            when (mutation) {
                "missing" -> receiver.parentNode.removeChild(receiver)
                "duplicate" -> receiver.parentNode.appendChild(receiver.cloneNode(true))
                "implicit_export" -> receiver.removeAttributeNS(ANDROID_NAMESPACE, "exported")
                else -> receiver.appendChild(manifest.createElement(mutation))
            }
            assertThrows(AssertionError::class.java) { assertScheduleManifestBoundary(manifest) }
        }
        for (enabled in listOf("", "true")) {
            val manifest = parseManifest(sourceManifests().first())
            val receiver = notificationDismissalReceiver(manifest)
            if (enabled.isEmpty()) receiver.removeAttributeNS(ANDROID_NAMESPACE, "enabled")
            else receiver.setAttributeNS(ANDROID_NAMESPACE, "android:enabled", enabled)
            assertScheduleManifestBoundary(manifest)
        }
    }

    @Test
    fun `Live Update experiment permission exists only in the exact debug overlays`() {
        for (mainManifest in sourceManifests()) {
            val sources = mainManifest.parentFile.parentFile
            val debugManifest = File(sources, "debug/AndroidManifest.xml")
            assertTrue("Required debug experiment overlay is missing: $debugManifest", debugManifest.isFile)
            assertDebugPromotionOverlay(parseManifest(debugManifest))
            assertNoPromotionPermission(parseManifest(mainManifest))
            val releaseManifest = File(sources, "release/AndroidManifest.xml")
            if (releaseManifest.exists()) assertNoPromotionPermission(parseManifest(releaseManifest))
        }
    }

    @Test
    fun `debug promotion overlay rejects extra permissions components and manifest overrides`() {
        val source = File(sourceManifests().first().parentFile.parentFile, "debug/AndroidManifest.xml")
        for (mutation in listOf(
            "missing_permission", "duplicate_permission", "wrong_permission", "sdk_qualified_permission",
            "permission_attribute", "permission_child", "manifest_attribute", "extra_namespace",
            "application", "uses-sdk", "permission", "receiver",
        )) {
            val document = parseManifest(source)
            val root = document.documentElement
            val permission = root.childElements().single()
            when (mutation) {
                "missing_permission" -> root.removeChild(permission)
                "duplicate_permission" -> root.appendChild(permission.cloneNode(true))
                "wrong_permission" -> permission.setAttributeNS(
                    ANDROID_NAMESPACE, "android:name", "android.permission.FOREGROUND_SERVICE",
                )
                "sdk_qualified_permission" -> document.renameNode(permission, null, "uses-permission-sdk-23")
                "permission_attribute" -> permission.setAttributeNS(ANDROID_NAMESPACE, "android:maxSdkVersion", "36")
                "permission_child" -> permission.appendChild(document.createElement("intent-filter"))
                "manifest_attribute" -> root.setAttribute("package", "com.justnoise.unreviewed")
                "extra_namespace" -> root.setAttributeNS(
                    XMLNS_NAMESPACE, "xmlns:tools", "http://schemas.android.com/tools",
                )
                else -> root.appendChild(document.createElement(mutation))
            }
            assertThrows(AssertionError::class.java) { assertDebugPromotionOverlay(document) }
        }
    }

    @Test
    fun `release overlays cannot request promotion even through an sdk qualified permission`() {
        val source = File(sourceManifests().first().parentFile.parentFile, "debug/AndroidManifest.xml")
        for (tag in listOf("uses-permission", "uses-permission-sdk-23", "uses-permission-sdk-m")) {
            val document = parseManifest(source)
            document.renameNode(document.documentElement.childElements().single(), null, tag)
            assertThrows(AssertionError::class.java) { assertNoPromotionPermission(document) }
        }
        val emptyOverlay = parseManifest(source)
        emptyOverlay.documentElement.removeChild(emptyOverlay.documentElement.childElements().single())
        assertNoPromotionPermission(emptyOverlay)
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
    fun `integrated service description discloses while enabled observation for schedules`() {
        val path = File(repositoryRoot, "spikes/android-integrated/app/src/main/res/values/strings.xml")
        val document = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }.newDocumentBuilder().parse(path)
        val strings = document.getElementsByTagName("string")
        val description = (0 until strings.length).map { strings.item(it) as Element }
            .single { it.getAttribute("name") == "integrated_accessibility_service_description" }
            .textContent

        listOf("While enabled", "foreground app identifiers", "temporarily keeps the latest",
            "scheduled starts", "During an eligible session", "cannot retrieve screen content").forEach {
            assertTrue("Service description must retain: $it", description.contains(it))
        }
        assertFalse("Observation must not be described as limited to an active session",
            description.contains("while a Justnoise session is active"))
    }

    @Test
    fun `Android privacy draft uses owner identity without claiming a published policy`() {
        val resourceRoot = File(repositoryRoot, "spikes/android-integrated/app/src/main/res/values")
        val factory = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }
        val privacy = factory.newDocumentBuilder().parse(File(resourceRoot, "privacy_draft.xml"))
        val items = privacy.getElementsByTagName("item")
        val paragraphs = (0 until items.length).map { items.item(it).textContent }
        val declaration = file("evidence/PLAY_DECLARATION_DRAFT.md").readText()

        assertTrue(paragraphs.contains("Publisher: Justnoise Media"))
        assertTrue(paragraphs.any { it.startsWith("Privacy contact: hello@justnoise.shop") })
        assertTrue(declaration.contains("**Publisher:** Justnoise Media"))
        assertTrue(declaration.contains("**Privacy contact:** hello@justnoise.shop"))
        for (copy in listOf(paragraphs.joinToString("\n"), declaration)) {
            assertFalse("The former publisher placeholder must not remain", copy.contains("[YOUR LEGAL NAME / REGISTERED BUSINESS NAME]"))
            assertFalse("The former contact placeholder must not remain", copy.contains("privacy@example.com"))
            assertTrue("The Android notice remains a draft", copy.contains("Privacy Policy draft"))
            assertTrue("A real public policy URL has not yet been supplied",
                copy.contains("[YOUR PUBLISHED ANDROID PRIVACY POLICY URL]"))
        }

        val settings = factory.newDocumentBuilder().parse(File(resourceRoot, "settings.xml"))
        val strings = settings.getElementsByTagName("string")
        val values = (0 until strings.length).map { strings.item(it) as Element }
            .associate { it.getAttribute("name") to it.textContent }
        assertTrue(values.getValue("integrated_settings_privacy_detail").contains("Justnoise Media"))
        assertEquals("DRAFT — NOT FINAL", values["integrated_settings_privacy_draft_title"])
        assertTrue("Supplying identity does not silently remove the unpublished-policy warning",
            values.getValue("integrated_settings_privacy_draft_warning").contains("not a published policy"))
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
        assertTrue(strings.contains("<string name=\"shield_open_justnoise\">Be Right Back</string>"))
        assertTrue(extractionRules.contains("<cloud-backup>"))
        assertTrue(extractionRules.contains("<device-transfer>"))
        assertTrue(extractionRules.contains("domain=\"sharedpref\" path=\".\""))
        assertTrue(resolver.contains("\"com.android.vending\""))
        assertTrue(resolver.contains("\"com.sec.android.app.samsungapps\""))
    }

    @Test
    fun `blocker has one primary action and recovery remains outside the overlay`() {
        val android = "http://schemas.android.com/apk/res/android"
        val document = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(file("app/src/main/res/layout/overlay_blocked.xml"))
        val nodes = document.getElementsByTagName("*")
        val elements = (0 until nodes.length).map { nodes.item(it) as Element }
        val buttons = elements.filter { it.tagName == "Button" }
        assertEquals(1, buttons.size)
        val primary = buttons.single()
        assertEquals("@+id/openJustnoiseButton", primary.getAttributeNS(android, "id"))
        assertEquals("56dp", primary.getAttributeNS(android, "layout_height"))
        assertEquals("false", primary.getAttributeNS(android, "textAllCaps"))
        assertEquals("LinearLayout", (primary.parentNode as Element).tagName)
        assertEquals("ScrollView", document.documentElement.tagName)
        assertEquals("true", document.documentElement.getAttributeNS(android, "fillViewport"))
        val artwork = elements.single { it.tagName == "ImageView" }
        assertEquals("@drawable/zap_button", artwork.getAttributeNS(android, "src"))
        assertEquals("no", artwork.getAttributeNS(android, "importantForAccessibility"))
        val source = file("app/src/main/java/com/justnoise/gate0/platform/restriction/OverlayController.kt").readText()
        assertTrue(source.contains("openAfterHiding(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))"))
        assertFalse(source.contains("MainActivity::class.java"))
        assertFalse(source.contains("StopRequested"))
        assertFalse(source.contains("SessionEngine"))
        assertFalse(source.contains("overlayHelpButton"))
        val setup = file("app/src/main/java/com/justnoise/gate0/app/MainActivity.kt").readText()
        assertTrue(setup.contains("Settings.ACTION_ACCESSIBILITY_SETTINGS"))
        val settingsHost = File(repositoryRoot,
            "spikes/android-integrated/app/src/main/java/com/justnoise/integrated/MainActivity.kt").readText()
        assertTrue(settingsHost.contains("onOpenAppInfo = ::openAppInfo"))
        assertTrue(settingsHost.contains("Settings.ACTION_APPLICATION_DETAILS_SETTINGS"))
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
                "override fun onUnbind(intent: Intent?): Boolean {\n        AccessibilitySetupReturn.cancel()\n        shutdownServiceState()",
            ),
        )
        assertTrue(source.contains("override fun onDestroy() {\n        AccessibilitySetupReturn.cancel()\n        shutdownServiceState()"))
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

    private fun assertDebugPromotionOverlay(document: Document) {
        val root = document.documentElement
        assertEquals("manifest", root.tagName)
        assertEquals(1, root.attributes.length)
        assertEquals(ANDROID_NAMESPACE, root.getAttributeNS(XMLNS_NAMESPACE, "android"))
        val children = root.childElements()
        assertEquals(1, children.size)
        val permission = children.single()
        assertEquals("uses-permission", permission.tagName)
        assertEquals(1, permission.attributes.length)
        assertEquals(PROMOTION_PERMISSION, permission.getAttributeNS(ANDROID_NAMESPACE, "name"))
        assertTrue(permission.childElements().isEmpty())
    }

    private fun assertNoPromotionPermission(document: Document) {
        assertEquals("manifest", document.documentElement.tagName)
        val requests = document.documentElement.childElements().filter { it.tagName.startsWith("uses-permission") }
        assertFalse(requests.any { it.getAttributeNS(ANDROID_NAMESPACE, "name") == PROMOTION_PERMISSION })
    }

    private fun assertScheduleManifestBoundary(document: Document) {
        val root = document.documentElement
        assertEquals("manifest", root.tagName)
        val permissions = root.childElements().filter { it.tagName.startsWith("uses-permission") }
        val expectedPermissions = setOf(
            "android.permission.NFC",
            "android.permission.POST_NOTIFICATIONS",
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
        assertEquals(2, receivers.size)
        val scheduleReceivers = receivers.filter { it.getAttributeNS(ANDROID_NAMESPACE, "name") in setOf(
            ".platform.schedule.ScheduleAlarmReceiver",
            "com.justnoise.gate0.platform.schedule.ScheduleAlarmReceiver",
        ) }
        assertEquals(1, scheduleReceivers.size)
        val receiver = scheduleReceivers.single()
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
        val dismissals = receivers.filter {
            it.getAttributeNS(ANDROID_NAMESPACE, "name") == NOTIFICATION_DISMISS_RECEIVER
        }
        assertEquals(1, dismissals.size)
        val dismissal = dismissals.single()
        for (index in 0 until dismissal.attributes.length) {
            val attribute = dismissal.attributes.item(index)
            assertEquals(ANDROID_NAMESPACE, attribute.namespaceURI)
            assertTrue(attribute.localName in setOf("name", "exported", "enabled"))
        }
        assertEquals("false", dismissal.getAttributeNS(ANDROID_NAMESPACE, "exported"))
        assertTrue(
            !dismissal.hasAttributeNS(ANDROID_NAMESPACE, "enabled") ||
                dismissal.getAttributeNS(ANDROID_NAMESPACE, "enabled") == "true",
        )
        assertTrue(dismissal.childElements().isEmpty())
    }

    private fun notificationDismissalReceiver(document: Document): Element =
        (document.getElementsByTagName("application").item(0) as Element).childElements().single {
            it.tagName == "receiver" && it.getAttributeNS(ANDROID_NAMESPACE, "name") == NOTIFICATION_DISMISS_RECEIVER
        }

    private fun Element.childElements(): List<Element> = (0 until childNodes.length)
        .mapNotNull { childNodes.item(it) as? Element }

    private companion object {
        const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
        const val XMLNS_NAMESPACE = "http://www.w3.org/2000/xmlns/"
        const val PROMOTION_PERMISSION = "android.permission.POST_PROMOTED_NOTIFICATIONS"
        const val NOTIFICATION_DISMISS_RECEIVER =
            "com.justnoise.gate0.platform.notification.SessionNotificationDismissReceiver"
    }
}
