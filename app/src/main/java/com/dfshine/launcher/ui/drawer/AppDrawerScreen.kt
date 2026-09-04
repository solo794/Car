package com.dfshine.launcher.ui.drawer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dfshine.launcher.data.LauncherViewModel
import com.dfshine.launcher.model.AppInfo
import com.dfshine.launcher.ui.components.AppIconTile

@Composable
fun AppDrawerScreen(
    viewModel: LauncherViewModel,
    onBack: () -> Unit,
    onLaunch: (AppInfo) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var menuForApp by remember { mutableStateOf<AppInfo?>(null) }
    val prefs = viewModel.prefs

    val filtered = viewModel.visibleApps.filter {
        query.isBlank() || it.label.contains(query, ignoreCase = true)
    }

    Scaffold(
        topBar = {
            Column(Modifier.statusBarsPadding()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                    Text(
                        text = "كل التطبيقات",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(start = 8.dp, top = 12.dp)
                    )
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    placeholder = { Text("ابحث عن تطبيق...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true
                )
            }
        }
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            items(filtered, key = { it.key }) { app ->
                Box {
                    AppIconTile(
                        app = app,
                        showLabel = true,
                        isLocked = app.key in prefs.lockedApps,
                        onClick = { onLaunch(app) },
                        onLongPress = { menuForApp = app }
                    )
                    DropdownMenu(
                        expanded = menuForApp?.key == app.key,
                        onDismissRequest = { menuForApp = null }
                    ) {
                        val inDock = app.key in prefs.dockApps
                        DropdownMenuItem(
                            text = { Text(if (inDock) "إزالة من الشريط السفلي" else "تثبيت في الشريط السفلي") },
                            onClick = {
                                val dock = prefs.dockApps.toMutableList()
                                if (inDock) dock.remove(app.key) else if (dock.size < 4) dock.add(app.key)
                                viewModel.setDockApps(dock)
                                menuForApp = null
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("إخفاء التطبيق") },
                            onClick = { viewModel.toggleHidden(app.key); menuForApp = null }
                        )
                        DropdownMenuItem(
                            text = { Text(if (app.key in prefs.lockedApps) "إلغاء القفل" else "قفل التطبيق") },
                            onClick = { viewModel.toggleLocked(app.key); menuForApp = null }
                        )
                    }
                }
            }
        }
    }
}
