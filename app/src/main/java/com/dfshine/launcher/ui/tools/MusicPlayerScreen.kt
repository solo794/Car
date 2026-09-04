package com.dfshine.launcher.ui.tools

import android.content.ContentUris
import android.provider.MediaStore
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dfshine.launcher.data.MediaLaunchBridge
import com.dfshine.launcher.service.MusicPlayerService
import com.dfshine.launcher.service.Track

@Composable
fun MusicPlayerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var tracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    val playerState by MusicPlayerService.stateFlow.collectAsState()

    LaunchedEffect(Unit) {
        tracks = queryAudioTracks(context)
        MediaLaunchBridge.pendingAudioUri?.let { uri ->
            MediaLaunchBridge.pendingAudioUri = null
            val fileTrack = Track(id = -1, title = uri.lastPathSegment ?: "ملف صوتي", artist = "", uri = uri)
            MusicPlayerService.playQueue(context, listOf(fileTrack) + tracks, 0)
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "رجوع") }
            Text(
                "مشغل الموسيقى",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        NowPlayingBar(playerState)

        if (tracks.isEmpty()) {
            Text(
                "لا يوجد ملفات صوتية على الجهاز",
                modifier = Modifier.padding(16.dp)
            )
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(tracks, key = { it.id }) { track ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { MusicPlayerService.playQueue(context, tracks, tracks.indexOf(track)) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Filled.MusicNote, contentDescription = null)
                        Column {
                            Text(track.title, style = MaterialTheme.typography.bodyLarge)
                            Text(track.artist, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NowPlayingBar(playerState: com.dfshine.launcher.service.PlayerUiState) {
    val track = playerState.currentTrack ?: return
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(track.title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            Text(track.artist, style = MaterialTheme.typography.bodySmall, maxLines = 1)
        }
        IconButton(onClick = { MusicPlayerService.previous() }) {
            Icon(Icons.Filled.SkipPrevious, contentDescription = "السابق")
        }
        IconButton(onClick = { MusicPlayerService.togglePlayPause() }) {
            Icon(
                if (playerState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = "تشغيل/إيقاف"
            )
        }
        IconButton(onClick = { MusicPlayerService.next() }) {
            Icon(Icons.Filled.SkipNext, contentDescription = "التالي")
        }
    }
}

private fun queryAudioTracks(context: android.content.Context): List<Track> {
    val tracks = mutableListOf<Track>()
    val projection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST
    )
    val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
    runCatching {
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            "${MediaStore.Audio.Media.TITLE} ASC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                tracks += Track(
                    id = id,
                    title = cursor.getString(titleCol) ?: "بدون عنوان",
                    artist = cursor.getString(artistCol) ?: "غير معروف",
                    uri = uri
                )
            }
        }
    }
    return tracks
}
