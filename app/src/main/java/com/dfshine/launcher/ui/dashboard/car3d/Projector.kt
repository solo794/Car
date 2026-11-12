package com.dfshine.launcher.ui.dashboard.car3d

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** A face ready to paint: screen polygon + final (shaded) colour, in back-to-front order. */
class DrawItem(
    val xs: FloatArray,
    val ys: FloatArray,
    val color: Color,
    val part: Part,
    /** Glow (lights facing the camera): centre + radius in px, 0 = none. */
    val glowX: Float = 0f,
    val glowY: Float = 0f,
    val glowR: Float = 0f
)

/**
 * Tiny software 3D pipeline: orbit camera (yaw/pitch/zoom), perspective
 * projection, painter's-algorithm depth sort and flat Lambert shading.
 * Pure math (no Android drawing) so it can also be exercised off-device.
 */
object Projector {
    private const val CAM_DIST = 9f
    private const val TARGET_Y = 0.62f
    private val LIGHT = V3(-0.35f, 0.80f, 0.50f).norm()

    fun project(
        faces: List<Face>,
        ground: List<Face>,
        yawDeg: Float,
        pitchDeg: Float,
        zoom: Float,
        width: Float,
        height: Float
    ): List<DrawItem> {
        val yaw = Math.toRadians(yawDeg.toDouble()).toFloat()
        val pitch = Math.toRadians(pitchDeg.toDouble()).toFloat()
        val cy = cos(yaw); val sy = sin(yaw)
        val cp = cos(pitch); val sp = sin(pitch)
        val f = minOf(width * 1.55f, height * 1.9f) * zoom
        val cx0 = width / 2f
        val cy0 = height * 0.54f

        // World -> camera. World x is mirrored so +x (car's right in CarModel) renders on the car's right.
        fun cam(v: V3): V3 {
            val x = -v.x
            val y = v.y - TARGET_Y
            val x1 = x * cy + v.z * sy
            val z1 = -x * sy + v.z * cy
            val y2 = y * cp - z1 * sp
            val z2 = y * sp + z1 * cp
            return V3(x1, y2, z2)
        }
        fun dir(v: V3): V3 {
            val x = -v.x
            val x1 = x * cy + v.z * sy
            val z1 = -x * sy + v.z * cy
            return V3(x1, v.y * cp - z1 * sp, v.y * sp + z1 * cp)
        }

        class Tmp(val depth: Float, val item: DrawItem)

        fun toItem(face: Face, sortable: Boolean): Tmp? {
            val n = face.pts.size
            if (n < 3) return null
            val c = Array(n) { cam(face.pts[it]) }
            val xs = FloatArray(n)
            val ys = FloatArray(n)
            var depthSum = 0f
            for (i in 0 until n) {
                val d = CAM_DIST - c[i].z
                if (d < 0.3f) return null
                xs[i] = cx0 + f * c[i].x / d
                ys[i] = cy0 - f * c[i].y / d
                depthSum += d
            }
            val depth = depthSum / n - face.bias
            val color = if (face.emissive) face.color else {
                // Newell normal (robust for any polygon), two-sided lighting.
                var nx = 0f; var ny = 0f; var nz = 0f
                for (i in 0 until n) {
                    val a = c[i]; val b = c[(i + 1) % n]
                    nx += (a.y - b.y) * (a.z + b.z)
                    ny += (a.z - b.z) * (a.x + b.x)
                    nz += (a.x - b.x) * (a.y + b.y)
                }
                val nrm = V3(nx, ny, nz).norm()
                val lambert = abs(nrm.dot(LIGHT))
                val facing = abs(nrm.z)
                val k = 0.38f + 0.52f * lambert + 0.12f * facing
                val spec = if (lambert > 0.93f) (lambert - 0.93f) * 3.5f else 0f
                shade(face.color, k, spec)
            }
            var gx = 0f; var gy = 0f; var gr = 0f
            val gn = face.glowNormal
            if (gn != null && face.glowRadius > 0f && dir(gn).z > 0.12f) {
                gx = xs.average().toFloat()
                gy = ys.average().toFloat()
                gr = f * face.glowRadius / (depth + face.bias)
            }
            return Tmp(if (sortable) depth else Float.MAX_VALUE, DrawItem(xs, ys, color, face.part, gx, gy, gr))
        }

        val out = ArrayList<DrawItem>(faces.size + ground.size)
        ground.forEach { g -> toItem(g, sortable = false)?.let { out += it.item } }
        faces.mapNotNull { toItem(it, sortable = true) }
            .sortedByDescending { it.depth }
            .forEach { out += it.item }
        return out
    }

    private fun FloatArray.average(): Double = sum().toDouble() / size

    private fun shade(c: Color, k: Float, spec: Float): Color {
        fun ch(v: Float) = (v * k + spec).coerceIn(0f, 1f)
        return Color(ch(c.red), ch(c.green), ch(c.blue), c.alpha)
    }

    /** Topmost face under (x, y): items are back-to-front, so search from the end. */
    fun hit(items: List<DrawItem>, x: Float, y: Float): Part? {
        for (i in items.indices.reversed()) {
            val it = items[i]
            if (inside(it.xs, it.ys, x, y)) return it.part
        }
        return null
    }

    private fun inside(xs: FloatArray, ys: FloatArray, x: Float, y: Float): Boolean {
        var c = false
        var j = xs.size - 1
        for (i in xs.indices) {
            if ((ys[i] > y) != (ys[j] > y) && x < (xs[j] - xs[i]) * (y - ys[i]) / (ys[j] - ys[i]) + xs[i]) c = !c
            j = i
        }
        return c
    }
}
