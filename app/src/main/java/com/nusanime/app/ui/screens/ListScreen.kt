package com.nusanime.app.ui.screens

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
                val env = when (source) {
                    Source.Home -> ApiClient.api.home()
                    Source.Ongoing -> ApiClient.api.ongoing(1)
                    Source.Completed -> ApiClient.api.completed(1)
                }
                val list = when (source) {
                    Source.Home -> env.data?.let { h ->
                        (h.recent?.animeList ?: emptyList()) +
                            (h.top10?.animeList ?: emptyList()) +
                            (h.batch?.animeList ?: emptyList()) +
                            (h.movie?.animeList ?: emptyList())
                    }
                    else -> env.data?.animeList
                }
                if (env.status != "success" || list == null)
                    "Server tidak merespons dengan benar (${env.message ?: env.status ?: "data kosong"})"
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
                val env = when (source) {
                    Source.Ongoing -> ApiClient.api.ongoing(page)
                    else -> ApiClient.api.completed(page)
                }
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
fun ListScreen(source: Source, onOpenDetail: (String) -> Unit) {
    val vm: ListViewModel = viewModel(
        key = when (source) { Source.Home -> "home"; Source.Ongoing -> "ongoing"; else -> "completed" },
        factory = viewModelFactory { initializer { ListViewModel(source) } },
    )

    when {
        vm.loading -> com.nusanime.app.ui.LoadingScreen()
        vm.error != null && vm.items.isEmpty() -> com.nusanime.app.ui.ErrorScreen(vm.error!!) { vm.load(initial = true) }
        else -> PullToRefreshList(vm, onOpenDetail)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PullToRefreshList(vm: ListViewModel, onOpenDetail: (String) -> Unit) {
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
            items(vm.items.size) { i ->
                val item = vm.items[i]
                AnimeCard(item) { item.animeId?.let(onOpenDetail) }
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
