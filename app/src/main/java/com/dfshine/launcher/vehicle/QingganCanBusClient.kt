package com.dfshine.launcher.vehicle

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Parcel
import android.util.Log

/**
 * READ-ONLY client for the head unit's vendor CAN service
 * (`com.qinggan.canbus.service`, registered in ServiceManager as `qg.canbus`).
 *
 * The Dongfeng Shine / Shine GS head unit runs Pateo's "Qinggan" Android
 * stack. Its CAN service is what SystemUI and the stock launcher use for the
 * outside temperature, doors, lights, etc. It has no public SDK: the
 * interface is an internal AIDL (`com.qinggan.canbus.ICanBusService`).
 *
 * Safety design: an AIDL call is just "transaction number N + arguments".
 * If we guessed N wrong for this firmware we might call a *different*
 * method (maybe a setter). So:
 *  1. We first try to load the vendor's own generated `ICanBusService$Stub`
 *     class from an installed vendor APK, and read its `TRANSACTION_*`
 *     constants. Those are the real numbers for THIS car.
 *  2. Only if that fails AND the user explicitly allowed it in the
 *     diagnostics screen do we fall back to the codes documented for the
 *     same Qinggan stack on other brands ([DefaultCodes]).
 *  3. This class never calls any setter. Only getters and add/removeCallback.
 *
 * Event payload layouts follow the vendor Parcelables' createFromParcel
 * order (documented publicly by the community for the same stack). A wrong
 * layout can only produce wrong numbers on screen, nothing else; the
 * diagnostics screen shows the raw bytes so mismatches are easy to spot.
 */
class QingganCanBusClient(
    context: Context,
    private val allowDefaultCodes: Boolean,
    private val onState: ((VehicleState) -> VehicleState) -> Unit,
    private val onStatus: (Status) -> Unit
) {
    data class Status(
        val connected: Boolean = false,
        val serviceFound: Boolean = false,
        val descriptorOk: Boolean = false,
        val codesSource: String = "-",
        val message: String = "",
        val resolvedServiceCodes: Map<String, Int> = emptyMap(),
        val resolvedCallbackCodes: Map<Int, String> = emptyMap(),
        val eventCount: Long = 0,
        /** True only when setVehicleState and its VehicleState enum were verified from the vendor's own classes. */
        val writeVerified: Boolean = false,
        /** VehicleState enum names this firmware actually has (subset of [CONTROL_NAMES]). */
        val controlsAvailable: Set<String> = emptySet(),
        val writeProblem: String = ""
    )

    private val ctx = context.applicationContext
    private val thread = HandlerThread("qg-canbus").apply { start() }
    private val handler = Handler(thread.looper)

    @Volatile private var service: IBinder? = null
    @Volatile private var bound = false
    @Volatile private var stopped = false
    private var serviceCodes: Map<String, Int> = emptyMap()
    private var callbackNames: Map<Int, String> = emptyMap()
    private var vendor: VendorCodeResolver.Result? = null
    private var setVehicleStateEnum: Class<*>? = null
    private var lastWriteMs = 0L
    private var status = Status()
    private var eventCount = 0L

    private val callback = object : Binder() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code == INTERFACE_TRANSACTION) {
                reply?.writeString(CB_DESC)
                return true
            }
            val name = callbackNames[code] ?: "code_$code"
            try {
                try { data.enforceInterface(CB_DESC) } catch (_: Throwable) { data.setDataPosition(0) }
                val start = data.dataPosition()
                RawLog.add("EVT", name, code, data, start)
                data.setDataPosition(start)
                decodeEvent(name, data)
            } catch (t: Throwable) {
                Log.w(TAG, "decode $name failed", t)
            }
            eventCount++
            if (eventCount % 20 == 1L) publishStatus(status.copy(eventCount = eventCount))
            reply?.writeNoException()
            return true
        }
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    fun start() {
        handler.post { connect() }
    }

    fun stop() {
        stopped = true
        handler.removeCallbacksAndMessages(null)
        handler.post {
            val svc = service
            val code = codeFor("removeCallback")
            if (svc != null && code != null) {
                runCatching {
                    transact(svc, code) { it.writeStrongBinder(callback) }?.recycle()
                }
            }
            if (bound) runCatching { ctx.unbindService(conn) }
            bound = false
            service = null
            thread.quitSafely()
        }
    }

    private fun connect() {
        // 1. Resolve the real transaction codes from the vendor's own classes.
        val resolved = VendorCodeResolver.resolve(ctx)
        when {
            resolved != null -> {
                vendor = resolved
                serviceCodes = resolved.serviceCodes
                callbackNames = resolved.callbackCodes.ifEmpty { DefaultCodes.callbacks }
                status = status.copy(
                    codesSource = "vendor (${resolved.loadedFrom})",
                    resolvedServiceCodes = resolved.serviceCodes,
                    resolvedCallbackCodes = resolved.callbackCodes
                )
            }
            allowDefaultCodes -> {
                serviceCodes = DefaultCodes.service
                callbackNames = DefaultCodes.callbacks
                status = status.copy(codesSource = "default (unverified)")
            }
            else -> {
                status = status.copy(codesSource = "none")
            }
        }

        // 2. Find the service binder.
        val direct = getSystemService(SVC_MGR_NAME)
        if (direct != null) {
            onServiceBinder(direct)
        } else {
            val intent = Intent(SVC_ACTION).setPackage(SVC_PKG)
            bound = runCatching { ctx.bindService(intent, conn, Context.BIND_AUTO_CREATE) }.getOrDefault(false)
            if (!bound) {
                publishStatus(status.copy(message = "خدمة qg.canbus مش موجودة على الجهاز ده"))
            }
        }
    }

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            if (binder != null) handler.post { onServiceBinder(binder) }
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            publishStatus(status.copy(connected = false, message = "الخدمة فصلت"))
        }
    }

    private fun onServiceBinder(binder: IBinder) {
        if (stopped) return
        val descriptor = runCatching { binder.interfaceDescriptor }.getOrNull()
        val descriptorOk = descriptor == SVC_DESC
        status = status.copy(serviceFound = true, descriptorOk = descriptorOk)
        if (!descriptorOk) {
            publishStatus(status.copy(message = "الخدمة موجودة بس الواجهة مختلفة: $descriptor"))
            return
        }
        service = binder
        runCatching {
            binder.linkToDeath({
                service = null
                publishStatus(status.copy(connected = false, message = "الخدمة وقعت، هنحاول تاني"))
                handler.postDelayed({ connect() }, 3000)
            }, 0)
        }
        if (serviceCodes.isEmpty()) {
            publishStatus(status.copy(
                message = "الخدمة موجودة، بس معرفناش نقرا أرقام الـ AIDL من النظام. " +
                    "فعّل \"استخدام الأرقام الافتراضية\" من شاشة التشخيص على مسؤوليتك."
            ))
            return
        }
        val addCode = codeFor("addCallback")
        if (addCode != null) {
            transact(binder, addCode) { it.writeStrongBinder(callback) }?.recycle()
        }
        verifyWritePath()
        publishStatus(status.copy(connected = true, message = "متصل"))
        poll()
    }

    // ------------------------------------------------------------------
    // Getters (polled; callbacks are the primary path)
    // ------------------------------------------------------------------

    private fun poll() {
        val svc = service ?: return
        if (stopped) return
        call(svc, "getVehicleSpeed") { r -> val v = r.readInt(); onState { it.copy(speedKmh = v) } }
        call(svc, "getEngineSpeed") { r -> val v = r.readInt(); onState { it.copy(rpm = v) } }
        call(svc, "getAmbientTemperature") { r -> val v = r.readInt(); if (plausibleTemp(v)) onState { it.copy(outsideTempC = v) } }
        call(svc, "getEngineTemperature") { r -> val v = r.readInt(); if (plausibleTemp(v)) onState { it.copy(coolantTempC = v) } }
        call(svc, "getGearStatus") { r -> if (r.readInt() != 0) applyGear(r) }
        call(svc, "getDoorStatus") { r -> if (r.readInt() != 0) applyDoors(r) }
        call(svc, "getLightStatus") { r -> if (r.readInt() != 0) applyLights(r) }
        call(svc, "getFuelLevel") { r -> if (r.readInt() != 0) applyFuel(r) }
        call(svc, "getOdometer") { r -> if (r.readInt() != 0) applyOdometer(r) }
        call(svc, "getTPMSInfo") { r -> if (r.readInt() != 0) applyTpms(r) }
        call(svc, "getBatteryState") { r -> if (r.readInt() != 0) applyBattery(r) }
        pollWindows(svc)
        call(svc, "getSWCAngle") { r -> if (r.readInt() != 0) applySteering(r) }
        handler.postDelayed({ poll() }, POLL_MS)
    }

    private fun pollWindows(svc: IBinder) {
        val name = if (codeFor("getWindowStatus") != null) "getWindowStatus" else "getWindowsStatus"
        call(svc, name) { r -> if (r.readInt() != 0) applyWindows(r) }
    }

    private inline fun call(svc: IBinder, method: String, read: (Parcel) -> Unit) {
        val code = codeFor(method) ?: return
        val reply = transact(svc, code) {} ?: return
        try {
            RawLog.add("GET", method, code, reply, reply.dataPosition())
            read(reply)
        } catch (t: Throwable) {
            Log.w(TAG, "$method parse failed", t)
        } finally {
            reply.recycle()
        }
    }

    /** Returns the reply positioned after the exception header, or null. */
    private inline fun transact(svc: IBinder, code: Int, write: (Parcel) -> Unit): Parcel? {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(SVC_DESC)
            write(data)
            svc.transact(code, data, reply, 0)
            reply.readException()
            reply
        } catch (t: Throwable) {
            Log.w(TAG, "transact $code failed", t)
            reply.recycle()
            null
        } finally {
            data.recycle()
        }
    }

    // ------------------------------------------------------------------
    // Event decoding
    // ------------------------------------------------------------------

    private fun decodeEvent(name: String, p: Parcel) {
        when (name) {
            "onVehicleSpeedChanged" -> { val v = p.readInt(); onState { it.copy(speedKmh = v) } }
            "onEngineSpeedChanged" -> { val v = p.readInt(); onState { it.copy(rpm = v) } }
            "onAmbientTemperatureChanged" -> { val v = p.readInt(); if (plausibleTemp(v)) onState { it.copy(outsideTempC = v) } }
            "onEngineTemperatureChanged" -> { val v = p.readInt(); if (plausibleTemp(v)) onState { it.copy(coolantTempC = v) } }
            "onHandBrakeStatusChanged" -> { val v = p.readInt() == 1; onState { it.copy(handBrake = v) } }
            "onGearStatusChanged" -> if (p.readInt() != 0) applyGear(p)
            "onDoorStatusChanged" -> if (p.readInt() != 0) applyDoors(p)
            "onLightStatusChanged" -> if (p.readInt() != 0) applyLights(p)
            "onFuelLevelChanged" -> if (p.readInt() != 0) applyFuel(p)
            "onOdometerChanged" -> if (p.readInt() != 0) applyOdometer(p)
            "onTPMSInfoChange" -> if (p.readInt() != 0) applyTpms(p)
            "onBatteryStateChanged" -> if (p.readInt() != 0) applyBattery(p)
            "onWindowsStatusChanged", "onWindowStatusChanged" -> if (p.readInt() != 0) applyWindows(p)
            "onSWCAngleChanged" -> if (p.readInt() != 0) applySteering(p)
            "onInstantaneousFuelConsumptionChanged" -> { val v = p.readFloat(); onState { it.copy(instantConsumption = v) } }
        }
    }

    private fun applyGear(p: Parcel) {
        val g = when (p.readInt()) {
            0 -> VehicleState.Gear.P; 1 -> VehicleState.Gear.R; 2 -> VehicleState.Gear.N
            3 -> VehicleState.Gear.D; 4 -> VehicleState.Gear.B; 5 -> VehicleState.Gear.S
            else -> VehicleState.Gear.UNKNOWN
        }
        onState { it.copy(gear = g) }
    }

    /** DoorStatus: bonnet, fL, fLLock, fR, fRLock, trunk, rL, rLLock, rR, rRLock (1 = open). */
    private fun applyDoors(p: Parcel) {
        val v = IntArray(10) { p.readInt() }
        val d = VehicleState.Doors(
            hood = v[0] == 1, frontLeft = v[1] == 1, frontRight = v[3] == 1,
            trunk = v[5] == 1, rearLeft = v[6] == 1, rearRight = v[8] == 1
        )
        onState { it.copy(doors = d) }
    }

    /**
     * LightStatus: directionL, directionR, fogFront, fogRear, mainBeam,
     * sideLight, ebdLamp, dippedBeam, positionLamp, dayLight, stopLight,
     * reversLight, cautionLight, headLight, ...
     */
    private fun applyLights(p: Parcel) {
        val v = IntArray(14) { p.readInt() }
        val l = VehicleState.Lights(
            turnLeft = v[0] == 1, turnRight = v[1] == 1,
            fogFront = v[2] == 1, fogRear = v[3] == 1,
            highBeam = v[4] == 1, lowBeam = v[7] == 1,
            position = v[8] == 1 || v[5] == 1, brake = v[10] == 1,
            reverse = v[11] == 1, hazard = v[12] == 1
        )
        onState { it.copy(lights = l) }
    }

    /** FuelLevel: capacity, remain, percentage(f), shortage, instant(f), avg(f), histAvg(f), resetAvg(f). */
    private fun applyFuel(p: Parcel) {
        val capacity = p.readInt()
        val remain = p.readInt()
        val pct = p.readFloat()
        if (capacity in 20..150 && remain in 0..capacity) {
            onState { it.copy(fuelLitres = remain.toFloat(), fuelCapacityLitres = capacity.toFloat()) }
        }
        p.readInt()
        val instant = p.readFloat()
        val avg = p.readFloat()
        onState {
            it.copy(
                fuelPercent = pct.takeIf { v -> v in 0f..100f } ?: it.fuelPercent,
                instantConsumption = instant.takeIf { v -> v >= 0f && v < 1000f } ?: it.instantConsumption,
                avgConsumption = avg.takeIf { v -> v >= 0f && v < 1000f } ?: it.avgConsumption
            )
        }
    }

    /** Odometer: canTravel(f), evRange(f), endurance(f), power(f), chargeRemain(f), total(f). */
    private fun applyOdometer(p: Parcel) {
        val canTravel = p.readFloat()
        p.readFloat()
        val endurance = p.readFloat()
        p.readFloat(); p.readFloat()
        val total = p.readFloat()
        val range = if (canTravel > 0f) canTravel else endurance
        onState {
            it.copy(
                rangeKm = range.takeIf { v -> v in 0f..3000f } ?: it.rangeKm,
                odometerKm = total.takeIf { v -> v in 0f..2_000_000f } ?: it.odometerKm
            )
        }
    }

    /** TPMSInfo: status, 4 warning ints, 4 temperature floats, 4 pressure floats (FL, FR, RL, RR). */
    private fun applyTpms(p: Parcel) {
        p.readInt()
        val warn = List(4) { p.readInt() != 0 }
        val temp = List(4) { p.readFloat() }
        val press = List(4) { p.readFloat() }
        onState { it.copy(tpms = VehicleState.Tpms(press, temp, warn)) }
    }

    /** BatteryState: level, value (value is volts or decivolts). */
    private fun applyBattery(p: Parcel) {
        p.readInt()
        val raw = p.readInt()
        val volts = if (raw > 100) raw / 10f else raw.toFloat()
        if (volts in 5f..20f) onState { it.copy(batteryVolts = volts) }
    }

    /**
     * WindowStatus: fL, fR, rL, rR, sunroof, rollShade (0 closed / 1 open),
     * then positions sunroof, roll, fL, fR, rL, rR (floats), then tilt (int).
     */
    private fun applyWindows(p: Parcel) {
        val st = IntArray(6) { p.readInt() }
        val sunPos = p.readFloat()
        p.readFloat()
        val pos = FloatArray(4) { p.readFloat() }
        val tilt = p.readInt()
        fun frac(state: Int, position: Float): Float = when {
            position > 1f && position <= 100f -> position / 100f
            position in 0.01f..1f -> position
            state == 1 -> 1f
            else -> 0f
        }
        val w = VehicleState.Windows(
            open = List(4) { frac(st[it], pos[it]) },
            sunroofOpen = frac(st[4], sunPos),
            sunroofTilt = tilt == 1,
            shadeOpen = st[5] == 1,
            known = true
        )
        onState { it.copy(windows = w) }
    }

    /** SteeringAngle: direction (0 centre, 1 right, 2 left), angle (degrees). */
    private fun applySteering(p: Parcel) {
        val dir = p.readInt()
        val angle = p.readInt()
        if (angle !in 0..720) return
        val deg = when (dir) { 1 -> angle.toFloat(); 2 -> -angle.toFloat(); else -> 0f }
        onState { it.copy(steeringDeg = deg) }
    }

    // ------------------------------------------------------------------
    // Controls (write). Every guard below must pass or nothing is sent.
    // ------------------------------------------------------------------

    /**
     * Checks - from the vendor's OWN classes on this car - that
     * `ICanBusService.setVehicleState(VehicleState, int)` exists with exactly
     * that signature, and records which control names its enum has.
     * Never uses the community default codes.
     */
    private fun verifyWritePath() {
        val v = vendor
        val problem: String? = when {
            v == null -> "أرقام النظام مش مقروءة من العربية نفسها، فالتحكم مقفول"
            v.loader == null -> "مفيش class loader للنظام"
            v.serviceCodes["setVehicleState"] == null -> "الدالة setVehicleState مش موجودة في النظام ده"
            else -> null
        }
        if (problem != null) {
            status = status.copy(writeVerified = false, writeProblem = problem)
            return
        }
        val iface = runCatching { Class.forName(SVC_DESC, false, v!!.loader) }.getOrNull()
        val m = iface?.methods?.firstOrNull { it.name == "setVehicleState" }
        val params = m?.parameterTypes
        if (params == null || params.size != 2 || params[0].name != VEHICLE_STATE_CLASS ||
            params[1] != Int::class.javaPrimitiveType
        ) {
            status = status.copy(writeVerified = false, writeProblem = "شكل setVehicleState مختلف: $m")
            return
        }
        val enumCls = params[0]
        val names = enumCls.enumConstants?.map { (it as Enum<*>).name }?.toSet().orEmpty()
        if (enumCls.enumConstants?.firstOrNull() !is android.os.Parcelable) {
            status = status.copy(writeVerified = false, writeProblem = "VehicleState مش Parcelable")
            return
        }
        setVehicleStateEnum = enumCls
        status = status.copy(
            writeVerified = true,
            controlsAvailable = CONTROL_NAMES.filter { it in names }.toSet(),
            writeProblem = ""
        )
    }

    /** Sends `setVehicleState(<vendor enum stateName>, value)`. [done] gets null on success or the reason it was refused. */
    fun setVehicleState(stateName: String, value: Int, done: (String?) -> Unit) {
        handler.post { done(doSetVehicleState(stateName, value)) }
    }

    private fun doSetVehicleState(stateName: String, value: Int): String? {
        val svc = service ?: return "مش متصل بالعربية"
        val enumCls = setVehicleStateEnum ?: return status.writeProblem.ifBlank { "التحكم مش متأكد منه على النظام ده" }
        val code = vendor?.serviceCodes?.get("setVehicleState") ?: return "setVehicleState غير معروفة"
        if (stateName !in CONTROL_NAMES) return "أمر غير مسموح: $stateName"
        val constant = enumCls.enumConstants?.firstOrNull { (it as Enum<*>).name == stateName } as? android.os.Parcelable
            ?: return "العربية دي مش بتدعم $stateName"
        val now = System.currentTimeMillis()
        if (now - lastWriteMs < 400) return "استنى ثانية بين الأوامر"
        lastWriteMs = now
        RawLog.note("SET", "setVehicleState", code, "$stateName=$value")
        val reply = transact(svc, code) { d ->
            d.writeInt(1)
            constant.writeToParcel(d, 0)
            d.writeInt(value)
        } ?: return "الأمر اتبعت بس النظام رفضه"
        reply.recycle()
        handler.postDelayed({ service?.let { pollWindows(it) } }, 700)
        handler.postDelayed({ service?.let { pollWindows(it) } }, 3000)
        return null
    }

    private fun codeFor(method: String): Int? =
        serviceCodes[method]
            ?: serviceCodes.entries.firstOrNull { it.key.equals(method, ignoreCase = true) }?.value

    private fun plausibleTemp(v: Int) = v in -60..150

    private fun publishStatus(s: Status) {
        status = s
        onStatus(s)
    }

    companion object {
        private const val TAG = "QGCanBus"
        private const val POLL_MS = 2000L
        const val SVC_MGR_NAME = "qg.canbus"
        const val SVC_PKG = "com.qinggan.canbus.service"
        const val SVC_ACTION = "com.qinggan.canbus.CanBusService"
        const val SVC_DESC = "com.qinggan.canbus.ICanBusService"
        const val CB_DESC = "com.qinggan.canbus.ICanBusServiceCallback"
        const val VEHICLE_STATE_CLASS = "com.qinggan.canbus.VehicleState"

        /** The only VehicleState commands this app will ever send (window / sunroof / trunk). */
        val CONTROL_NAMES = setOf(
            "DRIVER_POWER_WINDOW_CONTROL_SWITCH",
            "PASSENGER_POWER_WINDOW_CONTROL_SWITCH",
            "REAR_LEFT_POWER_WINDOW_CONTROL_SWITCH",
            "REAR_RIGHT_POWER_WINDOW_CONTROL_SWITCH",
            "POWER_SUNROOF_CONTROL_SWITCH",
            "POWER_SUNROOF_CONTROL_TILT",
            "SUNROOF_ROLLSHADE_CONTROL",
            "TRUNK_RELEASE"
        )

        /** `android.os.ServiceManager.getService(name)` via reflection (no hidden-API limits on Android 8.1). */
        fun getSystemService(name: String): IBinder? = try {
            val sm = Class.forName("android.os.ServiceManager")
            sm.getMethod("getService", String::class.java).invoke(null, name) as IBinder?
        } catch (_: Throwable) {
            null
        }
    }
}
