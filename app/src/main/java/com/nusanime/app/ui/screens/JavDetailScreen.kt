package com.nusanime.app.ui.screens

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * In-app 18+ watch: extracts iframe embeds from the javsubindo post page and
 * plays them in a WebView (no app ads on 18+ content).
 * route param = slug.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun JavDetailScreen(slug: String, onBack: () -> Unit) {
    var embeds by remember(slug) { mutableStateOf<List<String>>(emptyList()) }
    var current by remember(slug) { mutableStateOf<String?>(null) }
    var loading by remember(slug) { mutableStateOf(true) }

    LaunchedEffect(slug) {
        val list = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            com.nusanime.app.data.api.JavSource.embeds(slug)
        }
        embeds = list
        current = list.firstOrNull()
        loading = false
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Kembali") }
            Text("18+ · Konten Dewasa", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) { CircularProgressIndicator() }
        } else if (current == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text("Server tidak tersedia — coba lagi nanti", textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        } else {
            AndroidView(
                modifier = Modifier.fillMaxWidth().weight(1f),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        settings.userAgentString = "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36"
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                        webChromeClient = WebChromeClient()
                        webViewClient = WebViewClient()
                        loadUrl(current!!, mapOf("Referer" to "https://javsubindo.com/"))
                    }
                },
                update = { wv -> if (wv.url != current) wv.loadUrl(current!!, mapOf("Referer" to "https://javsubindo.com/")) },
            )
            if (embeds.size > 1) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    embeds.forEachIndexed { idx, e ->
                        FilterChip(
                            selected = current == e,
                            onClick = { current = e },
                            label = { Text("Server ${idx + 1}") },
                        )
                    }
                }
            }
        }
    }
}
