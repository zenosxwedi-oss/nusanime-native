package com.nusanime.app.ui.screens

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.withContext

/**
 * In-app 18+ watch. Primary: native ExoPlayer with direct m3u8 resolved from the
 * embed (no embed ads). Fallback: WebView with referer + Chrome mobile UA.
 * Multi-server chips. route param = slug.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun JavDetailScreen(slug: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var embeds by remember(slug) { mutableStateOf<List<com.nusanime.app.data.api.JavSource.JavServer>>(emptyList()) }
    var current by remember(slug) { mutableStateOf<com.nusanime.app.data.api.JavSource.JavServer?>(null) }
    var m3u8 by remember(slug) { mutableStateOf<String?>(null) }
    var loading by remember(slug) { mutableStateOf(true) }
    var resolving by remember(slug) { mutableStateOf(true) }

    LaunchedEffect(slug) {
        val list = withContext(kotlinx.coroutines.Dispatchers.IO) {
            com.nusanime.app.data.api.JavSource.embeds(slug)
        }
        embeds = list
        current = list.firstOrNull()
        loading = false
        // resolve direct stream for current server (native => skip embed ads)
        current?.let { c ->
            val s = withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.nusanime.app.data.api.JavSource.resolveM3u8(c.url) ?: com.nusanime.app.data.api.JavSource.resolveM3u8Direct(c.url)
            }
            if (s != null) m3u8 = s
            resolving = false
        } ?: run { resolving = false }
    }

    // Re-resolve when switching server
    LaunchedEffect(current?.url) {
        val c = current ?: return@LaunchedEffect
        if (m3u8 == null || embeds.any { it.url == c.url && it != embeds.firstOrNull() }) {
            resolving = true
            m3u8 = null
            val s = withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.nusanime.app.data.api.JavSource.resolveM3u8(c.url) ?: com.nusanime.app.data.api.JavSource.resolveM3u8Direct(c.url)
            }
            m3u8 = s
            resolving = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Kembali") }
            Text("18+ · Konten Dewasa", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        when {
            loading || resolving -> {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
            m3u8 != null -> {
                // NATIVE ExoPlayer — HLS relayed via our server. Direct-first w/ relay fallback handled by retry logic below.
                var triedAlt by remember(m3u8) { mutableStateOf(false) }
                val playErr = remember(m3u8) { androidx.compose.runtime.mutableStateOf<String?>(null) }
                val player = remember(m3u8) {
                    ExoPlayer.Builder(ctx)
                        .setMediaSourceFactory(
                            androidx.media3.exoplayer.source.DefaultMediaSourceFactory(
                                androidx.media3.datasource.DefaultDataSource.Factory(
                                    ctx,
                                    androidx.media3.datasource.DefaultHttpDataSource.Factory()
                                        .setConnectTimeoutMs(30000)
                                        .setReadTimeoutMs(30000)
                                        .setAllowCrossProtocolRedirects(true)
                                        .setUserAgent(com.nusanime.app.data.api.JAV_UA)
                                        .setDefaultRequestProperties(mapOf("Referer" to "https://nekovid.xyz/")),
                                ).setLoadErrorHandlingPolicy(
                                    androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy(6)
                                ),
                            )
                        )
                        .setLoadControl(androidx.media3.exoplayer.DefaultLoadControl.Builder()
                            .setBufferDurationsMs(30000, 120000, 5000, 15000)
                            .build())
                        .build().apply {
                            setMediaItem(MediaItem.Builder()
                                .setUri(m3u8)
                                .setMimeType(MimeTypes.APPLICATION_M3U8)
                                .build())
                            playWhenReady = true
                            prepare()
                            addListener(object : Player.Listener {
                                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                                    playErr.value = "Error: " + error.errorCode
                                    if (!triedAlt) {
                                        triedAlt = true
                                        // primary failed — swap to the other path (relay <-> direct)
                                        val embed = current?.url ?: return
                                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                                            java.util.concurrent.Executors.newSingleThreadExecutor().execute {
                                                val rel = kotlinx.coroutines.runBlocking {
                                                    if (m3u8!!.contains("/hls/")) com.nusanime.app.data.api.JavSource.resolveM3u8Direct(embed)
                                                    else com.nusanime.app.data.api.JavSource.resolveM3u8(embed)
                                                }
                                                if (rel != null) {
                                                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                                                        setMediaItem(MediaItem.Builder().setUri(rel).setMimeType(MimeTypes.APPLICATION_M3U8).build())
                                                        prepare(); playWhenReady = true
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            })
                        }
                }
                DisposableEffect(m3u8) { onDispose { player.release() } }
                Box(Modifier.fillMaxWidth().aspectRatio(16f/9f)) {
                AndroidView(
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(androidx.compose.ui.graphics.Color.Black),
                    factory = { p ->
                        PlayerView(p).apply {
                            this.player = player
                            useController = true
                            controllerShowTimeoutMs = 4000
                            setFullscreenButtonClickListener { isFs ->
                                // toggle landscape fullscreen
                                val act = context as? android.app.Activity ?: return@setFullscreenButtonClickListener
                                if (isFs) act.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                else act.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                            }
                        }
                    },
                )
                playErr.value?.let { Text(it, color = androidx.compose.ui.graphics.Color.White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.Center)) }
                }
            }
            current != null -> {
                // FALLBACK WebView
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                AndroidView(
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
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
                                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                                    // block popunder/redirect ads — keep playback in-frame
                                    return url != null && !url.contains("nekovid") && !url.contains("embed")
                                }
                            }
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                            loadUrl(current!!.url, mapOf("Referer" to "https://javsubindo.com/"))
                        }
                    },
                    update = { wv -> if (wv.url != current!!.url) wv.loadUrl(current!!.url, mapOf("Referer" to "https://javsubindo.com/")) },
                )
                }
            }
            else -> {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text("Server tidak tersedia — coba server lain atau lagi nanti", textAlign = TextAlign.Center)
                }
            }
        }

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
