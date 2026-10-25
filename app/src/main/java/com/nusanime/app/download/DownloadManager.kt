package com.nusanime.app.download

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import com.nusanime.app.MainApplication
import com.nusanime.app.data.db.AppDatabase
import com.nusanime.app.data.db.DownloadEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Simple foreground-free download manager: direct HTTP download to
 * Movies/Nusanime with Room-tracked progress. Uses a worker coroutine per
 * episode rather than DownloadManager so we can track bytes and retries.
 */
object DownloadManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val running = mutableSetOf<String>()

    const val STATE_QUEUED = 0
    const val STATE_RUNNING = 1
    const val STATE_DONE = 2
    const val STATE_FAILED = 3

    fun outputDir(context: Context): File =
        File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
            "Nusanime",
        ).apply { mkdirs() }

    fun enqueue(
        context: Context,
        episodeId: String,
        animeId: String,
        animeTitle: String,
        episodeLabel: String,
        poster: String?,
        url: String,
        quality: String?,
    ) {
        val db = AppDatabase.get(context)
        scope.launch {
            val safeTitle = animeTitle.replace(Regex("[^A-Za-z0-9 ._-]"), "").trim().take(50)
            val safeEp = episodeLabel.replace(Regex("[^A-Za-z0-9 ._-]"), "").trim().take(30)
            val ext = if (url.substringBefore('?').lowercase().endsWith(".m3u8")) "m3u8" else "mp4"
            val file = File(outputDir(context), "${safeTitle}_${safeEp}_$episodeId.$ext")
            db.downloadDao().upsert(
                DownloadEntry(
                    episodeId = episodeId,
                    animeId = animeId,
                    animeTitle = animeTitle,
                    episodeLabel = episodeLabel,
                    poster = poster,
                    url = url,
                    quality = quality,
                    state = STATE_QUEUED,
                    bytesDownloaded = 0,
                    bytesTotal = 0,
                    filePath = file.absolutePath,
                    updatedAt = System.currentTimeMillis(),
                )
            )
            startIfIdle(context)
        }
    }

    private suspend fun startIfIdle(context: Context) {
        val db = AppDatabase.get(context)
        val active = db.downloadDao().active()
        for (entry in active) {
            if (entry.episodeId in running) continue
            running.add(entry.episodeId)
            scope.launch { runDownload(entry) }
        }
    }

    private suspend fun runDownload(entry: DownloadEntry) {
        val ctx = MainApplication.instance
        val db = AppDatabase.get(ctx)
        try {
            db.downloadDao().upsert(entry.copy(state = STATE_RUNNING, updatedAt = System.currentTimeMillis()))
            val conn = URL(entry.url).openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            conn.setRequestProperty("User-Agent", "okhttp/4.12.0")
            conn.setRequestProperty("Range", "bytes=${File(entry.filePath ?: return).length()}-")
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: -1L
            val resume = conn.responseCode == 206
            val input = conn.inputStream
            val out = File(entry.filePath!!)
            var done = if (resume) out.length() else 0L
            if (!resume) { out.parentFile?.mkdirs(); out.createNewFile() }
            java.io.RandomAccessFile(out, "rw").use { raf ->
                raf.seek(done)
                val buf = ByteArray(64 * 1024)
                input.use { ins ->
                    while (true) {
                        val n = ins.read(buf)
                        if (n < 0) break
                        raf.write(buf, 0, n)
                        done += n
                    }
                }
            }
            db.downloadDao().upsert(
                entry.copy(
                    state = STATE_DONE,
                    bytesDownloaded = done,
                    bytesTotal = if (total > 0) total + (if (resume) 0 else 0) else done,
                    updatedAt = System.currentTimeMillis(),
                )
            )
        } catch (_: Exception) {
            db.downloadDao().upsert(entry.copy(state = STATE_FAILED, updatedAt = System.currentTimeMillis()))
        } finally {
            running.remove(entry.episodeId)
            withContext(Dispatchers.IO) { startIfIdle(ctx) }
        }
    }

    fun delete(context: Context, entry: DownloadEntry) {
        scope.launch {
            try { entry.filePath?.let { File(it).delete() } } catch (_: Exception) {}
            AppDatabase.get(context).downloadDao().delete(entry.episodeId)
        }
    }
}
