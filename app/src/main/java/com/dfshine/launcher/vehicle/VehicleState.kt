package com.dfshine.launcher.vehicle

/**
 * Snapshot of everything the dashboard shows. Every field is nullable:
 * null means "the car never told us" (the UI then shows "--") so we never
 * display a made-up number on the real car.
 */
data class VehicleState(
    val source: Source = Source.NONE,
    val speedKmh: Int? = null,
    val rpm: Int? = null,
    val gear: Gear? = null,
    val outsideTempC: Int? = null,
    val coolantTempC: Int? = null,
    val fuelPercent: Float? = null,
    /** Fuel left / tank size as reported by the car (litres on this platform). */
    val fuelLitres: Float? = null,
    val fuelCapacityLitres: Float? = null,
    /** Distance the car says it can still travel on the remaining fuel. */
    val rangeKm: Float? = null,
    val odometerKm: Float? = null,
    /** Raw instant consumption as reported by the CAN service (unit is vendor-defined). */
    val instantConsumption: Float? = null,
    val avgConsumption: Float? = null,
    val batteryVolts: Float? = null,
    val handBrake: Boolean? = null,
    /** Steering wheel angle in degrees, negative = left. */
    val steeringDeg: Float? = null,
    val windows: Windows = Windows(),
    val doors: Doors = Doors(),
    val lights: Lights = Lights(),
    val tpms: Tpms? = null,
    val lastUpdateMs: Long = 0L
) {
    enum class Source { NONE, LIVE, DEMO }

    enum class Gear(val label: String) { P("P"), R("R"), N("N"), D("D"), B("B"), S("S"), UNKNOWN("-") }

    data class Doors(
        val frontLeft: Boolean = false,
        val frontRight: Boolean = false,
        val rearLeft: Boolean = false,
        val rearRight: Boolean = false,
        val trunk: Boolean = false,
        val hood: Boolean = false
    ) {
        val anyOpen get() = frontLeft || frontRight || rearLeft || rearRight || trunk || hood
    }

    /** Opening per window, 0 = closed .. 1 = fully open (FL, FR, RL, RR) + sunroof. */
    data class Windows(
        val open: List<Float> = listOf(0f, 0f, 0f, 0f),
        val sunroofOpen: Float = 0f,
        val sunroofTilt: Boolean = false,
        val shadeOpen: Boolean = false,
        val known: Boolean = false
    ) {
        val anyOpen get() = open.any { it > 0.05f } || sunroofOpen > 0.05f || sunroofTilt
    }

    data class Lights(
        val turnLeft: Boolean = false,
        val turnRight: Boolean = false,
        val hazard: Boolean = false,
        val lowBeam: Boolean = false,
        val highBeam: Boolean = false,
        val fogFront: Boolean = false,
        val fogRear: Boolean = false,
        val position: Boolean = false,
        val brake: Boolean = false,
        val reverse: Boolean = false
    )

    /** Per-wheel tire pressure (kPa) and temperature (°C). Order: FL, FR, RL, RR. */
    data class Tpms(
        val pressureKpa: List<Float>,
        val tempC: List<Float>,
        val warning: List<Boolean>
    ) {
        val anyWarning get() = warning.any { it }
    }
}
