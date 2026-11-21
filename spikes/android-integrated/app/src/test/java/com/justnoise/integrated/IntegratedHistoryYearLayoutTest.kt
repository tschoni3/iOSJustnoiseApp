package com.justnoise.integrated

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IntegratedHistoryYearLayoutTest {
    @Test fun everyDayFitsCompactStandardAndEnlargedTextRemainingViewports() {
        listOf(365, 366).forEach { days ->
            listOf(288f to 150f, 328f to 240f, 352f to 450f, 736f to 300f).forEach { (width, height) ->
                val layout = IntegratedHistoryGridRules.yearLayout(days, width, height)
                assertTrue(layout.cellSizeDp > 0f)
                assertTrue(layout.columns * layout.cellSizeDp <= width + .001f)
                assertTrue(layout.rows * layout.cellSizeDp <= height + .001f)
                assertTrue(layout.columns * layout.rows >= days)
                assertTrue((layout.rows - 1) * layout.columns < days)
                // First through last day are present exactly once, in date order.
                val indices = (0 until layout.rows).flatMap { row ->
                    (row * layout.columns until minOf((row + 1) * layout.columns, days)).toList()
                }
                assertEquals((0 until days).toList(), indices)
            }
        }
    }

    @Test fun geometryUsesBothHeightAndWidthRatherThanFixedLargeTargets() {
        val short = IntegratedHistoryGridRules.yearLayout(366, 352f, 150f)
        val tall = IntegratedHistoryGridRules.yearLayout(366, 352f, 500f)
        assertTrue(short.columns > tall.columns)
        assertTrue(tall.cellSizeDp > short.cellSizeDp)
    }

    @Test fun zeroOrNonFiniteBoundsDoNotGenerateInvalidSizes() {
        listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY).forEach { size ->
            assertEquals(0f, IntegratedHistoryGridRules.yearLayout(366, size, 200f).cellSizeDp, 0f)
            assertEquals(0f, IntegratedHistoryGridRules.yearLayout(366, 300f, size).cellSizeDp, 0f)
        }
    }
}
