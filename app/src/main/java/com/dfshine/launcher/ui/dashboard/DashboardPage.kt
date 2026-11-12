package com.dfshine.launcher.ui.dashboard

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material.icons.filled.CameraAlt
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.CompositionLocalProvider
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

// =============================================================================
// Stateful entry point (reads the car, prefs, media; owns dialogs & messages)
// =============================================================================

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
    val context = LocalContext.current
    val version = viewModel.settingsVersion

    var showSunroof by remember { mutableStateOf(false) }
    var confirmTrunk by remember { mutableStateOf(false) }
    var realModelFailed by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf<QuickSlot?>(null) }
    var notice by remember { mutableStateOf<Notice?>(null) }
    val carColor = remember(version) {
        runCatching { Color(android.graphics.Color.parseColor(prefs.carColorHex)) }.getOrDefault(Color(0xFF6F8BA8))
    }

    fun send(c: Control, open: Boolean) {
        VehicleRepository.control(c, open) { err ->
            notice = if (err == null) Notice("تم إرسال الأمر", ok = true) else Notice(err, ok = false)
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

    val nowPlaying = rememberNowPlaying()
    val quickApps = remember(version, viewModel.allApps) {
        QUICK_SLOTS.map { slot -> slot to prefs.quickApp(slot.id)?.let { viewModel.appByKey(it) } }
    }

    DashboardContent(
        state = state,
        canWrite = status.writeVerified,
        accent = MaterialTheme.colorScheme.primary,
        tpmsUnit = prefs.tpmsUnit,
        nowPlaying = nowPlaying.value,
        quickApps = quickApps.map { (slot, app) -> QuickAppUi(slot, app?.label) },
        notice = notice,
        onNoticeShown = { notice = null },
        available = { VehicleRepository.isAvailable(it) },
        onWindowsAll = {
            val open = !state.windows.open.any { it > 0.5f }
            val main = Handler(Looper.getMainLooper())
            // Space the four commands out; the client also rate-limits.
            Control.WINDOWS.forEachIndexed { i, c -> main.postDelayed({ send(c, open) }, i * 450L) }
        },
        onSunroof = { showSunroof = true },
        onTrunk = { confirmTrunk = true },
        onQuickApp = { slot ->
            val app = quickApps.firstOrNull { it.first == slot }?.second
            if (app != null) onLaunch(app) else picking = slot
        },
        onQuickAppLong = { picking = it },
        onOpenDrawer = onOpenDrawer,
        onMedia = { action -> nowPlaying.value?.controller?.transportControls?.let(action) },
        onOpenDiagnostics = onOpenDiagnostics,
        onOpenSettings = onOpenSettings,
        car = { m ->
            if (prefs.carModelStyle == "real" && !realModelFailed) {
                RealCarView(state, ::onPart, onFailure = { realModelFailed = true }, modifier = m)
            } else {
                Car3DView(state, carColor, ::onPart, m)
            }
        },
        modifier = modifier
    )

    if (showSunroof) {
        SunroofDialog(state.windows, onDismiss = { showSunroof = false }) { c, open -> showSunroof = false; send(c, open) }
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

// =============================================================================
// Stateless UI (also used by the screenshot test)
// =============================================================================

data class Notice(val text: String, val ok: Boolean)
data class NowPlayingUi(val title: String, val artist: String, val playing: Boolean, val progress: Float?, val controller: MediaController? = null)
data class QuickSlot(val id: String, val label: String, val icon: ImageVector)
data class QuickAppUi(val slot: QuickSlot, val appLabel: String?)

val QUICK_SLOTS = listOf(
    QuickSlot("media", "ميديا", Icons.Filled.MusicNote),
    QuickSlot("maps", "خرائط", Icons.Filled.Map),
    QuickSlot("camera", "كاميرا", Icons.Filled.CameraAlt),
    QuickSlot("camera360", "360°", Icons.Filled.Videocam)
)

@Composable
fun DashboardContent(
    state: VehicleState,
    canWrite: Boolean,
    accent: Color,
    tpmsUnit: String,
    nowPlaying: NowPlayingUi?,
    quickApps: List<QuickAppUi>,
    notice: Notice?,
    onNoticeShown: () -> Unit,
    available: (Control) -> Boolean,
    onWindowsAll: () -> Unit,
    onSunroof: () -> Unit,
    onTrunk: () -> Unit,
    onQuickApp: (QuickSlot) -> Unit,
    onQuickAppLong: (QuickSlot) -> Unit,
    onOpenDrawer: () -> Unit,
    onMedia: ((android.media.session.MediaController.TransportControls) -> Unit) -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenSettings: () -> Unit,
    car: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
    now: Date? = null
) {
    var showTpms by remember { mutableStateOf(false) }
    Box(modifier.fillMaxSize().background(Dash.Background)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp)) {
            TopBar(state, canWrite, accent, onOpenDiagnostics, onOpenSettings, now)
            Spacer(Modifier.height(10.dp))
            HeroRow(state, accent)
            Spacer(Modifier.height(12.dp))
            TelltaleBar(state.lights)
            // ---- Stage ----
            Box(Modifier.weight(1f).fillMaxWidth()) {
                StageLight(accent)
                car(Modifier.fillMaxSize())
                ControlRail(
                    state, accent, available,
                    onWindowsAll, onSunroof, onTrunk,
                    Modifier.align(Alignment.CenterStart)
                )
                if (showTpms || state.tpms?.anyWarning == true) TpmsOverlay(state.tpms, tpmsUnit)
                Column(Modifier.align(Alignment.BottomCenter), horizontalAlignment = Alignment.CenterHorizontally) {
                    DoorWarning(state.doors)
                }
                TpmsButton(state.tpms, showTpms, { showTpms = !showTpms }, Modifier.align(Alignment.BottomEnd).padding(bottom = 6.dp))
                NoticePill(notice, onNoticeShown, Modifier.align(Alignment.TopCenter).padding(top = 6.dp))
            }
            TripCard(state, accent)
            Spacer(Modifier.height(10.dp))
            MediaCard(nowPlaying, accent, onMedia)
            Spacer(Modifier.height(10.dp))
            QuickBar(quickApps, accent, onQuickApp, onQuickAppLong, onOpenDrawer)
        }
    }
}

// ---------------------------------------------------------------------------
// Top bar
// ---------------------------------------------------------------------------

@Composable
private fun TopBar(s: VehicleState, canWrite: Boolean, accent: Color, onDiag: () -> Unit, onSettings: () -> Unit, fixedNow: Date?) {
    var now by remember { mutableStateOf(fixedNow ?: Date()) }
    if (fixedNow == null) LaunchedEffect(Unit) { while (true) { now = Date(); delay(1000) } }
    val time = remember { SimpleDateFormat("hh:mm", Locale.US) }
    val ampm = remember { SimpleDateFormat("a", Locale.US) }
    val date = remember { SimpleDateFormat("EEEE، d MMMM", Locale("ar")) }
    val (label, color) = when (s.source) {
        VehicleState.Source.LIVE -> (if (canWrite) "CAN · تحكم" else "CAN · مباشر") to Dash.Ok
        VehicleState.Source.DEMO -> "عرض تجريبي" to Dash.Warn
        VehicleState.Source.NONE -> "غير متصل" to Dash.TextTertiary
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.glass(radius = 16.dp).clickable(onClick = onDiag).padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(8.dp).clip(CircleShape).background(color)
                    .drawBehind { drawCircle(color.copy(alpha = 0.35f), radius = size.minDimension * 1.4f) }
            )
            Spacer(Modifier.width(8.dp))
            Text(label, style = Dash.Label.copy(color = Dash.TextPrimary))
        }
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(ampm.format(now), style = Dash.UnitText, modifier = Modifier.padding(end = 4.dp, bottom = 5.dp))
                Text(time.format(now), style = Dash.Clock)
            }
            Text(date.format(now), style = Dash.Caption.copy(color = Dash.TextSecondary))
        }
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier.size(40.dp).glass(radius = 20.dp).clickable(onClick = onSettings),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Filled.Settings, "الإعدادات", tint = Dash.TextSecondary, modifier = Modifier.size(20.dp)) }
    }
}

// ---------------------------------------------------------------------------
// Hero: environment | speed + gear | fuel
// ---------------------------------------------------------------------------

@Composable
private fun HeroRow(s: VehicleState, accent: Color) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).glass().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            MiniStat(Icons.Filled.Thermostat, s.outsideTempC?.toString() ?: "--", "°C", "برّه", accent)
            MiniStat(Icons.Filled.Thermostat, s.coolantTempC?.toString() ?: "--", "°C", "حرارة المحرك",
                if ((s.coolantTempC ?: 0) >= 110) Dash.Danger else accent)
        }
        Column(Modifier.weight(1.25f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(s.speedKmh?.toString() ?: "--", style = Dash.Hero)
            Text("كم/س", style = Dash.UnitText, modifier = Modifier.offset(y = (-8).dp))
            GearSelector(s.gear, accent)
        }
        Column(Modifier.weight(1f).glass().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val pct = s.fuelPercent
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.LocalGasStation, null, tint = fuelColor(pct, accent), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(pct?.let { "${it.toInt()}%" } ?: "--", style = Dash.Value)
            }
            FuelGauge(pct, accent)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(s.rangeKm?.toInt()?.toString() ?: "--", style = Dash.ValueSmall)
                Text(" كم متبقي", style = Dash.Label)
            }
        }
    }
}

private fun fuelColor(pct: Float?, accent: Color) = when {
    pct == null -> Dash.TextTertiary
    pct < 12f -> Dash.Danger
    pct < 25f -> Dash.Warn
    else -> accent
}

@Composable
private fun MiniStat(icon: ImageVector, value: String, unit: String, label: String, tint: Color) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(value, style = Dash.Value)
            Text(unit, style = Dash.UnitText, modifier = Modifier.padding(start = 2.dp))
        }
        Text(label, style = Dash.Caption)
    }
}

@Composable
private fun FuelGauge(pct: Float?, accent: Color) {
    val f by animateFloatAsState(((pct ?: 0f) / 100f).coerceIn(0f, 1f), tween(800), label = "fuel")
    val c = fuelColor(pct, accent)
    Box(
        Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Dash.Divider)
            .drawBehind {
                drawRoundRect(
                    Brush.horizontalGradient(listOf(c.copy(alpha = 0.55f), c)),
                    size = Size(size.width * f, size.height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2)
                )
            }
    )
}

@Composable
private fun GearSelector(gear: VehicleState.Gear?, accent: Color) {
    val gears = listOf(VehicleState.Gear.P, VehicleState.Gear.R, VehicleState.Gear.N, VehicleState.Gear.D)
    val sport = gear == VehicleState.Gear.S || gear == VehicleState.Gear.B
    val index = when {
        sport -> 3
        else -> gears.indexOf(gear)
    }
    val cell = 36.dp
    val x by animateDpAsState(cell * maxOf(index, 0), spring(stiffness = 500f), label = "gear")
    // Gear order is a physical sequence: keep it left-to-right even in RTL.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(Modifier.glass(radius = 14.dp).padding(4.dp)) {
            if (index >= 0) {
                Box(
                    Modifier.offset(x = x).size(cell, 30.dp).clip(RoundedCornerShape(10.dp))
                        .background(accent.copy(alpha = 0.22f))
                        .border(1.dp, accent.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
                )
            }
            Row {
                gears.forEachIndexed { i, g ->
                    val active = i == index
                    Box(Modifier.size(cell, 30.dp), contentAlignment = Alignment.Center) {
                        Text(
                            if (g == VehicleState.Gear.D && sport) gear!!.label else g.label,
                            color = if (active) accent else Dash.TextTertiary,
                            fontSize = 16.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Tell-tales
// ---------------------------------------------------------------------------

@Composable
private fun TelltaleBar(l: VehicleState.Lights) {
    var blinkOn by remember { mutableStateOf(true) }
    val blinking = l.turnLeft || l.turnRight || l.hazard
    if (blinking) LaunchedEffect(Unit) { while (true) { delay(420); blinkOn = !blinkOn } }
    val on = !blinking || blinkOn
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            Modifier.fillMaxWidth().glass(radius = 28.dp).padding(horizontal = 18.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TelltaleIcon(Telltale.TURN_LEFT, (l.turnLeft || l.hazard) && on, Dash.Signal)
            TelltaleIcon(Telltale.HAZARD, l.hazard && on, Dash.Danger)
            TelltaleIcon(Telltale.POSITION, l.position, Dash.Signal)
            TelltaleIcon(Telltale.LOW_BEAM, l.lowBeam, Dash.Signal)
            TelltaleIcon(Telltale.HIGH_BEAM, l.highBeam, Dash.HighBeam)
            TelltaleIcon(Telltale.FOG_FRONT, l.fogFront, Dash.Signal)
            TelltaleIcon(Telltale.FOG_REAR, l.fogRear, Dash.Warn)
            TelltaleIcon(Telltale.TURN_RIGHT, (l.turnRight || l.hazard) && on, Dash.Signal)
        }
    }
}

// ---------------------------------------------------------------------------
// Stage: spotlight + floor, control rail, TPMS, notices
// ---------------------------------------------------------------------------

@Composable
private fun StageLight(accent: Color) {
    Box(
        Modifier.fillMaxSize().drawBehind {
            val c = Offset(size.width / 2f, size.height * 0.48f)
            // Accent spotlight behind the car
            drawCircle(
                Brush.radialGradient(listOf(accent.copy(alpha = 0.20f), accent.copy(alpha = 0.06f), Color.Transparent), c, size.minDimension * 0.62f),
                radius = size.minDimension * 0.62f, center = c
            )
            // Studio floor: soft elliptical light pool under the wheels
            val floorH = size.height * 0.20f
            val floorTop = size.height * 0.62f
            drawOval(
                Brush.radialGradient(
                    listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.03f), Color.Transparent),
                    center = Offset(size.width / 2f, floorTop + floorH / 2f), radius = size.width * 0.48f
                ),
                topLeft = Offset(size.width * 0.04f, floorTop), size = Size(size.width * 0.92f, floorH)
            )
            // Thin horizon line
            drawLine(
                Brush.horizontalGradient(listOf(Color.Transparent, accent.copy(alpha = 0.25f), Color.Transparent)),
                Offset(0f, floorTop + floorH * 0.5f), Offset(size.width, floorTop + floorH * 0.5f), strokeWidth = 1.2f
            )
        }
    )
}

@Composable
private fun ControlRail(
    s: VehicleState,
    accent: Color,
    available: (Control) -> Boolean,
    onWindowsAll: () -> Unit,
    onSunroof: () -> Unit,
    onTrunk: () -> Unit,
    modifier: Modifier
) {
    val w = s.windows
    Column(
        modifier.glass(radius = 24.dp).padding(vertical = 8.dp, horizontal = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        RailButton(Icons.Filled.Window, "الشبابيك", w.open.any { it > 0.05f }, Control.WINDOWS.any(available), accent, onWindowsAll)
        RailButton(Icons.Filled.WbSunny, "السقف", w.sunroofOpen > 0.05f || w.sunroofTilt, available(Control.SUNROOF) || available(Control.SUNROOF_TILT), accent, onSunroof)
        RailButton(Icons.Filled.Luggage, "الشنطة", s.doors.trunk, available(Control.TRUNK), accent, onTrunk)
    }
}

@Composable
private fun RailButton(icon: ImageVector, label: String, active: Boolean, enabled: Boolean, accent: Color, onClick: () -> Unit) {
    val bg by animateFloatAsState(if (active) 1f else 0f, tween(300), label = "rail")
    Column(
        Modifier
            .width(64.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(accent.copy(alpha = 0.20f * bg))
            .clickable(onClick = onClick) // still tappable when unavailable: the notice explains why
            .alpha(if (enabled) 1f else 0.5f)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, null, tint = if (active) accent else Dash.TextPrimary, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, style = Dash.Caption.copy(color = if (active) accent else Dash.TextSecondary))
        Box(Modifier.padding(top = 4.dp).size(4.dp).clip(CircleShape).background(if (active) accent else Color.Transparent))
    }
}

@Composable
private fun TpmsButton(t: VehicleState.Tpms?, active: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val c = when {
        t == null -> Dash.TextTertiary
        t.anyWarning -> Dash.Danger
        else -> Dash.Ok
    }
    Row(
        modifier.glass(radius = 18.dp, tint = if (active) c else null).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(c))
        Spacer(Modifier.width(8.dp))
        Text("TPMS", color = c, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    }
}

@Composable
private fun TpmsOverlay(t: VehicleState.Tpms?, unit: String) {
    if (t == null) return
    Box(Modifier.fillMaxSize().padding(start = 84.dp, top = 8.dp, bottom = 48.dp)) {
        TireChip(t, 0, "أمامي شمال", unit, Modifier.align(Alignment.TopStart))
        TireChip(t, 1, "أمامي يمين", unit, Modifier.align(Alignment.TopEnd))
        TireChip(t, 2, "خلفي شمال", unit, Modifier.align(Alignment.BottomStart))
        TireChip(t, 3, "خلفي يمين", unit, Modifier.align(Alignment.BottomEnd))
    }
}

@Composable
private fun TireChip(t: VehicleState.Tpms, i: Int, name: String, unit: String, modifier: Modifier) {
    val warn = t.warning.getOrElse(i) { false }
    val kpa = t.pressureKpa.getOrElse(i) { 0f }
    val value = when (unit) {
        "kpa" -> "${kpa.toInt()}"
        "psi" -> String.format(Locale.US, "%.0f", kpa * 0.145038f)
        else -> String.format(Locale.US, "%.1f", kpa / 100f)
    }
    val unitLabel = when (unit) { "kpa" -> "kPa"; "psi" -> "psi"; else -> "bar" }
    val c = if (warn) Dash.Danger else Dash.Ok
    Column(modifier.glass(radius = 14.dp, tint = if (warn) Dash.Danger else null).padding(horizontal = 12.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = Dash.ValueSmall.copy(color = c))
            Text(" $unitLabel", style = Dash.UnitText)
        }
        Text("$name · ${t.tempC.getOrElse(i) { 0f }.toInt()}°", style = Dash.Caption)
    }
}

@Composable
private fun DoorWarning(d: VehicleState.Doors) {
    AnimatedVisibility(d.anyOpen, enter = fadeIn() + slideInVertically { it }, exit = fadeOut() + slideOutVertically { it }) {
        val open = buildList {
            if (d.frontLeft) add("أمامي شمال")
            if (d.frontRight) add("أمامي يمين")
            if (d.rearLeft) add("خلفي شمال")
            if (d.rearRight) add("خلفي يمين")
            if (d.trunk) add("الشنطة")
            if (d.hood) add("الكبوت")
        }
        Row(
            Modifier.padding(bottom = 8.dp).glass(radius = 16.dp, tint = Dash.Warn).padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Warning, null, tint = Dash.Warn, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("مفتوح: ${open.joinToString("، ")}", style = Dash.Label.copy(color = Dash.TextPrimary))
        }
    }
}

@Composable
private fun NoticePill(notice: Notice?, onShown: () -> Unit, modifier: Modifier) {
    var shown by remember { mutableStateOf<Notice?>(null) }
    LaunchedEffect(notice) {
        if (notice != null) {
            shown = notice
            delay(2600)
            shown = null
            onShown()
        }
    }
    AnimatedVisibility(shown != null, modifier = modifier, enter = fadeIn() + slideInVertically { -it }, exit = fadeOut() + slideOutVertically { -it }) {
        val n = shown ?: return@AnimatedVisibility
        val c = if (n.ok) Dash.Ok else Dash.Warn
        Row(
            Modifier.glass(radius = 18.dp, strong = true, tint = c).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(c))
            Spacer(Modifier.width(8.dp))
            Text(n.text, style = Dash.Label.copy(color = Dash.TextPrimary), maxLines = 2)
        }
    }
}

// ---------------------------------------------------------------------------
// Trip / consumption card
// ---------------------------------------------------------------------------

@Composable
private fun TripCard(s: VehicleState, accent: Color) {
    Row(
        Modifier.fillMaxWidth().glass().padding(horizontal = 6.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val litres = s.fuelLitres?.let { l -> s.fuelCapacityLitres?.let { "${l.toInt()}/${it.toInt()}" } ?: "${l.toInt()}" }
        TripStat(litres ?: "--", "لتر", "في الخزان")
        StatDivider()
        TripStat(s.rangeKm?.toInt()?.toString() ?: "--", "كم", "المسافة المتبقية", accent)
        StatDivider()
        TripStat(fmt(s.instantConsumption, 1), "", "استهلاك لحظي")
        StatDivider()
        TripStat(fmt(s.avgConsumption, 1), "", "متوسط الاستهلاك")
    }
}

@Composable
private fun RowScope.TripStat(value: String, unit: String, label: String, valueColor: Color = Dash.TextPrimary) {
    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = Dash.Value.copy(color = valueColor))
            if (unit.isNotEmpty()) Text(" $unit", style = Dash.UnitText)
        }
        Text(label, style = Dash.Caption, textAlign = TextAlign.Center)
    }
}

@Composable
private fun StatDivider() {
    Box(Modifier.width(1.dp).height(30.dp).background(Dash.Divider))
}

// ---------------------------------------------------------------------------
// Media
// ---------------------------------------------------------------------------

@Composable
private fun MediaCard(np: NowPlayingUi?, accent: Color, onMedia: ((android.media.session.MediaController.TransportControls) -> Unit) -> Unit) {
    Row(
        Modifier.fillMaxWidth().glass().padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(46.dp).clip(RoundedCornerShape(14.dp))
                .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.55f), accent.copy(alpha = 0.12f)))),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Filled.MusicNote, null, tint = Dash.TextPrimary, modifier = Modifier.size(22.dp)) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(np?.title?.ifBlank { null } ?: "مفيش حاجة شغالة", style = Dash.ValueSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(np?.artist.orEmpty().ifBlank { "اختار أغنية من الميديا" }, style = Dash.Label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            np?.progress?.let { p ->
                Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(Dash.Divider)) {
                    Box(Modifier.fillMaxWidth(p.coerceIn(0f, 1f)).fillMaxHeight().background(accent))
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MediaButton(Icons.Filled.SkipPrevious, 38.dp, accent) { onMedia { it.skipToPrevious() } }
                MediaButton(if (np?.playing == true) Icons.Filled.Pause else Icons.Filled.PlayArrow, 48.dp, accent, filled = true) {
                    onMedia { if (np?.playing == true) it.pause() else it.play() }
                }
                MediaButton(Icons.Filled.SkipNext, 38.dp, accent) { onMedia { it.skipToNext() } }
            }
        }
    }
}

@Composable
private fun MediaButton(icon: ImageVector, size: Dp, accent: Color, filled: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.padding(horizontal = 3.dp).size(size).clip(CircleShape)
            .background(if (filled) accent else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = if (filled) Color(0xFF041014) else Dash.TextPrimary, modifier = Modifier.size(size * 0.55f))
    }
}

// ---------------------------------------------------------------------------
// Quick apps
// ---------------------------------------------------------------------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QuickBar(
    apps: List<QuickAppUi>,
    accent: Color,
    onClick: (QuickSlot) -> Unit,
    onLong: (QuickSlot) -> Unit,
    onOpenDrawer: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().glass(radius = 26.dp).padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        apps.forEach { a ->
            QuickButton(a.slot.icon, a.appLabel?.take(10) ?: a.slot.label, accent, a.appLabel != null, { onClick(a.slot) }, { onLong(a.slot) })
        }
        QuickButton(Icons.Filled.Apps, "التطبيقات", accent, true, onOpenDrawer, onOpenDrawer)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QuickButton(icon: ImageVector, label: String, accent: Color, configured: Boolean, onClick: () -> Unit, onLong: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(16.dp)).combinedClickable(onClick = onClick, onLongClick = onLong).padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(46.dp).clip(RoundedCornerShape(15.dp))
                .background(if (configured) accent.copy(alpha = 0.16f) else Dash.Glass)
                .border(1.dp, if (configured) accent.copy(alpha = 0.35f) else Dash.GlassBorder, RoundedCornerShape(15.dp)),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = if (configured) accent else Dash.TextSecondary, modifier = Modifier.size(22.dp)) }
        Spacer(Modifier.height(4.dp))
        Text(label, style = Dash.Caption.copy(color = Dash.TextSecondary), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ---------------------------------------------------------------------------
// Dialogs & helpers
// ---------------------------------------------------------------------------

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

/** Polls the active media session once a second (needs notification-listener access). */
@Composable
private fun rememberNowPlaying(): androidx.compose.runtime.State<NowPlayingUi?> {
    val context = LocalContext.current
    val state = remember { mutableStateOf<NowPlayingUi?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            state.value = readNowPlaying(context)
            delay(1000)
        }
    }
    return state
}

private fun readNowPlaying(context: Context): NowPlayingUi? = try {
    val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    val controllers = manager.getActiveSessions(ComponentName(context, NotificationBadgeService::class.java))
    val c = controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: controllers.firstOrNull()
    c?.let {
        val md = it.metadata
        val duration = md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        val position = it.playbackState?.position ?: 0L
        NowPlayingUi(
            title = md?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "",
            artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: "",
            playing = it.playbackState?.state == PlaybackState.STATE_PLAYING,
            progress = if (duration > 0) position.toFloat() / duration else null,
            controller = it
        )
    }
} catch (_: Throwable) {
    // No notification-listener access yet (SecurityException) or no media service.
    null
}

private fun fmt(v: Float?, decimals: Int): String =
    v?.let { if (decimals == 0) it.toInt().toString() else String.format(Locale.US, "%.${decimals}f", it) } ?: "--"
