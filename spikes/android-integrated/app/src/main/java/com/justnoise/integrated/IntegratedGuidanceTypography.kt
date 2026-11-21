package com.justnoise.integrated

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** A small, consistent hierarchy for setup and first-session guidance. */
internal object IntegratedGuidanceTypography {
    val title = TextStyle(color = Color.Black, fontSize = 30.sp, lineHeight = 36.sp,
        fontWeight = FontWeight.Bold)
    val instruction = TextStyle(color = Color.Black, fontSize = 17.sp, lineHeight = 24.sp,
        fontWeight = FontWeight.SemiBold)
    val explanation = TextStyle(color = Color(0xFF555555), fontSize = 15.sp, lineHeight = 22.sp)
    val disclosure = TextStyle(color = Color(0xFF444444), fontSize = 13.sp, lineHeight = 19.sp)
    val section = TextStyle(color = Color.Black, fontSize = 15.sp, lineHeight = 22.sp,
        fontWeight = FontWeight.SemiBold)
    val caption = TextStyle(color = Color(0xFF555555), fontSize = 13.sp, lineHeight = 18.sp,
        fontWeight = FontWeight.Medium)
}
