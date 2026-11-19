package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkColorScheme =
  darkColorScheme(
    primary = MintPrimaryDark,
    onPrimary = MintOnPrimaryDark,
    primaryContainer = MintContainerDark,
    onPrimaryContainer = MintOnContainerDark,
    secondary = SlateSecondaryDark,
    onSecondary = ObsidianBackgroundDark,
    secondaryContainer = SlateSecondaryContainerDark,
    onSecondaryContainer = ObsidianInkDark,
    tertiary = AmberTertiaryDark,
    onTertiary = ObsidianBackgroundDark,
    tertiaryContainer = AmberContainerDark,
    onTertiaryContainer = AmberOnContainerDark,
    error = CrimsonErrorDark,
    onError = ObsidianBackgroundDark,
    errorContainer = CrimsonContainerDark,
    onErrorContainer = CrimsonOnContainerDark,
    background = ObsidianBackgroundDark,
    onBackground = ObsidianInkDark,
    surface = ObsidianSurfaceDark,
    onSurface = ObsidianInkDark,
    surfaceVariant = ObsidianSurfaceVariantDark,
    onSurfaceVariant = ObsidianMutedDark,
    outline = ObsidianOutlineDark,
    outlineVariant = ObsidianOutlineVariantDark,
  )

private val LightColorScheme =
  lightColorScheme(
    primary = EmeraldPrimaryLight,
    onPrimary = Color.White,
    primaryContainer = EmeraldContainerLight,
    onPrimaryContainer = EmeraldOnContainerLight,
    secondary = SlateSecondaryLight,
    onSecondary = Color.White,
    secondaryContainer = SlateSecondaryContainerLight,
    onSecondaryContainer = SlateInkLight,
    tertiary = AmberTertiaryLight,
    onTertiary = Color.White,
    tertiaryContainer = AmberContainerLight,
    onTertiaryContainer = AmberOnContainerLight,
    error = CrimsonErrorLight,
    onError = Color.White,
    errorContainer = CrimsonContainerLight,
    onErrorContainer = CrimsonOnContainerLight,
    background = SlateBackgroundLight,
    onBackground = SlateInkLight,
    surface = SlateSurfaceLight,
    onSurface = SlateInkLight,
    surfaceVariant = SlateSurfaceVariantLight,
    onSurfaceVariant = SlateMutedLight,
    outline = SlateOutlineLight,
    outlineVariant = SlateOutlineVariantLight,
  )

val AppShapes =
  Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
  )

@Composable
fun TxIdAutoPasteTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

  MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography,
    shapes = AppShapes,
    content = content,
  )
}
