package com.justnoise.gate0.domain

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SessionShortcutRulesTest {
    private val fixture = JSONObject(requireNotNull(javaClass.classLoader?.getResource(
        "android-session-shortcuts.v1.json")).readText())

    @Test fun allLongPressCases() {
        val cases = fixture.getJSONArray("longPress")
        for (index in 0 until cases.length()) {
            val row = cases.getJSONObject(index)
            assertEquals(row.getString("id"), row.getString("expected"), SessionShortcutRules.longPress(
                row.getBoolean("requested"), row.getBoolean("recovery"), row.getBoolean("account"),
                row.getBoolean("paired"), row.getBoolean("selection"), row.getBoolean("capability")).contractValue)
        }
    }

    @Test fun allEmergencyCases() {
        assertEquals(fixture.getInt("initialEmergencyUnlocks"), SessionShortcutRules.INITIAL_EMERGENCY_UNLOCKS)
        val cases = fixture.getJSONArray("emergency")
        for (index in 0 until cases.length()) {
            val row = cases.getJSONObject(index)
            assertEquals(row.getString("id"), row.getBoolean("expected"), SessionShortcutRules.emergencyAllowed(
                row.getBoolean("requested"), row.getBoolean("sameSession"), row.getInt("remaining")))
        }
    }
}
