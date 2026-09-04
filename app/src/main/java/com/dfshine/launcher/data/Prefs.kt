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

        const val KEY_NAV_BAR_ENABLED = "nav_bar_enabled"
        const val KEY_HVAC_APP = "hvac_target_app"
        const val KEY_HVAC_TEMP_UP_ACTION = "hvac_temp_up_action"
        const val KEY_HVAC_TEMP_DOWN_ACTION = "hvac_temp_down_action"
        const val KEY_HVAC_FAN_UP_ACTION = "hvac_fan_up_action"
        const val KEY_HVAC_FAN_DOWN_ACTION = "hvac_fan_down_action"
        const val KEY_HVAC_POWER_ACTION = "hvac_power_toggle_action"
        const val KEY_GPS_APP = "gps_target_app"
        const val KEY_MUSIC_WIDGET_DURING_GPS = "music_widget_during_gps"

        const val KEY_HVAC_AUTO_ACTION = "hvac_auto_toggle_action"
        const val KEY_HVAC_OFF_ACTION = "hvac_off_action"
        const val KEY_HVAC_VENT_ACTION = "hvac_vent_action"
        const val KEY_HVAC_DEFROST_ACTION = "hvac_defrost_toggle_action"
        const val KEY_HVAC_RECIRC_ACTION = "hvac_recirc_toggle_action"
        const val KEY_HVAC_ASSUMED_TEMP = "hvac_assumed_temp"
        const val KEY_HVAC_ASSUMED_FAN = "hvac_assumed_fan"
        const val KEY_HVAC_ASSUMED_AUTO = "hvac_assumed_auto"
        const val KEY_HVAC_ASSUMED_POWER = "hvac_assumed_power"
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

    /**
     * Fallback climate/GPS widget, drawn over whatever app is in the
     * foreground - OFF by default. The Dongfeng Shine's own climate bar
     * (temperature/AUTO/fan) is drawn by the system itself, independently
     * of whichever app is set as the default Home screen, so it should
     * keep working on its own after installing this launcher without
     * needing a replacement. Only turn this on if you test on the real
     * unit and find the native bar actually disappears.
     */
    var navBarEnabled: Boolean
        get() = sp.getBoolean(KEY_NAV_BAR_ENABLED, false)
        set(value) = sp.edit().putBoolean(KEY_NAV_BAR_ENABLED, value).apply()

    /** Component (package/activity) of the OEM climate-control screen. */
    var hvacTargetApp: String?
        get() = sp.getString(KEY_HVAC_APP, null)
        set(value) = sp.edit().putString(KEY_HVAC_APP, value).apply()

    /** Optional broadcast actions for direct AC commands, if this head
     *  unit's vendor documents them. When null, the AC tile just brings
     *  [hvacTargetApp] to the foreground instead. */
    var hvacTempUpAction: String?
        get() = sp.getString(KEY_HVAC_TEMP_UP_ACTION, null)
        set(value) = sp.edit().putString(KEY_HVAC_TEMP_UP_ACTION, value).apply()

    var hvacTempDownAction: String?
        get() = sp.getString(KEY_HVAC_TEMP_DOWN_ACTION, null)
        set(value) = sp.edit().putString(KEY_HVAC_TEMP_DOWN_ACTION, value).apply()

    var hvacFanUpAction: String?
        get() = sp.getString(KEY_HVAC_FAN_UP_ACTION, null)
        set(value) = sp.edit().putString(KEY_HVAC_FAN_UP_ACTION, value).apply()

    var hvacFanDownAction: String?
        get() = sp.getString(KEY_HVAC_FAN_DOWN_ACTION, null)
        set(value) = sp.edit().putString(KEY_HVAC_FAN_DOWN_ACTION, value).apply()

    var hvacPowerToggleAction: String?
        get() = sp.getString(KEY_HVAC_POWER_ACTION, null)
        set(value) = sp.edit().putString(KEY_HVAC_POWER_ACTION, value).apply()

    var hvacAutoToggleAction: String?
        get() = sp.getString(KEY_HVAC_AUTO_ACTION, null)
        set(value) = sp.edit().putString(KEY_HVAC_AUTO_ACTION, value).apply()

    var hvacOffAction: String?
        get() = sp.getString(KEY_HVAC_OFF_ACTION, null)
        set(value) = sp.edit().putString(KEY_HVAC_OFF_ACTION, value).apply()

    var hvacVentAction: String?
        get() = sp.getString(KEY_HVAC_VENT_ACTION, null)
        set(value) = sp.edit().putString(KEY_HVAC_VENT_ACTION, value).apply()

    var hvacDefrostToggleAction: String?
        get() = sp.getString(KEY_HVAC_DEFROST_ACTION, null)
        set(value) = sp.edit().putString(KEY_HVAC_DEFROST_ACTION, value).apply()

    var hvacRecircToggleAction: String?
        get() = sp.getString(KEY_HVAC_RECIRC_ACTION, null)
        set(value) = sp.edit().putString(KEY_HVAC_RECIRC_ACTION, value).apply()

    /** These reflect only what the widget itself has told the car to do
     *  (it cannot read the real HVAC state back - see [hvacTempUpAction]) -
     *  they persist so the numbers on screen stay consistent across
     *  restarts instead of resetting. */
    var hvacAssumedTemp: Int
        get() = sp.getInt(KEY_HVAC_ASSUMED_TEMP, 24)
        set(value) = sp.edit().putInt(KEY_HVAC_ASSUMED_TEMP, value).apply()

    var hvacAssumedFan: Int
        get() = sp.getInt(KEY_HVAC_ASSUMED_FAN, 2)
        set(value) = sp.edit().putInt(KEY_HVAC_ASSUMED_FAN, value).apply()

    var hvacAssumedAuto: Boolean
        get() = sp.getBoolean(KEY_HVAC_ASSUMED_AUTO, true)
        set(value) = sp.edit().putBoolean(KEY_HVAC_ASSUMED_AUTO, value).apply()

    var hvacAssumedPower: Boolean
        get() = sp.getBoolean(KEY_HVAC_ASSUMED_POWER, true)
        set(value) = sp.edit().putBoolean(KEY_HVAC_ASSUMED_POWER, value).apply()

    /** Component (package/activity) of the GPS/navigation app (any offline
     *  maps app sideloaded on the unit - Google/GMS-free ones since this
     *  head unit has no Google Play Services). */
    var gpsTargetApp: String?
        get() = sp.getString(KEY_GPS_APP, null)
        set(value) = sp.edit().putString(KEY_GPS_APP, value).apply()

    /** Auto-show the floating music widget (pinned to the upper half of
     *  the screen) whenever [gpsTargetApp] is the foreground app. */
    var musicWidgetDuringGps: Boolean
        get() = sp.getBoolean(KEY_MUSIC_WIDGET_DURING_GPS, true)
        set(value) = sp.edit().putBoolean(KEY_MUSIC_WIDGET_DURING_GPS, value).apply()
}
