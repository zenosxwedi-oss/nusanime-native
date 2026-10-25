package com.nusanime.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
    var playError by mutableStateOf<String?>(null)
    var resumePositionMs by mutableStateOf(0L)
    var autoNext by mutableStateOf(false)
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
                    animeId = d.animeId ?: "",
                    animeTitle = d.title ?: "",
                    poster = d.poster,
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
                val env = ApiClient.api.episode(episodeId)
                val d = env.data
                if (env.status != "success" || d?.title == null) "Gagal memuat episode (${env.message ?: env.status ?: "data kosong"})"
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
                    d.defaultStreamingUrl?.let { tryPlay(it) }
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
                val env = ApiClient.api.server(serverId)
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

                    val serverList = d.server?.qualities.orEmpty()
                        .flatMap { q -> q.serverList.orEmpty().map { it to q.title.orEmpty() } }
                        .filter { (s, _) -> s.serverId != null }
                        .filter { (s, _) -> !(s.title ?: "").contains("VIP", true) }
                    if (serverList.isNotEmpty()) {
                        Text("Server Streaming", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            serverList.forEachIndexed { i, (s, quality) ->
                                val label = buildString {
                                    append("Server ${i + 1}")
                                    if (quality.isNotBlank() && quality != "unknown") append(" • $quality")
                                }
                                OutlinedButton(onClick = { s.serverId?.let(vm::playServer) }) { Text(label) }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }

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
private fun PlayerArea(vm: PlayerViewModel) {
    val context = LocalContext.current
    var player by remember { mutableStateOf<ExoPlayer?>(null) }

    LaunchedEffect(vm.playingUrl) {
        val url = vm.playingUrl
        player?.release()
        player = null
        if (url != null) {
            val p = ExoPlayer.Builder(context).build()
            p.addListener(object : androidx.media3.common.Player.Listener {
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    vm.playError = "Gagal memutar: " + (error.errorCodeName ?: error.message ?: "unknown")
                    vm.playingUrl = null
                }
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == androidx.media3.common.Player.STATE_ENDED) {
                        vm.saveProgress(context, 0, 0)
                        if (vm.autoNext) vm.onEnded?.invoke()
                    }
                }
            })
            p.setMediaItem(MediaItem.fromUri(url))
            if (vm.resumePositionMs > 0) p.seekTo(vm.resumePositionMs)
            p.prepare()
            p.playWhenReady = true
            player = p
        }
    }

    // Save progress every 10s while playing
    LaunchedEffect(vm.playingUrl) {
        while (true) {
            kotlinx.coroutines.delay(10_000)
            val pl = player ?: break
            if (pl.isPlaying) vm.saveProgress(context, pl.currentPosition, pl.duration.coerceAtLeast(0))
        }
    }
    DisposableEffect(Unit) { onDispose { player?.release() } }

    Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(androidx.compose.ui.graphics.Color.Black)) {
        val url = vm.playingUrl
        if (url != null && player != null) {
            AndroidView(
                factory = { PlayerView(it).apply { useController = true } },
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
