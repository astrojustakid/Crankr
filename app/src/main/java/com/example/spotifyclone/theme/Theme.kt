package com.example.spotifyclone.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val KineticMonoColorScheme = darkColorScheme(
  primary = Primary,
  onPrimary = OnPrimary,
  primaryContainer = ElevatedBackground,
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
  MaterialTheme(colorScheme = KineticMonoColorScheme, typography = Typography, content = content)
}
