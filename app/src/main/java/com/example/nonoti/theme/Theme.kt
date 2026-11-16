package com.example.nonoti.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Dark mode: pure black canvas with deep purple controls & selections
private val DarkBackground = Color(0xFF000000)
private val DarkDeepPurple = Color(0xFF6B21A8)
private val DarkDeepPurpleContainer = Color(0xFF3B0764)
private val DarkAccentText = Color(0xFFC084FC)
private val DarkGlassSurface = Color(0xFF1C1C1E)
private val DarkGlassSurfaceHigh = Color(0xFF2C2C2E)

// Light mode: pure white canvas with pastel light-green controls & selections
private val LightBackground = Color(0xFFFFFFFF)
private val LightPastelGreen = Color(0xFF86EFAC)
private val LightPastelGreenContainer = Color(0xFFDCFCE7)
private val LightOnGreen = Color(0xFF052E16)
private val LightAccentText = Color(0xFF15803D)
private val LightGlassSurface = Color(0xFFF3F6F4)
private val LightGlassSurfaceHigh = Color(0xFFE7ECE9)

@Immutable
data class AppleGlassPalette(
    val isDark: Boolean,
    val canvas: Color,
    val controlAccent: Color,
    val onControlAccent: Color,
    val accentText: Color,
    val glassSurfaceTint: Color,
    val glassActiveTint: Color,
    val glassBarTint: Color,
    val glassDialogTint: Color,
    val glassInnerPillTint: Color,
    val switchOffTrack: Color,
    val divider: Color,
    val borderStart: Color,
    val borderMiddle: Color,
    val borderEnd: Color,
    val activeBorderStart: Color,
    val shadowSpot: Color,
    val mutedText: Color,
    val errorText: Color,
)

private val DarkApplePalette = AppleGlassPalette(
    isDark = true,
    canvas = DarkBackground,
    controlAccent = DarkDeepPurple,
    onControlAccent = Color.White,
    accentText = DarkAccentText,
    glassSurfaceTint = DarkGlassSurface.copy(alpha = 0.70f),
    glassActiveTint = DarkDeepPurple.copy(alpha = 0.28f),
    glassBarTint = Color(0xFF16161A).copy(alpha = 0.58f),
    glassDialogTint = Color(0xFF18181C).copy(alpha = 0.88f),
    glassInnerPillTint = Color.White.copy(alpha = 0.09f),
    switchOffTrack = Color(0xFF2C2C2E),
    divider = Color.White.copy(alpha = 0.09f),
    borderStart = Color.White.copy(alpha = 0.36f),
    borderMiddle = Color.White.copy(alpha = 0.12f),
    borderEnd = Color.White.copy(alpha = 0.04f),
    activeBorderStart = Color(0xFFA855F7).copy(alpha = 0.65f),
    shadowSpot = Color.Black.copy(alpha = 0.55f),
    mutedText = Color.White.copy(alpha = 0.65f),
    errorText = Color(0xFFFF6B6B),
)

private val LightApplePalette = AppleGlassPalette(
    isDark = false,
    canvas = LightBackground,
    controlAccent = LightPastelGreen,
    onControlAccent = LightOnGreen,
    accentText = LightAccentText,
    glassSurfaceTint = LightGlassSurface.copy(alpha = 0.84f),
    glassActiveTint = LightPastelGreen.copy(alpha = 0.38f),
    glassBarTint = Color.White.copy(alpha = 0.62f),
    glassDialogTint = Color(0xFFFAFDFB).copy(alpha = 0.92f),
    glassInnerPillTint = Color.Black.copy(alpha = 0.045f),
    switchOffTrack = Color(0xFFE5E5EA),
    divider = Color.Black.copy(alpha = 0.07f),
    borderStart = Color.White.copy(alpha = 0.95f),
    borderMiddle = Color.Black.copy(alpha = 0.06f),
    borderEnd = Color.Black.copy(alpha = 0.11f),
    activeBorderStart = Color(0xFF4ADE80).copy(alpha = 0.70f),
    shadowSpot = Color.Black.copy(alpha = 0.10f),
    mutedText = Color.Black.copy(alpha = 0.60f),
    errorText = Color(0xFFDC2626),
)

val LocalApplePalette = staticCompositionLocalOf { DarkApplePalette }

private val DarkColorScheme = darkColorScheme(
    primary = DarkDeepPurple,
    onPrimary = Color.White,
    primaryContainer = DarkDeepPurpleContainer,
    onPrimaryContainer = Color(0xFFF3E8FF),
    secondary = DarkAccentText,
    onSecondary = Color(0xFF2E1065),
    secondaryContainer = Color(0xFF2B1B42),
    onSecondaryContainer = Color(0xFFE9D5FF),
    tertiary = DarkDeepPurple,
    onTertiary = Color.White,
    tertiaryContainer = DarkDeepPurple.copy(alpha = 0.30f),
    onTertiaryContainer = Color.White,
    background = DarkBackground,
    onBackground = Color.White,
    surface = DarkGlassSurface,
    surfaceContainer = Color(0xFF141416),
    surfaceContainerLow = Color(0xFF0E0E10),
    surfaceContainerHigh = DarkGlassSurfaceHigh,
    onSurface = Color.White,
    surfaceVariant = DarkGlassSurfaceHigh,
    onSurfaceVariant = Color.White.copy(alpha = 0.65f),
    outline = Color.White.copy(alpha = 0.18f),
    error = Color(0xFFFF6B6B),
)

private val LightColorScheme = lightColorScheme(
    primary = LightPastelGreen,
    onPrimary = LightOnGreen,
    primaryContainer = LightPastelGreenContainer,
    onPrimaryContainer = LightOnGreen,
    secondary = LightAccentText,
    onSecondary = Color.White,
    secondaryContainer = LightPastelGreenContainer,
    onSecondaryContainer = LightOnGreen,
    tertiary = LightPastelGreen,
    onTertiary = LightOnGreen,
    tertiaryContainer = LightPastelGreen.copy(alpha = 0.45f),
    onTertiaryContainer = LightOnGreen,
    background = LightBackground,
    onBackground = Color(0xFF0B0F0D),
    surface = LightGlassSurface,
    surfaceContainer = LightGlassSurface,
    surfaceContainerLow = Color(0xFFF8FAF9),
    surfaceContainerHigh = LightGlassSurfaceHigh,
    onSurface = Color(0xFF0B0F0D),
    surfaceVariant = LightGlassSurfaceHigh,
    onSurfaceVariant = Color.Black.copy(alpha = 0.60f),
    outline = Color.Black.copy(alpha = 0.12f),
    error = Color(0xFFDC2626),
)

@Composable
fun NonotiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val applePalette = if (darkTheme) DarkApplePalette else LightApplePalette

    CompositionLocalProvider(LocalApplePalette provides applePalette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content,
        )
    }
}
