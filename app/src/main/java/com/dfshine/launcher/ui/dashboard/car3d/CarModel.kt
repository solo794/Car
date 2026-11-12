package com.dfshine.launcher.ui.dashboard.car3d

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** World space: metres, x = car's right(+)/left(-), y = up, z = forward(+). Origin on the ground under the car centre. */
data class V3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(o: V3) = V3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: V3) = V3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Float) = V3(x * s, y * s, z * s)
    fun cross(o: V3) = V3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
    fun dot(o: V3) = x * o.x + y * o.y + z * o.z
    fun len() = sqrt(dot(this))
    fun norm(): V3 { val l = len(); return if (l < 1e-6f) this else this * (1f / l) }
}

enum class Part { BODY, HOOD, TRUNK, DOOR_FL, DOOR_FR, DOOR_RL, DOOR_RR, ROOF, SUNROOF, WHEEL, LIGHT, INTERIOR }

/**
 * One flat polygon. [emissive] faces (lights) are not shaded. [bias] pulls a
 * face toward the camera in the depth sort, for decals lying on another face
 * (glass on an opening, a light on a bumper).
 */
class Face(
    val pts: List<V3>,
    val color: Color,
    val part: Part,
    val emissive: Boolean = false,
    val bias: Float = 0f,
    /** For lights: outward direction, used to draw a glow only when it faces the camera. */
    val glowNormal: V3? = null,
    val glowRadius: Float = 0f
)

/** Everything that moves on the model, already animated (0..1 fractions unless noted). */
data class CarPose(
    val doors: FloatArray = FloatArray(4),          // FL, FR, RL, RR opening
    val windows: FloatArray = FloatArray(4),        // FL, FR, RL, RR glass down
    val trunk: Float = 0f,
    val hood: Float = 0f,
    val sunroofOpen: Float = 0f,
    val sunroofTilt: Float = 0f,
    val steerRad: Float = 0f,                       // front-wheel yaw, + = left
    val wheelSpin: Float = 0f,                      // radians
    val lowBeam: Boolean = false,
    val highBeam: Boolean = false,
    val position: Boolean = false,
    val fogFront: Boolean = false,
    val fogRear: Boolean = false,
    val brake: Boolean = false,
    val reverse: Boolean = false,
    val leftSignal: Boolean = false,
    val rightSignal: Boolean = false,
    val bodyColor: Color = Color(0xFF6F8BA8)
)

/**
 * Builds the low-poly sedan for a given [CarPose]. Geometry is generated
 * from cross-section "stations" along the car's length, so the proportions
 * are easy to tune and every moving part (doors with their glass, trunk
 * lid, hood, sunroof panel, steered/spinning wheels) is its own set of
 * faces transformed around its real hinge.
 */
object CarModel {
    const val HALF_LEN = 2.30f
    private const val HW = 0.92f          // half width
    private const val BELT = 0.98f
    private const val WHEEL_R = 0.34f
    private const val WHEEL_Z = 1.38f
    private const val WHEEL_X = 0.80f
    private const val FRONT_DOOR_Z = 0.95f
    private const val B_PILLAR_Z = -0.15f
    private const val REAR_DOOR_END_Z = -0.95f
    private const val TRUNK_HINGE_Z = -1.25f

    private val GLASS = Color(0xFF1B2733)
    private val INTERIOR = Color(0xFF0A0D10)
    private val TIRE = Color(0xFF15171A)
    private val TIRE_HI = Color(0xFF24272B)
    private val RIM = Color(0xFFB9C2CA)
    private val RIM_DARK = Color(0xFF2A2F35)
    private val TRIM = Color(0xFF101418)
    private val AMBER = Color(0xFFFFB020)
    private val RED = Color(0xFFFF2D2D)
    private val WHITE = Color(0xFFF2FAFF)

    private val stations = floatArrayOf(
        2.30f, 2.22f, 2.05f, 1.80f, 1.62f, 1.38f, 1.14f, FRONT_DOOR_Z, 0.60f, 0.25f, B_PILLAR_Z,
        -0.55f, REAR_DOOR_END_Z, -1.14f, TRUNK_HINGE_Z, -1.38f, -1.62f, -1.80f, -2.05f, -2.22f, -2.30f
    )

    /** Greenhouse: z, roof height, roof half width, base half width. */
    private val green = arrayOf(
        floatArrayOf(0.95f, 0.99f, 0.78f, 0.86f),
        floatArrayOf(0.60f, 1.22f, 0.71f, 0.86f),
        floatArrayOf(0.25f, 1.40f, 0.66f, 0.86f),
        floatArrayOf(-0.15f, 1.44f, 0.66f, 0.86f),
        floatArrayOf(-0.55f, 1.43f, 0.66f, 0.86f),
        floatArrayOf(-0.95f, 1.30f, 0.68f, 0.86f),
        floatArrayOf(-1.25f, 1.02f, 0.76f, 0.84f)
    )

    private fun halfWidth(z: Float): Float {
        val e = abs(z) - 1.8f
        return if (e <= 0f) HW else HW - 0.16f * (e / 0.5f).pow(2)
    }

    private fun top(z: Float): Float = when {
        z >= FRONT_DOOR_Z -> BELT - 0.25f * ((z - FRONT_DOOR_Z) / (HALF_LEN - FRONT_DOOR_Z)).pow(1.5f)
        z <= TRUNK_HINGE_Z -> 1.02f - 0.07f * ((TRUNK_HINGE_Z - z) / (HALF_LEN + TRUNK_HINGE_Z)).pow(2)
        else -> BELT
    }

    private fun bottom(z: Float): Float {
        var b = 0.24f
        for (wc in floatArrayOf(WHEEL_Z, -WHEEL_Z)) {
            val d = abs(z - wc)
            if (d < 0.44f) b = max(b, 0.34f + sqrt(0.44f * 0.44f - d * d))
        }
        return b
    }

    /** Cross-section ring at z (10 points, left-bottom going over the top to right-bottom). */
    private fun ring(z: Float): List<V3> {
        val hw = halfWidth(z)
        val b = bottom(z)
        val t = top(z)
        val belt = if (z in TRUNK_HINGE_Z..FRONT_DOOR_Z) BELT else t - 0.03f
        val side = max(b + 0.05f, belt - 0.08f)
        val low = minOf(side, b + 0.10f)
        return listOf(
            V3(-hw * 0.94f, b, z), V3(-hw, low, z), V3(-hw, side, z), V3(-hw * 0.96f, belt, z), V3(-hw * 0.72f, t, z),
            V3(hw * 0.72f, t, z), V3(hw * 0.96f, belt, z), V3(hw, side, z), V3(hw, low, z), V3(hw * 0.94f, b, z)
        )
    }

    fun build(p: CarPose): Pair<List<Face>, List<Face>> {
        val faces = ArrayList<Face>(700)
        val ground = ArrayList<Face>(8)
        val body = p.bodyColor

        ground += shadow()
        if (p.lowBeam || p.highBeam) ground += beams(p.highBeam)

        // ---------------- Lower body ----------------
        val rings = stations.map { ring(it) }
        for (i in 0 until rings.size - 1) {
            val a = rings[i]
            val b = rings[i + 1]
            val zMid = (stations[i] + stations[i + 1]) / 2f
            for (j in a.indices) {
                val k = (j + 1) % a.size
                val quad = listOf(a[j], a[k], b[k], b[j])
                val leftSide = j in 0..2
                val rightSide = j in 6..8
                val topFace = j in 3..5
                val underside = j == 9
                val part = when {
                    underside -> Part.BODY
                    topFace && zMid > FRONT_DOOR_Z -> Part.HOOD
                    topFace && zMid < TRUNK_HINGE_Z -> Part.TRUNK
                    (leftSide || rightSide) && zMid in B_PILLAR_Z..FRONT_DOOR_Z && j != 0 && j != 9 ->
                        if (leftSide) Part.DOOR_FL else Part.DOOR_FR
                    (leftSide || rightSide) && zMid in REAR_DOOR_END_Z..B_PILLAR_Z && j != 0 && j != 9 ->
                        if (leftSide) Part.DOOR_RL else Part.DOOR_RR
                    else -> Part.BODY
                }
                val color = when {
                    underside -> TRIM
                    topFace && zMid in TRUNK_HINGE_Z..FRONT_DOOR_Z -> INTERIOR // floor under the cabin, seen through open windows
                    j == 0 || j == 8 -> body.darker(0.35f)                     // rocker
                    else -> body
                }
                faces += Face(transformPart(quad, part, p), color, part)
            }
        }
        // Door cavities - only while that door is open, otherwise they'd show through the closed panel.
        for ((idx, z0, z1) in listOf(
            Triple(0, FRONT_DOOR_Z, B_PILLAR_Z), Triple(1, FRONT_DOOR_Z, B_PILLAR_Z),
            Triple(2, B_PILLAR_Z, REAR_DOOR_END_Z), Triple(3, B_PILLAR_Z, REAR_DOOR_END_Z)
        )) {
            if (p.doors[idx] < 0.02f) continue
            run {
                val sx = if (idx % 2 == 0) -1f else 1f
                val x = sx * (HW - 0.06f)
                faces += Face(
                    listOf(V3(x, 0.32f, z0), V3(x, BELT, z0), V3(x, BELT, z1), V3(x, 0.32f, z1)),
                    INTERIOR, Part.INTERIOR, bias = -0.05f
                )
            }
        }
        // Front and rear caps (bumpers)
        faces += Face(rings.first(), body.darker(0.15f), Part.BODY)
        faces += Face(rings.last(), body.darker(0.15f), Part.BODY)
        // Grille
        faces += Face(
            listOf(V3(-0.42f, 0.42f, HALF_LEN + 0.004f), V3(-0.5f, 0.60f, HALF_LEN + 0.004f),
                V3(0.5f, 0.60f, HALF_LEN + 0.004f), V3(0.42f, 0.42f, HALF_LEN + 0.004f)),
            TRIM, Part.BODY, bias = 0.02f
        )

        // ---------------- Greenhouse ----------------
        for (i in 0 until green.size - 1) {
            val g0 = green[i]
            val g1 = green[i + 1]
            val z0 = g0[0]; val z1 = g1[0]
            val l0b = V3(-g0[3], BELT, z0); val l0t = V3(-g0[2], g0[1], z0)
            val l1b = V3(-g1[3], BELT, z1); val l1t = V3(-g1[2], g1[1], z1)
            val r0b = V3(g0[3], BELT, z0); val r0t = V3(g0[2], g0[1], z0)
            val r1b = V3(g1[3], BELT, z1); val r1t = V3(g1[2], g1[1], z1)

            // Roof / windshield / rear glass
            val roofColor = if (i <= 1 || i >= 4) GLASS else body
            val roofPart = if (i in 2..3) Part.ROOF else Part.BODY
            faces += Face(listOf(l0t, r0t, r1t, l1t), roofColor, roofPart)

            // Sides
            for (left in listOf(true, false)) {
                val q = if (left) listOf(l0b, l0t, l1t, l1b) else listOf(r0b, r0t, r1t, r1b)
                val windowIdx = when (i) {
                    1, 2 -> if (left) 0 else 1
                    3, 4 -> if (left) 2 else 3
                    else -> -1
                }
                if (windowIdx < 0) {
                    faces += Face(q, TRIM, Part.BODY) // A / C pillars
                    continue
                }
                val part = when (windowIdx) { 0 -> Part.DOOR_FL; 1 -> Part.DOOR_FR; 2 -> Part.DOOR_RL; else -> Part.DOOR_RR }
                val down = p.windows[windowIdx].coerceIn(0f, 1f)
                // Opening (interior) then the glass that is still up.
                faces += Face(transformPart(q, part, p), INTERIOR, part)
                if (down < 0.97f) {
                    val k = 1f - down
                    val glass = listOf(q[0], lerp(q[0], q[1], k), lerp(q[3], q[2], k), q[3])
                    faces += Face(transformPart(glass, part, p), GLASS.lighter(0.08f), part, bias = 0.01f)
                }
            }
        }
        // B-pillars
        for (sx in floatArrayOf(-1f, 1f)) {
            val x0 = sx * 0.86f; val x1 = sx * 0.66f
            faces += Face(
                listOf(V3(x0, BELT, B_PILLAR_Z + 0.04f), V3(x1, 1.44f, B_PILLAR_Z + 0.04f),
                    V3(x1, 1.44f, B_PILLAR_Z - 0.04f), V3(x0, BELT, B_PILLAR_Z - 0.04f)),
                TRIM, Part.BODY, bias = 0.02f
            )
        }

        sunroof(p, faces)
        mirrors(p, faces)
        wheels(p, faces)
        lights(p, faces)
        return faces to ground
    }

    // ------------------------------------------------------------------

    private fun transformPart(pts: List<V3>, part: Part, p: CarPose): List<V3> = when (part) {
        Part.DOOR_FL -> pts.map { swingDoor(it, -HW, FRONT_DOOR_Z, p.doors[0], left = true) }
        Part.DOOR_FR -> pts.map { swingDoor(it, HW, FRONT_DOOR_Z, p.doors[1], left = false) }
        Part.DOOR_RL -> pts.map { swingDoor(it, -HW, B_PILLAR_Z, p.doors[2], left = true) }
        Part.DOOR_RR -> pts.map { swingDoor(it, HW, B_PILLAR_Z, p.doors[3], left = false) }
        Part.TRUNK -> pts.map { lift(it, TRUNK_HINGE_Z, 1.02f, p.trunk * 1.2f, rearHinge = true) }
        Part.HOOD -> pts.map { lift(it, FRONT_DOOR_Z, BELT, p.hood * 0.9f, rearHinge = false) }
        else -> pts
    }

    /** Rotate around the vertical hinge at the door's front edge; the rear edge swings outward. */
    private fun swingDoor(v: V3, px: Float, pz: Float, open: Float, left: Boolean): V3 {
        if (open <= 0.001f) return v
        val a = 1.05f * open * (if (left) 1f else -1f)
        val dx = v.x - px
        val dz = v.z - pz
        val c = cos(a); val s = sin(a)
        return V3(px + dx * c + dz * s, v.y, pz - dx * s + dz * c)
    }

    /** Trunk: hinge at its front edge, rear rises. Hood: hinge at the windshield, front rises. */
    private fun lift(v: V3, hz: Float, hy: Float, angle: Float, rearHinge: Boolean): V3 {
        if (angle <= 0.001f) return v
        val a = if (rearHinge) angle else -angle
        val dy = v.y - hy
        val dz = v.z - hz
        val c = cos(a); val s = sin(a)
        return V3(v.x, hy + dy * c - dz * s, hz + dy * s + dz * c)
    }

    private fun sunroof(p: CarPose, out: MutableList<Face>) {
        val y = 1.445f
        val zf = 0.18f; val zr = -0.48f; val xw = 0.44f
        val open = p.sunroofOpen.coerceIn(0f, 1f)
        if (open > 0.02f) {
            out += Face(listOf(V3(-xw, y, zf), V3(xw, y, zf), V3(xw, y, zr), V3(-xw, y, zr)), INTERIOR, Part.SUNROOF, bias = 0.01f)
        }
        val slide = 0.55f * open
        val lift = 0.08f * p.sunroofTilt.coerceIn(0f, 1f)
        val gy = y + 0.012f + if (open > 0.02f) 0.03f else 0f
        out += Face(
            listOf(V3(-xw, gy, zf - slide), V3(xw, gy, zf - slide), V3(xw, gy + lift, zr - slide), V3(-xw, gy + lift, zr - slide)),
            GLASS.lighter(0.12f), Part.SUNROOF, bias = 0.02f
        )
    }

    private fun mirrors(p: CarPose, out: MutableList<Face>) {
        for (sx in floatArrayOf(-1f, 1f)) {
            val x0 = sx * 0.88f; val x1 = sx * 1.04f
            val y0 = 0.98f; val y1 = 1.08f; val z0 = 0.70f; val z1 = 0.56f
            val c = p.bodyColor.darker(0.1f)
            out += Face(listOf(V3(x0, y0, z0), V3(x1, y0, z0), V3(x1, y1, z0), V3(x0, y1, z0)), c, Part.BODY)
            out += Face(listOf(V3(x0, y0, z1), V3(x1, y0, z1), V3(x1, y1, z1), V3(x0, y1, z1)), TRIM, Part.BODY)
            out += Face(listOf(V3(x1, y0, z0), V3(x1, y0, z1), V3(x1, y1, z1), V3(x1, y1, z0)), c, Part.BODY)
            out += Face(listOf(V3(x0, y1, z0), V3(x1, y1, z0), V3(x1, y1, z1), V3(x0, y1, z1)), c.lighter(0.1f), Part.BODY)
            val on = if (sx < 0) p.leftSignal else p.rightSignal
            out += Face(
                listOf(V3(x1 + sx * 0.003f, y0 + 0.03f, z0), V3(x1 + sx * 0.003f, y0 + 0.03f, z1),
                    V3(x1 + sx * 0.003f, y0 + 0.05f, z1), V3(x1 + sx * 0.003f, y0 + 0.05f, z0)),
                if (on) AMBER else Color(0xFF4A3A20), Part.LIGHT, emissive = on, bias = 0.02f,
                glowNormal = if (on) V3(sx, 0f, 0f) else null, glowRadius = 0.25f
            )
        }
    }

    private fun wheels(p: CarPose, out: MutableList<Face>) {
        val n = 16
        val w = 0.115f
        for (wz in floatArrayOf(WHEEL_Z, -WHEEL_Z)) {
            val steer = if (wz > 0) -p.steerRad else 0f
            for (sx in floatArrayOf(-1f, 1f)) {
                val center = V3(sx * WHEEL_X, WHEEL_R, wz)
                fun local(lx: Float, r: Float, ang: Float): V3 {
                    // axle along x; rotate around axle by spin, then steer around y
                    val y = r * cos(ang)
                    val z = r * sin(ang)
                    val cs = cos(steer); val sn = sin(steer)
                    return center + V3(lx * cs + z * sn, y, -lx * sn + z * cs)
                }
                val outer = sx * w
                val inner = -sx * w
                // Tread (alternating shade so the spin is visible)
                for (i in 0 until n) {
                    val a0 = p.wheelSpin + i * (2f * Math.PI.toFloat() / n)
                    val a1 = a0 + 2f * Math.PI.toFloat() / n
                    out += Face(
                        listOf(local(outer, WHEEL_R, a0), local(outer, WHEEL_R, a1), local(inner, WHEEL_R, a1), local(inner, WHEEL_R, a0)),
                        if (i % 2 == 0) TIRE else TIRE_HI, Part.WHEEL
                    )
                }
                // Sidewall, rim, spokes (outer face)
                out += Face(List(n) { local(outer, WHEEL_R, it * 2f * Math.PI.toFloat() / n) }, TIRE, Part.WHEEL, bias = 0.005f)
                out += Face(List(n) { local(outer * 1.04f, 0.245f, it * 2f * Math.PI.toFloat() / n) }, RIM, Part.WHEEL, bias = 0.01f)
                for (s in 0 until 5) {
                    val a = p.wheelSpin + s * (2f * Math.PI.toFloat() / 5) + 0.63f
                    out += Face(
                        listOf(local(outer * 1.05f, 0.07f, a - 0.32f), local(outer * 1.05f, 0.21f, a - 0.42f),
                            local(outer * 1.05f, 0.21f, a + 0.42f), local(outer * 1.05f, 0.07f, a + 0.32f)),
                        RIM_DARK, Part.WHEEL, bias = 0.015f
                    )
                }
            }
        }
    }

    private fun lights(p: CarPose, out: MutableList<Face>) {
        val fz = HALF_LEN + 0.008f
        val rz = -HALF_LEN - 0.008f
        val front = V3(0f, 0f, 1f)
        val back = V3(0f, 0f, -1f)
        val headOn = p.lowBeam || p.highBeam
        val headColor = when {
            p.highBeam -> Color(0xFFD8ECFF)
            headOn -> WHITE
            p.position -> WHITE.copy(alpha = 0.75f)
            else -> Color(0xFF8A99A8)
        }
        for (sx in floatArrayOf(-1f, 1f)) {
            // Headlight strip
            out += quadX(sx * 0.46f, sx * 0.84f, 0.68f, 0.75f, fz, headColor, headOn || p.position, front, if (headOn) 0.55f else 0.25f)
            // Front turn signal
            val sig = if (sx < 0) p.leftSignal else p.rightSignal
            out += quadX(sx * 0.70f, sx * 0.84f, 0.62f, 0.67f, fz, if (sig) AMBER else Color(0xFF5A4A2A), sig, front, 0.4f)
            // Fog
            out += quadX(sx * 0.58f, sx * 0.76f, 0.30f, 0.35f, fz, if (p.fogFront) Color(0xFFFFE58A) else Color(0xFF3A3F45), p.fogFront, front, 0.35f)
            // Tail light corner + rear signal
            val tailOn = p.position || headOn || p.brake
            out += quadX(sx * 0.50f, sx * 0.86f, 0.86f, 0.93f, rz, if (p.brake) RED else if (tailOn) RED.copy(alpha = 0.7f) else Color(0xFF5A1E1E), tailOn, back, if (p.brake) 0.5f else 0.25f)
            out += quadX(sx * 0.70f, sx * 0.86f, 0.80f, 0.85f, rz, if (sig) AMBER else Color(0xFF4A3A20), sig, back, 0.4f)
        }
        // Light bars across front (DRL accent) and rear
        out += quadX(-0.46f, 0.46f, 0.735f, 0.75f, fz, if (headOn || p.position) WHITE else Color(0xFF6F7C88), headOn || p.position, front, 0f)
        out += quadX(-0.50f, 0.50f, 0.89f, 0.905f, rz, if (p.position || headOn || p.brake) RED else Color(0xFF5A1E1E), p.position || headOn || p.brake, back, 0f)
        out += quadX(-0.60f, 0.60f, 0.25f, 0.27f, fz, if (p.position) AMBER.copy(alpha = 0.9f) else Color(0xFF3A3F45), p.position, front, 0f)
        if (p.reverse) out += quadX(-0.12f, 0.12f, 0.80f, 0.86f, rz, WHITE, true, back, 0.5f)
        if (p.fogRear) out += quadX(-0.40f, -0.25f, 0.34f, 0.38f, rz, RED, true, back, 0.3f)
    }

    private fun quadX(x0: Float, x1: Float, y0: Float, y1: Float, z: Float, c: Color, on: Boolean, n: V3, glow: Float) =
        Face(
            listOf(V3(x0, y0, z), V3(x1, y0, z), V3(x1, y1, z), V3(x0, y1, z)), c, Part.LIGHT,
            emissive = on, bias = 0.03f, glowNormal = if (on && glow > 0f) n else null, glowRadius = glow
        )

    private fun shadow(): Face {
        val n = 28
        return Face(List(n) {
            val a = it * 2f * Math.PI.toFloat() / n
            V3(1.25f * cos(a), 0.002f, 2.75f * sin(a))
        }, Color.Black.copy(alpha = 0.45f), Part.BODY, emissive = true)
    }

    private fun beams(high: Boolean): List<Face> {
        val len = if (high) 7.5f else 4.0f
        val c = if (high) Color(0xFFBFE1FF) else Color(0xFFFFF6DC)
        return floatArrayOf(-1f, 1f).map { sx ->
            Face(
                listOf(
                    V3(sx * 0.48f, 0.004f, HALF_LEN + 0.1f), V3(sx * 0.84f, 0.004f, HALF_LEN + 0.1f),
                    V3(sx * 1.6f, 0.004f, HALF_LEN + len), V3(sx * 0.1f, 0.004f, HALF_LEN + len)
                ),
                c.copy(alpha = if (high) 0.40f else 0.30f), Part.LIGHT, emissive = true
            )
        }
    }

    private fun lerp(a: V3, b: V3, t: Float) = a + (b - a) * t
}

fun Color.darker(f: Float) = Color(red * (1 - f), green * (1 - f), blue * (1 - f), alpha)
fun Color.lighter(f: Float) = Color(red + (1 - red) * f, green + (1 - green) * f, blue + (1 - blue) * f, alpha)
