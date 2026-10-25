package com.nusanime.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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

    fun search(q: String) {
        viewModelScope.launch {
            loading = true
            error = try {
                val resp = ApiClient.api.search(q.trim())
                val list = (resp.body() as? com.nusanime.app.data.model.ListData)?.animeList
                if (!resp.isSuccessful || list == null) "Server tidak merespons dengan benar (${resp.code()})"
                else { results = list; searched = true; null }
            } catch (e: Exception) {
                "Gagal mencari: ${e.message ?: "kesalahan jaringan"}"
            }
            loading = false
        }
    }
}

@Composable
fun SearchScreen(onOpenDetail: (String) -> Unit) {
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
                items(vm.results) { item ->
                    AnimeCard(item) { item.animeId?.let(onOpenDetail) }
                }
            }
        }
    }
}
