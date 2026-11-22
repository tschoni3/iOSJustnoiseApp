package com.justnoise.integrated

import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Source wiring regressions; not evidence of rendered or physical-device acceptance. */
class IntegratedSettingsOrganizationTest {
    private val root = generateSequence(File(requireNotNull(System.getProperty("user.dir"))).canonicalFile) { it.parentFile }
        .first { File(it, "spikes/android-integrated").isDirectory }
    private val surface = File(root, "spikes/android-integrated/app/src/main/java/com/justnoise/integrated/IntegratedSettingsSurface.kt").readText()
    private val host = File(root, "spikes/android-integrated/app/src/main/java/com/justnoise/integrated/MainActivity.kt").readText()

    @Test fun everydayActionsPrecedeTheExplicitAdvancedEntry() {
        val sections = listOf("integrated_settings_account_group", "integrated_settings_zap_group",
            "integrated_settings_help_group", "integrated_settings_more_group", "integrated_settings_device_group")
        val positions = sections.map { surface.indexOf(it) }
        assertTrue(positions.all { it >= 0 })
        assertEquals(positions.sorted(), positions)
        assertTrue(surface.contains("if (!advanced) item"))
        assertTrue(surface.contains("if (advanced) item"))
        assertTrue(surface.contains("tag = \"settings-more\", onClick = onShowMore"))
    }

    @Test fun emergencyRemainsExplicitAndSessionBound() {
        assertTrue(surface.contains("tag = \"settings-emergency\", enabled = enabled"))
        assertTrue(surface.contains("confirmedSession == emergencySessionId"))
        assertTrue(surface.contains("onEmergencyUnzap(confirmedSession)"))
        assertFalse(surface.contains("Gate0Preferences("))
        assertFalse(surface.contains("startActivity("))
    }

    @Test fun nestedBackAndRestoreHaveAnExplicitParent() {
        assertTrue(host.contains("onShowMore = { settingsPage = IntegratedSettingsPage.ADVANCED }"))
        assertTrue(host.contains("settingsPage == IntegratedSettingsPage.PRIVACY -> settingsPage = IntegratedSettingsPage.ADVANCED"))
        assertTrue(host.contains("settingsPage == IntegratedSettingsPage.ADVANCED -> settingsPage = IntegratedSettingsPage.MAIN"))
        assertTrue(host.contains("outState.putString(SETTINGS_PAGE_KEY, settingsPage.name)"))
        assertTrue(surface.contains("state = if (advanced) moreListState else mainListState"))
    }

    @Test fun publicPrivacyPolicyIsOneTapFromSettingsAndDisclosure() {
        val strings = File(root, "spikes/android-integrated/app/src/main/res/values/settings.xml").readText()
        assertTrue(strings.contains(">https://justnoise.shop/pages/android-privacy-policy</string>"))
        val main = surface.substringAfter("if (!advanced) item {\n            SettingsGroup(stringResource(R.string.integrated_settings_help_group))")
            .substringBefore("integrated_settings_more_group")
        assertTrue(main.contains("tag = \"settings-privacy-policy-link\""))
        assertTrue(surface.contains("tag = \"privacy-policy-open\""))
        assertTrue(host.contains("launch(Intent(Intent.ACTION_VIEW, Uri.parse(url)))"))
        assertTrue(host.contains("launchPrivacyPolicy(getString(R.string.integrated_settings_privacy_policy_url), ::startActivity)"))
        assertEquals(2, Regex("onPrivacyPolicy = ::openPrivacyPolicy").findAll(host).count())
        val disclosure = File(root, "spikes/android-integrated/app/src/main/java/com/justnoise/integrated/IntegratedFirstRunSurface.kt")
            .readText().substringAfter("fun IntegratedAccessibilitySetupSurface(")
            .substringBefore("private fun AccessibilitySettingsGuide(")
        assertTrue(disclosure.contains("testTag(\"disclosure-privacy-policy\")"))
    }

    @Test fun supportFallbackAndVersionAreNotPermanentCards() {
        assertTrue(surface.contains("if (emailUnavailable) Column"))
        assertEquals(1, Regex("testTag\\(\"support-address\"\\)").findAll(surface).count())
        assertFalse(surface.contains("integrated_settings_about_group"))
        assertTrue(surface.contains("testTag(\"settings-version\")"))
    }
}
