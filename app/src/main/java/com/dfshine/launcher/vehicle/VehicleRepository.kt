package com.dfshine.launcher.vehicle

import android.content.Context
import android.os.Handler
import android.os.Looper
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
import kotlin.math.min
import kotlin.math.sin

/**
 * Single source of vehicle data (and the only way to send a control
 * command) for the whole launcher.
 *
 * Modes:
 *  - "auto": the real car through [QingganCanBusClient]. If the service
 *    isn't there, fields stay null and the UI shows "--".
 *  - "demo": simulated data; controls animate the model without touching the car.
 *  - "off" : nothing.
 */
object VehicleRepository {
    const val MODE_AUTO = "auto"
    const val MODE_DEMO = "demo"
    const val MODE_OFF = "off"

    enum class Control(val vendorName: String) {
        WINDOW_FL("DRIVER_POWER_WINDOW_CONTROL_SWITCH"),
        WINDOW_FR("PASSENGER_POWER_WINDOW_CONTROL_SWITCH"),
        WINDOW_RL("REAR_LEFT_POWER_WINDOW_CONTROL_SWITCH"),
        WINDOW_RR("REAR_RIGHT_POWER_WINDOW_CONTROL_SWITCH"),
        SUNROOF("POWER_SUNROOF_CONTROL_SWITCH"),
        SUNROOF_TILT("POWER_SUNROOF_CONTROL_TILT"),
        SHADE("SUNROOF_ROLLSHADE_CONTROL"),
        TRUNK("TRUNK_RELEASE");

        companion object {
            val WINDOWS = listOf(WINDOW_FL, WINDOW_FR, WINDOW_RL, WINDOW_RR)
        }
    }

    private val _state = MutableStateFlow(VehicleState())
    val state: StateFlow<VehicleState> = _state

    private val _status = MutableStateFlow(QingganCanBusClient.Status())
    val status: StateFlow<QingganCanBusClient.Status> = _status

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val main = Handler(Looper.getMainLooper())
    private var client: QingganCanBusClient? = null
    private var demoJob: Job? = null
    private var currentKey: String? = null

    @Volatile private var controlsEnabled = false
    @Volatile private var allowWhileMoving = false

    // Demo-only targets set by the user's taps.
    private val demoWindowTarget = FloatArray(4)
    @Volatile private var demoSunroofTarget = 0f
    @Volatile private var demoTilt = false
    @Volatile private var demoShade = false
    @Volatile private var demoTrunk = false

    fun configureControls(enabled: Boolean, whileMoving: Boolean) {
        controlsEnabled = enabled
        allowWhileMoving = whileMoving
    }

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
    // Controls
    // ------------------------------------------------------------------

    /** Is this control usable right now (for greying out buttons)? */
    fun isAvailable(c: Control): Boolean = when (_state.value.source) {
        VehicleState.Source.DEMO -> true
        VehicleState.Source.LIVE -> controlsEnabled && _status.value.writeVerified &&
            c.vendorName in _status.value.controlsAvailable
        VehicleState.Source.NONE -> false
    }

    /**
     * Sends one command. [done] runs on the main thread with null on
     * success, or the reason (Arabic, user-facing) it was not sent.
     */
    fun control(c: Control, open: Boolean, done: (String?) -> Unit) {
        val reply: (String?) -> Unit = { msg -> main.post { done(msg) } }
        val s = _state.value
        when (s.source) {
            VehicleState.Source.NONE -> reply("مفيش اتصال بالعربية")
            VehicleState.Source.DEMO -> {
                simulate(c, open)
                reply(null)
            }
            VehicleState.Source.LIVE -> {
                val refusal = safetyCheck(c, s)
                if (refusal != null) {
                    reply(refusal)
                    return
                }
                val cl = client ?: return reply("مش متصل بالعربية")
                cl.setVehicleState(c.vendorName, if (open) 1 else 0, reply)
            }
        }
    }

    private fun safetyCheck(c: Control, s: VehicleState): String? {
        val st = _status.value
        return when {
            !controlsEnabled -> "التحكم مقفول. فعّله من الإعدادات ← لوحة العربية"
            !st.writeVerified -> st.writeProblem.ifBlank { "التحكم مش متأكد منه على النظام ده" }
            c.vendorName !in st.controlsAvailable -> "العربية دي مش بتدعم الأمر ده"
            s.speedKmh == null -> "مش عارفين سرعة العربية، فالتحكم مقفول للأمان"
            c == Control.TRUNK && s.speedKmh > 0 -> "وقّف العربية الأول عشان تفتح الشنطة"
            c == Control.TRUNK && s.gear != null && s.gear != VehicleState.Gear.P -> "حط الفتيس على P الأول"
            s.speedKmh > 0 && !allowWhileMoving -> "وقّف العربية الأول (أو فعّل \"التحكم أثناء الحركة\" من الإعدادات)"
            else -> null
        }
    }

    private fun simulate(c: Control, open: Boolean) {
        when (c) {
            Control.WINDOW_FL -> demoWindowTarget[0] = if (open) 1f else 0f
            Control.WINDOW_FR -> demoWindowTarget[1] = if (open) 1f else 0f
            Control.WINDOW_RL -> demoWindowTarget[2] = if (open) 1f else 0f
            Control.WINDOW_RR -> demoWindowTarget[3] = if (open) 1f else 0f
            Control.SUNROOF -> { demoSunroofTarget = if (open) 1f else 0f; if (open) demoTilt = false }
            Control.SUNROOF_TILT -> { demoTilt = open; if (open) demoSunroofTarget = 0f }
            Control.SHADE -> demoShade = open
            Control.TRUNK -> demoTrunk = open
        }
    }

    // ------------------------------------------------------------------
    // Demo: a loop that exercises every widget on the dashboard.
    // ------------------------------------------------------------------

    private suspend fun runDemo() {
        var t = 0.0
        var odo = 12480f
        val win = FloatArray(4)
        var sun = 0f
        while (scope.isActive) {
            val cycle = (t % 60).toInt()
            val parked = cycle < 10
            val speed = if (parked) 0 else max(0.0, 70 + 50 * sin(t / 9)).toInt()
            odo += speed / 3600f / 4f
            for (i in 0..3) win[i] = approach(win[i], demoWindowTarget[i], 0.08f)
            sun = approach(sun, demoSunroofTarget, 0.06f)
            _state.value = VehicleState(
                source = VehicleState.Source.DEMO,
                speedKmh = speed,
                rpm = if (speed == 0) 780 else 1200 + speed * 22,
                gear = if (parked) VehicleState.Gear.P else VehicleState.Gear.D,
                outsideTempC = 27,
                coolantTempC = 90,
                fuelPercent = 72f,
                fuelLitres = 36f,
                fuelCapacityLitres = 50f,
                rangeKm = 519f,
                odometerKm = odo,
                instantConsumption = if (speed == 0) 0.8f else 5.5f + (speed % 7) / 2f,
                avgConsumption = 6.9f,
                batteryVolts = 14.1f,
                handBrake = parked,
                steeringDeg = if (parked) 0f else (220 * sin(t / 3)).toFloat(),
                windows = VehicleState.Windows(
                    open = win.toList(), sunroofOpen = sun, sunroofTilt = demoTilt,
                    shadeOpen = demoShade, known = true
                ),
                doors = VehicleState.Doors(
                    frontLeft = cycle in 2..6,
                    rearRight = cycle in 4..6,
                    trunk = demoTrunk
                ),
                lights = VehicleState.Lights(
                    turnLeft = cycle in 20..26,
                    turnRight = cycle in 40..45,
                    hazard = cycle in 52..55,
                    lowBeam = true,
                    position = true,
                    highBeam = cycle in 30..35,
                    fogFront = cycle in 30..38,
                    brake = speed in 1..30,
                    reverse = false
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

    private fun approach(v: Float, target: Float, step: Float) =
        if (v < target) min(target, v + step) else max(target, v - step)
}
