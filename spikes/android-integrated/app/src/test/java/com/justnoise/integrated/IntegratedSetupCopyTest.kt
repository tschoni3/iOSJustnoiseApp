package com.justnoise.integrated

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/** Copy-only guards: these do not grant consent or establish service availability. */
class IntegratedSetupCopyTest {
    private fun strings(): Map<String, String> {
        val source = generateSequence(File(requireNotNull(System.getProperty("user.dir"))).canonicalFile) { it.parentFile }
            .map { File(it, "spikes/android-gate0/app/src/main/res/values/strings.xml") }
            .first { it.isFile }
        val nodes = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }.newDocumentBuilder().parse(source).getElementsByTagName("string")
        return (0 until nodes.length).associate { index ->
            val element = nodes.item(index) as Element
            element.getAttribute("name") to element.textContent
        }
    }

    @Test fun disclosureKeepsDataAndConsentBoundariesInCustomerLanguage() {
        val copy = strings()
        val disclosure = copy.getValue("accessibility_disclosure")
        assertTrue(disclosure.startsWith("Justnoise uses Android Accessibility only while you have explicitly enabled"))
        listOf("does not read screen content", "Nothing is sent off this device",
            "exact-alarm access is requested separately", "never catches up old starts",
            "History contains no app names or event log", "Session time does not prove continuous blocking",
            "disable Accessibility, Force Stop, or uninstall at any time").forEach {
            assertTrue("Disclosure must retain: $it", disclosure.contains(it))
        }
        assertTrue(copy.getValue("consent_checkbox").contains("I understand this disclosure"))
        assertTrue(copy.getValue("status_consent_required").contains("App blocking is off"))
    }

    @Test fun normalSetupActionsDoNotUsePrototypeTerminology() {
        val copy = strings()
        listOf("gate_title", "accessibility_disclosure", "consent_checkbox", "zap_title",
            "zap_explanation", "pair_zap", "withdraw", "status_withdrawn").forEach { key ->
            val value = copy.getValue(key)
            listOf("Gate 0", "prototype", "test Zap", "feasibility").forEach {
                assertFalse("$key must not expose $it", value.contains(it))
            }
        }
    }

    @Test fun scanResultCopyDoesNotExposeProtocolOrImplementationDiagnostics() {
        val copy = strings()
        copy.filterKeys { it.startsWith("status_scan_") || it in setOf(
            "status_pair_success", "status_pair_storage_failed", "status_stopped", "zap_explanation") }
            .forEach { (key, value) ->
                listOf("NDEF", "UTF-8", "payload", "overlay", "State did not change").forEach {
                    assertFalse("$key must not expose $it", value.contains(it))
                }
            }
    }
}
