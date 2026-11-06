package com.nusanime.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ===== DARK CYBER ELEGANT =====
// Latar hitam pekat, aksen cyan neon, tipografi tegas. Ringan: tanpa gradien berat.
val CyberBg = Color(0xFF06080D)          // hitam pekat
val CyberSurface = Color(0xFF0C1017)     // surface
val CyberCard = Color(0xFF111722)        // kartu
val CyberCardHigh = Color(0xFF18202E)    // kartu terangkat
val CyberAccent = Color(0xFF00E5FF)      // cyan neon
val CyberAccentDim = Color(0xFF0891B2)
val CyberText = Color(0xFFE6F1FF)
val CyberTextDim = Color(0xFF8A97AB)
val CyberOutline = Color(0xFF1E2A3A)
val CyberOnAccent = Color(0xFF00131A)

val AccentRed = CyberAccent   // kompat alias
val BgDark = CyberBg

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

private val CyberColors = darkColorScheme(
    primary = CyberAccent,
    onPrimary = CyberOnAccent,
    primaryContainer = Color(0xFF003540),
    onPrimaryContainer = Color(0xFFB8F6FF),
    secondary = CyberAccentDim,
    secondaryContainer = CyberCard,
    onSecondaryContainer = CyberText,
    background = CyberBg,
    onBackground = CyberText,
    surface = CyberSurface,
    onSurface = CyberText,
    surfaceVariant = CyberCard,
    onSurfaceVariant = CyberTextDim,
    surfaceContainer = CyberCard,
    surfaceContainerLow = CyberSurface,
    surfaceContainerHigh = CyberCardHigh,
    outline = CyberOutline,
    error = Color(0xFFFF5470),
)

private val CyberType = Typography(
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp, letterSpacing = 0.2.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 17.sp, letterSpacing = 0.1.sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp),
)

// Mode lama: "light" tidak ada lagi — selalu dark cyber (lebih ringan & konsisten)
@Composable
fun NusanimeTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CyberColors,
        typography = CyberType,
        content = content,
    )
}
