package com.nusanime.app.data.api

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Movie Bar — katalog TMDB via javapi /movie2/, play via rantai vidsrc
 * server-side (/movie/play?imdb=). Tanpa 18+, tanpa MovieBox, tanpa MissAV.
 */
object MovieSource {
    private const val API = "https://vpn.nusanime.web.id/javapi"
    private val gson = Gson()

    data class M2Item(val tmdbId: Int = 0, val title: String = "", val poster: String? = null, val year: String = "", val rating: Double = 0.0)
    data class M2ListRes(val page: Int = 1, val items: List<M2Item> = emptyList(), val next: Int? = null)
    data class M2Row(val title: String = "", val items: List<M2Item> = emptyList())
    data class M2Home(val rows: List<M2Row> = emptyList())
    data class M2Genre(val id: Int = 0, val name: String = "")
    data class M2Detail(
        val tmdbId: Int = 0, val title: String = "", val poster: String? = null,
        val backdrop: String? = null, val overview: String? = null, val year: String = "",
        val rating: Double = 0.0, val runtime: Int? = null,
        val genres: List<String> = emptyList(), val imdbId: String? = null,
    )
    data class MPlay(val m3u8: String? = null)
    data class MSubs(val srt: String? = null)

    private fun apiGet(path: String): String? = try {
        val conn = URL(API + path).openConnection() as HttpURLConnection
        conn.connectTimeout = 10000; conn.readTimeout = 30000
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/124 Mobile Safari/537.36")
        conn.instanceFollowRedirects = true
        if (conn.responseCode !in 200..299) null
        else conn.inputStream.bufferedReader().use { it.readText() }
    } catch (_: Exception) { null }

    suspend fun home(): M2Home = withContext(Dispatchers.IO) {
        try { gson.fromJson(apiGet("/movie2/home"), M2Home::class.java) ?: M2Home() }
        catch (_: Exception) { M2Home() }
    }

    suspend fun list(kind: String, page: Int = 1): M2ListRes = withContext(Dispatchers.IO) {
        try { gson.fromJson(apiGet("/movie2/list?kind=$kind&page=$page"), M2ListRes::class.java) ?: M2ListRes() }
        catch (_: Exception) { M2ListRes() }
    }

    suspend fun genres(): List<M2Genre> = withContext(Dispatchers.IO) {
        try { gson.fromJson(apiGet("/movie2/genres"), Array<M2Genre>::class.java)?.toList() ?: emptyList() }
        catch (_: Exception) { emptyList() }
    }

    suspend fun byGenre(id: Int, page: Int = 1): M2ListRes = withContext(Dispatchers.IO) {
        try { gson.fromJson(apiGet("/movie2/genre?id=$id&page=$page"), M2ListRes::class.java) ?: M2ListRes() }
        catch (_: Exception) { M2ListRes() }
    }

    suspend fun search(q: String, page: Int = 1): M2ListRes = withContext(Dispatchers.IO) {
        try { gson.fromJson(apiGet("/movie2/search?q=" + java.net.URLEncoder.encode(q, "UTF-8") + "&page=$page"), M2ListRes::class.java) ?: M2ListRes() }
        catch (_: Exception) { M2ListRes() }
    }

    suspend fun detail(tmdbId: Int): M2Detail? = withContext(Dispatchers.IO) {
        try { gson.fromJson(apiGet("/movie2/detail?id=$tmdbId"), M2Detail::class.java) }
        catch (_: Exception) { null }
    }

    /** Resolve playable relay m3u8 for a TMDB movie via its IMDb id. */
    suspend fun play(tmdbId: Int): String? = withContext(Dispatchers.IO) {
        val d = detail(tmdbId) ?: return@withContext null
        val imdb = d.imdbId ?: return@withContext null
        try {
            val r = gson.fromJson(apiGet("/movie/play?imdb=" + java.net.URLEncoder.encode(imdb, "UTF-8")), MPlay::class.java)
            val rel = r?.m3u8 ?: return@withContext null
            if (rel.startsWith("/")) API + rel else rel
        } catch (_: Exception) { null }
    }

    /** Cari subtitle Indonesia (SRT) untuk film via IMDb id; return URL absolut atau null. */
    suspend fun subs(imdbId: String): String? = withContext(Dispatchers.IO) {
        try {
            val r = gson.fromJson(apiGet("/movie2/subs?imdb=" + java.net.URLEncoder.encode(imdbId, "UTF-8")), MSubs::class.java)
            r?.srt?.let { if (it.startsWith("/")) API + it else it }
        } catch (_: Exception) { null }
    }

    fun toItems(res: M2ListRes): List<com.nusanime.app.data.model.AnimeItem> = res.items.map {
        com.nusanime.app.data.model.AnimeItem(
            title = it.title, poster = it.poster, animeId = "tmdb:" + it.tmdbId,
            episodeId = null, episodes = null, releasedOn = it.year.ifEmpty { null }, type = "movie",
            score = null, statusField = null, estimation = null, genreList = null, siteIdx = 0,
        )
    }
}
