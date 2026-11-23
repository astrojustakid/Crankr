package com.example.spotifyclone.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val StealthDarkColorScheme = darkColorScheme(
  primary = Primary,
  onPrimary = Neutral,
  primaryContainer = CrimsonRedDark,
  onPrimaryContainer = Secondary,
  secondary = Secondary,
  onSecondary = Neutral,
  tertiary = Tertiary,
  onTertiary = Neutral,
  background = Neutral,
  onBackground = Secondary,
  surface = Surface,
  onSurface = Secondary,
  surfaceVariant = CardSurface,
  onSurfaceVariant = Tertiary,
  outline = Border
)

@Composable
fun SpotifyCloneTheme(
  content: @Composable () -> Unit,
) {
  MaterialTheme(colorScheme = StealthDarkColorScheme, typography = Typography, content = content)
}
