package com.dfshine.launcher.ui.dashboard.car3d

import android.content.Context

/**
 * Remembers whether the real (Filament) model was running when the process
 * died. A native GPU crash can't be caught in Kotlin, so: mark "running" on
 * start, clear it on a clean stop or after ~10 s of stable rendering. If the
 * app comes back with the mark still set twice in a row, the dashboard
 * falls back to the lightweight drawn model instead of crash-looping.
 */
object CrashGuard {
    private const val PREFS = "real_model_guard"
    private const val RUNNING = "running"
    private const val STRIKES = "strikes"
    private const val MAX_STRIKES = 2

    /** Called once per process start, before the dashboard decides which model to show. */
    fun checkOnLaunch(context: Context) {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (sp.getBoolean(RUNNING, false)) {
            sp.edit().putInt(STRIKES, sp.getInt(STRIKES, 0) + 1).putBoolean(RUNNING, false).apply()
        }
    }

    fun realModelBlocked(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(STRIKES, 0) >= MAX_STRIKES

    fun realModelStarting(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(RUNNING, true).commit()
    }

    fun realModelStable(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(RUNNING, false).putInt(STRIKES, 0).apply()
    }

    fun realModelStopped(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(RUNNING, false).apply()
    }

    /** User explicitly chose the real model again in Settings. */
    fun reset(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
