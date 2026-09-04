package com.dfshine.launcher.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.dfshine.launcher.LauncherApp
import com.dfshine.launcher.MainActivity
import com.dfshine.launcher.R
import com.dfshine.launcher.data.Prefs
import com.dfshine.launcher.util.PermissionUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Draws the launcher's floating "Picture-in-Picture" tools on top of
 * whatever app is currently in the foreground: a mini music player that
 * controls the system-wide active media session, a small dashboard clock,
 * and a quick-launch bubble for the apps pinned in Settings.
 *
 * This uses [WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY] (needs
 * the "Display over other apps" permission) rather than embedding another
 * app's real window - Android does not let a normal (non-system) app host
 * another app's Activity inside its own overlay, so true window-in-window
 * PiP of an arbitrary third-party app is not possible without root or a
 * system-privileged launcher. This is the closest legitimate equivalent.
 */
class FloatingPipService : Service() {

    private lateinit var windowManager: WindowManager
    private var panelView: View? = null
    private var bubbleView: View? = null
    private var mode: Mode = Mode.NONE
    private var mediaController: MediaController? = null
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var wasInGpsApp = false
    private var panelPinnedUpperHalf = false

    enum class Mode { NONE, MUSIC, CLOCK, QUICK_LAUNCH }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        startForeground(NOTIFICATION_ID, buildForegroundNotification())
        startGpsWatcher()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW_MUSIC -> showPanel(Mode.MUSIC, pinnedUpperHalf = false)
            ACTION_SHOW_CLOCK -> showPanel(Mode.CLOCK, pinnedUpperHalf = false)
            ACTION_SHOW_QUICK_LAUNCH -> showPanel(Mode.QUICK_LAUNCH, pinnedUpperHalf = false)
            ACTION_HIDE -> collapseToBubble()
            ACTION_STOP -> stopSelf()
            else -> Unit
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        removeAllViews()
        serviceScope.cancel()
        super.onDestroy()
    }

    // ---------------------------------------------------------------
    // GPS foreground-app watcher: while the driver has the configured
    // navigation app open, pin the music widget above the map instead of
    // leaving it wherever it last was (and collapsed while nothing needs
    // it) - "فوق نص الشاشة لو ماشي على GPS".
    // ---------------------------------------------------------------

    private fun startGpsWatcher() {
        serviceScope.launch {
            while (isActive) {
                delay(2000)
                runCatching { checkGpsForeground() }
            }
        }
    }

    private fun checkGpsForeground() {
        val prefs = Prefs(this)
        if (!prefs.musicWidgetDuringGps) return
        val gpsPackage = prefs.gpsTargetApp?.substringBefore("/") ?: return
        if (!PermissionUtils.hasUsageAccess(this)) return

        val inGpsApp = currentForegroundPackage() == gpsPackage
        if (inGpsApp && !wasInGpsApp) {
            showPanel(Mode.MUSIC, pinnedUpperHalf = true)
        } else if (!inGpsApp && wasInGpsApp && mode == Mode.MUSIC) {
            collapseToBubble()
        }
        wasInGpsApp = inGpsApp
    }

    private fun currentForegroundPackage(): String? {
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        val events = usageStatsManager.queryEvents(end - 10_000, end)
        var lastForeground: String? = null
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                lastForeground = event.packageName
            }
        }
        return lastForeground
    }

    // ---------------------------------------------------------------
    // Panel lifecycle
    // ---------------------------------------------------------------

    private fun showPanel(newMode: Mode, pinnedUpperHalf: Boolean = panelPinnedUpperHalf) {
        mode = newMode
        panelPinnedUpperHalf = pinnedUpperHalf
        removeBubble()
        removePanel()

        val content: View = when (newMode) {
            Mode.MUSIC -> buildMusicPanel()
            Mode.CLOCK -> buildClockPanel()
            Mode.QUICK_LAUNCH -> buildQuickLaunchPanel()
            Mode.NONE -> return
        }

        val params = overlayParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            // Upper half of a tall portrait screen, clear of both the
            // status bar and (usually) the turn-by-turn banner most nav
            // apps draw at the very top.
            y = if (pinnedUpperHalf) (resources.displayMetrics.heightPixels * 0.18).toInt() else 200
        }

        makeDraggable(content, params) { removeAllViews() }
        panelView = content
        windowManager.addView(content, params)
    }

    private fun collapseToBubble() {
        removePanel()
        if (bubbleView != null) return

        val bubble = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_media_play)
            background = roundedBackground(SHINE_ACCENT, alpha = 235)
            setColorFilter(Color.WHITE)
            setPadding(dp(14), dp(14), dp(14), dp(14))
        }

        val params = overlayParams(dp(56), dp(56)).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 200
        }

        makeDraggable(bubble, params) { showPanel(if (mode == Mode.NONE) Mode.MUSIC else mode) }
        bubbleView = bubble
        windowManager.addView(bubble, params)
    }

    private fun removeAllViews() {
        removePanel()
        removeBubble()
    }

    private fun removePanel() {
        panelView?.let { runCatching { windowManager.removeView(it) } }
        panelView = null
        mediaController?.unregisterCallback(mediaCallback)
        mediaController = null
    }

    private fun removeBubble() {
        bubbleView?.let { runCatching { windowManager.removeView(it) } }
        bubbleView = null
    }

    // ---------------------------------------------------------------
    // Panel builders
    // ---------------------------------------------------------------

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = roundedBackground(Color.parseColor("#151B23"), alpha = 245)
        setPadding(dp(14), dp(10), dp(14), dp(14))
        elevation = dp(8).toFloat()
    }

    private fun header(title: String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(TextView(this@FloatingPipService).apply {
            text = title
            setTextColor(Color.WHITE)
            textSize = 13f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        addView(ImageButton(this@FloatingPipService).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            background = null
            setColorFilter(Color.parseColor("#8A94A0"))
            setOnClickListener { collapseToBubble() }
        })
    }

    private fun buildMusicPanel(): View {
        val root = card()
        root.addView(header("المشغل العائم"))

        val trackTitle = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 15f
            setPadding(0, dp(8), 0, dp(4))
            text = "لا يوجد تشغيل حالياً"
        }
        root.addView(trackTitle)

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        fun iconButton(res: Int, onClick: () -> Unit) = ImageButton(this).apply {
            setImageResource(res)
            background = null
            setColorFilter(Color.WHITE)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            setOnClickListener { onClick() }
        }

        val controller = activeMediaController()
        mediaController = controller
        controller?.registerCallback(mediaCallback)
        trackTitle.text = controller?.metadata
            ?.getString(android.media.MediaMetadata.METADATA_KEY_TITLE)
            ?: "لا يوجد تشغيل حالياً"

        controls.addView(iconButton(android.R.drawable.ic_media_previous) { controller?.transportControls?.skipToPrevious() })
        controls.addView(iconButton(android.R.drawable.ic_media_play) {
            val playing = controller?.playbackState?.state == PlaybackState.STATE_PLAYING
            if (playing) controller?.transportControls?.pause() else controller?.transportControls?.play()
        })
        controls.addView(iconButton(android.R.drawable.ic_media_next) { controller?.transportControls?.skipToNext() })

        root.addView(controls)
        return root
    }

    private fun buildClockPanel(): View {
        val root = card()
        root.addView(header("لوحة السرعة والوقت"))
        root.addView(TextClock(this).apply {
            format12Hour = "hh:mm a"
            format24Hour = null
            setTextColor(Color.WHITE)
            textSize = 30f
            setPadding(0, dp(6), 0, 0)
        })
        root.addView(TextClock(this).apply {
            format12Hour = "EEEE، d MMMM"
            format24Hour = null
            setTextColor(Color.parseColor("#8A94A0"))
            textSize = 13f
        })
        return root
    }

    private fun buildQuickLaunchPanel(): View {
        val root = card()
        root.addView(header("إطلاق سريع"))

        val prefs = Prefs(this)
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, 0)
        }

        val targets = prefs.dockApps.take(4).ifEmpty { listOf<String>() }
        if (targets.isEmpty()) {
            row.addView(TextView(this).apply {
                text = "لا توجد تطبيقات مثبتة في اللوحة السريعة بعد"
                setTextColor(Color.parseColor("#8A94A0"))
                textSize = 12f
            })
        } else {
            targets.forEach { componentKey ->
                val packageName = componentKey.substringBefore("/")
                row.addView(ImageButton(this).apply {
                    val icon = runCatching { packageManager.getApplicationIcon(packageName) }.getOrNull()
                    if (icon != null) setImageDrawable(icon) else setImageResource(android.R.drawable.sym_def_app_icon)
                    background = null
                    setPadding(dp(8), dp(8), dp(8), dp(8))
                    setOnClickListener {
                        packageManager.getLaunchIntentForPackage(packageName)?.let {
                            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            startActivity(it)
                        }
                    }
                })
            }
        }
        root.addView(row)
        return root
    }

    private val mediaCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            if (mode == Mode.MUSIC) showPanel(Mode.MUSIC)
        }
        override fun onMetadataChanged(metadata: android.media.MediaMetadata?) {
            if (mode == Mode.MUSIC) showPanel(Mode.MUSIC)
        }
    }

    private fun activeMediaController(): MediaController? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) return null
        return try {
            val manager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val listenerComponent = ComponentName(this, NotificationBadgeService::class.java)
            manager.getActiveSessions(listenerComponent).firstOrNull()
        } catch (securityException: SecurityException) {
            // Notification listener access not granted yet - Settings screen
            // guides the user to enable it.
            null
        }
    }

    // ---------------------------------------------------------------
    // Drag handling
    // ---------------------------------------------------------------

    private fun makeDraggable(view: View, params: WindowManager.LayoutParams, onTap: () -> Unit) {
        var startX = 0
        var startY = 0
        var startTouchX = 0f
        var startTouchY = 0f
        var moved = false

        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    startTouchX = event.rawX
                    startTouchY = event.rawY
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - startTouchX).toInt()
                    val dy = (event.rawY - startTouchY).toInt()
                    if (abs(dx) > 6 || abs(dy) > 6) moved = true
                    params.x = startX + dx
                    params.y = startY + dy
                    runCatching { windowManager.updateViewLayout(v, params) }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) onTap()
                    true
                }
                else -> false
            }
        }
    }

    // ---------------------------------------------------------------
    // Small view helpers
    // ---------------------------------------------------------------

    private fun overlayParams(width: Int, height: Int): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        return WindowManager.LayoutParams(
            width,
            height,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
    }

    private fun roundedBackground(color: Int, alpha: Int): android.graphics.drawable.Drawable {
        val drawable = android.graphics.drawable.GradientDrawable()
        drawable.cornerRadius = dp(16).toFloat()
        drawable.setColor(Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color)))
        return drawable
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun buildForegroundNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, LauncherApp.CHANNEL_FLOATING)
            .setContentTitle("الأدوات العائمة تعمل")
            .setContentText("لمس لإخفاء أو إظهار اللوحة العائمة")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(openApp)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 4201
        private val SHINE_ACCENT = Color.parseColor("#2FB6A6")

        const val ACTION_SHOW_MUSIC = "com.dfshine.launcher.action.SHOW_MUSIC"
        const val ACTION_SHOW_CLOCK = "com.dfshine.launcher.action.SHOW_CLOCK"
        const val ACTION_SHOW_QUICK_LAUNCH = "com.dfshine.launcher.action.SHOW_QUICK_LAUNCH"
        const val ACTION_HIDE = "com.dfshine.launcher.action.HIDE"
        const val ACTION_STOP = "com.dfshine.launcher.action.STOP"

        fun intent(context: Context, action: String): Intent =
            Intent(context, FloatingPipService::class.java).setAction(action)
    }
}
