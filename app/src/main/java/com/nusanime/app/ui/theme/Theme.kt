package com.nusanime.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Palette matches AnimeX-App (HatsuneMikuUwU) — soft blue scheme
val PrimaryLight = Color(0xFF415F91)
val PrimaryDark = Color(0xFFAAC7FF)
val BackgroundLight = Color(0xFFF9F9FF)
val BackgroundDark = Color(0xFF111318)
val SurfaceContainerLight = Color(0xFFEDEDF4)
val SurfaceContainerDark = Color(0xFF1D2024)

private val LightColors = lightColorScheme(
    primary = PrimaryLight,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD6E3FF),
    onPrimaryContainer = Color(0xFF001B3F),
    secondary = Color(0xFF565F71),
    secondaryContainer = Color(0xFFDAE2F9),
    tertiary = Color(0xFF705575),
    background = BackgroundLight,
    onBackground = Color(0xFF191C20),
    surface = BackgroundLight,
    onSurface = Color(0xFF191C20),
    surfaceVariant = Color(0xFFE0E2EC),
    onSurfaceVariant = Color(0xFF44474E),
    surfaceContainer = SurfaceContainerLight,
    surfaceContainerLow = Color(0xFFF3F3FA),
    surfaceContainerHigh = Color(0xFFE7E8EE),
    outline = Color(0xFF74777F),
)

private val DarkColors = darkColorScheme(
    primary = PrimaryDark,
    onPrimary = Color(0xFF0A305F),
    primaryContainer = Color(0xFF284777),
    onPrimaryContainer = Color(0xFFD6E3FF),
    secondary = Color(0xFFBEC6DC),
    secondaryContainer = Color(0xFF3E4759),
    tertiary = Color(0xFFDDBCE0),
    background = BackgroundDark,
    onBackground = Color(0xFFE2E2E9),
    surface = BackgroundDark,
    onSurface = Color(0xFFE2E2E9),
    surfaceVariant = Color(0xFF44474E),
    onSurfaceVariant = Color(0xFFC4C6D0),
    surfaceContainer = SurfaceContainerDark,
    surfaceContainerLow = Color(0xFF191C20),
    surfaceContainerHigh = Color(0xFF282A2F),
    outline = Color(0xFF8E9099),
)

@Composable
fun NusanimeTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
