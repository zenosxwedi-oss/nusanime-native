package com.nusanime.app.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Splash screen shown at cold start: anime key-visual background, fade-in,
 * NUSANIME wordmark and loading dots.
 */
@Composable
fun SplashScreen() {
    var alpha by remember { mutableStateOf(0f) }

    val transition = rememberInfiniteTransition(label = "dots")
    val dotAlpha by transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "dot",
    )

    LaunchedEffect(Unit) {
        delay(60)
        alpha = 1f
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF111318)),
    ) {
        // Gradient background (instant, no blank image)
        Box(
            Modifier
                .fillMaxSize(),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color(0xFF111318).copy(alpha = 0.35f),
                            0.5f to Color(0xFF111318).copy(alpha = 0.55f),
                            1f to Color(0xFF111318),
                        )
                    ),
            )
        }

        Column(
            Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "NUSANIME",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Black,
                color = Color.White,
                modifier = Modifier.alpha(alpha),
            )
            Text(
                "Nonton anime, kapan saja",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.alpha(alpha),
            )
            Spacer(Modifier.height(24.dp))
            Text("●  ●  ●", color = Color(0xFFAAC7FF).copy(alpha = dotAlpha), style = MaterialTheme.typography.titleMedium)
        }

        Text(
            "v3.3.0",
            color = Color.White.copy(alpha = 0.4f),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
        )
    }
}
