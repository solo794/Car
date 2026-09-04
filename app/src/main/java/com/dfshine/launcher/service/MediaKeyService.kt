package com.dfshine.launcher.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.os.IBinder
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import com.dfshine.launcher.LauncherApp
import com.dfshine.launcher.MainActivity
import com.dfshine.launcher.R

/**
 * Lets the steering-wheel media buttons (play/pause/next/previous, wired
 * to the head unit as standard [KeyEvent] media keys) control whichever
 * music/navigation app is actually playing audio, instead of only working
 * while that app's own Activity is in the foreground.
 *
 * It registers an active [MediaSession] - Android routes hardware media
 * button broadcasts to the most recently active session - and simply
 * relays every button press to the real active session found through
 * [MediaSessionManager] (which requires the notification-listener
 * permission, shared with [NotificationBadgeService]).
 */
class MediaKeyService : Service() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())

        mediaSession = MediaSession(this, "ShineLauncherSteeringWheel").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
                    val keyEvent = extractKeyEvent(mediaButtonIntent)
                        ?: return super.onMediaButtonEvent(mediaButtonIntent)

                    if (keyEvent.action != KeyEvent.ACTION_DOWN) return true
                    relayToActiveSession(keyEvent.keyCode)
                    return true
                }
            })
            isActive = true
        }
    }

    @Suppress("DEPRECATION")
    private fun extractKeyEvent(mediaButtonIntent: Intent): KeyEvent? {
        return if (android.os.Build.VERSION.SDK_INT >= 33) {
            mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
        } else {
            mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
        }
    }

    private fun relayToActiveSession(keyCode: Int) {
        val controller = findRealActiveController() ?: return
        val transport = controller.transportControls
        when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_HEADSETHOOK -> {
                val isPlaying = controller.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING
                if (isPlaying) transport.pause() else transport.play()
            }
            KeyEvent.KEYCODE_MEDIA_PLAY -> transport.play()
            KeyEvent.KEYCODE_MEDIA_PAUSE -> transport.pause()
            KeyEvent.KEYCODE_MEDIA_NEXT -> transport.skipToNext()
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> transport.skipToPrevious()
            KeyEvent.KEYCODE_MEDIA_STOP -> transport.stop()
            KeyEvent.KEYCODE_VOLUME_UP -> adjustVolume(raise = true)
            KeyEvent.KEYCODE_VOLUME_DOWN -> adjustVolume(raise = false)
        }
    }

    private fun adjustVolume(raise: Boolean) {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        audioManager.adjustStreamVolume(
            android.media.AudioManager.STREAM_MUSIC,
            if (raise) android.media.AudioManager.ADJUST_RAISE else android.media.AudioManager.ADJUST_LOWER,
            android.media.AudioManager.FLAG_SHOW_UI
        )
    }

    private fun findRealActiveController(): MediaController? {
        return try {
            val manager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val listenerComponent = ComponentName(this, NotificationBadgeService::class.java)
            manager.getActiveSessions(listenerComponent)
                .firstOrNull { it.packageName != packageName }
        } catch (securityException: SecurityException) {
            null
        }
    }

    override fun onDestroy() {
        mediaSession?.isActive = false
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, LauncherApp.CHANNEL_MEDIA_KEYS)
            .setContentTitle("مفاتيح المقود مفعّلة")
            .setContentText("التحكم بالتشغيل من عجلة القيادة")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(openApp)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 4202
    }
}
