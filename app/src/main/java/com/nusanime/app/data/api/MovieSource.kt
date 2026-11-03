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
            gson.fromJson(json, Array<MItem>::class.java).map { m ->
                AnimeItem(
                    title = m.title, poster = m.poster, animeId = "movie:" + m.slug,
                    episodeId = null, episodes = null, releasedOn = null, type = "movie",
                    score = null, statusField = null, estimation = null, genreList = null, siteIdx = 0,
                )
            }
        } catch (_: Exception) { emptyList() }
    }

    suspend fun latest(page: Int = 1): List<AnimeItem> = withContext(Dispatchers.IO) { toItems(apiGet("/movie/latest?page=$page")) }
    suspend fun search(q: String): List<AnimeItem> = withContext(Dispatchers.IO) { toItems(apiGet("/movie/search?q=" + android.net.Uri.encode(q))) }
    suspend fun embeds(slug: String): List<MServer> = withContext(Dispatchers.IO) {
        try { gson.fromJson(apiGet("/movie/post?slug=" + android.net.Uri.encode(slug)), MServers::class.java).servers }
        catch (_: Exception) { emptyList() }
    }
}
