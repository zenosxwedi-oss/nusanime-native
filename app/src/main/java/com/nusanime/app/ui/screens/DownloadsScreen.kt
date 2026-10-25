package com.nusanime.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nusanime.app.data.db.AppDatabase
import com.nusanime.app.data.db.DownloadEntry
import com.nusanime.app.download.DownloadManager
import java.io.File

@Composable
fun DownloadsScreen() {
    val ctx = LocalContext.current
    val db = remember { AppDatabase.get(ctx) }
    val items by db.downloadDao().all().collectAsState(initial = emptyList())

    if (items.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Belum ada unduhan.\nBuka episode lalu ketuk tombol ⬇ untuk mengunduh.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(items, key = { it.episodeId }) { entry ->
            DownloadRow(entry)
        }
    }
}

@Composable
private fun DownloadRow(entry: DownloadEntry) {
    val ctx = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = entry.poster,
                contentDescription = entry.animeTitle,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(52.dp)
                    .height(72.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(entry.animeTitle, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    entry.episodeLabel + (entry.quality?.let { " • $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                when (entry.state) {
                    DownloadManager.STATE_DONE -> {
                        val size = entry.bytesDownloaded / (1024 * 1024)
                        Text("Selesai • ${size} MB", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    DownloadManager.STATE_FAILED -> Text("Gagal", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    DownloadManager.STATE_RUNNING -> {
                        val frac = if (entry.bytesTotal > 0) entry.bytesDownloaded.toFloat() / entry.bytesTotal else 0.15f
                        LinearProgressIndicator(progress = { frac.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                        val pct = if (entry.bytesTotal > 0) (entry.bytesDownloaded * 100 / entry.bytesTotal).toInt() else 0
                        Text("$pct%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    else -> Text("Antre…", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (entry.state == DownloadManager.STATE_DONE && entry.filePath != null && File(entry.filePath).exists()) {
                IconButton(onClick = {
                    // play the local file in an intent (video player)
                    try {
                        val uri = androidx.core.content.FileProvider.getUriForFile(
                            ctx, ctx.packageName + ".fileprovider", File(entry.filePath)
                        )
                        val i = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, "video/mp4")
                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        ctx.startActivity(i)
                    } catch (_: Exception) {}
                }) {
                    Icon(Icons.Default.PlayArrow, "Putar", tint = MaterialTheme.colorScheme.primary)
                }
            }
            IconButton(onClick = { DownloadManager.delete(ctx, entry) }) {
                Icon(Icons.Default.Delete, "Hapus", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
