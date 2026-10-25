package com.nusanime.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Box(Modifier.fillMaxWidth().height(240.dp)) {
                AsyncImage(
                    model = d.poster,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().alpha(0.5f),
                )
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.surface))
                    )
                )
                Row(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                    AsyncImage(
                        model = d.poster,
                        contentDescription = d.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.width(110.dp).aspectRatio(2f / 3f),
                    )
                    Column(Modifier.padding(start = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(d.title ?: "-", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            val ctx = androidx.compose.ui.platform.LocalContext.current
                            IconButton(onClick = { vm.toggleBookmark() }) {
                                Icon(
                                    if (vm.bookmarked) Icons.Filled.Bookmarks
                                    else Icons.Filled.BookmarkBorder,
                                    contentDescription = if (vm.bookmarked) "Hapus bookmark" else "Bookmark",
                                    tint = if (vm.bookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { vm.toggleFollow(ctx) }) {
                                Icon(
                                    if (vm.followed) Icons.Filled.NotificationsActive
                                    else Icons.Filled.NotificationsNone,
                                    contentDescription = if (vm.followed) "Berhenti ikuti" else "Ikuti",
                                    tint = if (vm.followed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        listOfNotNull(d.status, d.type, d.duration, d.season).forEach {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        vm.trailerUrl?.let { url ->
                            val tctx = androidx.compose.ui.platform.LocalContext.current
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(onClick = {
                                try {
                                    tctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)))
                                } catch (_: Exception) {}
                            }) {
                                Icon(Icons.Filled.PlayArrow, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Tonton Trailer")
                            }
                        }
                    }
                }
            }
        }
        item {
            Column(Modifier.padding(16.dp)) {
                val genres = d.genreList?.mapNotNull { it.title }.orEmpty()
                if (genres.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        genres.forEach { g ->
                            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small) {
                                Text(g, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
                val syn = d.synopsisText()
                if (syn.isNotBlank()) {
                    Text("Sinopsis", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(syn, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                }
                Text("Daftar Episode", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
            }
        }
        items(d.episodeList.orEmpty()) { ep ->
            EpisodeRow(ep, onClick = { ep.episodeId?.let(onOpenEpisode) })
        }
        if (d.episodeList.isNullOrEmpty()) {
            item {
                Text("Belum ada episode.", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Spacer(Modifier.height(16.dp))
            Text("Rekomendasi", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
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
                    contentPadding = PaddingValues(horizontal = 16.dp),
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
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun EpisodeRow(ep: EpisodeItem, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), shape = MaterialTheme.shapes.small) {
            Text("▶", Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.primary)
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(ep.titleText(), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            (ep.date ?: ep.releasedOn)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
}
