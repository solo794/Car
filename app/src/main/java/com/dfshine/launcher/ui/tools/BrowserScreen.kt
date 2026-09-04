package com.dfshine.launcher.ui.tools

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.net.Uri
import android.os.Environment
import android.webkit.URLUtil
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.viewinterop.AndroidView

/**
 * A minimal built-in browser - its main job is downloading APK files
 * (offline maps, etc.) since there is no Google Play Store on this head
 * unit. Downloads go through Android's own [DownloadManager] into the
 * public Downloads folder, where the File Manager can find and install
 * them.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var addressBarText by remember { mutableStateOf("https://") }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var pendingUrl by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.Close, contentDescription = "إغلاق") }
            IconButton(onClick = { webViewRef?.let { if (it.canGoBack()) it.goBack() } }) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "السابق")
            }
            IconButton(onClick = { webViewRef?.let { if (it.canGoForward()) it.goForward() } }) {
                Icon(Icons.Filled.ArrowForward, contentDescription = "التالي")
            }
            IconButton(onClick = { webViewRef?.reload() }) {
                Icon(Icons.Filled.Refresh, contentDescription = "تحديث")
            }
            OutlinedTextField(
                value = addressBarText,
                onValueChange = { addressBarText = it },
                singleLine = true,
                modifier = Modifier.weight(1f),
                placeholder = { Text("اكتب رابط الموقع...") }
            )
            IconButton(onClick = { pendingUrl = normalizeUrl(addressBarText) }) {
                Text("اذهب")
            }
        }

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            url?.let { addressBarText = it }
                        }
                    }
                    setDownloadListener { url, _, contentDisposition, mimeType, _ ->
                        downloadFile(context, url, contentDisposition, mimeType)
                    }
                    webViewRef = this
                    loadUrl("https://www.google.com/search?q=offline+maps+android+apk")
                }
            },
            update = { webView ->
                pendingUrl?.let { url ->
                    webView.loadUrl(url)
                    pendingUrl = null
                }
            }
        )
    }
}

private fun normalizeUrl(input: String): String {
    val trimmed = input.trim()
    if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed
    // Looks like a search query rather than a domain
    return if (trimmed.contains(" ") || !trimmed.contains(".")) {
        "https://www.google.com/search?q=${Uri.encode(trimmed)}"
    } else {
        "https://$trimmed"
    }
}

private fun downloadFile(context: android.content.Context, url: String, contentDisposition: String?, mimeType: String?) {
    val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
    val request = DownloadManager.Request(Uri.parse(url)).apply {
        setMimeType(mimeType)
        setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
        setAllowedOverMetered(true)
    }
    val downloadManager = context.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as DownloadManager
    downloadManager.enqueue(request)
    android.widget.Toast.makeText(
        context,
        "بيتم التنزيل إلى مجلد Download - افتحه من مدير الملفات بعد ما يخلص",
        android.widget.Toast.LENGTH_LONG
    ).show()
}
