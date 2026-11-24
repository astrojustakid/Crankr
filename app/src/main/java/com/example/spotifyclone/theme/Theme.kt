package com.example.spotifyclone.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.example.spotifyclone.service.CrankrAppearanceManager

@Composable
fun SpotifyCloneTheme(
  content: @Composable () -> Unit,
) {
  val context = LocalContext.current
  val appearanceManager = remember(context) { CrankrAppearanceManager.getInstance(context) }
  val activePreset by appearanceManager.themePreset.collectAsState()
  val palette = CrankrThemeState.activePalette

  val dynamicColorScheme = remember(activePreset, palette) {
    darkColorScheme(
      primary = palette.primary,
      onPrimary = palette.onPrimary,
      primaryContainer = palette.primaryContainer,
      onPrimaryContainer = palette.textPrimary,
      secondary = palette.secondary,
      onSecondary = palette.onPrimary,
      tertiary = palette.tertiary,
      onTertiary = palette.onPrimary,
      background = palette.background,
      onBackground = palette.textPrimary,
      surface = palette.surface,
      onSurface = palette.textPrimary,
      surfaceVariant = palette.cardSurface,
      onSurfaceVariant = palette.tertiary,
      outline = palette.border
    )
  }

  MaterialTheme(colorScheme = dynamicColorScheme, typography = Typography, content = content)
}
