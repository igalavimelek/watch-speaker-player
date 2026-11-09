package com.example.watchspeakerplayer.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme

private val WatchDarkColorScheme = ColorScheme(
  primary = Color(0xFF66D9EF),
  onPrimary = Color(0xFF003543),
  primaryContainer = Color(0xFF004E61),
  onPrimaryContainer = Color(0xFFB8EAFF),
  secondary = Color(0xFFFFB4A1),
  onSecondary = Color(0xFF561F11),
  secondaryContainer = Color(0xFF723525),
  onSecondaryContainer = Color(0xFFFFDBD2),
  tertiary = Color(0xFFA6F2D1),
  onTertiary = Color(0xFF003826),
  background = Color.Black,
  onBackground = Color(0xFFEAF2F6),
  surfaceContainerLow = Color(0xFF141B1F),
  surfaceContainer = Color(0xFF1C252B),
  surfaceContainerHigh = Color(0xFF26323A),
  onSurface = Color(0xFFEAF2F6),
  onSurfaceVariant = Color(0xFFB4C5CE),
)

@Composable
fun WatchSpeakerPlayerTheme(content: @Composable () -> Unit) {
  MaterialTheme(
    colorScheme = WatchDarkColorScheme,
    content = content,
  )
}
