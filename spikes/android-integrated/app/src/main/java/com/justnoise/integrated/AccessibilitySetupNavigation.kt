package com.justnoise.integrated

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.justnoise.gate0.platform.restriction.Gate0AccessibilityService
import com.justnoise.gate0.platform.restriction.AccessibilitySetupReturn

/** Navigation only. Android and the user still own service activation and confirmation. */
internal object AccessibilitySetupNavigation {
    // AOSP Settings handles this action with a flattened service component. Some OEMs
    // omit it, so never depend on it for access or use private Settings fragments.
    // https://android.googlesource.com/platform/packages/apps/Settings/+/HEAD/src/com/android/settings/accessibility/AccessibilityDetailsSettingsFragment.java
    const val DETAILS_ACTION = "android.settings.ACCESSIBILITY_DETAILS_SETTINGS"
    const val COMPONENT_EXTRA = "android.intent.extra.COMPONENT_NAME"
    // Best-effort Samsung entry; newer One UI releases may not expose it.
    const val INSTALLED_SERVICES_ACTION = "com.samsung.accessibility.installed_service"
    const val GENERAL_ACTION = "android.settings.ACCESSIBILITY_SETTINGS"

    enum class Route { SERVICE_DETAILS, INSTALLED_SERVICES, ACCESSIBILITY_LIST }

    fun action(route: Route): String = when (route) {
        Route.SERVICE_DETAILS -> DETAILS_ACTION
        Route.INSTALLED_SERVICES -> INSTALLED_SERVICES_ACTION
        Route.ACCESSIBILITY_LIST -> GENERAL_ACTION
    }

    fun launchFirstSupported(tryOpen: (Route) -> Boolean): Boolean =
        Route.entries.any(tryOpen)

    fun open(context: Context): Boolean {
        AccessibilitySetupReturn.arm()
        val opened = launchFirstSupported { route ->
            val intent = Intent(action(route))
            // The Samsung action belongs to Samsung's accessibility package, not Settings.
            intent.setPackage(if (route == Route.INSTALLED_SERVICES) "com.samsung.accessibility"
                else "com.android.settings")
            if (route == Route.SERVICE_DETAILS) {
                // Installed package identity differs between development and Play builds.
                val service = ComponentName(context, Gate0AccessibilityService::class.java)
                intent.putExtra(COMPONENT_EXTRA, service.flattenToString())
            }
            try {
                context.startActivity(intent)
                true
            } catch (_: ActivityNotFoundException) {
                false
            } catch (_: SecurityException) {
                false
            }
        }
        if (!opened) AccessibilitySetupReturn.cancel()
        return opened
    }
}
