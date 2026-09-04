package com.dfshine.launcher.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.dfshine.launcher.LauncherApp
import com.dfshine.launcher.MainActivity
import com.dfshine.launcher.R
import com.dfshine.launcher.data.Prefs

/**
 * A fixed bar pinned to the bottom of the screen, drawn over whatever app
 * is currently in the foreground - unlike [FloatingPipService]'s bubble,
 * this one isn't meant to be dragged or dismissed by the driver; it stays
 * put as a constant AC + GPS shortcut row, similar to how these car head
 * units normally keep climate controls reachable no matter what app is
 * open.
 *
 * It cannot read or set the real AC temperature/fan state - there is no
 * public Android API for a vehicle's HVAC hardware, and Dongfeng doesn't
 * publish one for this unit. What it *can* do, honestly:
 *  - Jump straight to the OEM's own climate-control screen (configured in
 *    Settings), so it's one tap away from any app.
 *  - Send the exact HVAC command broadcasts in Settings -> Climate Bar,
 *    if this unit's vendor documents them (some Chinese head unit ROMs
 *    do) - in which case the +/-/power buttons work without leaving the
 *    current app at all.
 */
class PersistentNavBarService : Service() {

    private lateinit var windowManager: WindowManager
    private var barView: View? = null
    private var hvacPanel: View? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        startForeground(NOTIFICATION_ID, buildNotification())
        showBar()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) stopSelf()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        barView?.let { runCatching { windowManager.removeView(it) } }
        hvacPanel?.let { runCatching { windowManager.removeView(it) } }
        barView = null
        hvacPanel = null
        super.onDestroy()
    }

    private fun showBar() {
        val prefs = Prefs(this)

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = solidBackground(Color.parseColor("#0B0F14"), alpha = 250)
            setPadding(dp(8), dp(6), dp(8), dp(6))
        }

        bar.addView(navTile(android.R.drawable.ic_menu_myplaces, "الرئيسية") { goHome() })
        bar.addView(navTile(android.R.drawable.ic_menu_mapmode, "الملاحة") { launchGps(prefs) })
        bar.addView(
            navTile(android.R.drawable.ic_menu_manage, "التكييف") { onAcTap(prefs) },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.BOTTOM }

        barView = bar
        windowManager.addView(bar, params)
    }

    private fun navTile(icon: Int, label: String, onClick: () -> Unit): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(4), dp(10), dp(4))
            isClickable = true
            addView(ImageButton(this@PersistentNavBarService).apply {
                setImageResource(icon)
                background = null
                setColorFilter(Color.WHITE)
                setOnClickListener { onClick() }
            })
            addView(TextView(this@PersistentNavBarService).apply {
                text = label
                setTextColor(Color.parseColor("#F2F5F7"))
                textSize = 11f
                gravity = Gravity.CENTER
            })
        }
    }

    private fun goHome() {
        val intent = Intent(this, MainActivity::class.java).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        )
        startActivity(intent)
    }

    private fun launchGps(prefs: Prefs) {
        val component = prefs.gpsTargetApp
        if (component == null) {
            toast("اختر تطبيق الملاحة من الإعدادات ← GPS والملاحة")
            return
        }
        launchByPackage(component.substringBefore("/"), "تطبيق الملاحة غير مثبت")
    }

    private fun onAcTap(prefs: Prefs) {
        val hasCommands = listOf(
            prefs.hvacTempUpAction, prefs.hvacTempDownAction,
            prefs.hvacFanUpAction, prefs.hvacFanDownAction, prefs.hvacPowerToggleAction
        ).any { !it.isNullOrBlank() }

        if (hasCommands) {
            toggleHvacPanel(prefs)
        } else if (prefs.hvacTargetApp != null) {
            launchByPackage(prefs.hvacTargetApp!!.substringBefore("/"), "تطبيق التكييف غير موجود")
        } else {
            toast("اضبط شاشة أو أزرار التكييف من الإعدادات ← شريط التكييف")
        }
    }

    private fun launchByPackage(packageName: String, notInstalledMessage: String) {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        if (intent == null) {
            toast(notInstalledMessage)
            return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    private fun toggleHvacPanel(prefs: Prefs) {
        if (hvacPanel != null) {
            hvacPanel?.let { runCatching { windowManager.removeView(it) } }
            hvacPanel = null
            return
        }

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = solidBackground(Color.parseColor("#151B23"), alpha = 250)
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }

        fun cmdButton(label: String, action: String?) {
            if (action.isNullOrBlank()) return
            panel.addView(ImageButton(this).apply {
                setImageResource(android.R.drawable.ic_menu_send)
                contentDescription = label
                background = solidBackground(Color.parseColor("#2FB6A6"), alpha = 235)
                setColorFilter(Color.WHITE)
                setPadding(dp(14), dp(10), dp(14), dp(10))
                val margin = dp(6)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(margin, 0, margin, 0) }
                setOnClickListener { sendBroadcast(Intent(action)) }
            })
        }

        cmdButton("تبريد أكثر", prefs.hvacTempUpAction)
        cmdButton("تبريد أقل", prefs.hvacTempDownAction)
        cmdButton("مروحة أسرع", prefs.hvacFanUpAction)
        cmdButton("مروحة أبطأ", prefs.hvacFanDownAction)
        cmdButton("تشغيل/إيقاف", prefs.hvacPowerToggleAction)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM
            y = dp(70)
        }

        hvacPanel = panel
        windowManager.addView(panel, params)
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    private fun overlayType(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    } else {
        @Suppress("DEPRECATION")
        WindowManager.LayoutParams.TYPE_PHONE
    }

    private fun solidBackground(color: Int, alpha: Int): android.graphics.drawable.Drawable {
        val drawable = android.graphics.drawable.GradientDrawable()
        drawable.setColor(Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color)))
        return drawable
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, LauncherApp.CHANNEL_FLOATING)
            .setContentTitle("الشريط السفلي (التكييف والملاحة) يعمل")
            .setContentText("اضغط لفتح الإعدادات")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(openApp)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 4203
        const val ACTION_STOP = "com.dfshine.launcher.action.STOP_NAV_BAR"

        fun startIntent(context: Context): Intent = Intent(context, PersistentNavBarService::class.java)
        fun stopIntent(context: Context): Intent =
            Intent(context, PersistentNavBarService::class.java).setAction(ACTION_STOP)
    }
}
