package com.dfshine.launcher.ui.tools

import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import android.widget.MediaController as AndroidMediaController
import android.widget.VideoView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.dfshine.launcher.data.MediaLaunchBridge

private data class VideoItem(val id: Long, val title: String, val uri: Uri)

@Composable
fun VideoPlayerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var videos by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var nowPlaying by remember { mutableStateOf<Uri?>(null) }

    LaunchedEffect(Unit) {
        videos = queryVideos(context)
        MediaLaunchBridge.pendingVideoUri?.let { uri ->
            MediaLaunchBridge.pendingVideoUri = null
            nowPlaying = uri
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (nowPlaying != null) nowPlaying = null else onBack() }) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "رجوع")
            }
            Text(
                "مشغل الفيديو",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        val playingUri = nowPlaying
        if (playingUri != null) {
            var lastLoadedUri by remember { mutableStateOf<Uri?>(null) }
            AndroidView(
                modifier = Modifier.fillMaxWidth().height(320.dp),
                factory = { ctx ->
                    VideoView(ctx).apply {
                        setMediaController(AndroidMediaController(ctx).also { it.setAnchorView(this) })
                        setOnPreparedListener { start() }
                    }
                },
                update = { videoView ->
                    if (lastLoadedUri != playingUri) {
                        lastLoadedUri = playingUri
                        videoView.setVideoURI(playingUri)
                    }
                }
            )
        } else if (videos.isEmpty()) {
            Text("لا يوجد ملفات فيديو على الجهاز", modifier = Modifier.padding(16.dp))
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(videos, key = { it.id }) { video ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { nowPlaying = video.uri }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Filled.Movie, contentDescription = null)
                        Text(video.title, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}

private fun queryVideos(context: android.content.Context): List<VideoItem> {
    val videos = mutableListOf<VideoItem>()
    val projection = arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME)
    runCatching {
        context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            "${MediaStore.Video.Media.DISPLAY_NAME} ASC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                videos += VideoItem(id = id, title = cursor.getString(nameCol) ?: "فيديو", uri = uri)
            }
        }
    }
    return videos
}
