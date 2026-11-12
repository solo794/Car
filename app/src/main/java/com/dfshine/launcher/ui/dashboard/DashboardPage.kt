package com.dfshine.launcher.ui.dashboard

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Luggage
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Window
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dfshine.launcher.data.LauncherViewModel
import com.dfshine.launcher.model.AppInfo
import com.dfshine.launcher.service.NotificationBadgeService
import com.dfshine.launcher.ui.components.AppPickerDialog
import com.dfshine.launcher.ui.dashboard.car3d.Car3DView
import com.dfshine.launcher.ui.dashboard.car3d.Part
import com.dfshine.launcher.ui.dashboard.car3d.RealCarView
import com.dfshine.launcher.vehicle.VehicleRepository
import com.dfshine.launcher.vehicle.VehicleRepository.Control
import com.dfshine.launcher.vehicle.VehicleState
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val TextMain = Color(0xFFEAF4F6)
private val TextDim = Color(0xFF8FA3AA)
private val PanelBg = Color(0x33000000)
private val Ok = Color(0xFF3DDC97)
private val Warn = Color(0xFFFF5A5A)
private val Amber = Color(0xFFFFB020)

/**
 * Car dashboard home page: header (clock, temps, fuel), digital speed + gear,
 * light indicators, the interactive 3D car with its control column and
 * TPMS, a fuel card, a quick-apps bar and the now-playing card.
 * The OEM climate bar stays where it is - it is a system layer drawn by the
 * head unit over every app, so this page simply leaves room for it.
 */
@Composable
fun DashboardPage(
    viewModel: LauncherViewModel,
    onLaunch: (AppInfo) -> Unit,
    onOpenDrawer: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by VehicleRepository.state.collectAsState()
    val status by VehicleRepository.status.collectAsState()
    val prefs = viewModel.prefs
    val accent = MaterialTheme.colorScheme.primary
    val context = LocalContext.current
    var showSunroof by remember { mutableStateOf(false) }
    var confirmTrunk by remember { mutableStateOf(false) }
    var realModelFailed by remember { mutableStateOf(false) }
    var showTpms by remember { mutableStateOf(false) }
    val carColor = remember(viewModel.settingsVersion) {
        runCatching { Color(android.graphics.Color.parseColor(prefs.carColorHex)) }.getOrDefault(Color(0xFF6F8BA8))
    }

    fun send(c: Control, open: Boolean) {
        VehicleRepository.control(c, open) { err ->
            Toast.makeText(context, err ?: "تم إرسال الأمر", Toast.LENGTH_SHORT).show()
        }
    }

    fun toggleWindow(i: Int) = send(Control.WINDOWS[i], state.windows.open.getOrElse(i) { 0f } < 0.5f)

    fun onPart(part: Part) {
        when (part) {
            Part.DOOR_FL -> toggleWindow(0)
            Part.DOOR_FR -> toggleWindow(1)
            Part.DOOR_RL -> toggleWindow(2)
            Part.DOOR_RR -> toggleWindow(3)
            Part.ROOF, Part.SUNROOF -> showSunroof = true
            Part.TRUNK -> confirmTrunk = true
            else -> {}
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    listOf(Color(0xFF1C5560), Color(0xFF0C2A31), Color(0xFF05090C)),
                    radius = 1600f
                )
            )
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 6.dp)) {
            TopInfoRow(state)
            SpeedGearRow(state, accent)
            Spacer(Modifier.height(4.dp))
            IndicatorBar(state.lights, accent)
            Row(Modifier.weight(1f).fillMaxWidth()) {
                ControlColumn(
                    state = state,
                    accent = accent,
                    onWindowsAll = {
                        val open = !state.windows.open.any { it > 0.5f }
                        val main = Handler(Looper.getMainLooper())
                        // Space the four commands out; the client also rate-limits.
                        Control.WINDOWS.forEachIndexed { i, c -> main.postDelayed({ send(c, open) }, i * 450L) }
                    },
                    onSunroof = { showSunroof = true },
                    onTrunk = { confirmTrunk = true }
                )
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    if (prefs.carModelStyle == "real" && !realModelFailed) {
                        RealCarView(state, ::onPart, onFailure = { realModelFailed = true }, modifier = Modifier.fillMaxSize())
                    } else {
                        Car3DView(state, carColor, ::onPart, Modifier.fillMaxSize())
                    }
                    // Tyre pressures: shown on demand (TPMS button) or automatically on a warning.
                    if (showTpms || state.tpms?.anyWarning == true) TpmsCorners(state.tpms, prefs.tpmsUnit)
                    TpmsButton(
                        tpms = state.tpms,
                        active = showTpms,
                        onClick = { showTpms = !showTpms },
                        modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 26.dp, end = 4.dp)
                    )
                    Column(Modifier.align(Alignment.BottomCenter), horizontalAlignment = Alignment.CenterHorizontally) {
                        DoorWarning(state.doors)
                        Text(
                            "اسحب للّف · اضغط على باب للشباك · السقف · الشنطة",
                            color = TextDim.copy(alpha = 0.6f), fontSize = 10.sp
                        )
                    }
                }
            }
            FuelCard(state)
            Spacer(Modifier.height(6.dp))
            QuickAppsBar(viewModel, onLaunch, onOpenDrawer, accent)
            Spacer(Modifier.height(6.dp))
            NowPlayingCard(accent)
            StatusRow(state, status.writeVerified, onOpenDiagnostics, onOpenSettings)
        }
    }

    if (showSunroof) {
        SunroofDialog(
            state.windows,
            onDismiss = { showSunroof = false },
            onAction = { c, open -> showSunroof = false; send(c, open) }
        )
    }
    if (confirmTrunk) {
        val demo = state.source == VehicleState.Source.DEMO
        AlertDialog(
            onDismissRequest = { confirmTrunk = false },
            title = { Text("الشنطة") },
            text = {
                Text(
                    if (state.doors.trunk) "الشنطة مفتوحة. اقفلها بإيدك."
                    else "تفتح الشنطة؟ لازم العربية تكون واقفة والفتيس على P."
                )
            },
            confirmButton = {
                if (!state.doors.trunk || demo) {
                    TextButton(onClick = { confirmTrunk = false; send(Control.TRUNK, !state.doors.trunk) }) {
                        Text(if (state.doors.trunk) "اقفل (عرض)" else "افتح")
                    }
                }
            },
            dismissButton = { TextButton(onClick = { confirmTrunk = false }) { Text("إلغاء") } }
        )
    }
}

// ---------------------------------------------------------------------------
// Header
// ---------------------------------------------------------------------------

@Composable
private fun TopInfoRow(s: VehicleState) {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date(); delay(1000)
        }
    }
    val time = remember { SimpleDateFormat("hh:mm", Locale.US) }
    val ampm = remember { SimpleDateFormat("a", Locale.US) }
    val date = remember { SimpleDateFormat("EEEE d MMMM", Locale("ar")) }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            InfoLine(Icons.Filled.Thermostat, s.outsideTempC?.let { "$it°C" } ?: "--", "برّه")
            InfoLine(Icons.Filled.Thermostat, s.coolantTempC?.let { "$it°C" } ?: "--", "المحرك")
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    time.format(now), color = TextMain, fontSize = 40.sp,
                    fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace
                )
                Text(ampm.format(now), color = TextDim, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
            }
            Text(date.format(now), color = TextDim, fontSize = 12.sp)
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            InfoLine(Icons.Filled.LocalGasStation, s.fuelPercent?.let { "${it.toInt()}%" } ?: "--", "بنزين", alignEnd = true)
            InfoLine(Icons.Filled.DirectionsCar, s.rangeKm?.let { "${it.toInt()}" } ?: "--", "كم", alignEnd = true)
        }
    }
}

@Composable
private fun InfoLine(icon: ImageVector, value: String, label: String, alignEnd: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (!alignEnd) Icon(icon, null, tint = TextDim, modifier = Modifier.size(16.dp).padding(end = 2.dp))
        Text(value, color = TextMain, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text(" $label", color = TextDim, fontSize = 11.sp)
        if (alignEnd) Icon(icon, null, tint = TextDim, modifier = Modifier.size(16.dp).padding(start = 2.dp))
    }
}

@Composable
private fun SpeedGearRow(s: VehicleState, accent: Color) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                s.speedKmh?.toString() ?: "--", color = TextMain, fontSize = 50.sp,
                fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace
            )
            Text("KM/H", color = TextDim, fontSize = 11.sp, modifier = Modifier.offset(y = (-6).dp))
        }
        Spacer(Modifier.width(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(VehicleState.Gear.P, VehicleState.Gear.R, VehicleState.Gear.N, VehicleState.Gear.D).forEach { g ->
                val sport = s.gear == VehicleState.Gear.S || s.gear == VehicleState.Gear.B
                val active = s.gear == g || (g == VehicleState.Gear.D && sport)
                val label = if (g == VehicleState.Gear.D && sport) s.gear!!.label else g.label
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (active) accent.copy(alpha = 0.25f) else PanelBg)
                        .border(1.dp, if (active) accent else Color.Transparent, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(label, color = if (active) accent else TextDim, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Light indicators
// ---------------------------------------------------------------------------

@Composable
private fun IndicatorBar(l: VehicleState.Lights, accent: Color) {
    val blink by rememberInfiniteTransition(label = "ind").animateFloat(
        0f, 1f, infiniteRepeatable(tween(420), RepeatMode.Reverse), label = "indA"
    )
    val on = blink > 0.5f
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(PanelBg)
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(28.dp))
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IndicatorIcon(Icons.Filled.ArrowBack, (l.turnLeft || l.hazard) && on, Ok)
        IndicatorText("⚠", l.hazard && on, Color(0xFFFF6A3D), "انتظار")
        IndicatorText("≡D", l.fogFront, Ok, "ضباب أمامي")
        IndicatorText("≡O", l.fogRear, Amber, "ضباب خلفي")
        IndicatorText("◐", l.position, Ok, "جانبي")
        IndicatorText("▼", l.lowBeam, Ok, "عادي")
        IndicatorText("▲", l.highBeam, Color(0xFF4D9DFF), "عالي")
        IndicatorIcon(Icons.Filled.ArrowForward, (l.turnRight || l.hazard) && on, Ok)
    }
}

@Composable
private fun IndicatorIcon(icon: ImageVector, active: Boolean, color: Color) {
    Icon(icon, null, tint = if (active) color else TextDim.copy(alpha = 0.35f), modifier = Modifier.size(24.dp))
}

@Composable
private fun IndicatorText(glyph: String, active: Boolean, color: Color, label: String) {
    val c = if (active) color else TextDim.copy(alpha = 0.35f)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(glyph, color = c, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text(label, color = c, fontSize = 8.sp)
    }
}

// ---------------------------------------------------------------------------
// Controls next to the model
// ---------------------------------------------------------------------------

@Composable
private fun ControlColumn(
    state: VehicleState,
    accent: Color,
    onWindowsAll: () -> Unit,
    onSunroof: () -> Unit,
    onTrunk: () -> Unit
) {
    val w = state.windows
    Column(
        Modifier.fillMaxHeight().padding(top = 12.dp, end = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val windowsAvail = Control.WINDOWS.any { VehicleRepository.isAvailable(it) }
        ControlButton(
            Icons.Filled.Window, if (w.open.any { it > 0.5f }) "اقفل الشبابيك" else "افتح الشبابيك",
            w.open.any { it > 0.05f }, windowsAvail, accent, onWindowsAll
        )
        val sunAvail = VehicleRepository.isAvailable(Control.SUNROOF) || VehicleRepository.isAvailable(Control.SUNROOF_TILT)
        ControlButton(Icons.Filled.WbSunny, "فتحة السقف", w.sunroofOpen > 0.05f || w.sunroofTilt, sunAvail, accent, onSunroof)
        ControlButton(Icons.Filled.Luggage, "الشنطة", state.doors.trunk, VehicleRepository.isAvailable(Control.TRUNK), accent, onTrunk)
    }
}

@Composable
private fun ControlButton(icon: ImageVector, label: String, active: Boolean, available: Boolean, accent: Color, onClick: () -> Unit) {
    Column(
        Modifier
            .width(62.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (active) accent.copy(alpha = 0.22f) else PanelBg)
            .border(1.dp, if (active) accent else accent.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick) // still clickable when unavailable: the toast explains why
            .alpha(if (available) 1f else 0.45f)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, null, tint = if (active) accent else TextMain, modifier = Modifier.size(24.dp))
        Text(label, color = TextMain, fontSize = 9.sp, textAlign = TextAlign.Center, lineHeight = 11.sp)
    }
}

@Composable
private fun SunroofDialog(w: VehicleState.Windows, onDismiss: () -> Unit, onAction: (Control, Boolean) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("فتحة السقف") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val now = when {
                    w.sunroofOpen > 0.05f -> "مفتوحة ${(w.sunroofOpen * 100).toInt()}%"
                    w.sunroofTilt -> "مايلة"
                    else -> "مقفولة"
                }
                Text("الحالة: $now" + if (w.shadeOpen) " · الستارة مفتوحة" else "")
                listOf(
                    Triple("فتح", Control.SUNROOF, true),
                    Triple("إمالة", Control.SUNROOF_TILT, true),
                    Triple("قفل", if (w.sunroofTilt) Control.SUNROOF_TILT else Control.SUNROOF, false),
                    Triple(if (w.shadeOpen) "قفل الستارة" else "فتح الستارة", Control.SHADE, !w.shadeOpen)
                ).forEach { (label, c, open) ->
                    TextButton(onClick = { onAction(c, open) }, modifier = Modifier.fillMaxWidth()) { Text(label) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("إغلاق") } }
    )
}

// ---------------------------------------------------------------------------
// TPMS + door warning
// ---------------------------------------------------------------------------

@Composable
private fun TpmsCorners(t: VehicleState.Tpms?, unit: String) {
    if (t == null) return
    Box(Modifier.fillMaxSize().padding(4.dp)) {
        TireLabel(t, 0, "أ.ش", unit, Modifier.align(Alignment.TopStart))
        TireLabel(t, 1, "أ.ي", unit, Modifier.align(Alignment.TopEnd))
        TireLabel(t, 2, "خ.ش", unit, Modifier.align(Alignment.BottomStart).padding(bottom = 22.dp))
        TireLabel(t, 3, "خ.ي", unit, Modifier.align(Alignment.BottomEnd).padding(bottom = 22.dp))
    }
}

@Composable
private fun TpmsButton(tpms: VehicleState.Tpms?, active: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val warn = tpms?.anyWarning == true
    val color = when {
        tpms == null -> TextDim
        warn -> Warn
        else -> Ok
    }
    Row(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (active) color.copy(alpha = 0.18f) else PanelBg)
            .border(1.5.dp, color.copy(alpha = 0.8f), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("(!) ", color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text("TPMS", color = color, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TireLabel(t: VehicleState.Tpms, i: Int, name: String, unit: String, modifier: Modifier) {
    val warn = t.warning.getOrElse(i) { false }
    val kpa = t.pressureKpa.getOrElse(i) { 0f }
    val value = when (unit) {
        "kpa" -> "${kpa.toInt()} kPa"
        "psi" -> String.format(Locale.US, "%.0f psi", kpa * 0.145038f)
        else -> String.format(Locale.US, "%.1f bar", kpa / 100f)
    }
    val color = if (warn) Warn else Ok
    Column(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(PanelBg)
            .border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text("$name · ${t.tempC.getOrElse(i) { 0f }.toInt()}°C", color = TextDim, fontSize = 10.sp)
    }
}

@Composable
private fun DoorWarning(d: VehicleState.Doors) {
    if (!d.anyOpen) return
    val open = buildList {
        if (d.frontLeft) add("أمامي شمال")
        if (d.frontRight) add("أمامي يمين")
        if (d.rearLeft) add("خلفي شمال")
        if (d.rearRight) add("خلفي يمين")
        if (d.trunk) add("الشنطة")
        if (d.hood) add("الكبوت")
    }
    Row(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Warn.copy(alpha = 0.18f))
            .padding(horizontal = 12.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Warning, null, tint = Warn, modifier = Modifier.size(16.dp))
        Text(" مفتوح: ${open.joinToString("، ")}", color = TextMain, fontSize = 12.sp)
    }
}

// ---------------------------------------------------------------------------
// Fuel card
// ---------------------------------------------------------------------------

@Composable
private fun FuelCard(s: VehicleState) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(PanelBg)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.LocalGasStation, null, tint = Amber, modifier = Modifier.size(26.dp))
        val litres = s.fuelLitres?.let { l ->
            s.fuelCapacityLitres?.let { "${l.toInt()}/${it.toInt()}" } ?: "${l.toInt()}"
        } ?: s.fuelPercent?.let { "${it.toInt()}%" } ?: "--"
        FuelStat(litres, if (s.fuelLitres != null) "لتر" else "الخزان")
        FuelStat(s.rangeKm?.toInt()?.toString() ?: "--", "كم متبقي")
        FuelStat(fmt(s.instantConsumption, 1), "لحظي")
        FuelStat(fmt(s.avgConsumption, 1), "متوسط")
    }
}

@Composable
private fun FuelStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = TextMain, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(label, color = TextDim, fontSize = 10.sp)
    }
}

// ---------------------------------------------------------------------------
// Quick apps
// ---------------------------------------------------------------------------

private data class QuickSlot(val id: String, val label: String, val icon: ImageVector)

private val QUICK_SLOTS = listOf(
    QuickSlot("media", "ميديا", Icons.Filled.MusicNote),
    QuickSlot("maps", "خرائط", Icons.Filled.Map),
    QuickSlot("camera", "كاميرا", Icons.Filled.CameraAlt),
    QuickSlot("camera360", "360°", Icons.Filled.Videocam)
)

@Composable
private fun QuickAppsBar(viewModel: LauncherViewModel, onLaunch: (AppInfo) -> Unit, onOpenDrawer: () -> Unit, accent: Color) {
    val prefs = viewModel.prefs
    var picking by remember { mutableStateOf<QuickSlot?>(null) }
    val version = viewModel.settingsVersion
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(PanelBg)
            .border(1.dp, accent.copy(alpha = 0.25f), RoundedCornerShape(22.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        QUICK_SLOTS.forEach { slot ->
            val app = remember(version, viewModel.allApps) { prefs.quickApp(slot.id)?.let { viewModel.appByKey(it) } }
            QuickButton(
                slot.icon, app?.label?.take(10) ?: slot.label,
                onClick = { if (app != null) onLaunch(app) else picking = slot },
                onLongClick = { picking = slot }
            )
        }
        QuickButton(Icons.Filled.Apps, "التطبيقات", onClick = onOpenDrawer, onLongClick = onOpenDrawer)
    }
    picking?.let { slot ->
        AppPickerDialog(
            title = "اختار تطبيق «${slot.label}» (ضغطة طويلة تغيّره بعدين)",
            apps = viewModel.allApps,
            onDismiss = { picking = null },
            onPick = { app ->
                prefs.setQuickApp(slot.id, app.key)
                viewModel.notifySettingsChanged()
                picking = null
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QuickButton(icon: ImageVector, label: String, onClick: () -> Unit, onLongClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, null, tint = TextMain, modifier = Modifier.size(26.dp))
        Text(label, color = TextDim, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ---------------------------------------------------------------------------
// Status
// ---------------------------------------------------------------------------

@Composable
private fun StatusRow(s: VehicleState, writeVerified: Boolean, onOpenDiagnostics: () -> Unit, onOpenSettings: () -> Unit) {
    val (label, color) = when (s.source) {
        VehicleState.Source.LIVE -> (if (writeVerified) "CAN مباشر · تحكم متاح" else "CAN مباشر · قراءة بس") to Ok
        VehicleState.Source.DEMO -> "وضع العرض" to Amber
        VehicleState.Source.NONE -> "مفيش اتصال بالعربية" to TextDim
    }
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(PanelBg)
                .clickable(onClick = onOpenDiagnostics)
                .padding(horizontal = 10.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(color))
            Text("  $label", color = TextMain, fontSize = 11.sp)
        }
        Text(
            listOfNotNull(
                s.odometerKm?.let { "عدّاد ${it.toInt()} كم" },
                s.batteryVolts?.let { String.format(Locale.US, "%.1fV", it) },
                s.rpm?.let { "$it RPM" }
            ).joinToString("  ·  "),
            color = TextDim, fontSize = 11.sp,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            textAlign = TextAlign.Center
        )
        Icon(
            Icons.Filled.Settings, contentDescription = "الإعدادات", tint = TextDim,
            modifier = Modifier.size(28.dp).clip(CircleShape).clickable(onClick = onOpenSettings).padding(4.dp)
        )
    }
}

// ---------------------------------------------------------------------------
// Now playing
// ---------------------------------------------------------------------------

private data class NowPlaying(val title: String, val artist: String, val playing: Boolean, val controller: MediaController?)

@Composable
private fun NowPlayingCard(accent: Color) {
    val context = LocalContext.current
    var np by remember { mutableStateOf<NowPlaying?>(null) }
    var noAccess by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            val r = readNowPlaying(context)
            noAccess = r == null
            np = r?.getOrNull()
            delay(1000)
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(PanelBg)
            .border(1.dp, accent.copy(alpha = 0.25f), RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                np?.title ?: if (noAccess) "فعّل \"الوصول للإشعارات\" عشان تظهر الأغنية" else "مفيش حاجة شغالة",
                color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(np?.artist.orEmpty(), color = TextDim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        val tc = np?.controller?.transportControls
        MediaButton(Icons.Filled.SkipPrevious, 32.dp, accent) { tc?.skipToPrevious() }
        MediaButton(if (np?.playing == true) Icons.Filled.Pause else Icons.Filled.PlayArrow, 42.dp, accent, filled = true) {
            if (np?.playing == true) tc?.pause() else tc?.play()
        }
        MediaButton(Icons.Filled.SkipNext, 32.dp, accent) { tc?.skipToNext() }
    }
}

@Composable
private fun MediaButton(icon: ImageVector, size: Dp, accent: Color, filled: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(start = 8.dp)
            .size(size)
            .clip(CircleShape)
            .background(if (filled) accent else PanelBg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = if (filled) Color.Black else TextMain, modifier = Modifier.fillMaxHeight(0.6f))
    }
}

/** null = no notification-listener access; Result(null) = nothing playing. */
private fun readNowPlaying(context: Context): Result<NowPlaying?>? = try {
    val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    val controllers = manager.getActiveSessions(ComponentName(context, NotificationBadgeService::class.java))
    val c = controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: controllers.firstOrNull()
    Result.success(c?.let {
        val md = it.metadata
        NowPlaying(
            title = md?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "",
            artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: "",
            playing = it.playbackState?.state == PlaybackState.STATE_PLAYING,
            controller = it
        )
    })
} catch (_: SecurityException) {
    null
}

private fun fmt(v: Float?, decimals: Int): String =
    v?.let { if (decimals == 0) it.toInt().toString() else String.format(Locale.US, "%.${decimals}f", it) } ?: "--"
