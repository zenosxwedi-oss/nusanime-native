package com.nusanime.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.style.TextAlign
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

/** Movie Bar watch screen — fullscreen proper + keep screen on + subtitle Indonesia otomatis. */
@Composable
fun MovieScreen(slug: String, onBack: () -> Unit) {
    val tmdbId = slug.removePrefix("tmdb:").toIntOrNull()
    var detail by remember(slug) { mutableStateOf<com.nusanime.app.data.api.MovieSource.M2Detail?>(null) }
    var m3u8 by remember(slug) { mutableStateOf<String?>(null) }
    var failed by remember(slug) { mutableStateOf(false) }
    var subUrl by remember(slug) { mutableStateOf<String?>(null) }
    var subState by remember(slug) { mutableStateOf("none") } // none | loading | on | na

    LaunchedEffect(slug) {
        if (tmdbId == null) { failed = true; return@LaunchedEffect }
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val p = com.nusanime.app.data.api.MovieSource.detail(tmdbId)
            detail = p
            m3u8 = com.nusanime.app.data.api.MovieSource.play(tmdbId)
        }
        if (m3u8 == null) failed = true
    }

    // Auto subtitle Indonesia: setelah stream siap, cari SRT Indonesia via server.
    LaunchedEffect(m3u8, subUrl) {
        if (m3u8 != null && subUrl == null && subState == "none") {
            subState = "loading"
            val imdb = detail?.imdbId
            val url = if (imdb != null) com.nusanime.app.data.api.MovieSource.subs(imdb) else null
            if (url != null) { subUrl = url; subState = "on" } else subState = "na"
        }
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
            val ctx = LocalContext.current
            val player = remember(m3u8) {
                ExoPlayer.Builder(ctx).build().apply {
                    playWhenReady = true
                }
            }
            // Attach media item + subtitle side-load saat subUrl berubah.
            DisposableEffect(m3u8, subUrl) {
                val builder = MediaItem.Builder().setUri(m3u8!!)
                    .setMimeType(MimeTypes.APPLICATION_M3U8)
                if (subUrl != null) {
                    val subConf = androidx.media3.common.MediaItem.SubtitleConfiguration
                        .Builder(android.net.Uri.parse(subUrl!!))
                        .setMimeType(MimeTypes.APPLICATION_SUBRIP)
                        .setLanguage("id")
                        .setSelectionFlags(androidx.media3.common.C.SELECTION_FLAG_DEFAULT or androidx.media3.common.C.SELECTION_FLAG_AUTOSELECT)
                        .setLabel("Indonesia")
                        .build()
                    builder.setSubtitleConfigurations(listOf(subConf))
                }
                player.setMediaItem(builder.build())
                player.prepare()
                onDispose { }
            }
            DisposableEffect(m3u8) { onDispose {
                player.release()
                val act0 = ctx as? android.app.Activity ?: return@onDispose
                val w0 = act0.window
                w0.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                androidx.core.view.WindowCompat.setDecorFitsSystemWindows(w0, true)
                androidx.core.view.WindowInsetsControllerCompat(w0, w0.decorView).show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                act0.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            } }

            // Keep screen on selama ada player.
            DisposableEffect(m3u8) {
                val act = ctx as? android.app.Activity
                act?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                onDispose { act?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
            }

            var isFs by remember(m3u8) { mutableStateOf(false) }
            // Back saat fullscreen keluar fullscreen dulu, bukan keluar halaman.
            val backDisp = remember(isFs) {
                object : androidx.activity.OnBackPressedCallback(true) {
                    override fun handleOnBackPressed() {
                        if (isFs) isFs = false else { this.isEnabled = false; onBack() }
                    }
                }
            }
            DisposableEffect(isFs) {
                val act = ctx as? android.app.Activity
                (act as? androidx.activity.ComponentActivity)?.onBackPressedDispatcher?.addCallback(backDisp)
                onDispose { backDisp.remove() }
            }
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
                } else {
                    c.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                }
            }
            AndroidView(
                modifier = mod,
                factory = { pp ->
                    PlayerView(pp).apply {
                        this.player = player; useController = true; controllerShowTimeoutMs = 4000
                        setFullscreenButtonClickListener { fs -> isFs = fs }
                    }
                },
                update = { v -> v.layoutParams = v.layoutParams?.apply { width = android.view.ViewGroup.LayoutParams.MATCH_PARENT; height = if (isFs) android.view.ViewGroup.LayoutParams.MATCH_PARENT else android.view.ViewGroup.LayoutParams.WRAP_CONTENT } },
            )
            val d = detail
            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
                Text(d?.title ?: "", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(12.dp))
                Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    d?.year?.takeIf { it.isNotEmpty() }?.let { AssistChip(onClick = {}, label = { Text(it) }) }
                    d?.rating?.takeIf { it > 0 }?.let { AssistChip(onClick = {}, label = { Text("★ %.1f".format(it)) }) }
                    d?.runtime?.let { AssistChip(onClick = {}, label = { Text("${it}m") }) }
                }
                if (subState == "on" || subState == "na") {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        AssistChip(
                            onClick = {},
                            label = { Text(if (subState == "on") "💬 Subtitle Indonesia: aktif" else "💬 Subtitle Indonesia: tidak tersedia") },
                        )
                    }
                } else if (subState == "loading") {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text("  Mencari subtitle Indonesia…", style = MaterialTheme.typography.bodySmall)
                    }
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
