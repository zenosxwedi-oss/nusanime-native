package com.nusanime.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color

// Theme inspired by dark anime apps (Trakteer-style):
// deep navy background, vivid red accent
val AccentRed = Color(0xFFFF4757)
val BgDark = Color(0xFF0F1420)
val BgDarker = Color(0xFF0A0E17)
val CardDark = Color(0xFF1A2130)
val BgLight = Color(0xFFF6F7FB)
val CardLight = Color(0xFFFFFFFF)

// Theme mode persisted: 0 = system, 1 = light, 2 = dark
object ThemePrefs {
    private const val PREFS = "theme_prefs"
    private const val KEY = "mode"
    fun mode(context: android.content.Context): Int =
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).getInt(KEY, 0)
    fun setMode(context: android.content.Context, value: Int) {
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).edit().putInt(KEY, value).apply()
    }
}

private val DarkColors = darkColorScheme(
    primary = AccentRed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3D1A20),
    onPrimaryContainer = Color(0xFFFFD9DC),
    secondary = Color(0xFF8A94A8),
    secondaryContainer = CardDark,
    background = BgDark,
    onBackground = Color(0xFFE9ECF3),
    surface = BgDark,
    onSurface = Color(0xFFE9ECF3),
    surfaceVariant = CardDark,
    onSurfaceVariant = Color(0xFF9AA3B5),
    surfaceContainer = CardDark,
    surfaceContainerLow = BgDarker,
    surfaceContainerHigh = Color(0xFF232B3D),
    outline = Color(0xFF3A4358),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFFD32F2F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD6),
    onPrimaryContainer = Color(0xFF410002),
    secondary = Color(0xFF565F71),
    background = BgLight,
    onBackground = Color(0xFF191C20),
    surface = BgLight,
    onSurface = Color(0xFF191C20),
    surfaceVariant = Color(0xFFE0E2EC),
    onSurfaceVariant = Color(0xFF44474E),
    surfaceContainer = CardLight,
)

@Composable
fun NusanimeTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val mode = remember { ThemePrefs.mode(ctx) }
    val useDark = when (mode) {
        1 -> false
        2 -> true
        else -> darkTheme
    }
    MaterialTheme(
        colorScheme = if (useDark) DarkColors else LightColors,
        content = content,
    )
}
