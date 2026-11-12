package com.dfshine.launcher.vehicle

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.sin

/**
 * Single source of vehicle data for the whole launcher.
 *
 * Modes:
 *  - "auto": read the real car through [QingganCanBusClient]. If the
 *    service isn't there, fields stay null and the UI shows "--".
 *  - "demo": simulated data, to preview the dashboard off the car.
 *  - "off" : nothing.
 */
object VehicleRepository {
    const val MODE_AUTO = "auto"
    const val MODE_DEMO = "demo"
    const val MODE_OFF = "off"

    private val _state = MutableStateFlow(VehicleState())
    val state: StateFlow<VehicleState> = _state

    private val _status = MutableStateFlow(QingganCanBusClient.Status())
    val status: StateFlow<QingganCanBusClient.Status> = _status

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var client: QingganCanBusClient? = null
    private var demoJob: Job? = null
    private var currentKey: String? = null

    @Synchronized
    fun start(context: Context, mode: String, allowDefaultCodes: Boolean) {
        val key = "$mode/$allowDefaultCodes"
        if (key == currentKey) return
        stopInternal()
        currentKey = key
        _state.value = VehicleState()
        when (mode) {
            MODE_AUTO -> {
                val c = QingganCanBusClient(
                    context,
                    allowDefaultCodes,
                    onState = { change ->
                        _state.update {
                            change(it).copy(source = VehicleState.Source.LIVE, lastUpdateMs = System.currentTimeMillis())
                        }
                    },
                    onStatus = { _status.value = it }
                )
                client = c
                c.start()
            }
            MODE_DEMO -> {
                _status.value = QingganCanBusClient.Status(message = "وضع العرض (بيانات تجريبية)")
                demoJob = scope.launch { runDemo() }
            }
            else -> _status.value = QingganCanBusClient.Status(message = "بيانات العربية مقفولة")
        }
    }

    /** Forces a reconnect (e.g. after toggling an option in diagnostics). */
    fun restart(context: Context, mode: String, allowDefaultCodes: Boolean) {
        synchronized(this) { currentKey = null }
        start(context, mode, allowDefaultCodes)
    }

    private fun stopInternal() {
        client?.stop()
        client = null
        demoJob?.cancel()
        demoJob = null
    }

    // ------------------------------------------------------------------
    // Demo: a short loop that exercises every widget on the dashboard.
    // ------------------------------------------------------------------

    private suspend fun runDemo() {
        var t = 0.0
        var odo = 12480f
        while (scope.isActive) {
            val cycle = (t % 60).toInt()
            val speed = max(0.0, 70 + 50 * sin(t / 9)).toInt().let { if (cycle < 8) 0 else it }
            odo += speed / 3600f / 4f
            _state.value = VehicleState(
                source = VehicleState.Source.DEMO,
                speedKmh = speed,
                rpm = if (speed == 0) 780 else 1200 + speed * 22,
                gear = if (cycle < 8) VehicleState.Gear.P else VehicleState.Gear.D,
                outsideTempC = 27,
                coolantTempC = 90,
                fuelPercent = 72f,
                rangeKm = 519f,
                odometerKm = odo,
                instantConsumption = if (speed == 0) 0.8f else 5.5f + (speed % 7) / 2f,
                avgConsumption = 6.9f,
                batteryVolts = 14.1f,
                handBrake = cycle < 8,
                doors = VehicleState.Doors(
                    frontLeft = cycle in 2..6,
                    rearRight = cycle in 4..6,
                    trunk = cycle in 5..7
                ),
                lights = VehicleState.Lights(
                    turnLeft = cycle in 20..26,
                    turnRight = cycle in 40..45,
                    hazard = cycle in 52..55,
                    lowBeam = true,
                    position = true,
                    highBeam = cycle in 30..35,
                    fogFront = cycle in 30..38,
                    brake = speed in 1..30
                ),
                tpms = VehicleState.Tpms(
                    pressureKpa = listOf(240f, 238f, 235f, if (cycle in 46..50) 180f else 236f),
                    tempC = listOf(31f, 31f, 30f, 30f),
                    warning = listOf(false, false, false, cycle in 46..50)
                ),
                lastUpdateMs = System.currentTimeMillis()
            )
            t += 0.25
            delay(250)
        }
    }
}
