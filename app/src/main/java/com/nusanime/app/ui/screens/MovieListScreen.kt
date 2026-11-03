package com.nusanime.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems

/** Semua Movie (lk21) — grid + tombol muat lagi. */
@Composable
fun MovieListScreen(onOpenMovie: (String) -> Unit, onBack: () -> Unit) {
    var items by remember { mutableStateOf<List<com.nusanime.app.data.api.MovieSource.MItem>>(emptyList()) }
    var page by remember { mutableStateOf(1) }
    var loading by remember { mutableStateOf(true) }
    var endReached by remember { mutableStateOf(false) }

    suspend fun load(p: Int) {
        loading = true
        val got = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            com.nusanime.app.data.api.MovieSource.latestRaw(p)
        }
        if (got.isEmpty()) endReached = true
        else {
            val seen = items.map { it.slug }.toSet()
            items = items + got.filter { it.slug !in seen }
            page = p
        }
        loading = false
    }

    LaunchedEffect(Unit) { load(1) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Kembali") }
            Text("🎬 Movie — Semua", style = MaterialTheme.typography.titleSmall)
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(items, key = { it.slug }) { m ->
                Card(onClick = { onOpenMovie(m.slug) }) {
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
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                    if (loading) CircularProgressIndicator(Modifier.size(28.dp))
                    else if (!endReached) OutlinedButton(onClick = { kotlinx.coroutines.runBlocking { load(page + 1) } }) { Text("Muat lagi") }
                    else Text("Semua film sudah ditampilkan", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
