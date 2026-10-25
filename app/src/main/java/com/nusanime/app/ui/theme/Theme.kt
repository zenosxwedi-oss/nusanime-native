package com.nusanime.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Purple = Color(0xFF8B5CF6)
val PurpleDark = Color(0xFF6D28D9)
val PurpleContainer = Color(0xFFEDE9FE)

private val LightColors = lightColorScheme(
    primary = PurpleDark,
    secondary = Purple,
    primaryContainer = PurpleContainer,
    surface = Color(0xFFFAFAFA),
    background = Color(0xFFFAFAFA),
)

private val DarkColors = darkColorScheme(
    primary = Purple,
    secondary = Purple.copy(alpha = 0.7f),
    surface = Color(0xFF141218),
    background = Color(0xFF141218),
)

@Composable
fun NusanimeTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
