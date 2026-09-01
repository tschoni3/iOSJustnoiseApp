package com.justnoise.gate0.platform.nfc

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OneShotScanCoordinatorTest {
    @Test
    fun `one callback can claim an armed scan`() {
        val coordinator = OneShotScanCoordinator<String>()
        val token = coordinator.begin("pair")

        assertTrue(coordinator.claim(token))
        assertFalse(coordinator.claim(token))
        assertTrue(coordinator.isClaimActive(token))

        coordinator.finish(token)
        assertFalse(coordinator.isActive())
    }

    @Test
    fun `cancel invalidates a callback that is already reading`() {
        val coordinator = OneShotScanCoordinator<String>()
        val token = coordinator.begin("toggle")
        assertTrue(coordinator.claim(token))

        coordinator.cancel()

        assertFalse(coordinator.isClaimActive(token))
        assertFalse(coordinator.isActive())
    }

    @Test
    fun `late claimed callback cannot finish a replacement scan`() {
        val coordinator = OneShotScanCoordinator<String>()
        val stale = coordinator.begin("pair")
        assertTrue(coordinator.claim(stale))

        val replacement = coordinator.begin("toggle")
        coordinator.finish(stale)

        assertTrue(coordinator.isActive())
        assertTrue(coordinator.claim(replacement))
        assertTrue(coordinator.isClaimActive(replacement))
    }

    @Test
    fun `queued callback from replaced scan cannot claim the replacement`() {
        val coordinator = OneShotScanCoordinator<String>()
        val stale = coordinator.begin("pair")
        val replacement = coordinator.begin("toggle")

        assertFalse(coordinator.claim(stale))
        assertTrue(coordinator.claim(replacement))
        assertTrue(coordinator.isClaimActive(replacement))
    }

    @Test
    fun `token scoped cancellation cannot cancel a replacement scan`() {
        val coordinator = OneShotScanCoordinator<String>()
        val stale = coordinator.begin("pair")
        val replacement = coordinator.begin("toggle")

        coordinator.cancel(stale)

        assertTrue(coordinator.isActive())
        assertTrue(coordinator.claim(replacement))
    }
}
