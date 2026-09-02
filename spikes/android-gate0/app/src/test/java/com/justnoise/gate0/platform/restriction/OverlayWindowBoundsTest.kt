package com.justnoise.gate0.platform.restriction

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OverlayWindowBoundsTest {
    @Test
    fun `A16 portrait leaves status and three button navigation outside window`() {
        assertEquals(
            OverlayWindowBounds(0, 100, 1080, 2105),
            OverlayWindowBounds.calculate(0, 0, 1080, 2340, 0, 100, 0, 135),
        )
    }

    @Test
    fun `landscape excludes cutout and right navigation without mirroring coordinates`() {
        assertEquals(
            OverlayWindowBounds(100, 0, 2105, 1080),
            OverlayWindowBounds.calculate(0, 0, 2340, 1080, 100, 0, 135, 0),
        )
    }

    @Test
    fun `left navigation is excluded independently of layout direction`() {
        assertEquals(
            OverlayWindowBounds(135, 0, 2205, 1080),
            OverlayWindowBounds.calculate(0, 0, 2340, 1080, 135, 0, 0, 0),
        )
    }

    @Test
    fun `zero visible navigation inset does not leave a target content gap`() {
        assertEquals(
            OverlayWindowBounds(0, 100, 1080, 2240),
            OverlayWindowBounds.calculate(0, 0, 1080, 2340, 0, 100, 0, 0),
        )
    }

    @Test
    fun `window metrics origin is preserved`() {
        assertEquals(
            OverlayWindowBounds(50, 160, 1080, 2105),
            OverlayWindowBounds.calculate(50, 60, 1130, 2400, 0, 100, 0, 135),
        )
    }

    @Test
    fun `invalid empty negative or overflowing area cannot become a full screen window`() {
        assertNull(OverlayWindowBounds.calculate(0, 0, 1080, 2340, -1, 0, 0, 0))
        assertNull(OverlayWindowBounds.calculate(0, 0, 1080, 2340, 1080, 0, 0, 0))
        assertNull(OverlayWindowBounds.calculate(0, 0, 1080, 2340, 0, 2341, 0, 0))
        assertNull(OverlayWindowBounds.calculate(0, 0, 0, 0, 0, 0, 0, 0))
        assertNull(OverlayWindowBounds.calculate(Int.MIN_VALUE, 0, Int.MAX_VALUE, 2340, 0, 0, 0, 0))
    }
}
