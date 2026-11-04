package com.nusanime.app.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private val MB_GENRES = listOf("Populer", "Action", "Comedy", "Horror", "Romance", "Sci-Fi", "Thriller", "Animation", "Adventure", "Drama", "Crime", "Fantasy")

/** Semua Movie (MovieBox) — grid + genre chips + muat otomatis. */
@Composable
fun MovieListScreen(onOpenMovie: (String) -> Unit, onBack: () -> Unit, onOpenMissAv: () -> Unit = {}) {
    var genre by remember { mutableStateOf("Populer") }
    var items by remember(genre) { mutableStateOf<List<com.nusanime.app.data.api.MovieSource.MItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    val gridState = rememberLazyGridState()

    suspend fun load(g: String) {
        loading = true
        val got = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            if (g == "Populer") com.nusanime.app.data.api.MovieSource.latestRaw(1)
            else com.nusanime.app.data.api.MovieSource.genreRaw(g)
        }
        items = got
        loading = false
    }

    LaunchedEffect(genre) { load(genre) }

    // auto reload when scrolled to bottom is not paged (catalog page is fixed 24) — refresh button instead
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Kembali") }
            Text("🎬 Movie — Semua", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.weight(1f))
            Button(onClick = onOpenMissAv, colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color(0xFF7C3AED))) {
                Text("MissAV", style = MaterialTheme.typography.labelSmall)
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MB_GENRES.forEach { g ->
                FilterChip(selected = genre == g, onClick = { genre = g }, label = { Text(g) })
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
            if (loading) {
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                    Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(28.dp))
                    }
                }
            }
            if (!loading && items.isEmpty()) {
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                    Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                        Text("Tidak ada hasil", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
