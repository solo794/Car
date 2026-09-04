package com.dfshine.launcher.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Thin wrapper around [SharedPreferences] holding every user-configurable
 * launcher setting. Ordered lists (dock apps, hidden apps, home-page
 * layout) are stored as a "||"-delimited string instead of pulling in a
 * JSON dependency - the values themselves (package/activity component
 * names) never contain "||".
 */
class Prefs(context: Context) {

    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("shine_launcher_prefs", Context.MODE_PRIVATE)

    private companion object {
        const val KEY_THEME_MODE = "theme_mode" // "system" | "light" | "dark"
        const val KEY_ACCENT_COLOR = "accent_color_hex"
        const val KEY_WALLPAPER_URI = "wallpaper_uri"
        const val KEY_GRID_COLUMNS = "grid_columns"
        const val KEY_ICON_LABELS = "show_icon_labels"
        const val KEY_HOME_PAGES = "home_pages_count"
        const val KEY_DOCK_APPS = "dock_apps"
        const val KEY_HIDDEN_APPS = "hidden_apps"
        const val KEY_APP_LOCK_PIN = "app_lock_pin"
        const val KEY_LOCKED_APPS = "locked_apps"
        const val KEY_STEERING_KEYS_ENABLED = "steering_keys_enabled"
        const val KEY_REVERSE_CAMERA_ENABLED = "reverse_camera_enabled"
        const val KEY_REVERSE_CAMERA_CUSTOM_ACTION = "reverse_camera_custom_action"
        const val KEY_REVERSE_CAMERA_APP = "reverse_camera_target_app"
        const val KEY_FLOATING_TOOLS_ON_BOOT = "floating_tools_on_boot"
        const val KEY_NOTIFICATION_BADGES = "notification_badges_enabled"
        const val KEY_ANIMATIONS_ENABLED = "animations_enabled"
        const val KEY_FIRST_RUN_DONE = "first_run_done"
    }

    private fun putList(key: String, values: List<String>) {
        sp.edit().putString(key, values.joinToString("||")).apply()
    }

    private fun getList(key: String): List<String> {
        val raw = sp.getString(key, null) ?: return emptyList()
        if (raw.isBlank()) return emptyList()
        return raw.split("||")
    }

    var themeMode: String
        get() = sp.getString(KEY_THEME_MODE, "dark") ?: "dark"
        set(value) = sp.edit().putString(KEY_THEME_MODE, value).apply()

    var accentColorHex: String
        get() = sp.getString(KEY_ACCENT_COLOR, "#2FB6A6") ?: "#2FB6A6"
        set(value) = sp.edit().putString(KEY_ACCENT_COLOR, value).apply()

    var wallpaperUri: String?
        get() = sp.getString(KEY_WALLPAPER_URI, null)
        set(value) = sp.edit().putString(KEY_WALLPAPER_URI, value).apply()

    /** Icons per row. Tuned defaults suit a tall, narrow car screen. */
    var gridColumns: Int
        get() = sp.getInt(KEY_GRID_COLUMNS, 4)
        set(value) = sp.edit().putInt(KEY_GRID_COLUMNS, value).apply()

    var showIconLabels: Boolean
        get() = sp.getBoolean(KEY_ICON_LABELS, true)
        set(value) = sp.edit().putBoolean(KEY_ICON_LABELS, value).apply()

    var homePagesCount: Int
        get() = sp.getInt(KEY_HOME_PAGES, 2)
        set(value) = sp.edit().putInt(KEY_HOME_PAGES, value).apply()

    var dockApps: List<String>
        get() = getList(KEY_DOCK_APPS)
        set(value) = putList(KEY_DOCK_APPS, value)

    var hiddenApps: Set<String>
        get() = sp.getStringSet(KEY_HIDDEN_APPS, emptySet()) ?: emptySet()
        set(value) = sp.edit().putStringSet(KEY_HIDDEN_APPS, value).apply()

    var appLockPin: String?
        get() = sp.getString(KEY_APP_LOCK_PIN, null)
        set(value) = sp.edit().putString(KEY_APP_LOCK_PIN, value).apply()

    var lockedApps: Set<String>
        get() = sp.getStringSet(KEY_LOCKED_APPS, emptySet()) ?: emptySet()
        set(value) = sp.edit().putStringSet(KEY_LOCKED_APPS, value).apply()

    var steeringKeysEnabled: Boolean
        get() = sp.getBoolean(KEY_STEERING_KEYS_ENABLED, false)
        set(value) = sp.edit().putBoolean(KEY_STEERING_KEYS_ENABLED, value).apply()

    var reverseCameraEnabled: Boolean
        get() = sp.getBoolean(KEY_REVERSE_CAMERA_ENABLED, true)
        set(value) = sp.edit().putBoolean(KEY_REVERSE_CAMERA_ENABLED, value).apply()

    /** Extra broadcast action to listen for, in case this head unit uses a
     *  non-standard "reverse gear engaged" action name. */
    var reverseCameraCustomAction: String?
        get() = sp.getString(KEY_REVERSE_CAMERA_CUSTOM_ACTION, null)
        set(value) = sp.edit().putString(KEY_REVERSE_CAMERA_CUSTOM_ACTION, value).apply()

    /** Component (package/activity) of the camera app to show on reverse. */
    var reverseCameraTargetApp: String?
        get() = sp.getString(KEY_REVERSE_CAMERA_APP, null)
        set(value) = sp.edit().putString(KEY_REVERSE_CAMERA_APP, value).apply()

    var floatingToolsOnBoot: Boolean
        get() = sp.getBoolean(KEY_FLOATING_TOOLS_ON_BOOT, false)
        set(value) = sp.edit().putBoolean(KEY_FLOATING_TOOLS_ON_BOOT, value).apply()

    var notificationBadgesEnabled: Boolean
        get() = sp.getBoolean(KEY_NOTIFICATION_BADGES, true)
        set(value) = sp.edit().putBoolean(KEY_NOTIFICATION_BADGES, value).apply()

    var animationsEnabled: Boolean
        get() = sp.getBoolean(KEY_ANIMATIONS_ENABLED, true)
        set(value) = sp.edit().putBoolean(KEY_ANIMATIONS_ENABLED, value).apply()

    var firstRunDone: Boolean
        get() = sp.getBoolean(KEY_FIRST_RUN_DONE, false)
        set(value) = sp.edit().putBoolean(KEY_FIRST_RUN_DONE, value).apply()
}
