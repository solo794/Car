package com.dfshine.launcher.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.dfshine.launcher.model.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Loads every launchable app on the device (used for the App Drawer, dock
 * picker and reverse-camera app picker). Results are not cached across
 * calls so newly installed/removed apps always show up without a restart.
 */
class AppRepository(private val context: Context) {

    suspend fun loadAllApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolveInfos = pm.queryIntentActivities(mainIntent, PackageManager.MATCH_ALL)

        resolveInfos
            .mapNotNull { resolveInfo ->
                val activityInfo = resolveInfo.activityInfo ?: return@mapNotNull null
                val label = resolveInfo.loadLabel(pm)?.toString() ?: activityInfo.packageName
                val icon = runCatching { resolveInfo.loadIcon(pm) }.getOrNull()
                AppInfo(
                    packageName = activityInfo.packageName,
                    activityName = activityInfo.name,
                    label = label,
                    icon = icon,
                    isSystemApp = (activityInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    fun launchIntentFor(app: AppInfo): Intent? {
        return context.packageManager.getLaunchIntentForPackage(app.packageName)
            ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    }
}
