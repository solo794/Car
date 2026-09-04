package com.dfshine.launcher.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dfshine.launcher.data.LauncherViewModel
import com.dfshine.launcher.model.AppInfo
import com.dfshine.launcher.ui.components.AppIconTile

/** Bottom dock: up to 4 pinned favorite apps plus the App Drawer button. */
@Composable
fun DockBar(
    viewModel: LauncherViewModel,
    onOpenDrawer: () -> Unit,
    onLaunch: (AppInfo) -> Unit,
    onRequestUnlock: (AppInfo, onUnlocked: () -> Unit) -> Unit
) {
    val prefs = viewModel.prefs
    val dockApps = prefs.dockApps.mapNotNull { viewModel.appByKey(it) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        dockApps.take(4).forEach { app ->
            val isLocked = app.key in prefs.lockedApps
            AppIconTile(
                app = app,
                showLabel = false,
                isLocked = isLocked,
                onClick = { if (isLocked) onRequestUnlock(app) { onLaunch(app) } else onLaunch(app) },
                onLongPress = {}
            )
        }

        IconButton(onClick = onOpenDrawer) {
            Icon(
                imageVector = Icons.Filled.Apps,
                contentDescription = "كل التطبيقات",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
