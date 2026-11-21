package com.justnoise.integrated

import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IntegratedGuidanceTypographyTest {
    @Test fun instructionsStandOutWithoutShrinkingRequiredExplanations() {
        val type = IntegratedGuidanceTypography
        assertTrue(type.title.fontSize.value > type.instruction.fontSize.value)
        assertTrue(type.instruction.fontSize.value > type.explanation.fontSize.value)
        assertEquals(FontWeight.SemiBold, type.instruction.fontWeight)
        assertTrue(type.explanation.fontSize.value >= 15f)
        assertTrue(type.explanation.lineHeight.value >= 22f)
        assertEquals(13f, type.disclosure.fontSize.value)
        assertTrue(type.disclosure.lineHeight.value >= 19f)
    }
}
