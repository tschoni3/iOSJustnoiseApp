package com.justnoise.integrated

import android.app.Activity
import kotlinx.coroutines.flow.StateFlow

/** Optional production adapter. Offline development apps do not implement it. */
data class IntegratedAccountAccess(val ready: Boolean = false, val generation: Long = 0,
    val signedIn: Boolean = false) {
    val productAllowed: Boolean get() = ready && signedIn

    /** iOS-style order: introduction, first mode and physical pairing before required sign-in. */
    fun blocksRoute(route: IntegratedFirstRunRoute, loadedGeneration: Long): Boolean =
        !ready || (route == IntegratedFirstRunRoute.MAIN &&
            (!productAllowed || loadedGeneration != generation))
}
interface IntegratedAccountHost {
    val accountAccess: StateFlow<IntegratedAccountAccess>
    fun openAccount(activity: Activity)
}
