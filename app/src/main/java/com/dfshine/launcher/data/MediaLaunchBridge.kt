package com.dfshine.launcher.data

import android.net.Uri

/**
 * Tiny in-process hand-off so the File Manager can open a tapped audio or
 * video file directly in the built-in Music/Video Player screens without
 * threading a Uri through Compose Navigation's string-only route
 * arguments. Single-activity app, single process - a plain singleton is
 * enough; nothing here needs to survive process death.
 */
object MediaLaunchBridge {
    var pendingAudioUri: Uri? = null
    var pendingVideoUri: Uri? = null
}
