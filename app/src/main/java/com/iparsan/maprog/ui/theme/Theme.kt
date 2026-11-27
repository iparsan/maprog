package com.iparsan.maprog.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val MaprogGreen = Color(0xFF2E7D32)
val MaprogGreenDark = Color(0xFF1B5E20)
val TrackOrange = Color(0xFFFF6D00)
val PositionBlue = Color(0xFF1E88E5)
val RecRed = Color(0xFFE53935)

private val Light = lightColorScheme(
    primary = MaprogGreen,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC8E6C9),
    onPrimaryContainer = MaprogGreenDark,
    secondary = TrackOrange,
    onSecondary = Color.White,
    tertiary = PositionBlue,
    error = RecRed,
)

private val Dark = darkColorScheme(
    primary = Color(0xFF81C784),
    onPrimary = Color(0xFF003300),
    primaryContainer = MaprogGreenDark,
    secondary = Color(0xFFFFAB40),
    tertiary = Color(0xFF64B5F6),
    error = Color(0xFFEF9A9A),
)

@Composable
fun MaprogTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
