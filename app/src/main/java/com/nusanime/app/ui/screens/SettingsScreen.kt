package com.nusanime.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nusanime.app.update.SelfUpdater
import com.nusanime.app.update.UpdateState
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val current = remember { SelfUpdater.currentVersionName(ctx) }
    var status by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    var remote by remember { mutableStateOf<SelfUpdater.ReleaseInfo?>(null) }
    val upState by UpdateState.st

    Column(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Pengaturan", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Versi aplikasi: $current", color = MaterialTheme.colorScheme.onSurfaceVariant)

        val ctxTheme = LocalContext.current
        var themeMode by remember { mutableStateOf(com.nusanime.app.ui.theme.ThemePrefs.mode(ctxTheme)) }
        Text("Tema", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = themeMode == 0, onClick = { themeMode = 0; com.nusanime.app.ui.theme.ThemePrefs.setMode(ctxTheme, 0); (ctxTheme as? android.app.Activity)?.recreate() }, label = { Text("Sistem") })
            FilterChip(selected = themeMode == 1, onClick = { themeMode = 1; com.nusanime.app.ui.theme.ThemePrefs.setMode(ctxTheme, 1); (ctxTheme as? android.app.Activity)?.recreate() }, label = { Text("Terang") })
            FilterChip(selected = themeMode == 2, onClick = { themeMode = 2; com.nusanime.app.ui.theme.ThemePrefs.setMode(ctxTheme, 2); (ctxTheme as? android.app.Activity)?.recreate() }, label = { Text("Gelap") })
        }

        Spacer(Modifier.height(8.dp))
        // 18+ mode toggle
        var adult by remember { mutableStateOf(com.nusanime.app.data.api.AdultPrefs.enabled(ctx)) }
        var showAgeDialog by remember { mutableStateOf(false) }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("Mode 18+", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    "Menampilkan konten dewasa di Beranda. Hanya untuk usia 18 tahun ke atas.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = adult,
                onCheckedChange = { on ->
                    if (on) showAgeDialog = true else { adult = false; com.nusanime.app.data.api.AdultPrefs.set(ctx, false) }
                },
            )
        }
        if (showAgeDialog) {
            AlertDialog(
                onDismissRequest = { showAgeDialog = false },
                title = { Text("Konfirmasi Usia") },
                text = { Text("Konten 18+ mengandung materi dewasa. Dengan mengaktifkan, kamu menyatakan berusia 18 tahun atau lebih dan bertanggung jawab penuh atas pilihan ini.") },
                confirmButton = {
                    TextButton(onClick = {
                        showAgeDialog = false
                        adult = true
                        com.nusanime.app.data.api.AdultPrefs.set(ctx, true)
                    }) { Text("Saya 18+") }
                },
                dismissButton = { TextButton(onClick = { showAgeDialog = false }) { Text("Batal") } },
            )
        }

        Spacer(Modifier.height(8.dp))

        Button(
            enabled = !checking && !upState.active,
            onClick = {
                scope.launch {
                    checking = true
                    status = "Memeriksa…"
                    val info = SelfUpdater.latest(ctx)
                    checking = false
                    if (info == null) {
                        status = "Gagal memeriksa (cek internet)"
                    } else if (SelfUpdater.isNewer(info.versionName, current)) {
                        remote = info
                        status = "Versi baru tersedia: v${info.versionName}"
                    } else {
                        remote = null
                        status = "Sudah versi terbaru ✅"
                    }
                }
            },
        ) { Text(if (checking) "Memeriksa…" else "Cek Pembaruan") }

        remote?.let { info ->
            Button(
                enabled = !upState.active,
                onClick = { UpdateState.start(ctx, info) },
            ) { Text(if (upState.active) "Mengunduh…" else "Unduh v${info.versionName} (bisa sambil nonton)") }
        }

        if (upState.ready != null) {
            Button(onClick = { UpdateState.install(ctx) }) { Text("Install update sekarang") }
        }

        if (upState.active) {
            LinearProgressIndicator(progress = { upState.progress }, modifier = Modifier.fillMaxWidth())
            Text(
                if (upState.progress > 0f) "${(upState.progress * 100).toInt()}% — boleh keluar dari halaman ini, unduhan tetap jalan" else "Menyiapkan unduhan…",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        upState.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        status?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }

        Spacer(Modifier.height(8.dp))
        Text(
            "Tips: kalau Android menolak install, izinkan \"Install aplikasi tidak dikenal\" untuk Nusanime saat diminta.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
