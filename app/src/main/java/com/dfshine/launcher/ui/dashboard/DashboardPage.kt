package com.dfshine.launcher.ui.dashboard

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dfshine.launcher.service.NotificationBadgeService
import com.dfshine.launcher.vehicle.VehicleRepository
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
private val CarBody = Color(0xFF5E7C99)

/**
 * The car dashboard home page: clock, trip/fuel/temperature readouts,
 * speed + gear, light indicators, a live top view of the car (doors,
 * lights, TPMS) and a now-playing card. All numbers come from
 * [VehicleRepository]; anything the car hasn't reported shows "--".
 */
@Composable
fun DashboardPage(
    tpmsUnit: String,
    onOpenDiagnostics: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by VehicleRepository.state.collectAsState()
    val accent = MaterialTheme.colorScheme.primary

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
        Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 8.dp)) {
            TopInfoRow(state)
            Spacer(Modifier.height(6.dp))
            SpeedGearRow(state, accent)
            Spacer(Modifier.height(8.dp))
            IndicatorBar(state.lights, accent)
            CarWithTpms(
                state = state,
                accent = accent,
                tpmsUnit = tpmsUnit,
                modifier = Modifier.weight(1f).fillMaxWidth()
            )
            StatusRow(state, onOpenDiagnostics, onOpenSettings)
            Spacer(Modifier.height(8.dp))
            NowPlayingCard(accent)
        }
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
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            InfoLine(Icons.Filled.Speed, fmt(s.instantConsumption, 1), "استهلاك")
            InfoLine(Icons.Filled.Navigation, fmt(s.rangeKm, 0), "كم متبقي")
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    time.format(now), color = TextMain, fontSize = 46.sp,
                    fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace
                )
                Text(ampm.format(now), color = TextDim, fontSize = 14.sp, modifier = Modifier.padding(start = 4.dp, bottom = 10.dp))
            }
            Text(date.format(now), color = TextDim, fontSize = 13.sp)
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            InfoLine(Icons.Filled.Thermostat, s.outsideTempC?.let { "$it°C" } ?: "--", "برّه", alignEnd = true)
            InfoLine(Icons.Filled.Thermostat, s.coolantTempC?.let { "$it°C" } ?: "--", "المحرك", alignEnd = true)
            InfoLine(Icons.Filled.LocalGasStation, s.fuelPercent?.let { "${it.toInt()}%" } ?: "--", "بنزين", alignEnd = true)
        }
    }
}

@Composable
private fun InfoLine(icon: ImageVector, value: String, label: String, alignEnd: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (!alignEnd) Icon(icon, null, tint = TextDim, modifier = Modifier.size(16.dp).padding(end = 2.dp))
        Text(value, color = TextMain, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
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
                s.speedKmh?.toString() ?: "--", color = TextMain, fontSize = 54.sp,
                fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace
            )
            Text("KM/H", color = TextDim, fontSize = 12.sp, modifier = Modifier.offset(y = (-6).dp))
        }
        Spacer(Modifier.width(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(VehicleState.Gear.P, VehicleState.Gear.R, VehicleState.Gear.N, VehicleState.Gear.D).forEach { g ->
                val active = s.gear == g || (g == VehicleState.Gear.D && (s.gear == VehicleState.Gear.S || s.gear == VehicleState.Gear.B))
                val label = if (g == VehicleState.Gear.D && active && s.gear != VehicleState.Gear.D) s.gear!!.label else g.label
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (active) accent.copy(alpha = 0.25f) else PanelBg)
                        .border(1.dp, if (active) accent else Color.Transparent, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label, color = if (active) accent else TextDim,
                        fontSize = 18.sp, fontWeight = FontWeight.Bold
                    )
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
    val amber = Color(0xFFFFB020)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(PanelBg)
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(28.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IndicatorIcon(Icons.Filled.ArrowBack, (l.turnLeft || l.hazard) && on, Ok)
        IndicatorText("⚠", l.hazard && on, Color(0xFFFF6A3D), "انتظار")
        IndicatorText("≡D", l.fogFront, Ok, "ضباب أمامي")
        IndicatorText("≡O", l.fogRear, amber, "ضباب خلفي")
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

/** Text-glyph indicator with a tiny Arabic caption. */
@Composable
private fun IndicatorText(glyph: String, active: Boolean, color: Color, label: String) {
    val c = if (active) color else TextDim.copy(alpha = 0.35f)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(glyph, color = c, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(label, color = c, fontSize = 8.sp)
    }
}

// ---------------------------------------------------------------------------
// Car + TPMS
// ---------------------------------------------------------------------------

@Composable
private fun CarWithTpms(state: VehicleState, accent: Color, tpmsUnit: String, modifier: Modifier) {
    BoxWithConstraints(modifier) {
        val h = maxHeight
        val frontTop = (h * WHEEL_FRONT_Y - 22.dp).coerceAtLeast(0.dp)
        val rearTop = (h * WHEEL_REAR_Y - 22.dp).coerceAtLeast(0.dp)
        CarTopView(state, CarBody, accent, Modifier.fillMaxSize())
        val t = state.tpms
        if (t != null) {
            TireLabel(t, 0, tpmsUnit, Modifier.align(Alignment.TopStart).padding(top = frontTop))
            TireLabel(t, 1, tpmsUnit, Modifier.align(Alignment.TopEnd).padding(top = frontTop))
            TireLabel(t, 2, tpmsUnit, Modifier.align(Alignment.TopStart).padding(top = rearTop))
            TireLabel(t, 3, tpmsUnit, Modifier.align(Alignment.TopEnd).padding(top = rearTop))
        }
        DoorWarning(state.doors, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun TireLabel(t: VehicleState.Tpms, i: Int, unit: String, modifier: Modifier) {
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
            .padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, color = color, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text("${t.tempC.getOrElse(i) { 0f }.toInt()}°C", color = TextDim, fontSize = 11.sp)
    }
}

@Composable
private fun DoorWarning(d: VehicleState.Doors, modifier: Modifier) {
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
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Warn.copy(alpha = 0.18f))
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Warning, null, tint = Warn, modifier = Modifier.size(16.dp))
        Text(" مفتوح: ${open.joinToString("، ")}", color = TextMain, fontSize = 12.sp)
    }
}

// ---------------------------------------------------------------------------
// Data-source status
// ---------------------------------------------------------------------------

@Composable
private fun StatusRow(s: VehicleState, onOpenDiagnostics: () -> Unit, onOpenSettings: () -> Unit) {
    val (label, color) = when (s.source) {
        VehicleState.Source.LIVE -> "CAN مباشر" to Ok
        VehicleState.Source.DEMO -> "وضع العرض" to Color(0xFFFFB020)
        VehicleState.Source.NONE -> "مفيش اتصال بالعربية" to TextDim
    }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(PanelBg)
                .clickable(onClick = onOpenDiagnostics)
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(color))
            Text("  $label", color = TextMain, fontSize = 12.sp)
        }
        Text(
            listOfNotNull(
                s.odometerKm?.let { "عدّاد ${it.toInt()} كم" },
                s.batteryVolts?.let { String.format(Locale.US, "%.1fV", it) },
                s.rpm?.let { "$it RPM" }
            ).joinToString("  ·  "),
            color = TextDim, fontSize = 12.sp,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
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
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                np?.title ?: if (noAccess) "فعّل \"الوصول للإشعارات\" عشان تظهر الأغنية" else "مفيش حاجة شغالة",
                color = TextMain, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(np?.artist.orEmpty(), color = TextDim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        val tc = np?.controller?.transportControls
        MediaButton(Icons.Filled.SkipPrevious, 34.dp, accent) { tc?.skipToPrevious() }
        MediaButton(if (np?.playing == true) Icons.Filled.Pause else Icons.Filled.PlayArrow, 44.dp, accent, filled = true) {
            if (np?.playing == true) tc?.pause() else tc?.play()
        }
        MediaButton(Icons.Filled.SkipNext, 34.dp, accent) { tc?.skipToNext() }
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
