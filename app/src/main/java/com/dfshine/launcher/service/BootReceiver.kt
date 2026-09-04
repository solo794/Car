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
        if (prefs.floatingToolsOnBoot && PermissionUtils.canDrawOverlays(context)) {
            val serviceIntent = FloatingPipService.intent(context, FloatingPipService.ACTION_SHOW_QUICK_LAUNCH)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }

        if (prefs.steeringKeysEnabled) {
            val mediaServiceIntent = Intent(context, MediaKeyService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, mediaServiceIntent)
            } else {
                context.startService(mediaServiceIntent)
            }
        }
    }
}
