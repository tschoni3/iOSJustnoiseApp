package com.justnoise.app.auth

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductionPrivacyNoticeTest {
    @Test fun connectedReleaseOverridesTheOfflineDevelopmentDraft() {
        val root = File(requireNotNull(System.getProperty("justnoise.repositoryRoot")))
        val file = File(root, "android-app/app/src/release/res/values/privacy.xml")
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val arrays = document.getElementsByTagName("string-array")
        assertEquals(1, arrays.length)
        assertEquals("integrated_android_privacy_draft_paragraphs",
            arrays.item(0).attributes.getNamedItem("name").nodeValue)
        val text = document.documentElement.textContent
        listOf("Justnoise Media", "hello@justnoise.shop", "Supabase", "Resend", "Google",
            "PostHog", "GeoIP", "approximate location", "off by default",
            "Delete account", "android-privacy-policy", "android-account-deletion",
            "does not sync modes", "stay on your phone").forEach {
            assertTrue("Missing production disclosure: $it", text.contains(it))
        }
        listOf("YOUR PUBLISHED", "DRAFT — NOT FINAL", "no app accounts",
            "There is no server account to delete").forEach {
            assertFalse("Offline development claim leaked into production: $it", text.contains(it))
        }
    }
}
