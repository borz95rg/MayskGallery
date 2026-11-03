package com.maysk.gallery.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
    ThemePreset.ARCTIC, ThemePreset.ICE_WIND -> false
    ThemePreset.ORIGINAL, ThemePreset.GLASS, ThemePreset.LUXURY, ThemePreset.NEON,
    ThemePreset.DEEP_BLUE, ThemePreset.VIOLET, ThemePreset.MINT_COAL,
    ThemePreset.COPPER, ThemePreset.ROYAL_BLUE, ThemePreset.DESERT,
    ThemePreset.EMERALD, ThemePreset.DARK_MAGIC -> true
    else -> system
}

@Composable
private fun schemeFor(s: UiSettings, dark: Boolean): ColorScheme {
    fun scheme(bg: Long, surface: Long, accent: Long, variant: Long, light: Boolean): ColorScheme {
        val background = Color(bg)
        val card = Color(surface)
        val primary = Color(accent)
        val primaryText = if (primary.luminance() > .5f) Color(0xFF121212) else Color.White
        val text = if (background.luminance() > .5f) Color(0xFF121212) else Color.White
        val secondary = if (background.luminance() > .5f) Color(0xFF5F6368) else Color(0xFF9AA0A6)
        return if (light) lightColorScheme(
            primary = primary, onPrimary = primaryText,
            background = background, onBackground = text,
            surface = card, onSurface = if (card.luminance() > .5f) Color(0xFF121212) else Color.White,
            surfaceVariant = Color(variant),
            onSurfaceVariant = if (Color(variant).luminance() > .5f) Color(0xFF5F6368) else Color(0xFF9AA0A6),
            secondary = primary, onSecondary = primaryText
        ) else darkColorScheme(
            primary = primary, onPrimary = primaryText,
            background = background, onBackground = text,
            surface = card, onSurface = Color.White,
            surfaceVariant = Color(variant), onSurfaceVariant = secondary,
            secondary = primary, onSecondary = primaryText
        )
    }
    val result = when (s.preset) {
        ThemePreset.DEEP_BLUE -> scheme(0xFF0D1B2A, 0xFF17283A, 0xFF1B263B, 0xFF24384D, false)
        ThemePreset.ARCTIC -> scheme(0xFFF8F9FA, 0xFFFFFFFF, 0xFF007AFF, 0xFFE9EEF4, true)
        ThemePreset.VIOLET -> scheme(0xFF1A0B2E, 0xFF281640, 0xFF7B2CBF, 0xFF382052, false)
        ThemePreset.MINT_COAL -> scheme(0xFF12181B, 0xFF1D292D, 0xFF00B4D8, 0xFF29383D, false)
        ThemePreset.COPPER -> scheme(0xFF2B1B17, 0xFF3B2923, 0xFFE07A5F, 0xFF49332B, false)
        ThemePreset.ROYAL_BLUE -> scheme(0xFF0A192F, 0xFF142943, 0xFF64FFDA, 0xFF203B58, false)
        ThemePreset.ICE_WIND -> scheme(0xFFE0F2FE, 0xFFF0F9FF, 0xFF0284C7, 0xFFBAE6FD, true)
        ThemePreset.DESERT -> scheme(0xFF2A2421, 0xFF3A302B, 0xFFF4A261, 0xFF4A3B32, false)
        ThemePreset.EMERALD -> scheme(0xFF0F2027, 0xFF19333A, 0xFF2EC4B6, 0xFF25434A, false)
        ThemePreset.DARK_MAGIC -> scheme(0xFF050505, 0xFF151515, 0xFFE63946, 0xFF242424, false)
        ThemePreset.ORIGINAL -> scheme(0xFF000000, 0xFF121214, 0xFFEDEDED, 0xFF232326, false)
        ThemePreset.CLASSIC -> if (dark) scheme(0xFF101114, 0xFF1B1C20, 0xFF6AA4FF, 0xFF2A2B31, false) else scheme(0xFFF6F6F8, 0xFFFFFFFF, 0xFF1E6FE0, 0xFFE9E9EE, true)
        ThemePreset.MODERN -> if (Build.VERSION.SDK_INT >= 31) {
            val ctx = LocalContext.current
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        } else if (dark) darkColorScheme() else lightColorScheme()
        ThemePreset.GLASS -> scheme(0xFF120A2A, 0xFF2A1A5E, 0xFFB88CFF, 0xFF3A2878, false)
        ThemePreset.LUXURY -> scheme(0xFF0B0B0B, 0xFF181818, 0xFFD4AF37, 0xFF242424, false)
        ThemePreset.NEON -> scheme(0xFF07040F, 0xFF1A0C33, 0xFFFF2BD6, 0xFF28104D, false)
        ThemePreset.MINIMAL -> if (dark) scheme(0xFF000000, 0xFF111111, 0xFFFFFFFF, 0xFF1C1C1C, false) else scheme(0xFFFFFFFF, 0xFFF4F4F4, 0xFF000000, 0xFFEAEAEA, true)
        ThemePreset.ISTYLE -> if (dark) scheme(0xFF000000, 0xFF1C1C1E, 0xFF0A84FF, 0xFF2C2C2E, false) else scheme(0xFFF2F2F7, 0xFFFFFFFF, 0xFF0A84FF, 0xFFE5E5EA, true)
    }
    return if (s.accent != 0) {
        val a = Color(s.accent)
        result.copy(primary = a, onPrimary = if (a.luminance() > .5f) Color(0xFF121212) else Color.White)
    } else result
}

@Composable
fun MayskTheme(s: UiSettings, content: @Composable () -> Unit) {
    val dark = isDark(s, isSystemInDarkTheme())
    val scheme = schemeFor(s, dark)
    val typo = if (s.preset == ThemePreset.ORIGINAL) {
        val b = Typography()
        val sf = { st: androidx.compose.ui.text.TextStyle ->
            st.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Normal)
        }
        b.copy(
            headlineLarge = sf(b.headlineLarge), headlineMedium = sf(b.headlineMedium), headlineSmall = sf(b.headlineSmall),
            titleLarge = sf(b.titleLarge), titleMedium = sf(b.titleMedium),
        )
    } else Typography()
    val d = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(d.density, d.fontScale * s.fontScale)) {
        MaterialTheme(colorScheme = scheme, typography = typo, content = content)
    }
}

/** Фон всего приложения: у Glass и Neon — градиент. */
@Composable
fun appBackground(s: UiSettings): Brush {
    val c = MaterialTheme.colorScheme
    return when (s.preset) {
        ThemePreset.GLASS, ThemePreset.VIOLET -> Brush.verticalGradient(listOf(Color(0xFF2B1466), Color(0xFF120A2A), Color(0xFF3A0F55)))
        ThemePreset.NEON -> Brush.verticalGradient(listOf(Color(0xFF1B0638), Color(0xFF07040F), Color(0xFF05222E)))
        ThemePreset.LUXURY, ThemePreset.COPPER -> Brush.verticalGradient(listOf(Color(0xFF1A1405), Color(0xFF0B0B0B)))
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
        .background(Color.White.copy(alpha = s.frost * 0.18f))
        .border(1.dp, Color.White.copy(alpha = 0.14f), shape)
}
