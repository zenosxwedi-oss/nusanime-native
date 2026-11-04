package com.nusanime.app.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Movie Bar — katalog TMDB: chips (Populer/Trending/Top/Baru) + genre + muat otomatis. */
@Composable
fun MovieListScreen(onOpenMovie: (String) -> Unit, onBack: () -> Unit) {
    var tab by remember { mutableStateOf("popular") } // popular | trending | top_rated | now_playing | upcoming | g:<id>
    var genres by remember { mutableStateOf<List<com.nusanime.app.data.api.MovieSource.M2Genre>>(emptyList()) }
    var items by remember(tab) { mutableStateOf<List<com.nusanime.app.data.api.MovieSource.M2Item>>(emptyList()) }
    var page by remember(tab) { mutableStateOf(1) }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var endReached by remember(tab) { mutableStateOf(false) }
    val gridState = rememberLazyGridState()

    LaunchedEffect(Unit) {
        genres = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            com.nusanime.app.data.api.MovieSource.genres()
        }
    }

    suspend fun load(p: Int) {
        if (p == 1) loading = true else loadingMore = true
        val res = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            if (tab.startsWith("g:")) com.nusanime.app.data.api.MovieSource.byGenre(tab.removePrefix("g:").toInt(), p)
            else com.nusanime.app.data.api.MovieSource.list(tab, p)
        }
        if (p == 1) items = res.items else items = items + res.items
        if (res.items.isEmpty() || res.next == null) endReached = true
        page = p
        loading = false; loadingMore = false
    }

    LaunchedEffect(tab) { endReached = false; load(1) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Kembali") }
            Text("🎬 Movie Bar", style = MaterialTheme.typography.titleSmall)
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = tab == "popular", onClick = { tab = "popular" }, label = { Text("🔥 Populer") })
            FilterChip(selected = tab == "trending", onClick = { tab = "trending" }, label = { Text("📈 Trending") })
            FilterChip(selected = tab == "top_rated", onClick = { tab = "top_rated" }, label = { Text("⭐ Terbaik") })
            FilterChip(selected = tab == "now_playing", onClick = { tab = "now_playing" }, label = { Text("🆕 Baru") })
            FilterChip(selected = tab == "upcoming", onClick = { tab = "upcoming" }, label = { Text("📅 Segera") })
            genres.forEach { g ->
                FilterChip(selected = tab == "g:" + g.id, onClick = { tab = "g:" + g.id }, label = { Text(g.name) })
            }
        }
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(items, key = { "tmdb" + it.tmdbId }) { m ->
                Card(onClick = { onOpenMovie("tmdb:" + m.tmdbId) }) {
                    Column {
                        coil.compose.AsyncImage(
                            model = m.poster,
                            contentDescription = m.title,
                            modifier = Modifier.fillMaxWidth().height(185.dp),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        )
                        Text(m.title, maxLines = 2, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(5.dp))
                        Text(
                            listOfNotNull(m.year.ifEmpty { null }, m.rating.takeIf { it > 0 }?.let { "★ %.1f".format(it) }).joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            if (!loading && !loadingMore && !endReached && items.isNotEmpty()) {
                item(span = { GridItemSpan(3) }) {
                    LaunchedEffect(items.size) { load(page + 1) }
                    Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(22.dp))
                    }
                }
            }
            if (loading) {
                item(span = { GridItemSpan(3) }) {
                    Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(28.dp))
                    }
                }
            }
            if (!loading && items.isEmpty()) {
                item(span = { GridItemSpan(3) }) {
                    Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                        Text("Tidak ada hasil", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
