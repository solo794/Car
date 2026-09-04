package com.dfshine.launcher.ui.tools

import android.content.Context
import android.content.Intent
import android.os.Environment
import android.webkit.MimeTypeMap
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
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.dfshine.launcher.data.MediaLaunchBridge
import com.dfshine.launcher.util.PermissionUtils
import java.io.File

/**
 * A basic file manager: browse anything copied over USB, and open or
 * install (.apk) what you tap. This is the escape hatch for a head unit
 * with no Google Play Store - the built-in Browser downloads a file, this
 * screen installs or opens it.
 */
@Composable
fun FileManagerScreen(
    onBack: () -> Unit,
    onOpenMusicPlayer: () -> Unit,
    onOpenVideoPlayer: () -> Unit
) {
    val context = LocalContext.current
    var currentDir by remember { mutableStateOf(rootDir()) }
    // Not memoized on purpose: re-checked on every recomposition (e.g. when
    // navigating back into this screen after granting the permission in
    // Settings) rather than going stale like a `remember`-cached value would.
    val hasAccess = PermissionUtils.hasAllFilesAccess(context)

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "رجوع") }
            Text(
                "مدير الملفات",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        if (!hasAccess) {
            Column(Modifier.padding(16.dp)) {
                Text("محتاج إذن الوصول لكل الملفات عشان يقدر يستعرض ملفاتك.")
                Button(
                    onClick = { context.startActivity(PermissionUtils.manageAllFilesIntent(context)) },
                    modifier = Modifier.padding(top = 8.dp)
                ) { Text("منح الإذن") }
            }
        } else {
            Text(
                currentDir.absolutePath,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            )

            val entries = remember(currentDir) {
                currentDir.listFiles()
                    ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                    ?: emptyList()
            }

            LazyColumn(Modifier.fillMaxSize()) {
                val parent = currentDir.parentFile
                if (parent != null) {
                    item {
                        FileRow(name = "..", isDir = true) { currentDir = parent }
                    }
                }
                items(entries, key = { it.absolutePath }) { entry ->
                    FileRow(name = entry.name, isDir = entry.isDirectory) {
                        if (entry.isDirectory) {
                            currentDir = entry
                        } else {
                            openFile(context, entry, onOpenMusicPlayer, onOpenVideoPlayer)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FileRow(name: String, isDir: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(iconFor(name, isDir), contentDescription = null)
        Text(name, style = MaterialTheme.typography.bodyLarge)
    }
}

private fun iconFor(name: String, isDir: Boolean) = when {
    isDir -> Icons.Filled.Folder
    name.substringAfterLast('.', "").lowercase() in AUDIO_EXTENSIONS -> Icons.Filled.AudioFile
    name.substringAfterLast('.', "").lowercase() in VIDEO_EXTENSIONS -> Icons.Filled.Movie
    name.endsWith(".apk", ignoreCase = true) -> Icons.Filled.Description
    else -> Icons.Filled.InsertDriveFile
}

private fun rootDir(): File =
    runCatching { Environment.getExternalStorageDirectory() }.getOrNull() ?: File("/storage/emulated/0")

private val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "aac", "wav", "flac", "ogg")
private val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "avi", "webm", "3gp", "mov")

private fun openFile(
    context: Context,
    file: File,
    onOpenMusicPlayer: () -> Unit,
    onOpenVideoPlayer: () -> Unit
) {
    val extension = file.extension.lowercase()
    val uri = runCatching {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }.getOrElse {
        android.widget.Toast.makeText(context, "تعذّر فتح هذا الملف", android.widget.Toast.LENGTH_SHORT).show()
        return
    }

    when {
        extension == "apk" -> {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
        extension in AUDIO_EXTENSIONS -> {
            MediaLaunchBridge.pendingAudioUri = uri
            onOpenMusicPlayer()
        }
        extension in VIDEO_EXTENSIONS -> {
            MediaLaunchBridge.pendingVideoUri = uri
            onOpenVideoPlayer()
        }
        else -> {
            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "*/*"
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(Intent.createChooser(intent, "فتح باستخدام")) }
        }
    }
}
