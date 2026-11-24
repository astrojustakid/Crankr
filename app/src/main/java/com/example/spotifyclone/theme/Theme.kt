package com.example.spotifyclone.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val AuraLiquidColorScheme = darkColorScheme(
  primary = Primary,
  onPrimary = OnPrimary,
  primaryContainer = CrimsonRedDark,
  onPrimaryContainer = TextPrimary,
  secondary = Secondary,
  onSecondary = OnPrimary,
  tertiary = Tertiary,
  onTertiary = OnPrimary,
  background = Background,
  onBackground = TextPrimary,
  surface = Surface,
  onSurface = TextPrimary,
  surfaceVariant = CardSurface,
  onSurfaceVariant = TextSecondary,
  outline = Border
)

@Composable
fun SpotifyCloneTheme(
  content: @Composable () -> Unit,
) {
  MaterialTheme(colorScheme = AuraLiquidColorScheme, typography = Typography, content = content)
}
