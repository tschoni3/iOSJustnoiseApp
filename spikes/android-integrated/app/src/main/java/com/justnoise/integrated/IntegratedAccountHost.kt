package com.justnoise.integrated

import android.app.Activity
import kotlinx.coroutines.flow.StateFlow

/** Optional production adapter. Offline development apps do not implement it. */
data class IntegratedAccountAccess(val ready: Boolean = false, val generation: Long = 0,
    val signedIn: Boolean = false) {
    val productAllowed: Boolean get() = ready && signedIn

    /**
     * iOS-style order: introduction, first mode and physical pairing stay visible even
     * while account restoration is still running. Required sign-in only blocks MAIN.
     */
    fun blocksRoute(route: IntegratedFirstRunRoute, loadedGeneration: Long): Boolean =
        route == IntegratedFirstRunRoute.MAIN &&
            (!ready || !productAllowed || loadedGeneration != generation)
}
interface IntegratedAccountHost {
    val accountAccess: StateFlow<IntegratedAccountAccess>
    fun openAccount(activity: Activity)
}
