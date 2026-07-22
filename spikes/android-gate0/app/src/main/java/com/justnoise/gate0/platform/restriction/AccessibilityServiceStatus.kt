package com.justnoise.gate0.platform.restriction

import android.content.ComponentName
import android.content.Context
import android.provider.Settings

object AccessibilityServiceStatus {
    fun isEnabled(context: Context): Boolean {
        val expected = ComponentName(context, Gate0AccessibilityService::class.java)
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ).orEmpty()

        return enabledServices
            .split(':')
            .mapNotNull(ComponentName::unflattenFromString)
            .any { it == expected }
    }
}
