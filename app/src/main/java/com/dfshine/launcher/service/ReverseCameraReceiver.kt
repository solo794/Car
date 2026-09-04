package com.dfshine.launcher.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.dfshine.launcher.data.Prefs

/**
 * Fires when the head unit signals that reverse gear was engaged. The
 * exact broadcast action differs per manufacturer/ROM - the common ones
 * used by Chinese Android head units are declared statically in the
 * manifest, and a custom action string can be added in
 * Settings -> Reverse Camera if this unit uses a different one (register
 * it dynamically below).
 */
class ReverseCameraReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val prefs = Prefs(context)
        if (!prefs.reverseCameraEnabled) return

        val targetComponent = prefs.reverseCameraTargetApp ?: return
        val packageName = targetComponent.substringBefore("/")

        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP) }
            ?: return

        context.startActivity(launchIntent)
    }

    companion object {
        /** Dynamically registers the user's custom reverse-gear broadcast
         *  action (Settings -> Reverse Camera), in addition to the
         *  well-known ones already declared in AndroidManifest.xml. */
        fun registerDynamic(context: Context, receiver: BroadcastReceiver): Boolean {
            val customAction = Prefs(context).reverseCameraCustomAction ?: return false
            if (customAction.isBlank()) return false
            // RECEIVER_EXPORTED: the broadcast comes from the head unit's own
            // vendor/vehicle-service process, a different app - required on
            // Android 13+ or registerReceiver throws at runtime.
            ContextCompat.registerReceiver(
                context,
                receiver,
                android.content.IntentFilter(customAction),
                ContextCompat.RECEIVER_EXPORTED
            )
            return true
        }
    }
}
