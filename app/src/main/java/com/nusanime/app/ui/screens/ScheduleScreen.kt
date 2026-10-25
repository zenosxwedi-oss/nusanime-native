package com.nusanime.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nusanime.app.data.api.ApiClient
import com.nusanime.app.data.model.ScheduleData
import com.nusanime.app.ui.ErrorScreen
import com.nusanime.app.ui.LoadingScreen
import kotlinx.coroutines.launch

class ScheduleViewModel : ViewModel() {
    var days by mutableStateOf<ScheduleData?>(null)
    var loading by mutableStateOf(true)
    var error by mutableStateOf<String?>(null)
    var refreshing by mutableStateOf(false)

    init { load() }

    fun load() {
        viewModelScope.launch {
            error = try {
                val resp = ApiClient.api.schedule()
                val d = resp.body()
                if (!resp.isSuccessful || d?.days == null) "Server tidak merespons dengan benar (${resp.code()})"
                else { days = d; null }
            } catch (e: Exception) {
                "Gagal memuat jadwal: ${e.message ?: "kesalahan jaringan"}"
            }
            loading = false
            refreshing = false
        }
    }
}

@Composable
fun ScheduleScreen(onOpenDetail: (String) -> Unit) {
    val vm: ScheduleViewModel = viewModel(factory = viewModelFactory { initializer { ScheduleViewModel() } })

    if (vm.loading) { LoadingScreen(); return }
    vm.error?.let { err ->
        ErrorScreen(err) { vm.loading = true; vm.load() }
        return
    }

    Column {
        LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val dayList = vm.days?.days.orEmpty()
            items(dayList) { day ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            day.day ?: "-",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(8.dp))
                        (day.animeList ?: emptyList()).forEach { anime ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { anime.animeId?.let(onOpenDetail) }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(anime.title ?: "-", style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    anime.estimation?.let { Text("Est. $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                }
                                Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        }
                    }
                }
            }
        }
    }
}
