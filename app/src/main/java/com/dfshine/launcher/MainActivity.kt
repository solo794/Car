package com.dfshine.launcher

import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.dfshine.launcher.data.LauncherViewModel
import com.dfshine.launcher.model.AppInfo
import com.dfshine.launcher.ui.components.PinUnlockDialog
import com.dfshine.launcher.ui.drawer.AppDrawerScreen
import com.dfshine.launcher.ui.home.HomeScreen
import com.dfshine.launcher.ui.quickpanel.QuickPanel
import com.dfshine.launcher.ui.settings.SettingsScreen
import com.dfshine.launcher.ui.settings.SplitScreenScreen
import com.dfshine.launcher.ui.theme.ShineLauncherTheme

class MainActivity : ComponentActivity() {

    private val viewModel: LauncherViewModel by viewModels()

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* Settings screen shows which permissions are still missing. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestRuntimePermissions()

        setContent {
            // Read settingsVersion so the whole tree recomposes whenever a
            // Settings screen mutates Prefs (theme, grid, dock, ...).
            val settingsVersion = viewModel.settingsVersion
            val prefs = viewModel.prefs

            val accent = remember(settingsVersion) {
                runCatching { Color(AndroidColor.parseColor(prefs.accentColorHex)) }.getOrDefault(Color(0xFF2FB6A6))
            }
            val darkTheme = when (prefs.themeMode) {
                "dark" -> true
                "light" -> false
                else -> null
            }

            ShineLauncherTheme(accent = accent, darkTheme = darkTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    LauncherHost(viewModel)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.reloadApps()
    }

    private fun requestRuntimePermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions += android.Manifest.permission.POST_NOTIFICATIONS
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions += android.Manifest.permission.BLUETOOTH_CONNECT
            permissions += android.Manifest.permission.BLUETOOTH_SCAN
        }
        if (permissions.isNotEmpty()) requestPermissions.launch(permissions.toTypedArray())
    }
}

@androidx.compose.runtime.Composable
private fun LauncherHost(viewModel: LauncherViewModel) {
    val navController = rememberNavController()
    var showQuickPanel by remember { mutableStateOf(false) }
    var pendingUnlock by remember { mutableStateOf<Pair<AppInfo, () -> Unit>?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current

    fun launchApp(app: AppInfo) {
        val intent = context.packageManager.getLaunchIntentForPackage(app.packageName)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                viewModel = viewModel,
                onOpenDrawer = { navController.navigate("drawer") },
                onOpenQuickPanel = { showQuickPanel = true },
                onOpenSettings = { navController.navigate("settings") },
                onLaunch = ::launchApp,
                onRequestUnlock = { app, onUnlocked -> pendingUnlock = app to onUnlocked }
            )
        }
        composable("drawer") {
            AppDrawerScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onLaunch = ::launchApp
            )
        }
        composable("settings") {
            SettingsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenSplitScreen = { navController.navigate("splitscreen") }
            )
        }
        composable("splitscreen") {
            SplitScreenScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
    }

    if (showQuickPanel) {
        Dialog(
            onDismissRequest = { showQuickPanel = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(modifier = Modifier.fillMaxSize()) {
                QuickPanel(
                    viewModel = viewModel,
                    onOpenReverseCameraSettings = {
                        showQuickPanel = false
                        navController.navigate("settings")
                    },
                    onOpenSplitScreen = {
                        showQuickPanel = false
                        navController.navigate("splitscreen")
                    }
                )
            }
        }
    }

    pendingUnlock?.let { (app, onUnlocked) ->
        val pin = viewModel.prefs.appLockPin
        if (pin == null) {
            // No PIN configured yet - nothing to unlock against, let it
            // through. Runs as a side effect (not directly during
            // composition) since it launches an Activity and clears state.
            androidx.compose.runtime.LaunchedEffect(app.key) {
                onUnlocked()
                pendingUnlock = null
            }
        } else {
            PinUnlockDialog(
                appLabel = app.label,
                correctPin = pin,
                onDismiss = { pendingUnlock = null },
                onUnlocked = {
                    onUnlocked()
                    pendingUnlock = null
                }
            )
        }
    }
}
