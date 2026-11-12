package com.dfshine.launcher.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * "Midnight Studio" design tokens for the car dashboard.
 *
 * One accent colour (the user's, from Settings) carries identity; every
 * other colour has a fixed meaning so the driver reads state at a glance:
 * green = on / OK, amber = attention, red = danger, blue = high beam.
 * Surfaces are frosted glass over a graphite studio with a spotlight
 * behind the car.
 */
object Dash {
    // Studio background
    val BgTop = Color(0xFF0B1117)
    val BgMid = Color(0xFF070B0F)
    val BgBottom = Color(0xFF030405)

    // Text
    val TextPrimary = Color(0xFFF1F5F8)
    val TextSecondary = Color(0xFF8D9AA6)
    val TextTertiary = Color(0xFF55616C)

    // Glass surfaces
    val Glass = Color(0x12FFFFFF)
    val GlassStrong = Color(0x1EFFFFFF)
    val GlassBorder = Color(0x1AFFFFFF)
    val Divider = Color(0x14FFFFFF)

    // Semantic colours
    val Ok = Color(0xFF34D399)
    val Signal = Color(0xFF22C55E)
    val Warn = Color(0xFFF5A524)
    val Danger = Color(0xFFF43F5E)
    val HighBeam = Color(0xFF3B82F6)
    val Off = Color(0xFF3A444E)

    val CardRadius = 22.dp
    val CardShape = RoundedCornerShape(CardRadius)

    // Typography
    val Hero = TextStyle(fontSize = 76.sp, fontWeight = FontWeight.Light, color = TextPrimary, fontFeatureSettings = "tnum", letterSpacing = (-2).sp)
    val Clock = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Medium, color = TextPrimary, fontFeatureSettings = "tnum")
    val Value = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, fontFeatureSettings = "tnum")
    val ValueSmall = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, fontFeatureSettings = "tnum")
    val UnitText = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, color = TextSecondary)
    val Label = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, color = TextSecondary)
    val Caption = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Normal, color = TextTertiary)

    val Background = Brush.verticalGradient(0f to BgTop, 0.55f to BgMid, 1f to BgBottom)
}

/** Frosted glass card. */
fun Modifier.glass(radius: Dp = Dash.CardRadius, strong: Boolean = false, tint: Color? = null): Modifier {
    val shape = RoundedCornerShape(radius)
    return this
        .clip(shape)
        .background(
            Brush.verticalGradient(
                listOf(
                    (tint ?: Color.White).copy(alpha = if (strong) 0.13f else 0.085f),
                    (tint ?: Color.White).copy(alpha = if (strong) 0.06f else 0.03f)
                )
            )
        )
        .border(1.dp, Brush.verticalGradient(listOf(Color(0x2EFFFFFF), Color(0x0AFFFFFF))), shape)
}
