package com.dfshine.launcher.model

import android.graphics.drawable.Drawable

/**
 * Lightweight representation of an installed, launchable app.
 * [icon] is resolved lazily by the repository and cached in memory only
 * (never persisted) so the icon cache always reflects the currently
 * installed package.
 */
data class AppInfo(
    val packageName: String,
    val activityName: String,
    val label: String,
    val icon: Drawable?,
    val isSystemApp: Boolean = false
) {
    /** Stable key used for pinning, hiding and badge lookups. */
    val key: String get() = "$packageName/$activityName"
}
