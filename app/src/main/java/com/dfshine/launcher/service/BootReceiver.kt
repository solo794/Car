package com.dfshine.launcher.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import com.dfshine.launcher.data.Prefs
import com.dfshine.launcher.util.PermissionUtils

/**
 * Restores the launcher's background tools after the head unit reboots.
 * The home screen itself doesn't need relaunching here - the system
 * starts the default Home app automatically - this only restarts the
 * floating tools if the driver enabled "start on boot" in Settings.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != "android.intent.action.QUICKBOOT_POWERON" &&
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) return

        val prefs = Prefs(context)
        val canOverlay = PermissionUtils.canDrawOverlays(context)

        // The GPS-foreground watcher (auto-positions the music widget while
        // navigating) lives inside FloatingPipService, so it needs to be
        // running even if the driver never explicitly opened a floating
        // tool - start it quietly (collapsed to a bubble) whenever either
        // feature that depends on it is enabled.
        val needsFloatingService = prefs.floatingToolsOnBoot || prefs.musicWidgetDuringGps
        if (needsFloatingService && canOverlay) {
            val floatingAction = if (prefs.floatingToolsOnBoot) {
                FloatingPipService.ACTION_SHOW_QUICK_LAUNCH
            } else {
                FloatingPipService.ACTION_HIDE
            }
            startForegroundServiceCompat(context, FloatingPipService.intent(context, floatingAction))
        }

        if (prefs.navBarEnabled && canOverlay) {
            startForegroundServiceCompat(context, PersistentNavBarService.startIntent(context))
        }

        if (prefs.steeringKeysEnabled) {
            startForegroundServiceCompat(context, Intent(context, MediaKeyService::class.java))
        }
    }

    private fun startForegroundServiceCompat(context: Context, intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(context, intent)
        } else {
            context.startService(intent)
        }
    }
}
