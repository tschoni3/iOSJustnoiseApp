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
        selectedPackages: Set<String>,
        enforcementEligible: Boolean,
        overlayIsShowing: Boolean,
        isOwnActivityWindow: Boolean = false,
    ): OverlayEventDecision {
        if (!isWindowStateChange || foregroundPackage.isNullOrBlank()) {
            return OverlayEventDecision.IGNORE
        }
        if (overlayIsShowing && foregroundPackage == justnoisePackage && !isOwnActivityWindow) {
            return OverlayEventDecision.IGNORE
        }
        if (foregroundPackage == justnoisePackage) return OverlayEventDecision.HIDE
        return if (enforcementEligible && foregroundPackage in selectedPackages) {
            OverlayEventDecision.SHOW
        } else {
            OverlayEventDecision.HIDE
        }
    }
}
