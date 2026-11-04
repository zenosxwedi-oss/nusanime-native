package com.nusanime.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.ExperimentalFoundationApi

private val MV_GENRES = listOf("New", "Today Hot", "Weekly Hot", "Monthly Hot", "Chinese Subtitle", "Uncensored Leak", "Release")

/** MissAV browser — chips kategori/genre + grid + muat otomatis. slug item dibungkus "mv:" saat dibuka. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MissAvListScreen(onOpenMovie: (String) -> Unit, onBack: () -> Unit) {
    var tab by remember { mutableStateOf("New") }
    var genres by remember { mutableStateOf<List<com.nusanime.app.data.api.MovieSource.MvGenre>>(emptyList()) }
    var items by remember(tab) { mutableStateOf<List<com.nusanime.app.data.api.MovieSource.MvItem>>(emptyList()) }
    var page by remember(tab) { mutableStateOf(1) }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var endReached by remember(tab) { mutableStateOf(false) }
    val gridState = rememberLazyGridState()

    LaunchedEffect(Unit) {
        val g = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            com.nusanime.app.data.api.MovieSource.mvGenres()
        }
        genres = g
    }

    suspend fun load(p: Int) {
        if (p == 1) loading = true else loadingMore = true
        val res = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            when (tab) {
                "New" -> com.nusanime.app.data.api.MovieSource.mvHome(p)
                "Today Hot" -> com.nusanime.app.data.api.MovieSource.mvList("dm291/en/today-hot", p)
                "Weekly Hot" -> com.nusanime.app.data.api.MovieSource.mvList("dm169/en/weekly-hot", p)
                "Monthly Hot" -> com.nusanime.app.data.api.MovieSource.mvList("dm263/en/monthly-hot", p)
                "Chinese Subtitle" -> com.nusanime.app.data.api.MovieSource.mvList("dm265/en/chinese-subtitle", p)
                "Uncensored Leak" -> com.nusanime.app.data.api.MovieSource.mvList("dm628/en/uncensored-leak", p)
                "Release" -> com.nusanime.app.data.api.MovieSource.mvList("dm590/en/release", p)
                else -> com.nusanime.app.data.api.MovieSource.mvList("en/genres/" + java.net.URLEncoder.encode(tab, "UTF-8"), p)
            }
        }
        val got = res.items
        if (p == 1) items = got else items = items + got
        if (got.isEmpty() || res.next == null) endReached = true
        page = p
        loading = false; loadingMore = false
    }

    LaunchedEffect(tab) { endReached = false; load(1) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Kembali") }
            Text("MissAV — JAV HD", style = MaterialTheme.typography.titleSmall)
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MV_GENRES.forEach { g -> FilterChip(selected = tab == g, onClick = { tab = g }, label = { Text(g) }) }
            genres.forEach { g ->
                FilterChip(selected = tab == g.name, onClick = { tab = g.name }, label = { Text(g.name) })
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
            items(items, key = { it.slug }) { m ->
                Card(onClick = { onOpenMovie("mv:" + m.slug) }) {
                    Column {
                        coil.compose.AsyncImage(
                            model = m.poster,
                            contentDescription = m.title,
                            modifier = Modifier.fillMaxWidth().height(145.dp),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        )
                        Text(m.title, maxLines = 2, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(5.dp))
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
