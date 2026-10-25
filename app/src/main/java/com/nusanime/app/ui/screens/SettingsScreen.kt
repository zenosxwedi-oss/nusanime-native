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
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val current = remember { SelfUpdater.currentVersionName(ctx) }
    var status by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    var downloading by remember { mutableStateOf(false) }
    var progressNote by remember { mutableStateOf<String?>(null) }
    var dlProgress by remember { mutableStateOf(0f) }
    var remote by remember { mutableStateOf<SelfUpdater.ReleaseInfo?>(null) }

    Column(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Pengaturan", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Versi aplikasi: $current", color = MaterialTheme.colorScheme.onSurfaceVariant)

        // Theme mode selector
        val ctxTheme = LocalContext.current
        var themeMode by remember { mutableStateOf(com.nusanime.app.ui.theme.ThemePrefs.mode(ctxTheme)) }
        Text("Tema", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = themeMode == 0, onClick = { themeMode = 0; com.nusanime.app.ui.theme.ThemePrefs.setMode(ctxTheme, 0); (ctxTheme as? android.app.Activity)?.recreate() }, label = { Text("Sistem") })
            FilterChip(selected = themeMode == 1, onClick = { themeMode = 1; com.nusanime.app.ui.theme.ThemePrefs.setMode(ctxTheme, 1); (ctxTheme as? android.app.Activity)?.recreate() }, label = { Text("Terang") })
            FilterChip(selected = themeMode == 2, onClick = { themeMode = 2; com.nusanime.app.ui.theme.ThemePrefs.setMode(ctxTheme, 2); (ctxTheme as? android.app.Activity)?.recreate() }, label = { Text("Gelap") })
        }

        Spacer(Modifier.height(8.dp))

        Button(
            enabled = !checking && !downloading,
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
                enabled = !downloading,
                onClick = {
                    scope.launch {
                        downloading = true
                        dlProgress = 0f
                        progressNote = "Mengunduh v${info.versionName}…"
                        val f = SelfUpdater.downloadApk(ctx, info.apkUrl) { done, total ->
                            if (total > 0) dlProgress = (done.toFloat() / total).coerceIn(0f, 1f)
                        }
                        downloading = false
                        progressNote = null
                        if (f != null) {
                            status = "Unduhan selesai — membuka installer…"
                            SelfUpdater.install(ctx, f)
                        } else {
                            status = "Gagal mengunduh APK. Coba lagi atau cek koneksi."
                        }
                    }
                },
            ) { Text(if (downloading) "Mengunduh…" else "Unduh & Install v${info.versionName}") }
        }

        if (downloading) {
            LinearProgressIndicator(
                progress = { dlProgress },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                if (dlProgress > 0f) "${(dlProgress * 100).toInt()}%" else "Menyiapkan unduhan…",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        progressNote?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        status?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }

        Spacer(Modifier.height(8.dp))
        Text(
            "Tips: kalau Android menolak install, izinkan \"Install aplikasi tidak dikenal\" untuk Nusanime saat diminta.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
