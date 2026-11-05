package com.nusanime.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil.compose.AsyncImage
import com.nusanime.app.data.api.ApiClient
import com.nusanime.app.data.model.DetailData
import com.nusanime.app.data.model.EpisodeItem
import com.nusanime.app.ui.ErrorScreen
import com.nusanime.app.ui.LoadingScreen
import kotlinx.coroutines.launch

class DetailViewModel(private val animeId: String) : ViewModel() {
    var detail by mutableStateOf<DetailData?>(null)
    var loading by mutableStateOf(true)
    var error by mutableStateOf<String?>(null)
    var followed by mutableStateOf(false)
    var bookmarked by mutableStateOf(false)

    init {
        load()
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val ctx = com.nusanime.app.MainApplication.instance
                val db = com.nusanime.app.data.db.AppDatabase.get(ctx)
                followed = db.followedDao().get(animeId) != null
                bookmarked = db.bookmarkDao().get(animeId) != null
            } catch (_: Exception) {}
        }
    }

    var trailerUrl by mutableStateOf<String?>(null)

    init {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val t = com.nusanime.app.data.api.AnimeInApi.trailers(animeId).firstOrNull()
            val raw = t?.url_youtube?.trim()?.takeIf { it.isNotBlank() } ?: return@launch
            trailerUrl = when {
                raw.startsWith("http", true) -> raw
                raw.startsWith("www.", true) -> "https://$raw"
                else -> "https://www.youtube.com/watch?v=$raw"
            }
        }
    }

    fun toggleBookmark() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val ctx = com.nusanime.app.MainApplication.instance
                val db = com.nusanime.app.data.db.AppDatabase.get(ctx)
                if (bookmarked) {
                    db.bookmarkDao().delete(animeId)
                    bookmarked = false
                } else {
                    val dd = detail
                    if (dd != null) {
                        db.bookmarkDao().upsert(
                            com.nusanime.app.data.db.Bookmark(
                                animeId = animeId,
                                title = dd.title ?: animeId,
                                poster = dd.poster,
                                addedAt = System.currentTimeMillis(),
                            )
                        )
                        bookmarked = true
                    }
                }
            } catch (_: Exception) {}
        }
    }

    fun toggleFollow(context: android.content.Context) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val db = com.nusanime.app.data.db.AppDatabase.get(context)
            val d = detail
            if (followed) {
                db.followedDao().delete(animeId)
                followed = false
            } else {
                if (d != null) {
                    db.followedDao().upsert(
                        com.nusanime.app.data.db.Followed(
                            animeId = animeId,
                            title = d.title ?: animeId,
                            poster = d.poster,
                            lastEpisodeId = d.episodeList?.firstOrNull()?.episodeId,
                            followedAt = System.currentTimeMillis(),
                        )
                    )
                    followed = true
                }
            }
        }
    }

    fun load() {
        viewModelScope.launch {
            error = try {
                val env = com.nusanime.app.data.api.AxSource.detail(animeId)
                val d = env.data
                if (env.status != "success" || d?.title == null) "Gagal memuat detail (${env.message ?: env.status ?: "data kosong"})"
                else { detail = d; null }
            } catch (e: Exception) {
                "Gagal memuat detail: ${e.message ?: "kesalahan jaringan"}"
            }
            loading = false
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun DetailScreen(animeId: String, onBack: () -> Unit, onOpenEpisode: (String) -> Unit, onOpenDetail: (String) -> Unit) {
    val vm: DetailViewModel = viewModel(
        key = "detail_$animeId",
        factory = viewModelFactory { initializer { DetailViewModel(animeId) } },
    )

    if (vm.loading) { LoadingScreen(); return }
    vm.error?.let { ErrorScreen(it) { vm.load() }; return }

    val d = vm.detail ?: return
    androidx.compose.runtime.LaunchedEffect(d) {
        d.episodeList.orEmpty().forEach { ep ->
            ep.episodeId?.let { eid -> com.nusanime.app.data.PosterCache.put(eid, com.nusanime.app.data.PosterCache.Info(d.poster, animeId, d.title)) }
        }
    }
    val allEps = d.episodeList.orEmpty()
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    Column {
        // Header kembali (dipisah dari area scroll, seperti halaman JADWAL yang pasti bisa di-scroll)
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali")
            }
            Text("Detail", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            val hctx = androidx.compose.ui.platform.LocalContext.current
            IconButton(onClick = { vm.toggleBookmark() }) {
                Icon(if (vm.bookmarked) Icons.Filled.Bookmarks else Icons.Filled.BookmarkBorder, contentDescription = "Bookmark", tint = if (vm.bookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Poster + info dalam CARD (pola sama dengan halaman JADWAL yang terbukti bisa scroll)
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(12.dp)) {
                        Row {
                            AsyncImage(
                                model = d.poster,
                                contentDescription = d.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.width(100.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(12.dp)),
                            )
                            Column(Modifier.padding(start = 12.dp)) {
                                Text(d.title ?: "-", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                Spacer(Modifier.height(6.dp))
                                listOfNotNull(d.status, d.type, d.duration, d.season).forEach {
                                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                d.score?.let {
                                    Text("★ ${it}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                }
                                vm.followed.let {
                                    TextButton(onClick = { vm.toggleFollow(androidx.compose.ui.platform.LocalContext.current) }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                                        Text(if (it) "🔔 Mengikuti" else "🔔 Ikuti", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }
                        if (!d.genreList.isNullOrEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text(d.genreList.orEmpty().mapNotNull { it.title }.joinToString(" • "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        val syn = d.synopsisText()
                        if (syn.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(syn, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 4, overflow = TextOverflow.Ellipsis)
                        }
                        vm.trailerUrl?.let { url ->
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(onClick = {
                                try { androidx.compose.ui.platform.LocalContext.current.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))) } catch (_: Exception) {}
                            }) {
                                Icon(Icons.Filled.PlayArrow, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Tonton Trailer")
                            }
                        }
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Daftar Episode (${allEps.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = {
                        scope.launch { listState.animateScrollToItem(0) }
                    }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                        Text("⬆", style = MaterialTheme.typography.labelLarge)
                    }
                    OutlinedButton(onClick = {
                        scope.launch { if (allEps.isNotEmpty()) listState.animateScrollToItem(allEps.size - 1) }
                    }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                        Text("Ep 1 ⬇", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            itemsIndexed(allEps, contentType = { _, _ -> "ep" }) { idx, ep ->
                EpisodeRow(ep, index = idx, onClick = { ep.episodeId?.let(onOpenEpisode) })
            }
            if (allEps.isEmpty()) {
                item { Text("Belum ada episode.", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            item {
                Text("Rekomendasi", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
            }
            item {
                val recs = remember { mutableStateOf<List<com.nusanime.app.data.model.AnimeItem>>(emptyList()) }
                androidx.compose.runtime.LaunchedEffect(animeId) {
                    try {
                        val env = com.nusanime.app.data.api.AxSource.home()
                        val h = env.data
                        recs.value = (h?.top10?.animeList.orEmpty() + h?.recent?.animeList.orEmpty())
                            .distinctBy { it.animeId }
                            .filter { it.animeId != null && it.animeId != animeId }
                            .take(12)
                    } catch (_: Exception) {}
                }
                if (recs.value.isEmpty()) {
                    Text("Memuat rekomendasi...", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                } else {
                    androidx.compose.foundation.lazy.LazyRow(
                        contentPadding = PaddingValues(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(recs.value, key = { it.animeId ?: it.title ?: "" }) { r ->
                            Column(Modifier.width(110.dp).clickable { r.animeId?.let { id -> onOpenDetail(id) } }) {
                                AsyncImage(
                                    model = r.poster,
                                    contentDescription = r.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxWidth().height(155.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(r.title ?: "", style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeRow(ep: EpisodeItem, index: Int, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(34.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("${index + 1}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(ep.titleText(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                (ep.date ?: ep.releasedOn)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Icon(Icons.Filled.PlayArrow, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
    }
}
