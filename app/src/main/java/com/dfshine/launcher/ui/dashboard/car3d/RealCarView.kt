package com.dfshine.launcher.ui.dashboard.car3d

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Choreographer
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.dfshine.launcher.vehicle.VehicleState
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.IndirectLight
import com.google.android.filament.LightManager
import com.google.android.filament.Renderer
import com.google.android.filament.Texture
import com.google.android.filament.View
import com.google.android.filament.android.UiHelper
import com.google.android.filament.utils.Manipulator
import com.google.android.filament.utils.ModelViewer
import com.google.android.filament.utils.Utils
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The real car (rigged glTF of the Aeolus Shine/Yixuan) rendered with
 * Google's Filament PBR engine. Same contract as [Car3DView]: drag to orbit,
 * pinch to zoom, double-tap to reset, tap a part to get [onPartTap]; doors,
 * trunk, hood, steering and wheel spin follow [state].
 *
 * If Filament or the model can't be loaded, [onFailure] is called once so
 * the dashboard can fall back to the lightweight procedural model.
 */
@Composable
fun RealCarView(
    state: VehicleState,
    onPartTap: (Part) -> Unit,
    onFailure: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val tap by rememberUpdatedState(onPartTap)
    val fail by rememberUpdatedState(onFailure)
    val controller = remember { RealCarHost.obtain(context) }
    if (controller == null) {
        LaunchedEffect(Unit) { fail() }
        return
    }
    DisposableEffect(controller) {
        controller.onPartTap = { tap(it) }
        controller.onFailure = { fail() }
        onDispose {
            controller.onPartTap = {}
            controller.onFailure = {}
        }
    }
    AndroidView(
        modifier = modifier,
        // The same TextureView (and engine) is reused for the whole process:
        // leaving the page only removes it from the hierarchy. Destroying /
        // re-binding Filament on every navigation crashed natively.
        factory = { controller.textureView.also { (it.parent as? ViewGroup)?.removeView(it) } },
        update = { controller.target = state }
    )
}

/** Process-wide holder: one Filament engine + model + TextureView for the app's lifetime. */
private object RealCarHost {
    private var controller: RealCarController? = null
    private var failed = false

    fun obtain(context: Context): RealCarController? {
        if (failed) return null
        controller?.let { if (!it.broken) return it else { failed = true; return null } }
        return try {
            val app = context.applicationContext
            RealCarController(app, TextureView(app)).also { controller = it }
        } catch (t: Throwable) {
            Log.e("RealCarView", "Filament init failed", t)
            failed = true
            null
        }
    }
}

/** Owns the Filament engine, the loaded asset and the per-frame part animation. */
private class RealCarController(
    context: Context,
    val textureView: TextureView
) : Choreographer.FrameCallback {

    @Volatile var target: VehicleState = VehicleState()
    var onPartTap: (Part) -> Unit = {}
    var onFailure: () -> Unit = {}
    private var looping = false
    val broken: Boolean get() = destroyed

    private val engine: Engine
    private val viewer: ModelViewer
    private val manipulator: Manipulator
    private val uiHelper: UiHelper
    private val choreographer = Choreographer.getInstance()
    private var destroyed = false
    private var lastFrameNs = 0L
    private var lastRenderNs = 0L
    private var renderedFrames = 0

    // Animated values (smoothed towards target each frame)
    private val doors = FloatArray(4)
    private val windows = FloatArray(4)
    private var sunOpen = 0f
    private var sunTilt = 0f
    private var trunk = 0f
    private var hood = 0f
    private var steer = 0f
    private var spin = 0f

    private class Rig(val entity: Int, val base: FloatArray)
    private val rig = HashMap<String, Rig>()
    private val partByEntity = HashMap<Int, Part>()

    init {
        CrashGuard.realModelStarting(context)
        Utils.init()
        engine = Engine.create()
        uiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK).apply { isOpaque = false }
        manipulator = Manipulator.Builder()
            .targetPosition(0f, -0.05f, -4f)
            .orbitHomePosition(3.3f, 1.0f, -0.6f)   // front three-quarter, driver (left) side
            .zoomSpeed(0.05f)
            .orbitSpeed(0.006f, 0.006f)
            .viewport(1, 1)
            .build(Manipulator.Mode.ORBIT)
        viewer = ModelViewer(textureView, engine, uiHelper, manipulator)
        textureView.isOpaque = false

        viewer.view.blendMode = View.BlendMode.TRANSLUCENT
        viewer.renderer.clearOptions = Renderer.ClearOptions().apply {
            clear = true
            clearColor = floatArrayOf(0f, 0f, 0f, 0f)
        }
        viewer.view.dynamicResolutionOptions = viewer.view.dynamicResolutionOptions.apply { enabled = false }
        viewer.view.antiAliasing = View.AntiAliasing.FXAA

        val bytes = context.assets.open(MODEL_ASSET).use { it.readBytes() }
        val buffer = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).put(bytes).also { it.rewind() }
        viewer.loadModelGlb(buffer)
        val asset = viewer.asset ?: throw IllegalStateException("model failed to load")
        // Frame the car ourselves: gltfio's bounding box ignores node transforms,
        // so transformToUnitCube() would mis-size a rigged model. The car is
        // CAR_LENGTH model units long, centred at (0, CAR_CENTER_Y, CAR_CENTER_Z).
        run {
            val k = 2f / CAR_LENGTH
            val tm0 = engine.transformManager
            tm0.setTransform(
                tm0.getInstance(asset.root),
                floatArrayOf(k, 0f, 0f, 0f, 0f, k, 0f, 0f, 0f, 0f, k, 0f, 0f, -k * CAR_CENTER_Y, -4f - k * CAR_CENTER_Z, 1f)
            )
        }
        // Slightly darker exposure than ModelViewer's default: keeps the paint from blowing out.
        viewer.camera.setExposure(16f, 1f / 125f, 80f)

        val tm = engine.transformManager
        for (name in RIG_NAMES) {
            val e = asset.getFirstEntityByName(name)
            if (e == 0) continue
            val inst = tm.getInstance(e)
            val base = FloatArray(16)
            tm.getTransform(inst, base)
            rig[name] = Rig(e, base)
        }
        for (e in asset.renderableEntities) {
            partByEntity[e] = when (asset.getName(e)) {
                "door_fl", "glass_fl" -> Part.DOOR_FL; "door_fr", "glass_fr" -> Part.DOOR_FR
                "door_rl", "glass_rl" -> Part.DOOR_RL; "door_rr", "glass_rr" -> Part.DOOR_RR
                "trunk", "trunk_glass" -> Part.TRUNK; "hood", "hood_glass" -> Part.HOOD
                "body_glass", "roof", "roof_cutout" -> Part.ROOF
                "sunroof", "sunroof_frame" -> Part.SUNROOF
                else -> Part.BODY
            }
        }
        if (rig.isEmpty()) Log.w(TAG, "model has no rig nodes; parts won't move")

        setupLights()
        installGestures()
        // Only animate/render while the view is on screen.
        textureView.addOnAttachStateChangeListener(object : android.view.View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: android.view.View) { startLoop() }
            override fun onViewDetachedFromWindow(v: android.view.View) { stopLoop() }
        })
    }

    private fun startLoop() {
        if (looping || destroyed) return
        looping = true
        lastFrameNs = 0L
        choreographer.postFrameCallback(this)
    }

    private fun stopLoop() {
        looping = false
        choreographer.removeFrameCallback(this)
        CrashGuard.realModelStopped(textureView.context)   // a clean exit, not a crash
    }

    private fun setupLights() {
        val scene = viewer.scene
        scene.indirectLight = StudioEnvironment.create(engine)
        // Key light (ModelViewer already adds one sun; add a cool rim light from behind).
        val rim = EntityManager.get().create()
        LightManager.Builder(LightManager.Type.DIRECTIONAL)
            .color(0.80f, 0.90f, 1.0f)
            .intensity(60_000f)
            .direction(-0.4f, -0.6f, 0.7f)
            .castShadows(false)
            .build(engine, rim)
        scene.addEntity(rim)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun installGestures() {
        val detector = GestureDetector(textureView.context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                manipulator.jumpToBookmark(manipulator.homeBookmark)
                return true
            }
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                val h = textureView.height
                if (destroyed) return true
                viewer.view.pick(e.x.toInt(), h - e.y.toInt(), Handler(Looper.getMainLooper())) { result ->
                    if (!destroyed) partByEntity[result.renderable]?.let(onPartTap)
                }
                return true
            }
        })
        textureView.setOnTouchListener { _, event ->
            detector.onTouchEvent(event)
            viewer.onTouchEvent(event)
            true
        }
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (destroyed || !looping) return
        choreographer.postFrameCallback(this)
        if (!uiHelper.isReadyToRender) return
        val dt = if (lastFrameNs == 0L) 0f else ((frameTimeNanos - lastFrameNs) / 1e9f).coerceAtMost(0.1f)
        lastFrameNs = frameTimeNanos
        animate(dt)
        // ~30 fps is plenty for a dashboard and keeps the head unit cool.
        if (frameTimeNanos - lastRenderNs < 30_000_000L) return
        lastRenderNs = frameTimeNanos
        try {
            viewer.render(frameTimeNanos)
            if (++renderedFrames == 300) CrashGuard.realModelStable(textureView.context)
        } catch (t: Throwable) {
            Log.e(TAG, "render failed", t)
            destroyed = true
            onFailure()
        }
    }

    private fun animate(dt: Float) {
        val s = target
        val k = 1f - exp(-dt * 5f)
        val d = s.doors
        val goals = floatArrayOf(b(d.frontLeft), b(d.frontRight), b(d.rearLeft), b(d.rearRight))
        for (i in 0..3) doors[i] += (goals[i] - doors[i]) * k
        val w = s.windows.open
        for (i in 0..3) windows[i] += (w.getOrElse(i) { 0f } - windows[i]) * min(1f, dt * 2.5f)
        trunk += (b(d.trunk) - trunk) * k
        sunOpen += (s.windows.sunroofOpen - sunOpen) * min(1f, dt * 1.5f)
        sunTilt += (b(s.windows.sunroofTilt) - sunTilt) * min(1f, dt * 2.5f)
        hood += (b(d.hood) - hood) * k
        val steerGoal = Math.toRadians(((s.steeringDeg ?: 0f) / 15f).coerceIn(-35f, 35f).toDouble()).toFloat()
        steer += (steerGoal - steer) * min(1f, dt * 8f)
        val kmh = (s.speedKmh ?: 0).toFloat()
        if (kmh > 0f) {
            val omega = kmh / 3.6f / 0.316f                       // real wheel rad/s
            spin += 9f * (1f - exp(-omega / 25f)) * dt            // eased so spokes never alias
            if (spin > 2 * PI) spin -= (2 * PI).toFloat()
        }

        val open = 1.0f // ~57°
        pose("door_fl", Mat.rotY(-open * doors[0]))
        pose("door_fr", Mat.rotY(open * doors[1]))
        pose("door_rl", Mat.rotY(-open * doors[2]))
        pose("door_rr", Mat.rotY(open * doors[3]))
        pose("trunk", Mat.rotX(-1.1f * trunk))
        pose("hood", Mat.rotX(-0.8f * hood))
        pose("steer_fl", Mat.rotY(steer))
        pose("steer_fr", Mat.rotY(steer))
        for (w in arrayOf("wheel_fl", "wheel_fr", "wheel_rl", "wheel_rr")) pose(w, Mat.rotX(spin))
        // Window glass slides straight down into its door (model units; the root is scaled to metres).
        GLASS.forEachIndexed { i, g -> pose(g, Mat.translateY(-WINDOW_DROP * windows[i])) }
        // Spoiler-type sunroof: pops up at the back, then slides rearwards over the roof.
        val pop = min(1f, sunOpen * 4f)
        pose("sunroof", Mat.mul(Mat.translate(0f, 0.012f * pop, -0.17f * sunOpen), Mat.rotX(0.12f * sunTilt + 0.04f * pop)))
        // The roof skin under the glass is hidden while open so the cabin shows through.
        pose("roof_cutout", Mat.scale(if (sunOpen > 0.02f) 0f else 1f))
    }

    private fun pose(name: String, local: FloatArray) {
        val r = rig[name] ?: return
        val tm = engine.transformManager
        tm.setTransform(tm.getInstance(r.entity), Mat.mul(r.base, local))
    }

    private fun b(v: Boolean) = if (v) 1f else 0f

    /**
     * Tear-down order matters: detach the UiHelper first (it destroys the
     * swap chain while the engine is still alive). Destroying the engine
     * first left the TextureView calling back into a dead engine when the
     * page was swiped away - a native crash that killed the whole launcher.
     */
    fun destroy() {
        if (destroyed) return
        destroyed = true
        choreographer.removeFrameCallback(this)
        textureView.setOnTouchListener(null)
        runCatching { uiHelper.detach() }.onFailure { Log.w(TAG, "detach", it) }
        runCatching { viewer.destroyModel() }.onFailure { Log.w(TAG, "destroyModel", it) }
        runCatching { engine.flushAndWait() }
        runCatching { engine.destroy() }.onFailure { Log.w(TAG, "engine.destroy", it) }
        CrashGuard.realModelStopped(textureView.context)
    }

    companion object {
        private const val TAG = "RealCarView"
        const val MODEL_ASSET = "models/shine.glb"
        private const val CAR_LENGTH = 1.175f
        private const val CAR_CENTER_Y = 0.20f
        private const val CAR_CENTER_Z = -0.009f
        private val RIG_NAMES = listOf(
            "door_fl", "door_fr", "door_rl", "door_rr", "trunk", "hood",
            "steer_fl", "steer_fr", "wheel_fl", "wheel_fr", "wheel_rl", "wheel_rr",
            "glass_fl", "glass_fr", "glass_rl", "glass_rr", "sunroof", "roof_cutout"
        )
        private val GLASS = arrayOf("glass_fl", "glass_fr", "glass_rl", "glass_rr")
        private const val WINDOW_DROP = 0.12f
    }
}

/** Minimal column-major 4x4 helpers (Filament's layout). */
private object Mat {
    fun mul(a: FloatArray, b: FloatArray): FloatArray {
        val out = FloatArray(16)
        for (c in 0..3) for (r in 0..3) {
            var s = 0f
            for (k in 0..3) s += a[k * 4 + r] * b[c * 4 + k]
            out[c * 4 + r] = s
        }
        return out
    }
    fun translateY(t: Float) = translate(0f, t, 0f)
    fun translate(x: Float, y: Float, z: Float) = floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, x, y, z, 1f)
    fun scale(s: Float) = floatArrayOf(s, 0f, 0f, 0f, 0f, s, 0f, 0f, 0f, 0f, s, 0f, 0f, 0f, 0f, 1f)
    fun rotY(a: Float): FloatArray {
        val c = cos(a); val s = sin(a)
        return floatArrayOf(c, 0f, -s, 0f, 0f, 1f, 0f, 0f, s, 0f, c, 0f, 0f, 0f, 0f, 1f)
    }
    fun rotX(a: Float): FloatArray {
        val c = cos(a); val s = sin(a)
        return floatArrayOf(1f, 0f, 0f, 0f, 0f, c, s, 0f, 0f, -s, c, 0f, 0f, 0f, 0f, 1f)
    }
}

/**
 * A procedural "photo studio" environment (dark teal room with bright
 * softbox strips), so the paint, glass and rims get believable reflections
 * without shipping an HDR file. Built once as a small cubemap and
 * prefiltered by Filament for roughness.
 */
private object StudioEnvironment {
    private const val SIZE = 64

    fun create(engine: Engine): IndirectLight {
        val faceBytes = SIZE * SIZE * 3 * 4
        val data = ByteBuffer.allocateDirect(faceBytes * 6).order(ByteOrder.nativeOrder())
        for (face in 0 until 6) {
            for (y in 0 until SIZE) for (x in 0 until SIZE) {
                val u = (x + 0.5f) / SIZE * 2f - 1f
                val v = (y + 0.5f) / SIZE * 2f - 1f
                val dir = faceDir(face, u, v)
                val c = radiance(dir)
                data.putFloat(c[0]); data.putFloat(c[1]); data.putFloat(c[2])
            }
        }
        data.rewind()

        val tex = Texture.Builder()
            .width(SIZE).height(SIZE)
            .levels(7)
            .sampler(Texture.Sampler.SAMPLER_CUBEMAP)
            .format(Texture.InternalFormat.R11F_G11F_B10F)
            .build(engine)
        val offsets = IntArray(6) { it * faceBytes }
        tex.generatePrefilterMipmap(
            engine,
            Texture.PixelBufferDescriptor(data, Texture.Format.RGB, Texture.Type.FLOAT),
            offsets,
            Texture.PrefilterOptions()
        )
        // No explicit irradiance: Filament then derives diffuse light from the
        // prefiltered reflections cubemap itself.
        return IndirectLight.Builder()
            .reflections(tex)
            .intensity(24_000f)
            .build(engine)
    }

    /** Cubemap face order +X, -X, +Y, -Y, +Z, -Z. */
    private fun faceDir(face: Int, u: Float, v: Float): FloatArray {
        val d = when (face) {
            0 -> floatArrayOf(1f, -v, -u)
            1 -> floatArrayOf(-1f, -v, u)
            2 -> floatArrayOf(u, 1f, v)
            3 -> floatArrayOf(u, -1f, -v)
            4 -> floatArrayOf(u, -v, 1f)
            else -> floatArrayOf(-u, -v, -1f)
        }
        val l = sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2])
        return floatArrayOf(d[0] / l, d[1] / l, d[2] / l)
    }

    private fun smooth(e0: Float, e1: Float, x: Float): Float {
        val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun radiance(d: FloatArray): FloatArray {
        val y = d[1]
        // Base: dark teal floor/walls, lighter towards the ceiling.
        val t = (y * 0.5f + 0.5f)
        var r = 0.03f + 0.10f * t
        var g = 0.06f + 0.16f * t
        var b = 0.07f + 0.18f * t
        // Big overhead softbox with a soft edge
        val top = smooth(0.55f, 0.95f, y)
        r += 2.2f * top; g += 2.3f * top; b += 2.4f * top
        // Two broad side panels (long, soft highlight along the body side)
        val az = kotlin.math.atan2(d[0], d[2])
        val band = smooth(0.0f, 0.25f, y) * (1f - smooth(0.45f, 0.7f, y))
        val side = max(1f - kotlin.math.abs(az - 1.2f) / 0.7f, 1f - kotlin.math.abs(az + 1.9f) / 0.6f).coerceAtLeast(0f)
        r += 1.1f * band * side; g += 1.2f * band * side; b += 1.25f * band * side
        // Horizon glow
        val hz = max(0f, 1f - kotlin.math.abs(y) * 6f)
        r += 0.25f * hz; g += 0.35f * hz; b += 0.38f * hz
        return floatArrayOf(r, g, b)
    }

}
