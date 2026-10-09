package com.justnoise.gate0.feature

import org.junit.Assert.*
import org.junit.Test

class SessionLiveUpdateRulesTest {
    @Test fun requestContractNeedsSupportedVersionAndDeclaredPermissionNotDebugFlag() {
        assertFalse(SessionLiveUpdateRules.supported(35, 0, true))
        assertFalse(SessionLiveUpdateRules.supported(36, 3_600_000, true))
        assertFalse(SessionLiveUpdateRules.supported(36, 3_600_001, false))
        assertTrue(SessionLiveUpdateRules.supported(36, 3_600_001, true))
        assertTrue(SessionLiveUpdateRules.supported(37, 3_700_000, true))
    }

    @Test fun settingsNeverConfusesPermissionWithActualPromotion() {
        assertEquals(SessionLiveUpdateStatus.UNSUPPORTED, status(false, true, true, true))
        assertEquals(SessionLiveUpdateStatus.NOTIFICATIONS_OFF, status(true, false, true, true))
        assertEquals(SessionLiveUpdateStatus.PROMOTION_OFF, status(true, true, false, true))
        assertEquals(SessionLiveUpdateStatus.ALLOWED, status(true, true, true, false))
        assertEquals(SessionLiveUpdateStatus.PROMOTED, status(true, true, true, true))
        assertEquals(SessionLiveUpdateStatus.NOT_PROMOTED,
            SessionLiveUpdateRules.status(true, true, true, false, rejected = true))
    }

    @Test fun onlyHealthyRequestedPromotableSessionsCanBePosted() {
        val active = SessionNotificationProjection.SessionTime("synthetic", 1L)
        for (projection in listOf(active, SessionNotificationProjection.Hidden,
            SessionNotificationProjection.Attention("synthetic"))) {
            for (allowed in listOf(false, true)) for (requested in listOf(false, true))
                for (promotable in listOf(false, true)) {
                    assertEquals(projection == active && allowed && requested && promotable,
                        SessionLiveUpdateRules.shouldPost(projection, allowed, requested, promotable))
                }
        }
    }

    private fun status(supported: Boolean, enabled: Boolean, allowed: Boolean, promoted: Boolean) =
        SessionLiveUpdateRules.status(supported, enabled, allowed, promoted)
}
