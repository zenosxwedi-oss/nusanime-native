package com.nusanime.app.data.api

import java.net.HttpURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * HD poster lookup via AniList GraphQL (no key, works from mobile).
 * Results cached in-memory per session; failed lookups cached as null to avoid repeats.
 */
object HdPoster {
    private val cache = HashMap<String, String?>()

    private const val ENDPOINT = "https://graphql.anilist.co"

    private fun queryBody(title: String): String {
        val safe = title.replace("\\", "\\\\").replace("\"", "\\\"")
        return "{\"query\":\"query(\$s:String){Page(perPage:1){media(search:\$s,type:ANIME,sort:SEARCH_MATCH){coverImage{extraLarge large}}}}\",\"variables\":{\"s\":\"$safe\"}}"
    }

    private fun fetchHttp(title: String): String? = try {
        val conn = java.net.URL(ENDPOINT).openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Accept", "application/json")
        conn.doOutput = true
        conn.outputStream.use { it.write(queryBody(title).toByteArray(Charsets.UTF_8)) }
        if (conn.responseCode !in 200..299) null else {
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val media = com.google.gson.JsonParser.parseString(body).asJsonObject
                .getAsJsonObject("data").getAsJsonObject("Page")
                .getAsJsonArray("media")?.firstOrNull()?.asJsonObject
            media?.getAsJsonObject("coverImage")?.get("extraLarge")?.takeIf { !it.isJsonNull }?.asString
                ?: media?.getAsJsonObject("coverImage")?.get("large")?.takeIf { !it.isJsonNull }?.asString
        }
    } catch (_: Exception) { null }

    /** Non-blocking peek: cached value or null (does not trigger a fetch). */
    fun cached(title: String): String? = synchronized(cache) { cache[title] }

    /** Suspend fetch with in-memory cache. */
    suspend fun fetch(title: String): String? {
        if (title.isBlank()) return null
        synchronized(cache) { if (cache.containsKey(title)) return cache[title] }
        return coroutineScope {
            val me = async(Dispatchers.IO) { fetchHttp(title) }
            val url = me.await()
            synchronized(cache) { cache[title] = url }
            url
        }
    }
}
