package com.nusanime.app.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nusanime.app.data.api.ApiClient
import com.nusanime.app.data.model.AnimeItem
import kotlinx.coroutines.launch

sealed interface Source { data object Home : Source; data object Ongoing : Source; data object Completed : Source }

class ListViewModel(private val source: Source) : ViewModel() {
    var items by mutableStateOf<List<AnimeItem>>(emptyList())
    var loading by mutableStateOf(true)
    var refreshing by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    private var page = 1
    var loadingMore = false
        private set
    var endReached = false
        private set

    init { load(initial = true) }

    fun load(initial: Boolean) {
        viewModelScope.launch {
            if (initial) loading = true
            error = try {
                val list: List<AnimeItem>? = if (source == Source.Home) {
                    val env = com.nusanime.app.data.api.AxSource.home()
                    val h = env.data
                    if (h == null) null else
                        (h.recent?.animeList ?: emptyList()) +
                            (h.top10?.animeList ?: emptyList()) +
                            (h.batch?.animeList ?: emptyList()) +
                            (h.movie?.animeList ?: emptyList())
                } else {
                    val env: com.nusanime.app.data.model.Envelope<com.nusanime.app.data.model.ListData> =
                        if (source == Source.Ongoing) com.nusanime.app.data.api.AxSource.ongoing(1) else com.nusanime.app.data.api.AxSource.completed(1)
                    if (env.status != "success") null else env.data?.animeList
                }
                if (list == null)
                    "Server tidak merespons dengan benar"
                else { items = list; if (initial) { page = 1; endReached = false }; null }
            } catch (e: Exception) {
                "Gagal memuat: ${e.message ?: "kesalahan jaringan"}"
            }
            loading = false
            refreshing = false
        }
    }

    fun refresh() { refreshing = true; load(initial = false) }

    fun loadMore() {
        if (source == Source.Home || loadingMore || endReached || loading) return
        loadingMore = true
        viewModelScope.launch {
            try {
                page += 1
                val env: com.nusanime.app.data.model.Envelope<com.nusanime.app.data.model.ListData> =
                    if (source == Source.Ongoing) com.nusanime.app.data.api.AxSource.ongoing(page) else com.nusanime.app.data.api.AxSource.completed(page)
                val list = env.data?.animeList
                if (env.status != "success" || list.isNullOrEmpty()) endReached = true
                else items = items + list
            } catch (_: Exception) {
                endReached = true
            }
            loadingMore = false
        }
    }
}

@Composable
fun ListScreen(source: Source, onOpenDetail: (String) -> Unit, onOpenEpisode: (String) -> Unit = {}) {
    val vm: ListViewModel = viewModel(
        key = when (source) { Source.Home -> "home"; Source.Ongoing -> "ongoing"; else -> "completed" },
        factory = viewModelFactory { initializer { ListViewModel(source) } },
    )

    when {
        vm.loading -> com.nusanime.app.ui.LoadingScreen()
        vm.error != null && vm.items.isEmpty() -> com.nusanime.app.ui.ErrorScreen(vm.error!!) { vm.load(initial = true) }
        else -> PullToRefreshList(vm, source, onOpenDetail, onOpenEpisode)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PullToRefreshList(vm: ListViewModel, source: Source, onOpenDetail: (String) -> Unit, onOpenEpisode: (String) -> Unit) {
    PullToRefreshBox(
        isRefreshing = vm.refreshing,
        onRefresh = { vm.refresh() },
        modifier = Modifier.fillMaxSize(),
    ) {
        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
            columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (source == Source.Home) {
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                    ContinueWatchingRow(onOpen = onOpenEpisode)
                }
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                    LatestSlider(items = vm.items.take(10), onOpen = { item ->
                        val ep = item.episodeId
                        if (ep != null) onOpenEpisode(ep) else item.animeId?.let(onOpenDetail)
                    })
                }
            }
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                com.nusanime.app.ui.AdMobBanner()
            }
            // 18+ section (only if enabled in Settings)
            val ctx18 = androidx.compose.ui.platform.LocalContext.current
            var adultOn by remember { mutableStateOf(com.nusanime.app.data.api.AdultPrefs.enabled(ctx18)) }
            var javItems by remember { mutableStateOf<List<com.nusanime.app.data.model.AnimeItem>>(emptyList()) }
            LaunchedEffect(adultOn) {
                if (adultOn && source == Source.Home && javItems.isEmpty()) {
                    javItems = com.nusanime.app.data.api.JavSource.latest()
                }
            }
            if (adultOn && source == Source.Home && javItems.isNotEmpty()) {
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                        Text("18+", color = androidx.compose.ui.graphics.Color(0xFFE53935), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        Spacer(Modifier.width(6.dp))
                        Text("Konten Dewasa", style = MaterialTheme.typography.titleSmall, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    }
                }
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState())) {
                        javItems.take(10).forEach { j ->
                            val link = "https://nekopoi.care/" + (j.animeId?.removePrefix("jav:") ?: "") + "/"
                            androidx.compose.material3.Card(onClick = {
                                try {
                                    android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(link)).also { ctx18.startActivity(it) }
                                } catch (_: Exception) {}
                            }) {
                                Column(Modifier.width(110.dp)) {
                                    coil.compose.AsyncImage(
                                        model = j.poster,
                                        contentDescription = j.title,
                                        modifier = Modifier.fillMaxWidth().height(150.dp),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                    )
                                    Text(j.title ?: "", maxLines = 2, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(6.dp))
                                }
                            }
                        }
                    }
                }
            }
            if (source == Source.Home) {
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                    androidx.compose.foundation.layout.Spacer(Modifier.height(2.dp))
                }
            }
            items(vm.items.size) { i ->
                val item = vm.items[i]
                AnimeCard(item) {
                    val ep = item.episodeId
                    if (ep != null) onOpenEpisode(ep) else item.animeId?.let(onOpenDetail)
                }
                if (i >= vm.items.size - 6) LaunchedEffect(i, vm.items.size) { vm.loadMore() }
            }
            if (vm.loadingMore) {
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(28.dp))
                    }
                }
            }
            if (vm.endReached) {
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                    Text(
                        "Semua sudah ditampilkan",
                        Modifier.fillMaxWidth().padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
