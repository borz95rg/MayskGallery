package com.maysk.gallery.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.maysk.gallery.data.ThemePreset
import com.maysk.gallery.data.UiSettings

private fun isDark(s: UiSettings, system: Boolean): Boolean = when (s.preset) {
    ThemePreset.ORIGINAL, ThemePreset.GLASS, ThemePreset.LUXURY, ThemePreset.NEON -> true
    else -> system
}

@Composable
private fun schemeFor(s: UiSettings, dark: Boolean): ColorScheme {
    val ctx = LocalContext.current
    return when (s.preset) {
        ThemePreset.ORIGINAL -> darkColorScheme(
            primary = Color(0xFFEDEDED), onPrimary = Color.Black,
            background = Color.Black, onBackground = Color.White,
            surface = Color(0xFF121214), onSurface = Color.White,
            surfaceVariant = Color(0xFF232326), onSurfaceVariant = Color(0xFF9A9AA2),
        )

        ThemePreset.CLASSIC ->
            if (dark) darkColorScheme(
                primary = Color(0xFF6AA4FF), onPrimary = Color(0xFF00224D),
                background = Color(0xFF101114), onBackground = Color(0xFFE8E8EC),
                surface = Color(0xFF1B1C20), onSurface = Color(0xFFE8E8EC),
                surfaceVariant = Color(0xFF2A2B31), onSurfaceVariant = Color(0xFFB9BAC2),
            ) else lightColorScheme(
                primary = Color(0xFF1E6FE0), onPrimary = Color.White,
                background = Color(0xFFF6F6F8), onBackground = Color(0xFF16171A),
                surface = Color.White, onSurface = Color(0xFF16171A),
                surfaceVariant = Color(0xFFE9E9EE), onSurfaceVariant = Color(0xFF5A5B63),
            )

        ThemePreset.MODERN ->
            if (Build.VERSION.SDK_INT >= 31) {
                if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
            } else if (dark) darkColorScheme() else lightColorScheme()

        ThemePreset.GLASS -> darkColorScheme(
            primary = Color(0xFFB88CFF), onPrimary = Color(0xFF1B0A3C),
            secondary = Color(0xFFFF5FB0),
            background = Color(0xFF120A2A), onBackground = Color.White,
            surface = Color(0xFF2A1A5E), onSurface = Color.White,
            surfaceVariant = Color(0xFF3A2878), onSurfaceVariant = Color(0xFFD9CCFF),
        )

        ThemePreset.LUXURY -> darkColorScheme(
            primary = Color(0xFFD4AF37), onPrimary = Color.Black,
            secondary = Color(0xFFF1D77A),
            background = Color(0xFF0B0B0B), onBackground = Color(0xFFF5EBD0),
            surface = Color(0xFF181818), onSurface = Color(0xFFF5EBD0),
            surfaceVariant = Color(0xFF242424), onSurfaceVariant = Color(0xFFCDB877),
        )

        ThemePreset.NEON -> darkColorScheme(
            primary = Color(0xFFFF2BD6), onPrimary = Color.Black,
            secondary = Color(0xFF00E5FF),
            background = Color(0xFF07040F), onBackground = Color.White,
            surface = Color(0xFF1A0C33), onSurface = Color.White,
            surfaceVariant = Color(0xFF28104D), onSurfaceVariant = Color(0xFF8FF3FF),
        )

        ThemePreset.MINIMAL ->
            if (dark) darkColorScheme(
                primary = Color.White, onPrimary = Color.Black,
                background = Color.Black, onBackground = Color.White,
                surface = Color(0xFF111111), onSurface = Color.White,
                surfaceVariant = Color(0xFF1C1C1C), onSurfaceVariant = Color(0xFFAAAAAA),
            ) else lightColorScheme(
                primary = Color.Black, onPrimary = Color.White,
                background = Color.White, onBackground = Color.Black,
                surface = Color(0xFFF4F4F4), onSurface = Color.Black,
                surfaceVariant = Color(0xFFEAEAEA), onSurfaceVariant = Color(0xFF555555),
            )

        ThemePreset.ISTYLE ->
            if (dark) darkColorScheme(
                primary = Color(0xFF0A84FF), onPrimary = Color.White,
                background = Color.Black, onBackground = Color.White,
                surface = Color(0xFF1C1C1E), onSurface = Color.White,
                surfaceVariant = Color(0xFF2C2C2E), onSurfaceVariant = Color(0xFF9A9AA0),
            ) else lightColorScheme(
                primary = Color(0xFF0A84FF), onPrimary = Color.White,
                background = Color(0xFFF2F2F7), onBackground = Color.Black,
                surface = Color.White, onSurface = Color.Black,
                surfaceVariant = Color(0xFFE5E5EA), onSurfaceVariant = Color(0xFF6C6C70),
            )
    }
}

@Composable
fun MayskTheme(s: UiSettings, content: @Composable () -> Unit) {
    val dark = isDark(s, isSystemInDarkTheme())
    val scheme = schemeFor(s, dark)
    val d = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(d.density, d.fontScale * s.fontScale)) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

/** Фон всего приложения: у Glass и Neon — градиент. */
@Composable
fun appBackground(s: UiSettings): Brush {
    val c = MaterialTheme.colorScheme
    return when (s.preset) {
        ThemePreset.GLASS -> Brush.verticalGradient(listOf(Color(0xFF2B1466), Color(0xFF120A2A), Color(0xFF3A0F55)))
        ThemePreset.NEON -> Brush.verticalGradient(listOf(Color(0xFF1B0638), Color(0xFF07040F), Color(0xFF05222E)))
        ThemePreset.LUXURY -> Brush.verticalGradient(listOf(Color(0xFF1A1405), Color(0xFF0B0B0B)))
        else -> Brush.verticalGradient(listOf(c.background, c.background))
    }
}

// ---------- параметры «Конструктора» -> Compose ----------

fun UiSettings.shadowDp(): Dp = when (shadow) {
    0 -> 0.dp
    1 -> 3.dp
    2 -> 8.dp
    else -> 16.dp
}

/** Слайдер 0..80 -> процент скругления 0..50 (50 = круг у квадрата). */
fun UiSettings.shape(): Shape = RoundedCornerShape(percent = (cornerPercent * 0.625f).toInt().coerceIn(0, 50))

/** Для крупных панелей скругление мягче, чтобы они не превращались в капсулу. */
fun UiSettings.panelShape(): Shape = RoundedCornerShape((cornerPercent * 0.4f).dp)

/** Стеклянная поверхность: полупрозрачный фон, тонкая рамка, тень. */
@Composable
fun Modifier.glass(s: UiSettings, shape: Shape = s.panelShape()): Modifier {
    val c = MaterialTheme.colorScheme
    return this
        .shadow(s.shadowDp(), shape, clip = false)
        .clip(shape)
        .background(c.surface.copy(alpha = s.glassAlpha))
        .border(1.dp, Color.White.copy(alpha = 0.14f), shape)
}
