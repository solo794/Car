package com.dfshine.launcher.ui.dashboard.car3d

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import com.dfshine.launcher.vehicle.VehicleState
import kotlin.math.exp

/**
 * Interactive 3D car: drag to orbit 360°, pinch to zoom, double-tap to
 * reset the view, tap a part (door/window, trunk, roof) to get [onPartTap].
 * Doors, windows, trunk, hood, sunroof, steering, wheel spin and every lamp
 * follow [state] live.
 */
@Composable
fun Car3DView(
    state: VehicleState,
    bodyColor: Color,
    onPartTap: (Part) -> Unit,
    modifier: Modifier = Modifier
) {
    val d = state.doors
    val w = state.windows
    val anim = tween<Float>(700)
    val dFL by animateFloatAsState(if (d.frontLeft) 1f else 0f, anim, label = "dFL")
    val dFR by animateFloatAsState(if (d.frontRight) 1f else 0f, anim, label = "dFR")
    val dRL by animateFloatAsState(if (d.rearLeft) 1f else 0f, anim, label = "dRL")
    val dRR by animateFloatAsState(if (d.rearRight) 1f else 0f, anim, label = "dRR")
    val trunk by animateFloatAsState(if (d.trunk) 1f else 0f, anim, label = "trunk")
    val hood by animateFloatAsState(if (d.hood) 1f else 0f, anim, label = "hood")
    val wFL by animateFloatAsState(w.open.getOrElse(0) { 0f }, tween(400), label = "wFL")
    val wFR by animateFloatAsState(w.open.getOrElse(1) { 0f }, tween(400), label = "wFR")
    val wRL by animateFloatAsState(w.open.getOrElse(2) { 0f }, tween(400), label = "wRL")
    val wRR by animateFloatAsState(w.open.getOrElse(3) { 0f }, tween(400), label = "wRR")
    val sun by animateFloatAsState(w.sunroofOpen, tween(500), label = "sun")
    val tilt by animateFloatAsState(if (w.sunroofTilt) 1f else 0f, tween(500), label = "tilt")
    // Road-wheel angle ≈ steering-wheel angle / 15, clamped to a realistic lock.
    val steer by animateFloatAsState(
        Math.toRadians(((state.steeringDeg ?: 0f) / 15f).coerceIn(-35f, 35f).toDouble()).toFloat(),
        tween(250), label = "steer"
    )

    var yaw by remember { mutableFloatStateOf(DEFAULT_YAW) }
    var pitch by remember { mutableFloatStateOf(DEFAULT_PITCH) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var spin by remember { mutableFloatStateOf(0f) }
    var blinkOn by remember { mutableStateOf(false) }
    val items = remember { arrayOf<List<DrawItem>>(emptyList()) }

    val speedKmh by rememberUpdatedState(state.speedKmh ?: 0)
    val signalling by rememberUpdatedState(state.lights.turnLeft || state.lights.turnRight || state.lights.hazard)

    // Animation clock: wheel spin follows speed; turn signals blink. ~30 fps, idle when nothing moves.
    LaunchedEffect(Unit) {
        var last = 0L
        var blinkAcc = 0f
        var frameAcc = 0f
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else ((now - last) / 1e9f).coerceAtMost(0.1f)
                last = now
                frameAcc += dt
                if (signalling) {
                    blinkAcc += dt
                    if (blinkAcc >= 0.42f) { blinkAcc = 0f; blinkOn = !blinkOn }
                } else if (blinkOn) blinkOn = false
                if (speedKmh > 0 && frameAcc >= 1f / 30f) {
                    val omega = speedKmh / 3.6f / 0.34f             // real rad/s
                    val visual = 9f * (1f - exp(-omega / 25f))      // eased so spokes never alias
                    spin += visual * frameAcc
                    frameAcc = 0f
                }
            }
        }
    }

    val l = state.lights
    val pose = CarPose(
        doors = floatArrayOf(dFL, dFR, dRL, dRR),
        windows = floatArrayOf(wFL, wFR, wRL, wRR),
        trunk = trunk, hood = hood,
        sunroofOpen = sun, sunroofTilt = tilt,
        steerRad = steer, wheelSpin = spin,
        lowBeam = l.lowBeam, highBeam = l.highBeam, position = l.position,
        fogFront = l.fogFront, fogRear = l.fogRear, brake = l.brake, reverse = l.reverse,
        leftSignal = (l.turnLeft || l.hazard) && blinkOn,
        rightSignal = (l.turnRight || l.hazard) && blinkOn,
        bodyColor = bodyColor
    )

    Canvas(
        modifier
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, gestureZoom, _ ->
                    yaw = (yaw - pan.x * 0.35f) % 360f
                    pitch = (pitch + pan.y * 0.25f).coerceIn(2f, 75f)
                    zoom = (zoom * gestureZoom).coerceIn(0.6f, 2.2f)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { yaw = DEFAULT_YAW; pitch = DEFAULT_PITCH; zoom = 1f },
                    onTap = { pos -> Projector.hit(items[0], pos.x, pos.y)?.let(onPartTap) }
                )
            }
    ) {
        val (faces, ground) = CarModel.build(pose)
        val list = Projector.project(faces, ground, yaw, pitch, zoom, size.width, size.height)
        items[0] = list
        val path = Path()
        for (it in list) {
            path.reset()
            path.moveTo(it.xs[0], it.ys[0])
            for (i in 1 until it.xs.size) path.lineTo(it.xs[i], it.ys[i])
            path.close()
            drawPath(path, it.color)
        }
        for (it in list) {
            if (it.glowR <= 0f) continue
            val c = it.color
            drawCircle(
                Brush.radialGradient(
                    listOf(c.copy(alpha = 0.65f), c.copy(alpha = 0.18f), Color.Transparent),
                    center = Offset(it.glowX, it.glowY), radius = it.glowR
                ),
                radius = it.glowR, center = Offset(it.glowX, it.glowY)
            )
        }
    }
}

const val DEFAULT_YAW = -55f
const val DEFAULT_PITCH = 18f
