package com.nusanime.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nusanime.app.data.db.AppDatabase
import com.nusanime.app.data.db.WatchHistory

data class HistoryEntry(val episodeId: String, val animeId: String, val title: String, val poster: String?, val progress: Float)

@Composable
fun rememberContinueWatching(): List<HistoryEntry> {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    return produceState<List<HistoryEntry>>(emptyList()) {
        try {
            AppDatabase.get(ctx).historyDao().recent().collect { list ->
                value = list.map {
                    HistoryEntry(
                        it.episodeId, it.animeId, it.animeTitle, it.poster,
                        if (it.durationMs > 0) (it.positionMs.toFloat() / it.durationMs) else 0f
                    )
                }
            }
        } catch (_: Exception) {}
    }.value
}

@Composable
fun ContinueWatchingRow(onOpen: (String) -> Unit) {
    val items = rememberContinueWatching()
    if (items.isEmpty()) return
    Column {
        Text(
            "Lanjut Nonton",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(items, key = { it.episodeId }) { h ->
                Column(Modifier.width(120.dp).clickable { onOpen(h.episodeId) }) {
                    Box {
                        AsyncImage(
                            model = h.poster,
                            contentDescription = h.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(MaterialTheme.shapes.medium),
                        )
                        if (h.progress > 0f) {
                            LinearProgressIndicator(
                                progress = { h.progress.coerceIn(0f, 1f) },
                                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(4.dp),
                            )
                        }
                    }
                    Text(
                        h.title,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}
