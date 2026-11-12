package com.dfshine.launcher.ui.dashboard

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.dfshine.launcher.vehicle.VehicleState

private val Amber = Color(0xFFFFB020)
private val BrakeRed = Color(0xFFFF3B3B)
private val BeamWhite = Color(0xFFEAF6FF)
private val HighBeamBlue = Color(0xFF7FC8FF)
private val FogYellow = Color(0xFFFFE27A)

/**
 * Top-down view of a sedan (nose up), drawn in code so it scales to any
 * screen and needs no image assets. Shows doors/hood/trunk opening, head /
 * fog / brake / reverse lights and blinking turn signals.
 *
 * Exposes where the wheels are ([WHEEL_FRONT_Y], [WHEEL_REAR_Y], as a
 * fraction of the height) so the TPMS labels can line up with them.
 */
@Composable
fun CarTopView(
    state: VehicleState,
    bodyColor: Color,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val d = state.doors
    val l = state.lights
    val fl by animateFloatAsState(if (d.frontLeft) 1f else 0f, tween(600), label = "fl")
    val fr by animateFloatAsState(if (d.frontRight) 1f else 0f, tween(600), label = "fr")
    val rl by animateFloatAsState(if (d.rearLeft) 1f else 0f, tween(600), label = "rl")
    val rr by animateFloatAsState(if (d.rearRight) 1f else 0f, tween(600), label = "rr")
    val trunk by animateFloatAsState(if (d.trunk) 1f else 0f, tween(600), label = "trunk")
    val hood by animateFloatAsState(if (d.hood) 1f else 0f, tween(600), label = "hood")
    val low by animateFloatAsState(if (l.lowBeam || l.highBeam) 1f else 0f, tween(400), label = "low")
    val high by animateFloatAsState(if (l.highBeam) 1f else 0f, tween(400), label = "high")
    val blink by rememberInfiniteTransition(label = "blink").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(420), RepeatMode.Reverse), label = "blinkA"
    )
    val leftOn = (l.turnLeft || l.hazard) && blink > 0.5f
    val rightOn = (l.turnRight || l.hazard) && blink > 0.5f

    Canvas(modifier) {
        // Car body box: keep a realistic aspect ratio and center it.
        val bodyH = size.height * 0.78f
        val bodyW = (bodyH / 2.25f).coerceAtMost(size.width * 0.5f)
        val left = (size.width - bodyW) / 2f
        val top = size.height * 0.13f
        val body = Rect(left, top, left + bodyW, top + bodyH)

        drawBeams(body, low, high, l.fogFront)
        drawShadow(body)
        drawWheels(body)
        drawBody(body, bodyColor, hood, trunk, accent)
        drawCabin(body)
        drawMirrors(body, bodyColor, leftOn, rightOn)

        // Doors: front 30%..52%, rear 52%..71% of the length.
        drawDoor(body, isLeft = true, from = 0.30f, to = 0.52f, open = fl, color = bodyColor, accent = accent)
        drawDoor(body, isLeft = true, from = 0.52f, to = 0.71f, open = rl, color = bodyColor, accent = accent)
        drawDoor(body, isLeft = false, from = 0.30f, to = 0.52f, open = fr, color = bodyColor, accent = accent)
        drawDoor(body, isLeft = false, from = 0.52f, to = 0.71f, open = rr, color = bodyColor, accent = accent)

        drawLamps(body, l, low, leftOn, rightOn)
    }
}

const val WHEEL_FRONT_Y = 0.13f + 0.78f * 0.20f
const val WHEEL_REAR_Y = 0.13f + 0.78f * 0.79f

private fun DrawScope.drawShadow(b: Rect) {
    drawOval(
        brush = Brush.radialGradient(
            listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent),
            center = b.center, radius = b.height * 0.62f
        ),
        topLeft = Offset(b.left - b.width * 0.35f, b.top - b.height * 0.05f),
        size = Size(b.width * 1.7f, b.height * 1.1f)
    )
}

private fun DrawScope.drawWheels(b: Rect) {
    val ww = b.width * 0.13f
    val wh = b.height * 0.13f
    for (fy in listOf(0.20f, 0.79f)) {
        val cy = b.top + b.height * fy
        for (x in listOf(b.left - ww * 0.35f, b.right - ww * 0.65f)) {
            drawRoundRect(
                color = Color(0xFF0D0F12),
                topLeft = Offset(x, cy - wh / 2),
                size = Size(ww, wh),
                cornerRadius = CornerRadius(ww * 0.35f)
            )
        }
    }
}

private fun bodyPath(b: Rect): Path {
    val w = b.width
    val h = b.height
    return Path().apply {
        // Nose (top), slightly narrower than the cabin, curved bumper.
        moveTo(b.left + w * 0.18f, b.top + h * 0.012f)
        quadraticBezierTo(b.center.x, b.top - h * 0.012f, b.right - w * 0.18f, b.top + h * 0.012f)
        cubicTo(b.right - w * 0.02f, b.top + h * 0.03f, b.right, b.top + h * 0.10f, b.right, b.top + h * 0.18f)
        lineTo(b.right, b.bottom - h * 0.14f)
        cubicTo(b.right, b.bottom - h * 0.05f, b.right - w * 0.05f, b.bottom - h * 0.01f, b.right - w * 0.2f, b.bottom)
        quadraticBezierTo(b.center.x, b.bottom + h * 0.008f, b.left + w * 0.2f, b.bottom)
        cubicTo(b.left + w * 0.05f, b.bottom - h * 0.01f, b.left, b.bottom - h * 0.05f, b.left, b.bottom - h * 0.14f)
        lineTo(b.left, b.top + h * 0.18f)
        cubicTo(b.left, b.top + h * 0.10f, b.left + w * 0.02f, b.top + h * 0.03f, b.left + w * 0.18f, b.top + h * 0.012f)
        close()
    }
}

private fun DrawScope.drawBody(b: Rect, color: Color, hood: Float, trunk: Float, accent: Color) {
    val path = bodyPath(b)
    drawPath(
        path,
        Brush.horizontalGradient(
            listOf(color.darken(0.55f), color, color.lighten(0.25f), color, color.darken(0.55f)),
            startX = b.left, endX = b.right
        )
    )
    drawPath(path, Color.White.copy(alpha = 0.18f), style = Stroke(width = b.width * 0.012f))

    // Hood crease lines
    val h = b.height
    val w = b.width
    for (fx in listOf(0.32f, 0.68f)) {
        drawLine(
            Color.White.copy(alpha = 0.12f),
            Offset(b.left + w * fx, b.top + h * 0.05f),
            Offset(b.left + w * (0.5f + (fx - 0.5f) * 0.7f), b.top + h * 0.24f),
            strokeWidth = w * 0.01f
        )
    }

    // Hood open: lid lifted forward, opening highlighted.
    if (hood > 0.01f) {
        val lid = Rect(b.left + w * 0.1f, b.top + h * 0.02f, b.right - w * 0.1f, b.top + h * 0.25f)
        drawRoundRect(accent.copy(alpha = 0.35f * hood), lid.topLeft, lid.size, CornerRadius(w * 0.1f))
        drawRoundRect(
            color.lighten(0.2f), Offset(lid.left, lid.top - h * 0.06f * hood), lid.size,
            CornerRadius(w * 0.1f), alpha = 0.9f
        )
    }
    // Trunk open
    if (trunk > 0.01f) {
        val lid = Rect(b.left + w * 0.12f, b.bottom - h * 0.13f, b.right - w * 0.12f, b.bottom - h * 0.01f)
        drawRoundRect(accent.copy(alpha = 0.35f * trunk), lid.topLeft, lid.size, CornerRadius(w * 0.1f))
        drawRoundRect(
            color.lighten(0.2f), Offset(lid.left, lid.top + h * 0.06f * trunk), lid.size,
            CornerRadius(w * 0.1f), alpha = 0.9f
        )
    }
}

private fun DrawScope.drawCabin(b: Rect) {
    val w = b.width
    val h = b.height
    val glass = Brush.verticalGradient(
        listOf(Color(0xFF2B3A46), Color(0xFF0E151C)), startY = b.top + h * 0.27f, endY = b.top + h * 0.8f
    )
    // Windshield
    val ws = Path().apply {
        moveTo(b.left + w * 0.12f, b.top + h * 0.36f)
        quadraticBezierTo(b.center.x, b.top + h * 0.25f, b.right - w * 0.12f, b.top + h * 0.36f)
        lineTo(b.right - w * 0.17f, b.top + h * 0.45f)
        quadraticBezierTo(b.center.x, b.top + h * 0.415f, b.left + w * 0.17f, b.top + h * 0.45f)
        close()
    }
    drawPath(ws, glass)
    // Roof (panoramic glass)
    drawRoundRect(
        Brush.verticalGradient(listOf(Color(0xFF16212B), Color(0xFF0B1117))),
        topLeft = Offset(b.left + w * 0.17f, b.top + h * 0.455f),
        size = Size(w * 0.66f, h * 0.22f),
        cornerRadius = CornerRadius(w * 0.08f)
    )
    // Rear window
    val rw = Path().apply {
        moveTo(b.left + w * 0.17f, b.top + h * 0.685f)
        quadraticBezierTo(b.center.x, b.top + h * 0.67f, b.right - w * 0.17f, b.top + h * 0.685f)
        lineTo(b.right - w * 0.14f, b.top + h * 0.75f)
        quadraticBezierTo(b.center.x, b.top + h * 0.78f, b.left + w * 0.14f, b.top + h * 0.75f)
        close()
    }
    drawPath(rw, glass)
    // Glass reflection
    drawLine(
        Color.White.copy(alpha = 0.10f),
        Offset(b.left + w * 0.3f, b.top + h * 0.34f),
        Offset(b.left + w * 0.24f, b.top + h * 0.43f),
        strokeWidth = w * 0.03f
    )
}

private fun DrawScope.drawMirrors(b: Rect, color: Color, leftOn: Boolean, rightOn: Boolean) {
    val w = b.width
    val h = b.height
    val mw = w * 0.13f
    val mh = h * 0.035f
    val y = b.top + h * 0.355f
    drawOval(color.darken(0.2f), Offset(b.left - mw * 0.75f, y), Size(mw, mh))
    drawOval(color.darken(0.2f), Offset(b.right - mw * 0.25f, y), Size(mw, mh))
    if (leftOn) glow(Offset(b.left - mw * 0.5f, y + mh / 2), w * 0.12f, Amber)
    if (rightOn) glow(Offset(b.right + mw * 0.5f, y + mh / 2), w * 0.12f, Amber)
}

private fun DrawScope.drawDoor(
    b: Rect, isLeft: Boolean, from: Float, to: Float, open: Float, color: Color, accent: Color
) {
    val w = b.width
    val h = b.height
    val y0 = b.top + h * from
    val y1 = b.top + h * to
    val thick = w * 0.075f
    val x = if (isLeft) b.left else b.right - thick

    // Seam line always visible
    drawLine(Color.Black.copy(alpha = 0.35f), Offset(b.left + if (isLeft) 0f else w, y1), Offset(b.left + if (isLeft) thick * 1.6f else w - thick * 1.6f, y1), strokeWidth = w * 0.008f)
    if (open < 0.01f) return

    // Opening glow where the door was
    drawRect(accent.copy(alpha = 0.55f * open), Offset(x, y0), Size(thick, y1 - y0))
    // Door panel swinging out around its front hinge
    val pivot = Offset(if (isLeft) b.left else b.right, y0)
    rotate(degrees = (if (isLeft) 58f else -58f) * open, pivot = pivot) {
        val px = if (isLeft) b.left - thick * 0.2f else b.right - thick * 0.8f
        drawRoundRect(
            Brush.horizontalGradient(listOf(color.lighten(0.15f), color.darken(0.2f))),
            topLeft = Offset(px, y0),
            size = Size(thick, y1 - y0),
            cornerRadius = CornerRadius(thick * 0.4f)
        )
        drawRoundRect(
            accent,
            topLeft = Offset(px, y0),
            size = Size(thick, y1 - y0),
            cornerRadius = CornerRadius(thick * 0.4f),
            style = Stroke(width = w * 0.012f),
            alpha = open
        )
    }
}

private fun DrawScope.drawBeams(b: Rect, low: Float, high: Float, fog: Boolean) {
    val w = b.width
    val h = b.height
    if (low > 0.01f) {
        for (side in listOf(-1f, 1f)) {
            val sx = b.center.x + side * w * 0.33f
            val len = h * (0.13f + 0.1f * high)
            val beam = Path().apply {
                moveTo(sx - w * 0.06f, b.top + h * 0.02f)
                lineTo(sx + w * 0.06f, b.top + h * 0.02f)
                lineTo(sx + side * w * 0.1f + w * 0.2f, b.top - len)
                lineTo(sx + side * w * 0.1f - w * 0.2f, b.top - len)
                close()
            }
            val c = if (high > 0.5f) HighBeamBlue else BeamWhite
            drawPath(
                beam,
                Brush.verticalGradient(
                    listOf(Color.Transparent, c.copy(alpha = 0.45f * low)),
                    startY = b.top - len, endY = b.top + h * 0.02f
                )
            )
        }
    }
    if (fog) {
        for (side in listOf(-1f, 1f)) {
            glow(Offset(b.center.x + side * w * 0.38f, b.top + h * 0.03f), w * 0.2f, FogYellow)
        }
    }
}

private fun DrawScope.drawLamps(b: Rect, l: VehicleState.Lights, low: Float, leftOn: Boolean, rightOn: Boolean) {
    val w = b.width
    val h = b.height
    // Headlight strips
    val head = BeamWhite.copy(alpha = 0.35f + 0.65f * maxOf(low, if (l.position) 0.4f else 0f))
    for (side in listOf(-1f, 1f)) {
        val cx = b.center.x + side * w * 0.33f
        drawRoundRect(head, Offset(cx - w * 0.12f, b.top + h * 0.025f), Size(w * 0.24f, h * 0.012f), CornerRadius(h * 0.006f))
    }
    // Light bar across the tail
    val tailBright = if (l.brake) 1f else if (l.position || low > 0f) 0.55f else 0.25f
    drawRoundRect(
        BrakeRed.copy(alpha = tailBright),
        Offset(b.left + w * 0.12f, b.bottom - h * 0.02f), Size(w * 0.76f, h * 0.01f), CornerRadius(h * 0.005f)
    )
    if (l.brake) {
        glow(Offset(b.left + w * 0.2f, b.bottom), w * 0.25f, BrakeRed)
        glow(Offset(b.right - w * 0.2f, b.bottom), w * 0.25f, BrakeRed)
    }
    if (l.reverse) glow(Offset(b.center.x, b.bottom), w * 0.3f, BeamWhite)
    if (l.fogRear) glow(Offset(b.left + w * 0.35f, b.bottom), w * 0.18f, BrakeRed)
    // Turn signals at the 4 corners
    if (leftOn) {
        glow(Offset(b.left + w * 0.08f, b.top + h * 0.03f), w * 0.2f, Amber)
        glow(Offset(b.left + w * 0.08f, b.bottom - h * 0.02f), w * 0.2f, Amber)
    }
    if (rightOn) {
        glow(Offset(b.right - w * 0.08f, b.top + h * 0.03f), w * 0.2f, Amber)
        glow(Offset(b.right - w * 0.08f, b.bottom - h * 0.02f), w * 0.2f, Amber)
    }
}

private fun DrawScope.glow(center: Offset, radius: Float, color: Color) {
    drawCircle(
        Brush.radialGradient(listOf(color.copy(alpha = 0.95f), color.copy(alpha = 0.35f), Color.Transparent), center, radius),
        radius, center
    )
}

private fun Color.darken(f: Float) = Color(red * (1 - f), green * (1 - f), blue * (1 - f), alpha)
private fun Color.lighten(f: Float) = Color(red + (1 - red) * f, green + (1 - green) * f, blue + (1 - blue) * f, alpha)
