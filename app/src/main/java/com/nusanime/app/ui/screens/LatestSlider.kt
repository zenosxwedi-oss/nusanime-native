package com.nusanime.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nusanime.app.data.model.AnimeItem

/** Horizontal slider of latest updated anime shown at the top of Home. */
@Composable
fun LatestSlider(items: List<AnimeItem>, onOpen: (AnimeItem) -> Unit) {
    val featured = items.take(10)
    if (featured.isEmpty()) return
    Column {
        Text(
            "Update Terbaru",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(featured, key = { it.animeId ?: it.title ?: it.hashCode().toString() }) { item ->
                Box(
                    Modifier
                        .width(220.dp)
                        .height(124.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onOpen(item) },
                ) {
                    AsyncImage(
                        model = item.poster,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    0f to Color.Transparent,
                                    1f to Color.Black.copy(alpha = 0.85f),
                                )
                            ),
                    )
                    Column(Modifier.align(Alignment.BottomStart).padding(10.dp)) {
                        Text(
                            item.title ?: "",
                            color = Color.White,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        listOfNotNull(item.episodes?.let { "Ep $it" }, item.type).joinToString(" • ").takeIf { it.isNotBlank() }?.let {
                            Text(it, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}
