package com.example.nonoti.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Navy = Color(0xFF12324A)
private val Teal = Color(0xFF2E7180)
private val Amber = Color(0xFFB7682D)
private val Ink = Color(0xFF16232B)
private val Paper = Color(0xFFF6F8F8)

private val DarkColorScheme = darkColorScheme(
  primary = Color(0xFF9DD5DF),
  onPrimary = Color(0xFF07363E),
  primaryContainer = Color(0xFF24474D),
  onPrimaryContainer = Color(0xFFD2F3F7),
  secondary = Color(0xFFB8CDD1),
  onSecondary = Color(0xFF223437),
  secondaryContainer = Color(0xFF2B3D40),
  onSecondaryContainer = Color(0xFFD4E8EB),
  tertiary = Color(0xFFF1B27A),
  onTertiary = Color(0xFF4B2609),
  tertiaryContainer = Color(0xFF5B3820),
  onTertiaryContainer = Color(0xFFFFDCC1),
  background = Color(0xFF111416),
  onBackground = Color(0xFFE1E4E5),
  surface = Color(0xFF171B1D),
  surfaceContainer = Color(0xFF1D2224),
  surfaceContainerLow = Color(0xFF191E20),
  surfaceContainerHigh = Color(0xFF272C2E),
  onSurface = Color(0xFFE1E4E5),
  surfaceVariant = Color(0xFF3F484A),
  onSurfaceVariant = Color(0xFFBFC8CA),
  outline = Color(0xFF899294),
)

private val LightColorScheme =
  lightColorScheme(
    primary = Navy,
    onPrimary = Color.White,
    secondary = Teal,
    secondaryContainer = Color(0xFFD9EEF0),
    onSecondaryContainer = Ink,
    tertiary = Amber,
    tertiaryContainer = Color(0xFFFFE2CB),
    onTertiaryContainer = Ink,
    onBackground = Ink,
    background = Paper,
    surface = Color.White,
    surfaceContainer = Color(0xFFEDF1F1),
    surfaceContainerLow = Color(0xFFF2F5F5),
    surfaceContainerHigh = Color(0xFFE7EBEB),
    surfaceVariant = Color(0xFFDDE4E5),
    onSurfaceVariant = Color(0xFF41494B),
    outline = Color(0xFF71797B),
  )

@Composable
fun NonotiTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    if (darkTheme) DarkColorScheme else LightColorScheme

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
