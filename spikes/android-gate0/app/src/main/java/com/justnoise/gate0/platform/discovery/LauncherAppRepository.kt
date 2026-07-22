package com.justnoise.gate0.platform.discovery

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

data class LaunchableApp(
    val label: String,
    val packageName: String,
)

class LauncherAppRepository(
    private val context: Context,
    private val protectedPackageResolver: ProtectedPackageResolver =
        ProtectedPackageResolver(context),
) {
    fun load(): List<LaunchableApp> {
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val protectedPackages = protectedPackageResolver.resolve()

        return queryIntentActivities(launcherIntent)
            .asSequence()
            .map { resolveInfo ->
                LaunchableApp(
                    label = resolveInfo.loadLabel(context.packageManager).toString(),
                    packageName = resolveInfo.activityInfo.packageName,
                )
            }
            .filterNot { ProtectedPackagePolicy.isProtected(it.packageName, protectedPackages) }
            .distinctBy(LaunchableApp::packageName)
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, LaunchableApp::label))
            .toList()
    }

    @Suppress("DEPRECATION")
    private fun queryIntentActivities(intent: Intent) = if (android.os.Build.VERSION.SDK_INT >= 33) {
        context.packageManager.queryIntentActivities(
            intent,
            PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong()),
        )
    } else {
        context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
    }
}
