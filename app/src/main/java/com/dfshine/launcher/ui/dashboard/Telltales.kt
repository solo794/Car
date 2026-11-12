package com.dfshine.launcher.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Instrument-cluster style warning/indicator symbols, drawn as vectors (ISO 2575 look). */
enum class Telltale { TURN_LEFT, TURN_RIGHT, HAZARD, LOW_BEAM, HIGH_BEAM, FOG_FRONT, FOG_REAR, POSITION }

@Composable
fun TelltaleIcon(kind: Telltale, active: Boolean, activeColor: Color, size: Dp = 26.dp, modifier: Modifier = Modifier) {
    val color = if (active) activeColor else Dash.Off
    Canvas(modifier.size(size)) {
        if (active) {
            drawCircle(
                Brush.radialGradient(listOf(activeColor.copy(alpha = 0.35f), Color.Transparent)),
                radius = this.size.minDimension * 0.75f
            )
        }
        val s = this.size.minDimension
        val sw = s * 0.075f
        when (kind) {
            Telltale.TURN_LEFT -> arrow(color, left = true)
            Telltale.TURN_RIGHT -> arrow(color, left = false)
            Telltale.HAZARD -> {
                tri(color, 0.92f, Stroke(sw * 1.3f, join = StrokeJoin.Round))
                tri(color, 0.45f, Stroke(sw, join = StrokeJoin.Round))
            }
            Telltale.LOW_BEAM -> {
                lamp(color, facingLeft = true, sw = sw)
                beams(color, facingLeft = true, slope = 0.12f, sw = sw)
            }
            Telltale.HIGH_BEAM -> {
                lamp(color, facingLeft = true, sw = sw)
                beams(color, facingLeft = true, slope = 0f, sw = sw)
            }
            Telltale.FOG_FRONT -> {
                lamp(color, facingLeft = true, sw = sw)
                beams(color, facingLeft = true, slope = 0.12f, sw = sw)
                wave(color, x = s * 0.27f, sw = sw)
            }
            Telltale.FOG_REAR -> {
                scale(-1f, 1f) {
                    lamp(color, facingLeft = true, sw = sw)
                    beams(color, facingLeft = true, slope = 0f, sw = sw)
                    wave(color, x = s * 0.27f, sw = sw)
                }
            }
            Telltale.POSITION -> {
                // Two small lamps facing outwards with short rays.
                scale(0.62f, 0.62f, pivot = Offset(s * 0.22f, s * 0.5f)) { lamp(color, facingLeft = true, sw = sw * 1.5f); beams(color, true, 0f, sw * 1.5f, short = true) }
                scale(-0.62f, 0.62f, pivot = Offset(s * 0.78f, s * 0.5f)) { lamp(color, facingLeft = true, sw = sw * 1.5f); beams(color, true, 0f, sw * 1.5f, short = true) }
            }
        }
    }
}

private fun DrawScope.arrow(c: Color, left: Boolean) {
    val s = size.minDimension
    val p = Path().apply {
        moveTo(s * 0.06f, s * 0.5f)
        lineTo(s * 0.46f, s * 0.14f)
        lineTo(s * 0.46f, s * 0.34f)
        lineTo(s * 0.94f, s * 0.34f)
        lineTo(s * 0.94f, s * 0.66f)
        lineTo(s * 0.46f, s * 0.66f)
        lineTo(s * 0.46f, s * 0.86f)
        close()
    }
    if (left) drawPath(p, c) else scale(-1f, 1f) { drawPath(p, c) }
}

private fun DrawScope.tri(c: Color, k: Float, stroke: Stroke) {
    val s = size.minDimension
    val cx = s / 2f
    val h = s * 0.82f * k
    val top = s * 0.5f - h * 0.58f
    val p = Path().apply {
        moveTo(cx, top)
        lineTo(cx + h * 0.58f, top + h)
        lineTo(cx - h * 0.58f, top + h)
        close()
    }
    drawPath(p, c, style = stroke)
}

/** Lamp body: flat lens side + rounded back, lens facing left (towards x = 0). */
private fun DrawScope.lamp(c: Color, facingLeft: Boolean, sw: Float) {
    val s = size.minDimension
    val x0 = s * 0.50f
    val p = Path().apply {
        moveTo(x0, s * 0.20f)
        cubicTo(s * 0.98f, s * 0.20f, s * 0.98f, s * 0.80f, x0, s * 0.80f)
        close()
    }
    drawPath(p, c, style = Stroke(sw, join = StrokeJoin.Round))
}

private fun DrawScope.beams(c: Color, facingLeft: Boolean, slope: Float, sw: Float, short: Boolean = false) {
    val s = size.minDimension
    val x1 = s * 0.42f
    val x0 = if (short) s * 0.22f else s * 0.06f
    for (k in 0 until 4) {
        val y = s * (0.28f + k * 0.15f)
        drawLine(c, Offset(x1, y), Offset(x0, y + s * slope), sw, StrokeCap.Round)
    }
}

private fun DrawScope.wave(c: Color, x: Float, sw: Float) {
    val s = size.minDimension
    val p = Path().apply {
        moveTo(x, s * 0.12f)
        cubicTo(x + s * 0.08f, s * 0.30f, x - s * 0.08f, s * 0.45f, x, s * 0.55f)
        cubicTo(x + s * 0.08f, s * 0.68f, x - s * 0.08f, s * 0.80f, x, s * 0.90f)
    }
    drawPath(p, c, style = Stroke(sw, cap = StrokeCap.Round))
}

