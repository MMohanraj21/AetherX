package com.aetherx.assistant.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val CyberCyan = Color(0xFF00F0FF)
val CyberViolet = Color(0xFF7000FF)
val CyberMagenta = Color(0xFFFF0055)
val CyberBgDark = Color(0xFF050811)
val CyberSurface = Color(0xFF0A0E17)
val CyberGlass = Color(0xFF121826)
val CyberTextPrimary = Color(0xFFEAEFFF)
val CyberTextSecondary = Color(0xFF7A889B)

private val CyberColorScheme = darkColorScheme(
    primary = CyberCyan,
    onPrimary = Color.Black,
    secondary = CyberViolet,
    onSecondary = Color.White,
    tertiary = CyberMagenta,
    background = CyberBgDark,
    surface = CyberSurface,
    onBackground = CyberTextPrimary,
    onSurface = CyberTextPrimary
)

@Composable
fun AetherXTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CyberColorScheme,
        content = content
    )
}
