package com.dfshine.launcher.vehicle

import android.content.Context
import android.util.Log
import java.lang.reflect.Modifier

/**
 * Reads the *real* AIDL transaction numbers of the vendor CAN service on
 * this exact firmware, by loading the vendor's own generated
 * `ICanBusService$Stub` / `ICanBusServiceCallback$Stub` classes and reading
 * their `TRANSACTION_<method>` constants (standard AIDL codegen).
 *
 * The Stub classes live inside the vendor APKs that implement or use the
 * service, so we try each of them with `createPackageContext(INCLUDE_CODE)`.
 */
object VendorCodeResolver {

    data class Result(
        val loadedFrom: String,
        /** method name -> transaction code, e.g. "getVehicleSpeed" -> 26 */
        val serviceCodes: Map<String, Int>,
        /** transaction code -> callback method name, e.g. 16 -> "onVehicleSpeedChanged" */
        val callbackCodes: Map<Int, String>,
        /** Class loader that holds the vendor's com.qinggan.canbus.* classes (for typed writes). */
        val loader: ClassLoader? = null
    ) {
        override fun toString() =
            "Result(from=$loadedFrom, service=${serviceCodes.size} codes, callbacks=${callbackCodes.size} codes)"
    }

    private const val TAG = "VendorCodeResolver"
    private const val SVC_STUB = "com.qinggan.canbus.ICanBusService\$Stub"
    private const val CB_STUB = "com.qinggan.canbus.ICanBusServiceCallback\$Stub"

    /** Packages that are likely to contain the AIDL stubs, most likely first. */
    val CANDIDATE_PACKAGES = listOf(
        "com.qinggan.canbus.service",
        "com.qinggan.app.launcher",
        "com.android.systemui",
        "com.qinggan.app.hvac",
        "com.qinggan.app.setting",
        "com.qinggan.app.vehicle"
    )

    @Volatile private var cached: Result? = null

    fun resolve(context: Context): Result? {
        cached?.let { return it }
        // A shared framework jar on the boot classpath would make the class visible directly.
        tryLoad("bootclasspath", javaClass.classLoader)?.let { cached = it; return it }

        val installed = runCatching {
            context.packageManager.getInstalledPackages(0).map { it.packageName }
        }.getOrDefault(emptyList())
        val extra = installed.filter { it.startsWith("com.qinggan.") && it !in CANDIDATE_PACKAGES }

        for (pkg in CANDIDATE_PACKAGES + extra) {
            val loader = runCatching {
                context.createPackageContext(
                    pkg, Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY
                ).classLoader
            }.getOrNull() ?: continue
            tryLoad(pkg, loader)?.let { cached = it; return it }
        }
        return null
    }

    private fun tryLoad(from: String, loader: ClassLoader?): Result? {
        if (loader == null) return null
        return try {
            val svc = readTransactionConstants(Class.forName(SVC_STUB, false, loader))
            if (svc.isEmpty()) return null
            val cb = runCatching {
                readTransactionConstants(Class.forName(CB_STUB, false, loader))
            }.getOrDefault(emptyMap())
            Log.i(TAG, "Resolved ${svc.size} service / ${cb.size} callback codes from $from")
            Result(from, svc, cb.entries.associate { (k, v) -> v to k }, loader)
        } catch (_: Throwable) {
            null
        }
    }

    private fun readTransactionConstants(stub: Class<*>): Map<String, Int> {
        val out = linkedMapOf<String, Int>()
        for (f in stub.declaredFields) {
            if (!Modifier.isStatic(f.modifiers) || f.type != Int::class.javaPrimitiveType) continue
            if (!f.name.startsWith("TRANSACTION_")) continue
            runCatching {
                f.isAccessible = true
                out[f.name.removePrefix("TRANSACTION_")] = f.getInt(null)
            }
        }
        return out
    }
}

/**
 * Codes documented by the community for the same Qinggan CAN service on
 * other SAIC-GM-Wuling / Baojun firmware. Used ONLY when the vendor classes
 * cannot be loaded and the user explicitly opted in, because on a different
 * firmware version these numbers may point at different methods.
 */
object DefaultCodes {
    val service: Map<String, Int> = mapOf(
        "getOdometer" to 1,
        "getDoorStatus" to 2,
        "getLightStatus" to 5,
        "getGearStatus" to 6,
        "getAmbientTemperature" to 7,
        "getFuelLevel" to 9,
        "getEngineTemperature" to 11,
        "getBatteryState" to 19,
        "getVehicleSpeed" to 26,
        "getEngineSpeed" to 27,
        "addCallback" to 28,
        "removeCallback" to 29,
        "getTPMSInfo" to 65
    )

    val callbacks: Map<Int, String> = mapOf(
        1 to "onDoorStatusChanged",
        4 to "onAirConditionChanged",
        9 to "onHandBrakeStatusChanged",
        10 to "onLightStatusChanged",
        12 to "onGearStatusChanged",
        13 to "onEngineSpeedChanged",
        14 to "onAmbientTemperatureChanged",
        16 to "onVehicleSpeedChanged",
        17 to "onFuelLevelChanged",
        19 to "onEngineTemperatureChanged",
        25 to "onOdometerChanged",
        26 to "onBatteryStateChanged",
        45 to "onTPMSInfoChange",
        48 to "onInstantaneousFuelConsumptionChanged"
    )
}
