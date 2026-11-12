package com.dfshine.launcher

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.dfshine.launcher.ui.dashboard.DashboardContent
import com.dfshine.launcher.ui.dashboard.Notice
import com.dfshine.launcher.ui.dashboard.NowPlayingUi
import com.dfshine.launcher.ui.dashboard.QUICK_SLOTS
import com.dfshine.launcher.ui.dashboard.QuickAppUi
import com.dfshine.launcher.ui.theme.ShineLauncherTheme
import com.dfshine.launcher.vehicle.VehicleState
import org.junit.Rule
import org.junit.Test
import java.util.Date

/**
 * Renders the dashboard off-device (Paparazzi/layoutlib) so the design can be
 * reviewed from CI screenshots. The 3D view needs a GPU, so a pre-rendered
 * image of the same model stands in for it here.
 */
class DashboardSnapshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(
            screenWidth = 1080, screenHeight = 1440, xdpi = 240, ydpi = 240, density = Density.HIGH
        ),
        theme = "android:Theme.Material.NoActionBar"
    )

    private val car by lazy {
        javaClass.classLoader!!.getResourceAsStream("car_preview.png").use { BitmapFactory.decodeStream(it) }.asImageBitmap()
    }

    private val base = VehicleState(
        source = VehicleState.Source.DEMO,
        speedKmh = 72, rpm = 2100, gear = VehicleState.Gear.D,
        outsideTempC = 27, coolantTempC = 90,
        fuelPercent = 72f, fuelLitres = 36f, fuelCapacityLitres = 50f, rangeKm = 519f,
        instantConsumption = 6.2f, avgConsumption = 6.9f, odometerKm = 12480f, batteryVolts = 14.1f,
        lights = VehicleState.Lights(lowBeam = true, position = true, turnLeft = true),
        tpms = VehicleState.Tpms(listOf(240f, 238f, 236f, 237f), listOf(31f, 31f, 30f, 30f), listOf(false, false, false, false))
    )

    @Composable
    private fun Screen(state: VehicleState, notice: Notice? = null, playing: Boolean = true) {
        ShineLauncherTheme(accent = Color(0xFF2FB6A6), darkTheme = true) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Column(Modifier.fillMaxSize().background(Color.Black)) {
                    DashboardContent(
                        state = state,
                        canWrite = true,
                        accent = Color(0xFF2FB6A6),
                        tpmsUnit = "bar",
                        nowPlaying = if (playing) NowPlayingUi("Bohemian Rhapsody", "Queen", true, 0.38f) else null,
                        quickApps = QUICK_SLOTS.mapIndexed { i, s -> QuickAppUi(s, if (i == 1) "Yandex Maps" else null) },
                        notice = notice,
                        onNoticeShown = {},
                        available = { true },
                        onWindowsAll = {}, onSunroof = {}, onTrunk = {},
                        onQuickApp = {}, onQuickAppLong = {}, onOpenDrawer = {},
                        onMedia = {}, onOpenDiagnostics = {}, onOpenSettings = {},
                        car = { m ->
                            Box(m, contentAlignment = Alignment.Center) {
                                Image(car, null, Modifier.fillMaxWidth().padding(horizontal = 96.dp), contentScale = ContentScale.FillWidth)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        now = Date(1_799_000_000_000L)
                    )
                    // Where the head unit's own climate bar sits (15% of the height).
                    Box(Modifier.fillMaxWidth().height(144.dp).background(Color(0xFF000000)), contentAlignment = Alignment.Center) {
                        Text("شريط التكييف الأصلي بتاع العربية", color = Color(0xFF555555))
                    }
                }
            }
        }
    }

    @Test
    fun dashboard() = paparazzi.snapshot { Screen(base) }

    @Test
    fun dashboardAlerts() = paparazzi.snapshot {
        Screen(
            base.copy(
                speedKmh = 0, gear = VehicleState.Gear.P, fuelPercent = 18f, fuelLitres = 9f, rangeKm = 120f,
                doors = VehicleState.Doors(frontLeft = true, trunk = true),
                windows = VehicleState.Windows(open = listOf(1f, 0f, 0f, 0f), sunroofTilt = true, known = true),
                lights = VehicleState.Lights(hazard = true, highBeam = true, fogFront = true, fogRear = true),
                tpms = VehicleState.Tpms(listOf(240f, 238f, 236f, 175f), listOf(31f, 31f, 30f, 30f), listOf(false, false, false, true))
            ),
            playing = false
        )
    }
}
