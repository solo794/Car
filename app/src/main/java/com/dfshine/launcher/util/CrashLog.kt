package com.dfshine.launcher.util

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Saves the stack trace of any uncaught (Kotlin/Java) crash to a file so it
 * can be read and exported from the diagnostics screen, then lets Android
 * handle the crash as usual.
 */
object CrashLog {
    private const val FILE = "last_crash.txt"

    fun install(context: Context) {
        val app = context.applicationContext
        recordPreviousExit(app)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val sw = StringWriter()
                error.printStackTrace(PrintWriter(sw))
                val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                File(app.filesDir, FILE).writeText(
                    "time=$stamp thread=${thread.name}\n" +
                        "device=${Build.MANUFACTURER}/${Build.MODEL} android=${Build.VERSION.RELEASE}\n\n$sw"
                )
            }
            previous?.uncaughtException(thread, error)
        }
    }

    /**
     * Android 11+: ask the system why our previous process died. This also
     * catches native (GPU/driver) crashes that never reach the Java handler.
     */
    private fun recordPreviousExit(app: Context) {
        if (Build.VERSION.SDK_INT < 30) return
        runCatching {
            val am = app.getSystemService(android.app.ActivityManager::class.java) ?: return
            val sp = app.getSharedPreferences("crash_log", Context.MODE_PRIVATE)
            val lastSeen = sp.getLong("last_exit_ts", 0L)
            val info = am.getHistoricalProcessExitReasons(app.packageName, 0, 5)
                .firstOrNull { it.timestamp > lastSeen } ?: return
            sp.edit().putLong("last_exit_ts", info.timestamp).apply()
            val crash = info.reason == android.app.ApplicationExitInfo.REASON_CRASH ||
                info.reason == android.app.ApplicationExitInfo.REASON_CRASH_NATIVE ||
                info.reason == android.app.ApplicationExitInfo.REASON_ANR
            if (!crash) return
            val kind = when (info.reason) {
                android.app.ApplicationExitInfo.REASON_CRASH_NATIVE -> "NATIVE CRASH"
                android.app.ApplicationExitInfo.REASON_ANR -> "ANR (hang)"
                else -> "CRASH"
            }
            val trace = runCatching {
                info.traceInputStream?.use { input ->
                    val bytes = input.readBytes()
                    // Native tombstones are protobuf: keep the readable text runs.
                    String(bytes, Charsets.ISO_8859_1).replace(Regex("[^\\x20-\\x7E\\n]+"), " ").take(6000)
                }
            }.getOrNull().orEmpty()
            val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(info.timestamp))
            val existing = File(app.filesDir, FILE).takeIf { it.exists() }?.readText().orEmpty()
            File(app.filesDir, FILE).writeText(
                "[$kind] time=$stamp status=${info.status} desc=${info.description}\n" +
                    "device=${Build.MANUFACTURER}/${Build.MODEL} android=${Build.VERSION.RELEASE}\n$trace\n\n$existing"
            )
        }
    }

    fun read(context: Context): String? =
        File(context.filesDir, FILE).takeIf { it.exists() }?.readText()

    fun clear(context: Context) {
        File(context.filesDir, FILE).delete()
    }
}
