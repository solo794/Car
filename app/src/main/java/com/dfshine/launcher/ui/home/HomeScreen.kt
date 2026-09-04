package com.dfshine.launcher.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.dfshine.launcher.data.LauncherViewModel
import com.dfshine.launcher.model.AppInfo
import com.dfshine.launcher.service.NotificationBadgeService
import com.dfshine.launcher.ui.components.AppIconTile
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil

/**
 * The vertical (portrait) home screen: a swipeable multi-page icon grid
 * sized for a tall car screen, with a bottom dock and a status strip that
 * opens the Quick Panel when swiped down (or tapped).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    viewModel: LauncherViewModel,
    onOpenDrawer: () -> Unit,
    onOpenQuickPanel: () -> Unit,
    onOpenSettings: () -> Unit,
    onLaunch: (AppInfo) -> Unit,
    onRequestUnlock: (AppInfo, onUnlocked: () -> Unit) -> Unit
) {
    val prefs = viewModel.prefs
    val apps = viewModel.visibleApps
    val badgeCounts by NotificationBadgeService.badgeCountsFlow.collectAsState()
    val columns = prefs.gridColumns.coerceIn(3, 6)
    val perPage = columns * 5 // 5 rows per page suits a tall screen
    val pageCount = maxOf(1, ceil(apps.size / perPage.toDouble()).toInt())
    val pagerState = rememberPagerState(pageCount = { pageCount })

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(Unit) {
                detectVerticalDragGestures { _, dragAmount ->
                    if (dragAmount > 40) onOpenQuickPanel()
                }
            }
    ) {
        StatusStrip(
            modifier = Modifier.fillMaxWidth().statusBarsPadding(),
            onOpenQuickPanel = onOpenQuickPanel,
            onOpenSettings = onOpenSettings
        )

        Box(modifier = Modifier.weight(1f)) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val pageApps = apps.drop(page * perPage).take(perPage)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(pageApps, key = { it.key }) { app ->
                        val isLocked = app.key in prefs.lockedApps
                        AppIconTile(
                            app = app,
                            showLabel = prefs.showIconLabels,
                            badgeCount = if (prefs.notificationBadgesEnabled) badgeCounts[app.packageName] ?: 0 else 0,
                            isLocked = isLocked,
                            onClick = {
                                if (isLocked) onRequestUnlock(app) { onLaunch(app) } else onLaunch(app)
                            },
                            onLongPress = { /* full app management lives in the App Drawer */ }
                        )
                    }
                }
            }

            PageIndicator(
                pageCount = pageCount,
                currentPage = pagerState.currentPage,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 4.dp)
            )
        }

        DockBar(
            viewModel = viewModel,
            onOpenDrawer = onOpenDrawer,
            onLaunch = onLaunch,
            onRequestUnlock = onRequestUnlock
        )
    }
}

@Composable
private fun StatusStrip(
    modifier: Modifier = Modifier,
    onOpenQuickPanel: () -> Unit,
    onOpenSettings: () -> Unit
) {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(1000L * 30)
        }
    }
    val timeFormat = remember { SimpleDateFormat("hh:mm a", Locale("ar")) }

    Row(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable(onClick = onOpenQuickPanel),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = timeFormat.format(now),
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.titleMedium
        )
        IconButton(onClick = onOpenSettings) {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = "الإعدادات",
                tint = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

@Composable
private fun PageIndicator(pageCount: Int, currentPage: Int, modifier: Modifier = Modifier) {
    if (pageCount <= 1) return
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(pageCount) { index ->
            Box(
                modifier = Modifier
                    .size(if (index == currentPage) 8.dp else 6.dp)
                    .background(
                        color = if (index == currentPage) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
                        shape = CircleShape
                    )
            )
        }
    }
}
