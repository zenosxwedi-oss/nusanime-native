package com.nusanime.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.nusanime.app.data.api.ApiClient
import com.nusanime.app.data.model.AnimeItem
import com.nusanime.app.ui.ErrorScreen
import com.nusanime.app.ui.LoadingScreen

class SearchViewModel : ViewModel() {
    var query by mutableStateOf("")
    var results by mutableStateOf<List<AnimeItem>>(emptyList())
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var searched by mutableStateOf(false)
    private var job: kotlinx.coroutines.Job? = null

    fun onQueryChange(q: String) {
        query = q
        job?.cancel()
        if (q.isBlank()) { results = emptyList(); searched = false; error = null; return }
        job = viewModelScope.launch {
            delay(500) // debounce
            search(q)
        }
    }

    var javResults by mutableStateOf<List<AnimeItem>>(emptyList())

    fun search(q: String) {
        viewModelScope.launch {
            loading = true
            error = try {
                val env = com.nusanime.app.data.api.AxSource.search(q.trim())
                val list = env.data?.animeList
                if (env.status != "success" || list == null) "Server tidak merespons dengan benar (${env.message ?: env.status ?: "data kosong"})"
                else { results = list; searched = true; null }
            } catch (e: Exception) {
                "Gagal mencari: ${e.message ?: "kesalahan jaringan"}"
            }
            loading = false
            // 18+ search (parallel, non-blocking, only if enabled)
            if (com.nusanime.app.data.api.AdultPrefs.enabled(com.nusanime.app.MainApplication.instance)) {
                try { javResults = com.nusanime.app.data.api.JavSource.search(q.trim()) } catch (_: Exception) { javResults = emptyList() }
            } else javResults = emptyList()
        }
    }
}

@Composable
fun SearchScreen(onOpenDetail: (String) -> Unit, onOpen18: (String) -> Unit = {}) {
    val vm: SearchViewModel = androidx.lifecycle.viewmodel.compose.viewModel()

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = vm.query,
            onValueChange = vm::onQueryChange,
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            placeholder = { Text("Cari anime...") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
                if (vm.query.isNotEmpty()) IconButton(onClick = { vm.onQueryChange("") }) { Icon(Icons.Default.Close, "Bersihkan") }
            },
            singleLine = true,
            shape = MaterialTheme.shapes.extraLarge,
        )

        when {
            vm.loading -> LoadingScreen()
            vm.error != null -> ErrorScreen(vm.error!!) { vm.search(vm.query) }
            vm.searched && vm.results.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text("Tidak ada hasil untuk \"${vm.query}\"", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (vm.javResults.isNotEmpty()) {
                    item(span = { GridItemSpan(3) }) {
                        Text("🔴 18+ — Konten Dewasa", style = MaterialTheme.typography.titleSmall, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = androidx.compose.ui.graphics.Color(0xFFE53935))
                    }
                }
                items(vm.results) { item ->
                    AnimeCard(item) { item.animeId?.let(onOpenDetail) }
                }
                items(vm.javResults) { j ->
                    AnimeCard(j) { j.animeId?.removePrefix("jav:")?.let(onOpen18) }
                }
            }
        }
    }
}
