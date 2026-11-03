package com.nusanime.app.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nusanime.app.data.model.AnimeItem
import kotlinx.coroutines.launch

/** 18+ browse: tag (genre) chips + grid of posts from javsubindo. */
@Composable
fun JavGenreScreen(onOpen: (String) -> Unit, onBack: () -> Unit) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var tags by remember { mutableStateOf<List<String>>(emptyList()) }
    var selected by remember { mutableStateOf<String?>(null) }
    var items by remember { mutableStateOf<List<AnimeItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        tags = try { com.nusanime.app.data.api.JavSource.tags() } catch (_: Exception) { emptyList() }
        items = try { com.nusanime.app.data.api.JavSource.latest() } catch (_: Exception) { emptyList() }
        loading = false
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Kembali") }
            Text("18+ · Genre", style = MaterialTheme.typography.titleSmall, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = androidx.compose.ui.graphics.Color(0xFFE53935))
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            tags.forEach { t ->
                FilterChip(
                    selected = selected == t,
                    onClick = {
                        selected = if (selected == t) null else t
                        scope.launch {
                            loading = true
                            items = if (selected == null) com.nusanime.app.data.api.JavSource.latest()
                                    else try { com.nusanime.app.data.api.JavSource.tag(selected!!.lowercase().replace(" ", "-")) } catch (_: Exception) { emptyList() }
                            loading = false
                        }
                    },
                    label = { Text(t) },
                )
            }
        }
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (items.isEmpty()) {
                    item(span = { GridItemSpan(3) }) {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            Text("Tidak ada hasil", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                items(items) { j ->
                    AnimeCard(j) { j.animeId?.removePrefix("jav:")?.let(onOpen) }
                }
            }
        }
    }
}
