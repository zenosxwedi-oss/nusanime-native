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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.background

/** Movie watch screen — WebView vidlink embed (multi-server). */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MovieScreen(slug: String, onBack: () -> Unit) {
    var servers by remember(slug) { mutableStateOf<List<com.nusanime.app.data.api.MovieSource.MServer>>(emptyList()) }
    var current by remember(slug) { mutableStateOf<com.nusanime.app.data.api.MovieSource.MServer?>(null) }
    var loading by remember(slug) { mutableStateOf(true) }
    var stuckAt by remember(slug) { mutableStateOf(0L) }

    LaunchedEffect(slug) {
        val list = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            com.nusanime.app.data.api.MovieSource.embeds(slug)
        }
        servers = list
        current = list.firstOrNull()
        loading = false
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Kembali") }
            Text("Movie", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (loading) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (current == null) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text("Server tidak tersedia", textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        } else if (current!!.url.startsWith("movieplay:")) {
            // NATIVE HD — server-resolved vidsrc m3u8 via our relay
            var m3u8 by remember(current?.url) { mutableStateOf<String?>(null) }
            LaunchedEffect(current?.url) {
                m3u8 = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    com.nusanime.app.data.api.MovieSource.hdStream(current!!.url.removePrefix("movieplay:"))
                }
            }
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                val mm = m3u8
                if (mm == null) CircularProgressIndicator()
                else {
                    val ctx = androidx.compose.ui.platform.LocalContext.current
                    val player = remember(mm) {
                        androidx.media3.exoplayer.ExoPlayer.Builder(ctx).build().apply {
                            setMediaItem(androidx.media3.common.MediaItem.Builder().setUri(mm)
                                .setMimeType(androidx.media3.common.MimeTypes.APPLICATION_M3U8).build())
                            playWhenReady = true; prepare()
                        }
                    }
                    DisposableEffect(mm) { onDispose { player.release() } }
                    AndroidView(
                        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(androidx.compose.ui.graphics.Color.Black),
                        factory = { p ->
                            androidx.media3.ui.PlayerView(p).apply { this.player = player; useController = true }
                        },
                    )
                }
            }
        } else {
            // auto-advance to next server if embed stuck >15s
            LaunchedEffect(current?.url) {
                kotlinx.coroutines.delay(15000)
                if (servers.size > 1) {
                    val i = servers.indexOf(current)
                    current = servers[(i + 1) % servers.size]
                }
            }
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { c ->
                    WebView(c).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        settings.userAgentString = com.nusanime.app.data.api.JAV_UA
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                        webChromeClient = WebChromeClient()
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(v: android.webkit.WebView, url: String?) {
                                stuckAt = android.os.SystemClock.elapsedRealtime()
                            }
                        }
                        loadUrl(current!!.url, mapOf("Referer" to "https://lk21.tel/"))
                    }
                },
                update = { wv -> if (current != null && wv.url != current!!.url) wv.loadUrl(current!!.url, mapOf("Referer" to "https://lk21.tel/")) },
            )
            }
            }
            }
        }
        if (servers.size > 1) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                servers.forEachIndexed { idx, s ->
                    FilterChip(selected = current == s, onClick = { current = s }, label = { Text(s.label) })
                }
            }
        }
    }
}
