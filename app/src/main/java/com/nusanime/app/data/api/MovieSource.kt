package com.nusanime.app.data.api

import com.google.gson.Gson
import com.nusanime.app.data.model.AnimeItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** Movies from lk21.tel (via our javapi server). Plays in WebView (vidlink embed). */
object MovieSource {
    private const val API = "https://vpn.nusanime.web.id/javapi"
    private val gson = Gson()

    data class MItem(val slug: String = "", val title: String = "", val poster: String = "", val site: Int = 0)
    data class MServer(val label: String = "", val url: String = "")
    data class MServers(val servers: List<MServer> = emptyList())

    private fun apiGet(path: String): String? = try {
        val conn = URL(API + path).openConnection() as HttpURLConnection
        conn.connectTimeout = 10000; conn.readTimeout = 20000
        conn.setRequestProperty("User-Agent", JAV_UA)
        conn.instanceFollowRedirects = true
        if (conn.responseCode !in 200..299) null
        else conn.inputStream.bufferedReader().use { it.readText() }
    } catch (_: Exception) { null }

    fun toItems(json: String?): List<AnimeItem> {
        if (json == null) return emptyList()
        return try {
            // MovieBox items: {title, subjectId, poster, year}; legacy lk21: {slug, title, poster}
            val mb: Array<MbItem>? = try { gson.fromJson(json, Array<MbItem>::class.java) } catch (_: Exception) { null }
            if (mb != null && mb.isNotEmpty() && mb[0].subjectId.isNotEmpty()) {
                mb.filter { it.subjectId.isNotEmpty() }.map {
                    AnimeItem(
                        title = it.title, poster = it.poster, animeId = "mb:" + it.subjectId,
                        episodeId = null, episodes = null, releasedOn = it.year.ifEmpty { null }, type = "movie",
                        score = null, statusField = null, estimation = null, genreList = null, siteIdx = 0,
                    )
                }
            } else {
                gson.fromJson(json, Array<MItem>::class.java).map { m ->
                    AnimeItem(
                        title = m.title, poster = m.poster, animeId = "movie:" + m.slug,
                        episodeId = null, episodes = null, releasedOn = null, type = "movie",
                        score = null, statusField = null, estimation = null, genreList = null, siteIdx = 0,
                    )
                }
            }
        } catch (_: Exception) { emptyList() }
    }

    suspend fun latest(page: Int = 1): List<AnimeItem> = withContext(Dispatchers.IO) { toItems(apiGet("/movie/latest?page=$page")) }

    suspend fun latestRaw(page: Int = 1): List<MItem> = withContext(Dispatchers.IO) {
        val json = apiGet("/movie/latest") ?: return@withContext emptyList()
        try {
            val mb: Array<MbItem> = gson.fromJson(json, Array<MbItem>::class.java)
            mb.filter { it.subjectId.isNotEmpty() }.map { MItem(slug = "mb_" + it.subjectId, title = it.title, poster = it.poster, site = 0) }
        } catch (_: Exception) {
            try { gson.fromJson(json, Array<MItem>::class.java).toList() } catch (_: Exception) { emptyList() }
        }
    }
    suspend fun search(q: String): List<AnimeItem> = withContext(Dispatchers.IO) { toItems(apiGet("/movie/search?q=" + android.net.Uri.encode(q))) }
    /** Resolve the native HD stream (server-side vidsrc chain, relayed). */
    suspend fun hdStream(imdb: String): String? = withContext(Dispatchers.IO) {
        try {
            val r = gson.fromJson(apiGet("/movie/play?imdb=" + android.net.Uri.encode(imdb)), Map::class.java)
            val rel = r?.get("m3u8") as? String ?: return@withContext null
            if (rel.startsWith("/")) API + rel else rel
        } catch (_: Exception) { null }
    }

    suspend fun embeds(slug: String): List<MServer> = withContext(Dispatchers.IO) {
        try { gson.fromJson(apiGet("/movie/post?slug=" + android.net.Uri.encode(slug)), MServers::class.java).servers }
        catch (_: Exception) { emptyList() }
    }

    // ===== MovieBox provider =====
    data class MbItem(val title: String = "", val subjectId: String = "", val poster: String = "", val year: String = "")
    data class MbPlay(val mpd: String? = null, val cookie: String? = null, val resolution: Int = 0)

    suspend fun mbSearch(q: String): List<AnimeItem> = withContext(Dispatchers.IO) {
        try {
            val json = apiGet("/movie/mb/search?q=" + java.net.URLEncoder.encode(q, "UTF-8")) ?: return@withContext emptyList()
            val items: Array<MbItem> = gson.fromJson(json, Array<MbItem>::class.java) ?: return@withContext emptyList()
            items.filter { it.subjectId.isNotEmpty() }.map {
                AnimeItem(
                    title = it.title,
                    poster = it.poster,
                    animeId = "mb:" + it.subjectId,
                    episodeId = null,
                    episodes = null,
                    releasedOn = it.year.ifEmpty { null },
                    type = "movie",
                    score = null,
                    statusField = null,
                    estimation = null,
                    genreList = null,
                    siteIdx = null
                )
            }
        } catch (_: Exception) { emptyList() }
    }

    suspend fun mbPost(sid: String): MServers = withContext(Dispatchers.IO) {
        try {
            val json = apiGet("/movie/mb/post?sid=" + sid) ?: return@withContext MServers(emptyList())
            gson.fromJson(json, MServers::class.java) ?: MServers(emptyList())
        } catch (_: Exception) { MServers(emptyList()) }
    }

    suspend fun mbPlay(sid: String): MbPlay? = withContext(Dispatchers.IO) {
        try {
            val json = apiGet("/movie/mb/play?sid=" + sid) ?: return@withContext null
            gson.fromJson(json, MbPlay::class.java)
        } catch (_: Exception) { null }
    }
}
