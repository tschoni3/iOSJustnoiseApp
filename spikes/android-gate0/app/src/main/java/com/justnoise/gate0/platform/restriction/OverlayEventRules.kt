package com.justnoise.gate0.platform.restriction

enum class OverlayEventDecision {
    SHOW,
    HIDE,
    IGNORE,
}

object OverlayEventRules {
    fun decide(
        isWindowStateChange: Boolean,
        foregroundPackage: String?,
        justnoisePackage: String,
        selectedPackage: String?,
        enforcementEligible: Boolean,
        overlayIsShowing: Boolean,
    ): OverlayEventDecision {
        if (!isWindowStateChange || foregroundPackage.isNullOrBlank()) {
            return OverlayEventDecision.IGNORE
        }
        if (overlayIsShowing && foregroundPackage == justnoisePackage) {
            return OverlayEventDecision.IGNORE
        }
        return if (enforcementEligible && foregroundPackage == selectedPackage) {
            OverlayEventDecision.SHOW
        } else {
            OverlayEventDecision.HIDE
        }
    }
}
