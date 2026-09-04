package com.dfshine.launcher.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dfshine.launcher.data.LauncherViewModel
import com.dfshine.launcher.model.AppInfo
import com.dfshine.launcher.splitscreen.SplitScreenHelper
import com.dfshine.launcher.ui.components.AppPickerDialog

@Composable
fun SplitScreenScreen(viewModel: LauncherViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    var topApp by remember { mutableStateOf<AppInfo?>(null) }
    var bottomApp by remember { mutableStateOf<AppInfo?>(null) }
    var pickingSlot by remember { mutableStateOf<String?>(null) } // "top" | "bottom" | null

    Column(Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
        Row {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "رجوع") }
            Text(
                "الشاشة المقسمة",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 12.dp, start = 8.dp)
            )
        }

        Text(
            "اختر تطبيقاً للأعلى وتطبيقاً للأسفل. هذه الميزة تعمل فقط إذا كانت وحدة الشاشة تدعم النوافذ الحرة (freeform) - بعض شاشات السيارات الصينية المعدَّلة تدعمها افتراضياً، والشاشات الأصلية من المصنع غالباً لا تدعمها.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(vertical = 12.dp)
        )

        Card(modifier = Modifier.fillMaxWidth(), onClick = { pickingSlot = "top" }) {
            Text(
                "الأعلى: ${topApp?.label ?: "اختر تطبيقاً"}",
                modifier = Modifier.padding(16.dp)
            )
        }
        Card(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            onClick = { pickingSlot = "bottom" }
        ) {
            Text(
                "الأسفل: ${bottomApp?.label ?: "اختر تطبيقاً"}",
                modifier = Modifier.padding(16.dp)
            )
        }

        Button(
            onClick = {
                val top = topApp
                val bottom = bottomApp
                if (top != null && bottom != null) {
                    SplitScreenHelper.launchTopBottom(context, top.packageName, bottom.packageName)
                }
            },
            enabled = topApp != null && bottomApp != null,
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp)
        ) {
            Text("تشغيل الشاشة المقسمة")
        }
    }

    if (pickingSlot != null) {
        AppPickerDialog(
            title = "اختر التطبيق",
            apps = viewModel.visibleApps,
            onDismiss = { pickingSlot = null },
            onPick = { app ->
                if (pickingSlot == "top") topApp = app else bottomApp = app
                pickingSlot = null
            }
        )
    }
}
