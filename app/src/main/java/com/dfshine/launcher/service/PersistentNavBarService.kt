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
 * is currently in the foreground. Its main content is a climate-control
 * widget matching the head unit's own HVAC bar (temperature, AUTO/OFF,
 * vent/power, defrost/recirculation, fan speed), plus two small Home/GPS
 * shortcuts at the edges - unlike [FloatingPipService]'s bubble, this one
 * isn't meant to be dragged or dismissed by the driver.
 *
 * Read this before relying on it while driving: there is no public
 * Android API for a vehicle's real HVAC hardware, and Dongfeng hasn't
 * published one for this unit. The temperature/fan numbers shown here are
 * therefore *not read from the car* - they only track what this widget
 * itself has sent, starting from a locally-saved guess. Each button also
 * fires the broadcast action configured for it in Settings -> Climate
 * Widget, if you've set one; until you have real action names for this
 * unit, pressing a button updates the number on screen but does nothing
 * to the actual air conditioning. See the README for how to capture the
 * real broadcast names with `adb logcat` while using the system's own
 * climate controls.
 */
class PersistentNavBarService : Service() {

    private lateinit var windowManager: WindowManager
    private var barView: View? = null

    private var tempLabel: TextView? = null
    private var fanLabel: TextView? = null
    private var autoLabel: TextView? = null
    private var offLabel: TextView? = null

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
        barView = null
        super.onDestroy()
    }

    // ---------------------------------------------------------------
    // Layout
    // ---------------------------------------------------------------

    private fun showBar() {
        val prefs = Prefs(this)

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = solidBackground(Color.parseColor("#0B0F14"), alpha = 250)
            setPadding(dp(4), dp(6), dp(4), dp(6))
        }

        bar.addView(edgeShortcut(android.R.drawable.ic_menu_myplaces, "الرئيسية") { goHome() })
        bar.addView(temperatureColumn(prefs), weightedParams())
        bar.addView(modeColumn(prefs), weightedParams())
        bar.addView(ventPowerColumn(prefs), weightedParams())
        bar.addView(defrostRecircColumn(prefs), weightedParams())
        bar.addView(fanColumn(prefs), weightedParams())
        bar.addView(edgeShortcut(android.R.drawable.ic_menu_mapmode, "الملاحة") { launchGps(prefs) })

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

    private fun weightedParams() =
        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

    // --- Column 1: temperature ------------------------------------------------

    private fun temperatureColumn(prefs: Prefs): LinearLayout {
        val label = smallLabel("${prefs.hvacAssumedTemp}°C").also { tempLabel = it }
        return verticalStack(
            arrowButton("▲") { adjustTemp(prefs, +1) },
            label,
            arrowButton("▼") { adjustTemp(prefs, -1) }
        )
    }

    private fun adjustTemp(prefs: Prefs, delta: Int) {
        val newTemp = (prefs.hvacAssumedTemp + delta).coerceIn(16, 32)
        prefs.hvacAssumedTemp = newTemp
        tempLabel?.text = "$newTemp°C"
        sendConfiguredBroadcast(if (delta > 0) prefs.hvacTempUpAction else prefs.hvacTempDownAction)
    }

    // --- Column 2: AUTO / OFF mode ---------------------------------------------

    private fun modeColumn(prefs: Prefs): LinearLayout {
        val auto = smallButton("AUTO") { setAuto(prefs, true) }.also { autoLabel = it }
        val off = smallButton("OFF") { setAuto(prefs, false) }.also { offLabel = it }
        updateModeHighlight(prefs)
        return verticalStack(auto, off)
    }

    private fun setAuto(prefs: Prefs, auto: Boolean) {
        prefs.hvacAssumedAuto = auto
        updateModeHighlight(prefs)
        sendConfiguredBroadcast(if (auto) prefs.hvacAutoToggleAction else prefs.hvacOffAction)
    }

    private fun updateModeHighlight(prefs: Prefs) {
        val activeColor = Color.parseColor("#2FB6A6")
        val inactiveColor = Color.parseColor("#8A94A0")
        autoLabel?.setTextColor(if (prefs.hvacAssumedAuto) activeColor else inactiveColor)
        offLabel?.setTextColor(if (!prefs.hvacAssumedAuto) activeColor else inactiveColor)
    }

    // --- Column 3: vent direction + power ---------------------------------------

    private fun ventPowerColumn(prefs: Prefs): LinearLayout {
        val vent = iconButton("💨", "اتجاه الهواء") {
            sendConfiguredBroadcast(prefs.hvacVentAction)
            toastIfNoAction(prefs.hvacVentAction, "اتجاه الهواء")
        }
        val power = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_lock_power_off)
            background = roundBackground(if (prefs.hvacAssumedPower) Color.parseColor("#2FB6A6") else Color.parseColor("#3A4048"))
            setColorFilter(Color.WHITE)
            setPadding(dp(8), dp(8), dp(8), dp(8))
            contentDescription = "تشغيل / إيقاف التكييف"
        }
        power.setOnClickListener {
            val newState = !prefs.hvacAssumedPower
            prefs.hvacAssumedPower = newState
            power.background = roundBackground(if (newState) Color.parseColor("#2FB6A6") else Color.parseColor("#3A4048"))
            sendConfiguredBroadcast(prefs.hvacPowerToggleAction)
            toastIfNoAction(prefs.hvacPowerToggleAction, "تشغيل/إيقاف التكييف")
        }
        return verticalStack(vent, power)
    }

    // --- Column 4: defrost + recirculation --------------------------------------

    private fun defrostRecircColumn(prefs: Prefs): LinearLayout {
        val defrost = iconButton("❄", "إزالة الضباب") {
            sendConfiguredBroadcast(prefs.hvacDefrostToggleAction)
            toastIfNoAction(prefs.hvacDefrostToggleAction, "إزالة الضباب")
        }
        val recirc = iconButton("↻", "تدوير الهواء") {
            sendConfiguredBroadcast(prefs.hvacRecircToggleAction)
            toastIfNoAction(prefs.hvacRecircToggleAction, "تدوير الهواء")
        }
        return verticalStack(defrost, recirc)
    }

    // --- Column 5: fan speed -----------------------------------------------------

    private fun fanColumn(prefs: Prefs): LinearLayout {
        val label = smallLabel(fanText(prefs.hvacAssumedFan)).also { fanLabel = it }
        return verticalStack(
            arrowButton("▲") { adjustFan(prefs, +1) },
            label,
            arrowButton("▼") { adjustFan(prefs, -1) }
        )
    }

    private fun fanText(speed: Int) = if (speed <= 0) "OFF" else "🌀 $speed"

    private fun adjustFan(prefs: Prefs, delta: Int) {
        val newSpeed = (prefs.hvacAssumedFan + delta).coerceIn(0, 7)
        prefs.hvacAssumedFan = newSpeed
        fanLabel?.text = fanText(newSpeed)
        sendConfiguredBroadcast(if (delta > 0) prefs.hvacFanUpAction else prefs.hvacFanDownAction)
    }

    // ---------------------------------------------------------------
    // Small view builders
    // ---------------------------------------------------------------

    private fun verticalStack(vararg children: View): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        children.forEach { addView(it) }
    }

    private fun smallLabel(text: String): TextView = TextView(this).apply {
        this.text = text
        setTextColor(Color.WHITE)
        textSize = 13f
        gravity = Gravity.CENTER
        setPadding(0, dp(2), 0, dp(2))
    }

    private fun smallButton(text: String, onClick: () -> Unit): TextView = TextView(this).apply {
        this.text = text
        textSize = 11f
        gravity = Gravity.CENTER
        setPadding(dp(4), dp(2), dp(4), dp(2))
        isClickable = true
        setOnClickListener { onClick() }
    }

    private fun arrowButton(symbol: String, onClick: () -> Unit): TextView = TextView(this).apply {
        text = symbol
        setTextColor(Color.parseColor("#8A94A0"))
        textSize = 12f
        gravity = Gravity.CENTER
        setPadding(dp(6), dp(2), dp(6), dp(2))
        isClickable = true
        setOnClickListener { onClick() }
    }

    private fun iconButton(symbol: String, description: String, onClick: () -> Unit): TextView = TextView(this).apply {
        text = symbol
        textSize = 16f
        gravity = Gravity.CENTER
        setPadding(dp(4), dp(2), dp(4), dp(2))
        isClickable = true
        contentDescription = description
        setOnClickListener { onClick() }
    }

    private fun edgeShortcut(icon: Int, label: String, onClick: () -> Unit): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(2), dp(8), dp(2))
            addView(ImageButton(this@PersistentNavBarService).apply {
                setImageResource(icon)
                contentDescription = label
                background = null
                setColorFilter(Color.parseColor("#8A94A0"))
                setOnClickListener { onClick() }
            })
        }
    }

    private fun toastIfNoAction(action: String?, controlName: String) {
        if (action.isNullOrBlank()) {
            toast("$controlName: أضف Broadcast Action له من الإعدادات ← الشريط السفلي ليعمل فعلياً")
        }
    }

    private fun sendConfiguredBroadcast(action: String?) {
        if (action.isNullOrBlank()) return
        runCatching { sendBroadcast(Intent(action)) }
    }

    // ---------------------------------------------------------------
    // Navigation shortcuts
    // ---------------------------------------------------------------

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
        val packageName = component.substringBefore("/")
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent == null) {
            toast("تطبيق الملاحة غير مثبت")
            return
        }
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(launchIntent)
    }

    // ---------------------------------------------------------------
    // Small helpers
    // ---------------------------------------------------------------

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

    private fun roundBackground(color: Int): android.graphics.drawable.Drawable {
        val drawable = android.graphics.drawable.GradientDrawable()
        drawable.shape = android.graphics.drawable.GradientDrawable.OVAL
        drawable.setColor(color)
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
            .setContentTitle("شريط التكييف والملاحة يعمل")
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
