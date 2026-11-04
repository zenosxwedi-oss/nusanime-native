package com.nusanime.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.style.TextAlign

/** Movie Bar watch screen — TMDB detail + native ExoPlayer m3u8 (relay server). */
@Composable
fun MovieScreen(slug: String, onBack: () -> Unit) {
    val tmdbId = slug.removePrefix("tmdb:").toIntOrNull()
    var detail by remember(slug) { mutableStateOf<com.nusanime.app.data.api.MovieSource.M2Detail?>(null) }
    var m3u8 by remember(slug) { mutableStateOf<String?>(null) }
    var failed by remember(slug) { mutableStateOf(false) }

    LaunchedEffect(slug) {
        if (tmdbId == null) { failed = true; return@LaunchedEffect }
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val p = com.nusanime.app.data.api.MovieSource.detail(tmdbId)
            detail = p
            m3u8 = com.nusanime.app.data.api.MovieSource.play(tmdbId)
        }
        if (m3u8 == null) failed = true
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Kembali") }
            Text("Movie Bar", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (failed) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text("Stream tidak tersedia untuk judul ini", textAlign = TextAlign.Center)
            }
        } else if (m3u8 == null) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            val ctx = androidx.compose.ui.platform.LocalContext.current
            val player = remember(m3u8) {
                androidx.media3.exoplayer.ExoPlayer.Builder(ctx).build().apply {
                    setMediaItem(androidx.media3.common.MediaItem.Builder().setUri(m3u8!!)
                        .setMimeType(androidx.media3.common.MimeTypes.APPLICATION_M3U8).build())
                    playWhenReady = true; prepare()
                }
            }
            DisposableEffect(m3u8) { onDispose {
                player.release()
                val act0 = ctx as? android.app.Activity ?: return@onDispose
                val w0 = act0.window
                androidx.core.view.WindowCompat.setDecorFitsSystemWindows(w0, true)
                androidx.core.view.WindowInsetsControllerCompat(w0, w0.decorView).show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                act0.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            } }
            var isFs by remember(m3u8) { mutableStateOf(false) }
            val mod = if (isFs) Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black)
                      else Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(androidx.compose.ui.graphics.Color.Black)
            LaunchedEffect(isFs) {
                val act = ctx as? android.app.Activity ?: return@LaunchedEffect
                val w = act.window
                androidx.core.view.WindowCompat.setDecorFitsSystemWindows(w, !isFs)
                val c = androidx.core.view.WindowInsetsControllerCompat(w, w.decorView)
                if (isFs) {
                    c.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                    c.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    act.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                } else {
                    c.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                    act.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
            }
            AndroidView(
                modifier = mod,
                factory = { pp ->
                    androidx.media3.ui.PlayerView(pp).apply {
                        this.player = player; useController = true; controllerShowTimeoutMs = 4000
                        setFullscreenButtonClickListener { fs -> isFs = fs }
                    }
                },
                update = { v -> v.layoutParams = v.layoutParams?.apply { width = android.view.ViewGroup.LayoutParams.MATCH_PARENT; height = if (isFs) android.view.ViewGroup.LayoutParams.MATCH_PARENT else android.view.ViewGroup.LayoutParams.WRAP_CONTENT } },
            )
            val d = detail
            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
                Text(d?.title ?: "", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(12.dp))
                Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    d?.year?.takeIf { it.isNotEmpty() }?.let { AssistChip(onClick = {}, label = { Text(it) }) }
                    d?.rating?.takeIf { it > 0 }?.let { AssistChip(onClick = {}, label = { Text("★ %.1f".format(it)) }) }
                    d?.runtime?.let { AssistChip(onClick = {}, label = { Text("${it}m") }) }
                }
                if (!d?.genres.isNullOrEmpty()) {
                    Text("Genre: " + d!!.genres.joinToString(", "), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))
                }
                d?.overview?.takeIf { it.isNotEmpty() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                }
            }
        }
    }
}
