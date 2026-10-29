package com.koda.dev.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

// -------------------------------------------------------------------------
// Material 3 ColorSchemes for Koda Dev
// -------------------------------------------------------------------------

val KodaDarkColorScheme = darkColorScheme(
    primary = CyberCyan,
    onPrimary = Color(0xFF00363D),
    primaryContainer = CyberCyanContainer,
    onPrimaryContainer = OnCyberCyanContainer,
    secondary = ElectricVioletLight,
    onSecondary = Color(0xFF28005A),
    secondaryContainer = ElectricVioletContainer,
    onSecondaryContainer = OnElectricVioletContainer,
    tertiary = HyperIndigo,
    onTertiary = Color(0xFF1E1B4B),
    tertiaryContainer = HyperIndigoContainer,
    onTertiaryContainer = OnHyperIndigoContainer,
    background = VoidObsidian,
    onBackground = ContentPrimary,
    surface = ObsidianSurface,
    onSurface = ContentPrimary,
    surfaceVariant = ObsidianSurfaceVariant,
    onSurfaceVariant = ContentSecondary,
    surfaceTint = CyberCyan,
    outline = ObsidianBorder,
    outlineVariant = ObsidianBorderSubtle,
    error = TerminalError,
    onError = Color(0xFF450A0A),
    errorContainer = TerminalErrorContainer,
    onErrorContainer = Color(0xFFFCA5A5)
)

val KodaLightColorScheme = lightColorScheme(
    primary = Color(0xFF00838F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F7FA),
    onPrimaryContainer = Color(0xFF004D40),
    secondary = Color(0xFF6D28D9),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEDE9FE),
    onSecondaryContainer = Color(0xFF3B0764),
    tertiary = Color(0xFF4338CA),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE0E7FF),
    onTertiaryContainer = Color(0xFF1E1B4B),
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF475569),
    surfaceTint = Color(0xFF00838F),
    outline = Color(0xFFCBD5E1),
    outlineVariant = Color(0xFFE2E8F0),
    error = Color(0xFFDC2626),
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D)
)

// -------------------------------------------------------------------------
// KodaTheme Accessor
// -------------------------------------------------------------------------

object KodaTheme {
    val colors: KodaSemanticColors
        @Composable
        @ReadOnlyComposable
        get() = LocalKodaSemanticColors.current

    val codeTypography: KodaCodeTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalKodaCodeTypography.current

    val monospaceFont: FontFamily
        get() = JetBrainsMono
}

// -------------------------------------------------------------------------
// Root App Theme
// -------------------------------------------------------------------------

@Composable
fun KodaDevTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) KodaDarkColorScheme else KodaLightColorScheme
    val semanticColors = if (darkTheme) KodaDarkSemanticColors else KodaLightSemanticColors

    CompositionLocalProvider(
        LocalKodaSemanticColors provides semanticColors,
        LocalKodaCodeTypography provides KodaCodeTypographyDefaults
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = KodaTypography,
            content = content
        )
    }
}
