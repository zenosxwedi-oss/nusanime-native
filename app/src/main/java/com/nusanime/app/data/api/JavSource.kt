package com.nusanime.app.data.api

import android.content.Context
import com.google.gson.Gson
import com.nusanime.app.data.model.AnimeItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** Mode 18+ prefs (age-gated in Settings). */
object AdultPrefs {
    private const val KEY = "adult_mode_18"
    fun enabled(ctx: Context): Boolean = ctx.getSharedPreferences("nusa", 0).getBoolean(KEY, false)
    fun set(ctx: Context, v: Boolean) = ctx.getSharedPreferences("nusa", 0).edit().putBoolean(KEY, v).apply()
}

/**
 * 18+ source — calls OUR server API (https://vpn.nusanime.web.id/javapi/) which does
 * scraping of javsubindo/arenajav + m3u8 resolution server-side. Nothing to break on device.
 */
const val JAV_UA: String = "Mozilla/5.0 (Linux; Android 12; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"

object JavSource {
    private const val API = "https://vpn.nusanime.web.id/javapi"
    private val gson = Gson()

    data class JavItem(val slug: String = "", val title: String = "", val poster: String = "", val site: Int = 0)
    data class JavServer(val label: String = "", val url: String = "")
    data class ServersRes(val servers: List<JavServer> = emptyList())
    data class StreamRes(val m3u8: String? = null, val m3u8_direct: String? = null)

    private fun apiGet(path: String): String? = try {
        val conn = URL(API + path).openConnection() as HttpURLConnection
        conn.connectTimeout = 10000; conn.readTimeout = 20000
        conn.setRequestProperty("User-Agent", JAV_UA)
        conn.instanceFollowRedirects = true
        if (conn.responseCode !in 200..299) null
        else conn.inputStream.bufferedReader().use { it.readText() }
    } catch (_: Exception) { null }

    private fun toItems(json: String?): List<AnimeItem> {
        if (json == null) return emptyList()
        return try {
            gson.fromJson(json, Array<JavItem>::class.java).map { j ->
                AnimeItem(
                    title = j.title.ifEmpty { j.slug.replace("-", " ").replaceFirstChar { it.uppercase() } },
                    poster = j.poster,
                    animeId = "jav:" + j.slug,
                    episodeId = null, episodes = null, releasedOn = null, type = "jav",
                    score = null, statusField = null, estimation = null, genreList = null,
                    siteIdx = j.site,
                )
            }
        } catch (_: Exception) { emptyList() }
    }

    suspend fun latest(page: Int = 1): List<AnimeItem> = withContext(Dispatchers.IO) {
        toItems(apiGet("/latest?page=$page"))
    }

    suspend fun search(q: String): List<AnimeItem> = withContext(Dispatchers.IO) {
        toItems(apiGet("/search?q=" + android.net.Uri.encode(q)))
    }

    suspend fun tags(): List<String> = withContext(Dispatchers.IO) {
        try { gson.fromJson(apiGet("/tags"), Array<String>::class.java).toList() }
        catch (_: Exception) { listOf("uncensored", "censored", "jilbab", "tante", "abg") }
    }

    suspend fun tag(t: String): List<AnimeItem> = withContext(Dispatchers.IO) {
        toItems(apiGet("/tag/" + android.net.Uri.encode(t)))
    }

    suspend fun embeds(slug: String, siteIdx: Int = 0): List<JavServer> = withContext(Dispatchers.IO) {
        try {
            gson.fromJson(apiGet("/post?site=$siteIdx&slug=" + android.net.Uri.encode(slug)), ServersRes::class.java).servers
        } catch (_: Exception) { emptyList() }
    }

    /** Relay m3u8 (through our server — anti ISP block). */
    suspend fun resolveM3u8(embedUrl: String): String? = withContext(Dispatchers.IO) {
        try {
            val r = gson.fromJson(apiGet("/stream?u=" + android.net.Uri.encode(embedUrl)), StreamRes::class.java)
            val rel = r.m3u8 ?: return@withContext null
            if (rel.startsWith("/")) API + rel else rel
        } catch (_: Exception) { null }
    }

    /** Direct m3u8 from CDN (fast when ISP allows). */
    suspend fun resolveM3u8Direct(embedUrl: String): String? = withContext(Dispatchers.IO) {
        try {
            gson.fromJson(apiGet("/stream?u=" + android.net.Uri.encode(embedUrl)), StreamRes::class.java).m3u8_direct
        } catch (_: Exception) { null }
    }
}
