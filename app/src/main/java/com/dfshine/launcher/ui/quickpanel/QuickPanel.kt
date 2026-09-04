package com.dfshine.launcher.ui.quickpanel

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dfshine.launcher.data.LauncherViewModel
import com.dfshine.launcher.service.FloatingPipService
import com.dfshine.launcher.util.PermissionUtils
import androidx.core.content.ContextCompat

@Composable
fun QuickPanel(
    viewModel: LauncherViewModel,
    onOpenReverseCameraSettings: () -> Unit,
    onOpenSplitScreen: () -> Unit
) {
    val context = LocalContext.current
    val prefs = viewModel.prefs

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("اللوحة السريعة", style = MaterialTheme.typography.titleLarge)

        ConnectivityRow(context)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("السطوع", style = MaterialTheme.typography.labelLarge)
                BrightnessSlider(context)
                Divider(Modifier.padding(vertical = 8.dp))
                Text("مستوى الصوت", style = MaterialTheme.typography.labelLarge)
                VolumeSlider(context)
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("الأدوات العائمة (Picture in Picture)", style = MaterialTheme.typography.labelLarge)
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    FloatingActionChip(Icons.Filled.MusicNote, "المشغل") {
                        startFloating(context, FloatingPipService.ACTION_SHOW_MUSIC)
                    }
                    FloatingActionChip(Icons.Filled.Schedule, "الساعة") {
                        startFloating(context, FloatingPipService.ACTION_SHOW_CLOCK)
                    }
                    FloatingActionChip(Icons.Filled.CameraAlt, "الكاميرا") {
                        onOpenReverseCameraSettings()
                    }
                }
                if (!PermissionUtils.canDrawOverlays(context)) {
                    Text(
                        "امنح إذن \"العرض فوق التطبيقات الأخرى\" من الإعدادات لتفعيل الأدوات العائمة",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            onClick = onOpenSplitScreen
        ) {
            Row(
                Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("الشاشة المقسمة (Split Screen)", style = MaterialTheme.typography.labelLarge)
                    Text(
                        "يعمل فقط على الأجهزة التي تدعم النوافذ الحرة - اضغط للاختيار",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("كاميرا الرجوع للخلف تلقائياً", style = MaterialTheme.typography.labelLarge)
                Switch(
                    checked = prefs.reverseCameraEnabled,
                    onCheckedChange = {
                        prefs.reverseCameraEnabled = it
                        viewModel.notifySettingsChanged()
                    }
                )
            }
        }
    }
}

private fun startFloating(context: Context, action: String) {
    if (!PermissionUtils.canDrawOverlays(context)) return
    ContextCompat.startForegroundService(context, FloatingPipService.intent(context, action))
}

@Composable
private fun ConnectivityRow(context: Context) {
    var wifiOn by remember { mutableStateOf(isWifiOn(context)) }
    var btOn by remember { mutableStateOf(isBluetoothOn()) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Wifi, contentDescription = null)
                    Text("واي فاي", modifier = Modifier.padding(start = 8.dp))
                }
                Switch(checked = wifiOn, onCheckedChange = {
                    wifiOn = it
                    toggleWifi(context, it)
                })
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Bluetooth, contentDescription = null)
                    Text("بلوتوث", modifier = Modifier.padding(start = 8.dp))
                }
                Switch(checked = btOn, onCheckedChange = {
                    btOn = it
                    toggleBluetooth(context, it)
                })
            }
        }
    }
}

@Composable
private fun FloatingActionChip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(4.dp)
    ) {
        androidx.compose.material3.IconButton(onClick = onClick) {
            Icon(icon, contentDescription = label)
        }
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun BrightnessSlider(context: Context) {
    var value by remember { mutableFloatStateOf(currentBrightness(context)) }
    Slider(
        value = value,
        onValueChange = {
            value = it
            if (PermissionUtils.canWriteSettings(context)) {
                Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, (it * 255).toInt())
            }
        }
    )
}

@Composable
private fun VolumeSlider(context: Context) {
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val max = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var value by remember { mutableFloatStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) / max.toFloat()) }
    Slider(
        value = value,
        onValueChange = {
            value = it
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (it * max).toInt(), 0)
        }
    )
}

private fun currentBrightness(context: Context): Float = try {
    Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
} catch (e: Settings.SettingNotFoundException) {
    0.5f
}

private fun isWifiOn(context: Context): Boolean = try {
    (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager).isWifiEnabled
} catch (e: Exception) {
    false
}

@Suppress("DEPRECATION")
private fun toggleWifi(context: Context, enable: Boolean) {
    val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) {
        runCatching { wifiManager.isWifiEnabled = enable }
    } else {
        // Android 10+ no longer allows apps to toggle Wi-Fi directly.
        context.startActivity(Intent(Settings.Panel.ACTION_WIFI).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

private fun isBluetoothOn(): Boolean = try {
    BluetoothAdapter.getDefaultAdapter()?.isEnabled == true
} catch (e: Exception) {
    false
}

@Suppress("DEPRECATION", "MissingPermission")
private fun toggleBluetooth(context: Context, enable: Boolean) {
    val adapter = BluetoothAdapter.getDefaultAdapter() ?: return
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) {
        runCatching { if (enable) adapter.enable() else adapter.disable() }
    } else {
        context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
