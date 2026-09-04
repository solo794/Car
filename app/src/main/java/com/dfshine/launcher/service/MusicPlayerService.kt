package com.dfshine.launcher.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaMetadata
import android.media.MediaPlayer
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.Uri
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.dfshine.launcher.LauncherApp
import com.dfshine.launcher.MainActivity
import com.dfshine.launcher.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

data class Track(val id: Long, val title: String, val artist: String, val uri: Uri)

data class PlayerUiState(
    val queue: List<Track> = emptyList(),
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false
) {
    val currentTrack: Track? get() = queue.getOrNull(currentIndex)
}

/**
 * Built-in music player: plays local audio files (from MediaStore, or a
 * single file handed over by the File Manager) through a foreground
 * service so playback survives leaving the Music Player screen, and
 * publishes a [MediaSession] so the floating mini player in
 * [FloatingPipService] and the steering-wheel keys in [MediaKeyService]
 * both control it like any other music app.
 */
class MusicPlayerService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        startForeground(NOTIFICATION_ID, buildNotification(null))

        mediaSession = MediaSession(this, "ShineLauncherMusicPlayer").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() { resumeInternal() }
                override fun onPause() { pauseInternal() }
                override fun onSkipToNext() { nextInternal() }
                override fun onSkipToPrevious() { previousInternal() }
                override fun onStop() { stopSelf() }
            })
        }

        pendingQueue?.let { (tracks, index) ->
            playQueueInternal(tracks, index)
            pendingQueue = null
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        mediaPlayer?.release()
        mediaPlayer = null
        mediaSession?.release()
        mediaSession = null
        instance = null
        super.onDestroy()
    }

    // ---------------------------------------------------------------
    // Playback
    // ---------------------------------------------------------------

    private fun playQueueInternal(tracks: List<Track>, startIndex: Int) {
        state.update { it.copy(queue = tracks, currentIndex = startIndex) }
        playCurrent()
    }

    private fun playCurrent() {
        val track = state.value.currentTrack ?: return
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            setDataSource(this@MusicPlayerService, track.uri)
            setOnCompletionListener { nextInternal() }
            setOnPreparedListener {
                start()
                state.update { it.copy(isPlaying = true) }
                publishSessionState(track, isPlaying = true)
            }
            prepareAsync()
        }
        startForeground(NOTIFICATION_ID, buildNotification(track))
    }

    private fun resumeInternal() {
        if (mediaPlayer == null) {
            playCurrent()
            return
        }
        mediaPlayer?.start()
        state.update { it.copy(isPlaying = true) }
        state.value.currentTrack?.let { publishSessionState(it, isPlaying = true) }
    }

    private fun pauseInternal() {
        mediaPlayer?.pause()
        state.update { it.copy(isPlaying = false) }
        state.value.currentTrack?.let { publishSessionState(it, isPlaying = false) }
    }

    private fun nextInternal() {
        val s = state.value
        if (s.queue.isEmpty()) return
        val nextIndex = (s.currentIndex + 1).coerceAtMost(s.queue.size - 1)
        if (nextIndex == s.currentIndex) return
        state.update { it.copy(currentIndex = nextIndex) }
        playCurrent()
    }

    private fun previousInternal() {
        val s = state.value
        if (s.queue.isEmpty()) return
        val prevIndex = (s.currentIndex - 1).coerceAtLeast(0)
        state.update { it.copy(currentIndex = prevIndex) }
        playCurrent()
    }

    private fun publishSessionState(track: Track, isPlaying: Boolean) {
        val session = mediaSession ?: return
        session.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, track.title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, track.artist)
                .build()
        )
        session.setPlaybackState(
            PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
                        PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS
                )
                .setState(
                    if (isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                    mediaPlayer?.currentPosition?.toLong() ?: 0L,
                    1f
                )
                .build()
        )
        session.isActive = true
        startForeground(NOTIFICATION_ID, buildNotification(track))
    }

    private fun buildNotification(track: Track?): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, LauncherApp.CHANNEL_FLOATING)
            .setContentTitle(track?.title ?: "مشغل الموسيقى")
            .setContentText(track?.artist ?: "لا يوجد تشغيل حالياً")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(openApp)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 4204

        val state = MutableStateFlow(PlayerUiState())
        val stateFlow: StateFlow<PlayerUiState> get() = state

        private var instance: MusicPlayerService? = null
        private var pendingQueue: Pair<List<Track>, Int>? = null

        fun playQueue(context: Context, tracks: List<Track>, startIndex: Int) {
            val running = instance
            if (running != null) {
                running.playQueueInternal(tracks, startIndex)
            } else {
                pendingQueue = tracks to startIndex
                ContextCompat.startForegroundService(context, Intent(context, MusicPlayerService::class.java))
            }
        }

        fun togglePlayPause() {
            val running = instance ?: return
            if (state.value.isPlaying) running.pauseInternal() else running.resumeInternal()
        }

        fun next() { instance?.nextInternal() }
        fun previous() { instance?.previousInternal() }
    }
}
