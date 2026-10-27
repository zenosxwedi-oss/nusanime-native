package com.nusanime.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil.compose.AsyncImage
import com.nusanime.app.data.api.AxSource
import com.nusanime.app.data.model.AnimeItem
import com.nusanime.app.data.model.Genre
import com.nusanime.app.ui.ErrorScreen
import com.nusanime.app.ui.LoadingScreen
import kotlinx.coroutines.launch

class GenreViewModel : ViewModel() {
    var genres by mutableStateOf<List<Genre>>(emptyList())
    var results by mutableStateOf<List<AnimeItem>?>(null)
    var selected by mutableStateOf<String?>(null)
    var loading by mutableStateOf(true)
    var loadingResults by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    init { loadGenres() }

    fun loadGenres() {
        viewModelScope.launch {
            error = try {
                val g = AxSource.genreList()
                if (g.isEmpty()) "Gagal memuat daftar genre" else { genres = g; null }
            } catch (e: Exception) {
                "Gagal memuat genre: ${e.message ?: "kesalahan jaringan"}"
            }
            loading = false
        }
    }

    fun select(genre: Genre?) {
        selected = genre?.genreId
        results = null
        if (genre == null) return
        viewModelScope.launch {
            loadingResults = true
            error = try {
                val env = AxSource.genreAnime(genre.genreId ?: return@launch)
                val list = env.data?.animeList.orEmpty()
                if (list.isEmpty()) "Tidak ada anime untuk genre ini" else { results = list; null }
            } catch (e: Exception) {
                "Gagal memuat: ${e.message ?: "kesalahan jaringan"}"
            }
            loadingResults = false
        }
    }
}

@Composable
fun GenreScreen(onOpenDetail: (String) -> Unit) {
    val vm: GenreViewModel = viewModel(factory = viewModelFactory { initializer { GenreViewModel() } })

    Column(Modifier.fillMaxSize()) {
        Text(
            "Genre",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )

        if (vm.loading) {
            LoadingScreen()
            return@Column
        }
        vm.error?.let { msg ->
            if (vm.results == null && vm.selected == null) {
                ErrorScreen(message = msg) { vm.loadGenres() }
                return@Column
            }
        }

        // Genre chips
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GenreChip("Semua", vm.selected == null) { vm.select(null) }
            GenreChip(vm.genres.firstOrNull { it.genreId == vm.selected }?.title ?: "Pilih", vm.selected != null) { }
        }

        if (vm.selected == null) {
            // Grid of all genres
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 100.dp),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(vm.genres) { g ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .clickable { vm.select(g) }
                            .padding(0.dp),
                    ) {
                        Box(Modifier.padding(horizontal = 12.dp, vertical = 14.dp)) {
                            Text(
                                g.title ?: g.genreId ?: "",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        } else if (vm.loadingResults) {
            LoadingScreen()
        } else {
            val list = vm.results.orEmpty()
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 105.dp),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(list) { item ->
                    Column(Modifier.clickable { item.animeId?.let(onOpenDetail) }) {
                        AsyncImage(
                            model = item.poster,
                            contentDescription = item.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(2f / 3f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF1B1B22)),
                        )
                        Text(
                            item.title ?: "",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GenreChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        onClick = onClick,
    ) {
        Text(
            label,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}
