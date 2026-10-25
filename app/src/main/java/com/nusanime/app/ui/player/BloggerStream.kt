package com.nusanime.app.ui.player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Extracts a direct googlevideo stream URL from a Blogger video page
 * (https://www.blogger.com/video.g?token=...). The stream URL is IP-bound,
 * so it must be fetched from the device itself — it does NOT work if
 * resolved on a server.
 */
object BloggerStream {
    private const val BS = "\\"

    fun isBlogger(url: String): Boolean =
        url.contains("blogger.com/video.g") || url.contains("googleusercontent.com/videoplayback")

    /** Returns a direct playable URL, or null if extraction fails. */
    suspend fun extract(pageUrl: String): String? = withContext(Dispatchers.IO) {
        try {
            val html = httpGet(pageUrl)
            // unescape = & &quot;
            val h = html
                .replace(BS + "u003d", "=")
                .replace(BS + "u0026", "&")
                .replace("&quot;", "\"")
                .replace(BS + ",", ",")
            val urls = Regex("https://[a-z0-9.-]*googlevideo[.]com/videoplayback[?][A-Za-z0-9=_.,%:&/-]+")
                .findAll(h).map { it.value }.distinct().toList()
            // prefer highest itag present (18 = 360p progressive; blogger usually exposes one per token)
            urls.maxByOrNull { it.length } ?: urls.firstOrNull()
        } catch (_: Exception) {
            null
        }
    }

    private fun httpGet(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 20000
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
        conn.setRequestProperty("Referer", "https://www.blogger.com/")
        conn.setRequestProperty("Accept", "*/*")
        val code = conn.responseCode
        if (code !in 200..299) throw RuntimeException("HTTP $code")
        return conn.inputStream.bufferedReader().use { it.readText() }
    }
}
