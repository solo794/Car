package com.dfshine.launcher.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

val LauncherBackgroundDark = Color(0xFF0B0F14)
val LauncherSurfaceDark = Color(0xFF151B23)
val LauncherOnDark = Color(0xFFF2F5F7)
val LauncherMuted = Color(0xFF8A94A0)
val LauncherBackgroundLight = Color(0xFFF4F6F8)
val LauncherSurfaceLight = Color(0xFFFFFFFF)
val LauncherOnLight = Color(0xFF10151B)

/**
 * @param accent the user-selected accent color (hex string from [com.dfshine.launcher.data.Prefs]).
 * @param darkTheme null = follow system, true/false = forced.
 */
@Composable
fun ShineLauncherTheme(
    accent: Color,
    darkTheme: Boolean? = null,
    content: @Composable () -> Unit
) {
    val useDark = darkTheme ?: isSystemInDarkTheme()

    val colorScheme = if (useDark) {
        darkColorScheme(
            primary = accent,
            secondary = accent,
            background = LauncherBackgroundDark,
            surface = LauncherSurfaceDark,
            onBackground = LauncherOnDark,
            onSurface = LauncherOnDark
        )
    } else {
        lightColorScheme(
            primary = accent,
            secondary = accent,
            background = LauncherBackgroundLight,
            surface = LauncherSurfaceLight,
            onBackground = LauncherOnLight,
            onSurface = LauncherOnLight
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}

fun Color.toHex(): String = String.format("#%06X", 0xFFFFFF and this.toArgb())

val CarLabelStyle = TextStyle(fontSize = 13.sp)
