package com.nusanime.app.ui.player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object SkipIntro {
    data class Interval(val startMs: Long, val endMs: Long)

    /** Ask aniskip for intro interval; malId unknown for us, so use episode number+title-less lookup (type 1 = animeId; we query without). */
    suspend fun fetch(anilistId: Int?, episode: Int): Interval? = withContext(Dispatchers.IO) {
        if (anilistId == null || episode <= 0) return@withContext null
        try {
            val url = "https://api.aniskip.com/v2/skip-times/$anilistId/$episode?types=op&episodeLength=0"
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("User-Agent", "okhttp/4.12.0")
            try {
                if (conn.responseCode != 200) return@withContext null
                val body = conn.inputStream.bufferedReader().readText()
                val json = JSONObject(body)
                val found = json.optJSONArray("results") ?: return@withContext null
                if (found.length() == 0) return@withContext null
                val r = found.getJSONObject(0)
                val itv = r.getJSONObject("interval")
                Interval((itv.getDouble("startTime") * 1000).toLong(), (itv.getDouble("endTime") * 1000).toLong())
            } finally {
                conn.disconnect()
            }
        } catch (_: Exception) {
            null
        }
    }
}
