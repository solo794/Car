package com.dfshine.launcher

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.dfshine.launcher.service.ReverseCameraReceiver

class LauncherApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()

        // Android 8+ only auto-delivers a handful of "protected" implicit
        // broadcasts to manifest-declared receivers; a vendor-specific
        // reverse-gear action typically isn't one of them. Registering it
        // here too means it still works as long as the launcher's process
        // is alive (which, being the default Home app, is effectively all
        // the time on a running head unit).
        ReverseCameraReceiver.registerDynamic(this, ReverseCameraReceiver())
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_FLOATING,
                getString(R.string.notif_channel_floating),
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = getString(R.string.notif_channel_floating_desc) }
        )

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_MEDIA_KEYS,
                getString(R.string.notif_channel_media),
                NotificationManager.IMPORTANCE_MIN
            ).apply { description = getString(R.string.notif_channel_media_desc) }
        )
    }

    companion object {
        const val CHANNEL_FLOATING = "floating_tools"
        const val CHANNEL_MEDIA_KEYS = "media_keys"
    }
}
