package com.dfshine.launcher.splitscreen

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.util.Log

/**
 * Best-effort split-screen / dual-window launch for the two apps pinned
 * on the "Split Screen" tile in the Quick Panel.
 *
 * IMPORTANT (please read before relying on this in the car): stock,
 * non-rooted Android only lets the system's own Recents/SystemUI put two
 * apps into real split-screen - a normal third-party app (including a
 * Home/launcher app) has no public API to force it. Some Chinese
 * aftermarket/OEM car head units patch AOSP to allow freeform multi-window
 * out of the box (which is why launchers like this can offer "split
 * screen" at all); on those units the freeform launch below works. On a
 * stock, unmodified factory ROM (which is what the Dongfeng Shine's
 * built-in screen most likely runs) it will typically be silently ignored
 * by the system and both apps will simply open full-screen one after the
 * other - there is no safe way to detect this in advance, so this always
 * degrades gracefully rather than crashing.
 */
object SplitScreenHelper {

    private const val TAG = "SplitScreenHelper"

    /** WINDOWING_MODE_FREEFORM, hidden constant in android.app.WindowConfiguration. */
    private const val WINDOWING_MODE_FREEFORM = 5

    fun launchTopBottom(context: Context, topPackage: String, bottomPackage: String) {
        val screenHeight = context.resources.displayMetrics.heightPixels
        val screenWidth = context.resources.displayMetrics.widthPixels
        val half = screenHeight / 2

        launchFreeform(context, topPackage, Rect(0, 0, screenWidth, half))
        launchFreeform(context, bottomPackage, Rect(0, half, screenWidth, screenHeight))
    }

    private fun launchFreeform(context: Context, packageName: String, bounds: Rect) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)

        val options = ActivityOptions.makeBasic()
        options.launchBounds = bounds
        runCatching {
            // setLaunchWindowingMode is @hide on stock AOSP; only whitelisted
            // system-signature or vendor-patched ROMs honor it. Reflection
            // keeps the app building against the public SDK while still
            // trying the call - it's a no-op (caught below) everywhere else.
            val method = ActivityOptions::class.java.getMethod("setLaunchWindowingMode", Int::class.javaPrimitiveType)
            method.invoke(options, WINDOWING_MODE_FREEFORM)
        }.onFailure {
            Log.i(TAG, "Freeform windowing mode not available on this ROM; falling back to normal launch.")
        }

        runCatching {
            context.startActivity(launchIntent, options.toBundle())
        }.onFailure { error ->
            Log.w(TAG, "Could not launch $packageName in split mode, opening normally instead.", error)
            context.startActivity(launchIntent)
        }
    }
}
