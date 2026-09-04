package com.dfshine.launcher.pip

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager

/**
 * Detects apps that implement Android's real, first-party
 * Picture-in-Picture API themselves (YouTube, Google/Apple Maps, most
 * video and navigation apps do). The launcher can't force PiP on an app
 * that doesn't support it, but it can surface a small badge in the App
 * Drawer so the driver knows which apps will shrink to a floating window
 * when they press Home instead of closing outright.
 */
object PipLauncher {

    fun supportsNativePip(context: Context, packageName: String, activityName: String): Boolean {
        return try {
            val info = context.packageManager.getActivityInfo(
                android.content.ComponentName(packageName, activityName),
                PackageManager.GET_ACTIVITIES
            )
            (info.flags and ActivityInfo.FLAG_SUPPORTS_PICTURE_IN_PICTURE) != 0
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
}
