package com.nusanime.app.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.nusanime.app.data.api.ApiClient
import com.nusanime.app.data.model.DownloadData
import com.nusanime.app.data.model.EpisodeData
import com.nusanime.app.ui.ErrorScreen
import com.nusanime.app.ui.LoadingScreen
import kotlinx.coroutines.launch

class PlayerViewModel(private val episodeId: String) : ViewModel() {
    var data by mutableStateOf<EpisodeData?>(null)
    var loading by mutableStateOf(true)
    var error by mutableStateOf<String?>(null)
    var playingUrl by mutableStateOf<String?>(null)
    var prerollActive by mutableStateOf(false)
    var prerollFailed by mutableStateOf(false)
    var adElapsed by mutableStateOf(0)
    var adMuted by mutableStateOf(false)
    var playError by mutableStateOf<String?>(null)
    var resumePositionMs by mutableStateOf(0L)
    var autoNext by mutableStateOf(false)
    var fullscreen by mutableStateOf(false)
    var onEnded: (() -> Unit)? = null

    init { load() }

    /** Persist watch progress to Room. */
    fun saveProgress(context: android.content.Context, positionMs: Long, durationMs: Long) {
        val d = data ?: return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val db = com.nusanime.app.data.db.AppDatabase.get(context)
            db.historyDao().upsert(
                com.nusanime.app.data.db.WatchHistory(
                    episodeId = episodeId,
                    animeId = d.animeId ?: com.nusanime.app.data.PosterCache.get(episodeId)?.animeId ?: "",
                    animeTitle = d.title ?: com.nusanime.app.data.PosterCache.get(episodeId)?.animeTitle ?: "",
                    poster = d.poster ?: com.nusanime.app.data.PosterCache.get(episodeId)?.poster,
                    episodeTitle = episodeId.substringAfterLast('-'),
                    positionMs = positionMs,
                    durationMs = durationMs,
                    updatedAt = System.currentTimeMillis(),
                )
            )
        }
    }

    fun load() {
        viewModelScope.launch {
            error = try {
                val env = com.nusanime.app.data.api.AxSource.episode(episodeId)
                val d = env.data
                if (env.status != "success" || d == null) "Gagal memuat episode (${env.message ?: env.status ?: "data kosong"})"
                else {
                    data = d
                    playError = null
                    // restore resume position from history
                    try {
                        val ctx = com.nusanime.app.MainApplication.instance
                        val h = com.nusanime.app.data.db.AppDatabase.get(ctx).historyDao().get(episodeId)
                        if (h != null && h.durationMs > 0 && h.positionMs < h.durationMs - 15000) {
                            resumePositionMs = h.positionMs
                        }
                    } catch (_: Exception) {}
                    if (d.defaultStreamingUrl.isNullOrBlank()) {
                        playError = "Server streaming tidak tersedia. Tap Coba Lagi."
                    } else {
                        d.defaultStreamingUrl?.let { tryPlay(it) }
                    }
                    null
                }
            } catch (e: Exception) {
                "Gagal memuat episode: ${e.message ?: "kesalahan jaringan"}"
            }
            loading = false
        }
    }

    /** Fetch the real URL behind a serverId, then try to play it. */
    fun playServer(serverId: String) {
        viewModelScope.launch {
            playError = null
            try {
                val env = com.nusanime.app.data.api.AxSource.server(serverId)
                val url = env.data?.url
                if (env.status != "success" || url.isNullOrBlank()) playError = "Server tidak tersedia"
                else tryPlay(url)
            } catch (_: Exception) {
                playError = "Gagal mengambil server"
            }
        }
    }

    private fun tryPlay(url: String) {
        if (BloggerStream.isBlogger(url)) {
            viewModelScope.launch {
                val direct = BloggerStream.extract(url)
                if (direct != null) playingUrl = direct
                else {
                    playingUrl = null
                    playError = "Gagal mengambil stream. Coba server lain."
                }
            }
        } else {
            val direct = directPlayable(url)
            if (direct != null) playingUrl = direct
            else {
                playingUrl = null
                playError = "Server ini tidak bisa diputar langsung. Silakan gunakan tombol download atau coba server lain."
            }
        }
    }

    companion object {
        /** Only URLs pointing at an actual media stream are playable; page embeds are not. */
        fun directPlayable(url: String): String? {
            val u = url.substringBefore('?')
            val direct = listOf(".mp4", ".m3u8", ".mkv", ".webm", ".mpd")
            return if (direct.any { u.lowercase().endsWith(it) }) url else null
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun PlayerScreen(episodeId: String, onBack: () -> Unit, onOpenEpisode: (String) -> Unit) {
    val vm: PlayerViewModel = viewModel(
        key = "player_$episodeId",
        factory = viewModelFactory { initializer { PlayerViewModel(episodeId) } },
    )

    if (vm.fullscreen) {
        // True fullscreen: only the video, edge to edge, with a back button
        Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black)) {
            PlayerArea(vm, fillScreen = true)
            IconButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Kembali",
                    tint = androidx.compose.ui.graphics.Color.White,
                )
            }
        }
        return
    }
    Scaffold(topBar = {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali") }
            Text(vm.data?.title ?: "Nusanime", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }) { pad ->
        if (vm.loading) { LoadingScreen(Modifier.padding(pad)); return@Scaffold }
        vm.error?.let { ErrorScreen(it, Modifier.padding(pad)) { vm.load() }; return@Scaffold }
        val d = vm.data ?: return@Scaffold

        LazyColumn(Modifier.fillMaxSize().padding(pad)) {
            item { PlayerArea(vm) }
            item {
                Column(Modifier.padding(16.dp)) {
                    Text(d.title ?: "-", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    d.releasedOn?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Spacer(Modifier.height(16.dp))

                    Row(
                        Modifier.padding(top = 16.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Lanjut otomatis", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Switch(checked = vm.autoNext, onCheckedChange = { vm.autoNext = it })
                    }
                    if (vm.autoNext) {
                        vm.onEnded = { d.nextEpisode?.episodeId?.let(onOpenEpisode) }
                    }

                    val prev = d.prevEpisode?.episodeId
                    val next = d.nextEpisode?.episodeId
                    if (prev != null || next != null) {
                        Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            prev?.let { OutlinedButton(onClick = { onOpenEpisode(it) }) { Text("← Sebelumnya") } }
                            next?.let { Button(onClick = { onOpenEpisode(it) }) { Text("Selanjutnya →") } }
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun PlayerArea(vm: PlayerViewModel, fillScreen: Boolean = false) {
    val context = LocalContext.current
    var player by remember { mutableStateOf<ExoPlayer?>(null) }

    LaunchedEffect(vm.playingUrl) {
        val url = vm.playingUrl
        player?.release()
        player = null
        if (url != null) {
            val p = ExoPlayer.Builder(context).build()
            var retryCount = 0
            p.addListener(object : androidx.media3.common.Player.Listener {
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    if (vm.prerollActive) {
                        // ad failed to load: pause on black screen until user taps skip (do NOT auto-advance to content)
                        player?.pause()
                        vm.prerollFailed = true
                        return
                    }
                    // automatic retries for transient network errors (e.g. IO_NETWORK_CONNECTION_FAILED)
                    if (retryCount < 3) {
                        retryCount++
                        val pl = player
                        if (pl != null) {
                            pl.prepare(); pl.play()
                            return
                        }
                    }
                    vm.playError = "Gagal memutar: " + (error.errorCodeName ?: error.message ?: "unknown")
                    vm.playingUrl = null
                }
                override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                    if (vm.prerollActive && reason == androidx.media3.common.Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && !vm.prerollFailed) {
                        // ad finished naturally
                        vm.prerollActive = false
                        player?.volume = 1f
                    }
                }
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == androidx.media3.common.Player.STATE_ENDED) {
                        vm.prerollActive = false
                        vm.saveProgress(context, 0, 0)
                        if (vm.autoNext) vm.onEnded?.invoke()
                    }
                }
            })
            // Pre-roll ad (horizontal/vertical zones, random): playlist = [ad, content]
            com.nusanime.app.ui.player.VastAd.fetch { adMedia ->
                val content = MediaItem.fromUri(url)
                if (adMedia != null) {
                    val ad = MediaItem.fromUri(adMedia)
                    p.setMediaItems(listOf(ad, content))
                    vm.prerollActive = true
                    vm.prerollFailed = false
                    vm.adElapsed = 0
                    player?.volume = 0f
                    retryCount = 0
                } else {
                    p.setMediaItem(content)
                }
                if (vm.resumePositionMs > 0) p.seekTo(vm.resumePositionMs)
                p.prepare()
                p.playWhenReady = true
            }
            player = p
        }
    }

    // Save progress every 10s while playing
    LaunchedEffect(vm.playingUrl) {
        while (true) {
            kotlinx.coroutines.delay(10_000)
            val pl = player ?: break
            if (pl.isPlaying && !vm.prerollActive) vm.saveProgress(context, pl.currentPosition, pl.duration.coerceAtLeast(0))
        }
    }

    // Skip intro automatically: check aniskip once per URL, seek past op when reached
    var skipIntro by remember(vm.playingUrl) { mutableStateOf<SkipIntro.Interval?>(null) }
    LaunchedEffect(vm.playingUrl) {
        val itv = SkipIntro.fetch(null, 0)
        skipIntro = itv
    }
    LaunchedEffect(vm.playingUrl, skipIntro) {
        val itv = skipIntro ?: return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(1_000)
            val pl = player ?: break
            if (pl.currentPosition >= itv.startMs && pl.currentPosition < itv.endMs - 500) {
                pl.seekTo(itv.endMs)
                break
            }
        }
    }
    DisposableEffect(Unit) {
        val act = context as? android.app.Activity
        onDispose {
            player?.release()
            // Leaving player screen entirely: always restore portrait
            try { act?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT } catch (_: Exception) {}
        }
    }

    // Fullscreen mode: hide status bars, landscape, player fills screen
    val activity = context as? android.app.Activity

    fun enterFullscreen() {
        try {
            vm.fullscreen = true
            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            activity?.window?.let { w ->
                androidx.core.view.WindowCompat.setDecorFitsSystemWindows(w, false)
                val c = androidx.core.view.WindowInsetsControllerCompat(w, w.decorView)
                c.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                c.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
            // cutout: let video render into notch area
            activity?.window?.attributes?.let { a ->
                a.layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                activity.window.attributes = a
            }
        } catch (_: Exception) {}
    }

    fun exitFullscreen() {
        try {
            vm.fullscreen = false
            // Force back to portrait so the app doesn't stay sideways ("miring") after exiting fullscreen
            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            activity?.window?.let { w ->
                val c = androidx.core.view.WindowInsetsControllerCompat(w, w.decorView)
                c.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                androidx.core.view.WindowCompat.setDecorFitsSystemWindows(w, true)
            }
        } catch (_: Exception) {}
    }

    BackHandler(enabled = vm.fullscreen) { exitFullscreen() }

    // In fullscreen: fill the entire screen (FIT/FILL) so subtitle at the bottom edge stays visible.
    // In normal (portrait inline) mode: keep 16:9 letterbox.
    val playerModifier = if (fillScreen) Modifier.fillMaxSize() else Modifier.fillMaxWidth().aspectRatio(16f / 9f)

    // Keep screen ON while video plays (no sleep/dim during playback)
    val currentUrl = vm.playingUrl
    val isPlaying = player != null && currentUrl != null
    DisposableEffect(isPlaying) {
        val activityCtx = context as? android.app.Activity
        val window = activityCtx?.window
        var added = false
        if (isPlaying && window != null) {
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            added = true
        }
        onDispose { if (added) window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    Box(playerModifier.background(androidx.compose.ui.graphics.Color.Black)) {
        // Pre-roll ad overlay (web-style): countdown chip + Suara toggle + skip
        if (vm.prerollActive) {
            LaunchedEffect(vm.prerollActive) {
                while (vm.prerollActive) {
                    kotlinx.coroutines.delay(1000)
                    vm.adElapsed++
                }
            }
            val remaining = (15 - vm.adElapsed).coerceAtLeast(0)
            Text(
                "Iklan · Lewati dalam ${remaining}s",
                color = androidx.compose.ui.graphics.Color.White,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 10.dp)
                    .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f), MaterialTheme.shapes.small)
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            )
            FilledTonalButton(
                onClick = {
                    vm.adMuted = !vm.adMuted
                    player?.volume = if (vm.adMuted) 1f else 0f
                },
                modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp),
                colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                    containerColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f),
                    contentColor = androidx.compose.ui.graphics.Color.White,
                ),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            ) { Text(if (vm.adMuted) "🔊 Suara" else "🔇 Bisu", style = MaterialTheme.typography.labelMedium) }
        }
        // Pre-roll ad skip button (visible during ad playback)
        if (vm.prerollActive) {
            val pl = player
            androidx.compose.material3.FilledTonalButton(
                onClick = { vm.prerollActive = false; vm.prerollFailed = false; player?.volume = 1f; pl?.seekToNextMediaItem(); pl?.play() },
                modifier = Modifier.align(Alignment.TopStart).padding(top = 64.dp, start = 10.dp),
                colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                    containerColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f),
                    contentColor = androidx.compose.ui.graphics.Color.White,
                ),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 4.dp),
            ) { Text("Lewati Iklan ⏭", style = MaterialTheme.typography.labelMedium) }
        }
        // Quality/server switcher inside the video — top-left so it never collides with the bottom subtitle area
        val serversAll = vm.data?.server?.qualities.orEmpty()
        if (serversAll.isNotEmpty() && !fillScreen) {
            androidx.compose.foundation.lazy.LazyRow(
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
            ) {
                for (q in serversAll) {
                    for (sv in q.serverList.orEmpty()) {
                        item {
                            androidx.compose.material3.FilledTonalButton(
                                onClick = { sv.serverId?.let { vm.playServer(it) } },
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                                    containerColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.55f),
                                    contentColor = androidx.compose.ui.graphics.Color.White,
                                ),
                            ) { Text(q.title ?: sv.title ?: "OK", style = MaterialTheme.typography.labelSmall) }
                        }
                    }
                }
            }
        }
        val url = vm.playingUrl
        if (url != null && player != null) {
            AndroidView(
                factory = {
                    PlayerView(it).apply {
                        useController = true
                        resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                        setFullscreenButtonClickListener { isFs ->
                            if (isFs) enterFullscreen() else exitFullscreen()
                        }
                    }
                },
                update = { it.player = player },
                modifier = Modifier.fillMaxSize(),
            )
        } else if (vm.playError != null) {
            Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(vm.playError!!, color = androidx.compose.ui.graphics.Color.White, style = MaterialTheme.typography.bodyMedium)
                val servers = vm.data?.server?.qualities.orEmpty().flatMap { it.serverList.orEmpty() }
                if (servers.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text("Coba server lain di bawah.", color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(14.dp))
                Button(onClick = {
                    vm.playError = null
                    vm.loading = true
                    vm.load()
                }) { Text("🔄 Coba Lagi") }
            }
        } else {
            CircularProgressIndicator(Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.primary)
        }
    }
}

private fun flattenDownloads(d: DownloadData?): List<Pair<String, String>> {
    val out = mutableListOf<Pair<String, String>>()
    d?.formats?.forEach { f ->
        f.qualities?.forEach { q ->
            q.urls?.forEach { u ->
                val url = u.url ?: return@forEach
                out.add(Pair("${f.title.orEmpty()} • ${q.title.orEmpty()} • ${u.title.orEmpty()}", url))
            }
        }
    }
    return out.distinctBy { it.first + it.second }
}

@Composable
private fun DownloadLinkRow(label: String, url: String) {
    val ctx = LocalContext.current
    Surface(
        onClick = {
            val i = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
            try { ctx.startActivity(i) } catch (_: Exception) {}
        },
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(label, Modifier.padding(horizontal = 12.dp, vertical = 10.dp), style = MaterialTheme.typography.bodySmall, maxLines = 1)
    }
}
