package com.justnoise.integrated

enum class IntegratedFirstRunRoute { ONBOARDING, FIRST_MODE, ACTIVATION, MAIN }

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
        if (facts.sessionRequested || facts.recoveryRequired) {
            return IntegratedFirstRunRoute.MAIN
        }
        // A paired installation already finished first-run education, or predates it.
        if (facts.zapPaired && !facts.tutorialCompleted) return IntegratedFirstRunRoute.MAIN
        if (facts.zapPaired) {
            return if (restored == IntegratedFirstRunRoute.ACTIVATION) {
                IntegratedFirstRunRoute.ACTIVATION
            } else {
                IntegratedFirstRunRoute.MAIN
            }
        }
        // Unpaired after first-mode completion: iOS always shows NFCActivationView
        // until isActivated, including after process death or a stale MAIN restore.
        // 1.0.21 skipped this because saving the first mode writes selectedPackages
        // (existingSetup=true) before the tutorial flag is persisted; refreshTruth
        // then routed to MAIN, and `restored == MAIN` kept skipping Connect your Zap.
        if (facts.tutorialCompleted) return IntegratedFirstRunRoute.ACTIVATION
        // Keep an in-progress first-mode screen even after that package write.
        if (restored == IntegratedFirstRunRoute.FIRST_MODE) return IntegratedFirstRunRoute.FIRST_MODE
        // Existing disclosure or session history is not reset by the new introduction.
        if (facts.existingSetup) return IntegratedFirstRunRoute.MAIN
        if (restored == IntegratedFirstRunRoute.MAIN) return IntegratedFirstRunRoute.MAIN
        return IntegratedFirstRunRoute.ONBOARDING
    }

    fun needsBlockingSetup(
        facts: IntegratedFirstRunFacts,
        disclosureAccepted: Boolean,
        accessibilityEnabled: Boolean,
    ): Boolean = facts.zapPaired &&
        !facts.sessionRequested && !facts.recoveryRequired &&
        (!disclosureAccepted || !accessibilityEnabled)

    /** Required sign-in waits until first-run education, pairing, and Android permission setup finish. */
    fun shouldHoldRequiredAccount(
        route: IntegratedFirstRunRoute,
        showingAccessibilitySetup: Boolean,
        showingScheduleSetup: Boolean,
        needsBlockingSetup: Boolean,
    ): Boolean = route == IntegratedFirstRunRoute.ONBOARDING ||
        route == IntegratedFirstRunRoute.FIRST_MODE ||
        route == IntegratedFirstRunRoute.ACTIVATION ||
        showingAccessibilitySetup || showingScheduleSetup ||
        (route == IntegratedFirstRunRoute.MAIN && needsBlockingSetup)
}

/** Capture intent when arming the reader; never infer it from the current route on callback. */
enum class IntegratedScanPurpose { PAIR_INITIAL, TOGGLE_SESSION }
