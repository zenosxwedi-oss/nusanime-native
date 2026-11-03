package com.nusanime.app.ui.screens

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** In-app 18+ watch: extracts iframe embeds from the Nekopoi page and plays in WebView (no app ads on 18+ content). */
object JavDetail {
    suspend fun embeds(pageUrl: String): List<String> = withContext(Dispatchers.IO) {
        try {
            val conn = URL(pageUrl).openConnection() as HttpURLConnection
            conn.connectTimeout = 10000; conn.readTimeout = 15000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 12)")
            conn.instanceFollowRedirects = true
            if (conn.responseCode !in 200..299) return@withContext emptyList()
            val html = conn.inputStream.bufferedReader().use { it.readText() }
            val re = Regex("""<iframe[^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            re.findAll(html)
                .map { it.groupValues[1] }
                .filter { !it.contains("a-ads") && !it.contains("discordapp") && !it.contains("ad.") }
                .map { if (it.startsWith("//")) "https:$it" else it }
                .toList()
                .distinct()
        } catch (_: Exception) { emptyList() }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun JavDetailScreen(pageUrl: String, onBack: () -> Unit) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var embeds by remember { mutableStateOf<List<String>>(emptyList()) }
    var current by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(pageUrl) {
        embeds = JavDetail.embeds(pageUrl)
        current = embeds.firstOrNull()
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
                Text("Server tidak tersedia, coba lagi nanti")
            }
        } else {
            AndroidView(
                modifier = Modifier.fillMaxWidth().weight(1f),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        webChromeClient = WebChromeClient()
                        webViewClient = WebViewClient()
                        loadUrl(current!!)
                    }
                },
                update = { wv -> if (wv.url != current) wv.loadUrl(current!!) },
            )
            if (embeds.size > 1) {
                Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
