package com.justnoise.integrated

enum class IntegratedFirstRunRoute { ONBOARDING, ACTIVATION, MAIN }

/** Presentation routing only. None of these facts grants consent, pairs a Zap or starts a session. */
data class IntegratedFirstRunFacts(
    val tutorialCompleted: Boolean,
    val zapPaired: Boolean,
    val existingSetup: Boolean,
    val sessionRequested: Boolean,
    val recoveryRequired: Boolean,
)

object IntegratedFirstRunRules {
    fun route(
        facts: IntegratedFirstRunFacts,
        restored: IntegratedFirstRunRoute? = null,
    ): IntegratedFirstRunRoute {
        // Never let a new introduction or a stale restored screen obstruct a real stop/recovery.
        if (facts.sessionRequested || facts.recoveryRequired || facts.existingSetup) {
            return IntegratedFirstRunRoute.MAIN
        }
        // A pre-existing paired installation predates the tutorial. Skip education only:
        // the existing controller still decides whether consent and capability are ready.
        if (facts.zapPaired && !facts.tutorialCompleted) return IntegratedFirstRunRoute.MAIN
        if (restored == IntegratedFirstRunRoute.MAIN) return IntegratedFirstRunRoute.MAIN
        if (facts.tutorialCompleted) return IntegratedFirstRunRoute.ACTIVATION
        return IntegratedFirstRunRoute.ONBOARDING
    }

    fun page(restoredPage: Int): Int = restoredPage.coerceIn(0, 1)
}

/** Capture intent when arming the reader; never infer it from the current route on callback. */
enum class IntegratedScanPurpose { PAIR_INITIAL, TOGGLE_SESSION }
