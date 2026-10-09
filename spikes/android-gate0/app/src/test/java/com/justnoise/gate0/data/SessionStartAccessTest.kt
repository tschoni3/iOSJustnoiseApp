package com.justnoise.gate0.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SessionStartAccessTest {
    @Test fun productionAndDevelopmentFollowAccountAccessFixtures() {
        val source = requireNotNull(javaClass.classLoader!!.getResourceAsStream("android-account-access.v1.json"))
            .bufferedReader().use { it.readText() }
        val cases = JSONObject(source).getJSONArray("cases")
        for (i in 0 until cases.length()) {
            val case = cases.getJSONObject(i)
            val access = SessionStartAccess(case.getBoolean("required"))
            access.authorizedOwner = if (case.isNull("authorizedOwner")) null
                else LocalWorkspaceId.parse(case.getString("authorizedOwner"))
            assertEquals(case.getString("id"), case.getBoolean("canStart"),
                access.allows(LocalWorkspaceId.parse(case.getString("workspace"))))
        }
    }
}
