package net.kravos.jezik.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import net.kravos.jezik.data.ThemeMode

/** Bright, chunky colors in the spirit of Duolingo. */
object K {
    val Green = Color(0xFF58CC02)
    val GreenDark = Color(0xFF46A302)
    val Blue = Color(0xFF1CB0F6)
    val BlueDark = Color(0xFF1899D6)
    val Orange = Color(0xFFFF9600)
    val Red = Color(0xFFFF4B4B)
    val RedDark = Color(0xFFEA2B2B)
    val Gold = Color(0xFFFFC800)
    val Purple = Color(0xFFCE82FF)
    val Pink = Color(0xFFFF86D0)
    val Teal = Color(0xFF00CD9C)
    val units = listOf(Green, Purple, Blue, Orange, Pink, Teal, Red, Color(0xFF2B70C9), Color(0xFFA568CC), Color(0xFFE5A100))

    fun darker(c: Color, f: Float = 0.78f) = Color(c.red * f, c.green * f, c.blue * f, c.alpha)
}

data class Palette(
    val dark: Boolean,
    val card: Color,
    val border: Color,
    val dim: Color,
    val locked: Color,
    val okBg: Color,
    val badBg: Color,
)

val LocalPalette = staticCompositionLocalOf {
    Palette(true, Color(0xFF131F24), Color(0xFF37464F), Color(0xFF8A9BA5), Color(0xFF37464F), Color(0xFF1C2E12), Color(0xFF3A1A1C))
}

private val Dark = darkColorScheme(
    primary = K.Green,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF1C2E12),
    onPrimaryContainer = Color(0xFFB7F18D),
    secondary = K.Blue,
    secondaryContainer = Color(0xFF1B3A4B),
    onSecondaryContainer = Color(0xFFDDF4FF),
    tertiary = K.Orange,
    background = Color(0xFF131F24),
    onBackground = Color(0xFFF1F7FB),
    surface = Color(0xFF131F24),
    onSurface = Color(0xFFF1F7FB),
    surfaceVariant = Color(0xFF202F36),
    onSurfaceVariant = Color(0xFF8A9BA5),
    surfaceContainerLowest = Color(0xFF0E181C),
    surfaceContainerLow = Color(0xFF131F24),
    surfaceContainer = Color(0xFF1A2830),
    surfaceContainerHigh = Color(0xFF202F36),
    surfaceContainerHighest = Color(0xFF2A3B44),
    outline = Color(0xFF52656D),
    outlineVariant = Color(0xFF37464F),
    error = K.Red,
)

private val Light = lightColorScheme(
    primary = K.Green,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7FFB8),
    onPrimaryContainer = Color(0xFF2B5900),
    secondary = K.Blue,
    secondaryContainer = Color(0xFFDDF4FF),
    tertiary = K.Orange,
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFFFFFFF),
    onBackground = Color(0xFF3C3C3C),
    onSurface = Color(0xFF3C3C3C),
    surfaceVariant = Color(0xFFF7F7F7),
    onSurfaceVariant = Color(0xFF777777),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFF7F7F7),
    surfaceContainerHigh = Color(0xFFF0F0F0),
    surfaceContainerHighest = Color(0xFFE5E5E5),
    outlineVariant = Color(0xFFE5E5E5),
    error = K.Red,
)

private val Type = Typography().let { t ->
    t.copy(
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.ExtraBold, fontSize = 24.sp),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.Bold),
        labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp),
    )
}

@Composable
fun JezikTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val pal = if (dark) Palette(true, Color(0xFF131F24), Color(0xFF37464F), Color(0xFF8A9BA5), Color(0xFF37464F), Color(0xFF1C2E12), Color(0xFF3A1A1C))
    else Palette(false, Color.White, Color(0xFFE5E5E5), Color(0xFF777777), Color(0xFFE5E5E5), Color(0xFFD7FFB8), Color(0xFFFFDFE0))
    CompositionLocalProvider(LocalPalette provides pal) {
        MaterialTheme(colorScheme = if (dark) Dark else Light, typography = Type, content = content)
    }
}
