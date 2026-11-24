package com.vocalvault.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.vocalvault.app.data.ThemeMode

@Immutable
data class AppColors(
    val bg: Color,
    val surface1: Color,
    val surface2: Color,
    val hairline: Color,
    val textHi: Color,
    val textMid: Color,
    val textLow: Color,
    val accent: Color,
    val onAccent: Color,
    val isDark: Boolean,
)

data class AccentOption(val name: String, val color: Color, val onColor: Color = Color.White)

/** Rose (Apple-ish), Green (Spotify-ish), Red (YouTube-ish) plus a few more. */
val AccentOptions = listOf(
    AccentOption("Rose", Color(0xFFFA2D48)),
    AccentOption("Green", Color(0xFF1ED760), Color(0xFF06120A)),
    AccentOption("Red", Color(0xFFFF2B2B)),
    AccentOption("Violet", Color(0xFF9D6BFF)),
    AccentOption("Blue", Color(0xFF2E8BFF)),
    AccentOption("Amber", Color(0xFFFFB020), Color(0xFF1A1200)),
)

private fun darkColors(accent: AccentOption) = AppColors(
    bg = Color(0xFF000000),
    surface1 = Color(0xFF1C1C1E),
    surface2 = Color(0xFF2C2C2E),
    hairline = Color(0xFF38383A),
    textHi = Color(0xFFFFFFFF),
    textMid = Color(0xFF8E8E93),
    textLow = Color(0xFF636366),
    accent = accent.color,
    onAccent = accent.onColor,
    isDark = true,
)

private fun lightColors(accent: AccentOption) = AppColors(
    bg = Color(0xFFFFFFFF),
    surface1 = Color(0xFFF2F2F7),
    surface2 = Color(0xFFE5E5EA),
    hairline = Color(0xFFD1D1D6),
    textHi = Color(0xFF000000),
    textMid = Color(0xFF6C6C70),
    textLow = Color(0xFFAEAEB2),
    accent = accent.color,
    onAccent = accent.onColor,
    isDark = false,
)

val LocalAppColors = staticCompositionLocalOf { darkColors(AccentOptions[0]) }

val Bg: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.bg
val Surface1: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.surface1
val Surface2: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.surface2
val Hairline: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.hairline
val TextHi: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.textHi
val TextMid: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.textMid
val TextLow: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.textLow
val Accent: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.accent
val OnAccent: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.onAccent

private val Sans = FontFamily.SansSerif

private val AppTypography = Typography(
    displaySmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 41.sp, letterSpacing = (-0.4).sp),
    headlineMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.5).sp),
    headlineSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.3).sp),
    titleLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = (-0.3).sp),
    titleMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
    titleSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 17.sp),
    bodyLarge = TextStyle(fontFamily = Sans, fontSize = 15.sp, lineHeight = 21.sp),
    bodyMedium = TextStyle(fontFamily = Sans, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    labelMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 12.sp),
    labelSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 11.sp),
)

@Composable
fun resolveDark(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.System -> isSystemInDarkTheme()
    ThemeMode.Dark -> true
    ThemeMode.Light -> false
}

@Composable
fun VocalVaultTheme(dark: Boolean = true, accentIndex: Int = 0, content: @Composable () -> Unit) {
    val accent = AccentOptions.getOrElse(accentIndex) { AccentOptions[0] }
    val c = if (dark) darkColors(accent) else lightColors(accent)
    val scheme = if (dark) {
        darkColorScheme(
            primary = c.accent, onPrimary = c.onAccent, secondary = c.accent, onSecondary = c.onAccent,
            background = c.bg, onBackground = c.textHi, surface = c.bg, onSurface = c.textHi,
            surfaceVariant = c.surface1, onSurfaceVariant = c.textMid, surfaceContainer = c.surface1,
            surfaceContainerHigh = c.surface2, surfaceContainerHighest = c.surface2, surfaceContainerLow = c.surface1,
            outline = c.hairline, outlineVariant = c.hairline,
        )
    } else {
        lightColorScheme(
            primary = c.accent, onPrimary = c.onAccent, secondary = c.accent, onSecondary = c.onAccent,
            background = c.bg, onBackground = c.textHi, surface = c.bg, onSurface = c.textHi,
            surfaceVariant = c.surface1, onSurfaceVariant = c.textMid, surfaceContainer = c.surface1,
            surfaceContainerHigh = c.surface2, surfaceContainerHighest = c.surface2, surfaceContainerLow = c.surface1,
            outline = c.hairline, outlineVariant = c.hairline,
        )
    }
    CompositionLocalProvider(LocalAppColors provides c) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography) {
            // No Surface at the root, so set the default text/icon colour explicitly.
            CompositionLocalProvider(LocalContentColor provides c.textHi, content = content)
        }
    }
}

/** Now Playing is always dark over the artwork colour, like Apple Music / Spotify. */
@Composable
fun ForceDark(content: @Composable () -> Unit) {
    val current = LocalAppColors.current
    if (current.isDark) return content()
    VocalVaultTheme(dark = true, accentIndex = AccentOptions.indexOfFirst { it.color == current.accent }.coerceAtLeast(0), content = content)
}
