package com.justnoise.gate0.platform.discovery

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.telecom.TelecomManager

object ProtectedPackagePolicy {
    fun isProtected(candidate: String, protectedPackages: Set<String>): Boolean =
        candidate.isBlank() || candidate in protectedPackages
}

class ProtectedPackageResolver(
    private val context: Context,
) {
    fun resolve(): Set<String> = buildSet {
        add(context.packageName)
        addAll(BASELINE_SYSTEM_PACKAGES)
        addAll(resolveHomePackages())
        resolvePackage(Intent(Settings.ACTION_SETTINGS))?.let(::add)
        resolvePackage(
            Intent(
                Intent.ACTION_DELETE,
                Uri.parse("package:${context.packageName}"),
            ),
        )?.let(::add)

        context.getSystemService(TelecomManager::class.java)
            ?.defaultDialerPackage
            ?.let(::add)
    }

    private fun resolveHomePackages(): Set<String> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return queryIntentActivities(intent)
            .mapTo(mutableSetOf()) { it.activityInfo.packageName }
    }

    private fun resolvePackage(intent: Intent): String? = context.packageManager
        .resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        ?.activityInfo
        ?.packageName

    @Suppress("DEPRECATION")
    private fun queryIntentActivities(intent: Intent) = if (android.os.Build.VERSION.SDK_INT >= 33) {
        context.packageManager.queryIntentActivities(
            intent,
            PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()),
        )
    } else {
        context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
    }

    companion object {
        private val BASELINE_SYSTEM_PACKAGES = setOf(
            "com.android.settings",
            "com.android.systemui",
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            "com.samsung.android.packageinstaller",
            "com.android.vending",
            "com.sec.android.app.samsungapps",
            "com.android.emergency",
            "com.google.android.emergency",
        )
    }
}
