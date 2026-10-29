package com.nusanime.app.data.api

import java.net.HttpURLConnection
import java.net.URLEncoder

/**
 * HD poster lookup via AniList GraphQL (no key, works from mobile).
 * Results cached in-memory per session; failed lookups cached too (null) to avoid repeat calls.
 */
object HdPoster {
    private val cache = HashMap<String, String?>()
    private val inFlight = HashMap<String, kotlinx.coroutines.Deferred<String?>>()

    private val endpoint = "https://graphql.anilist.co"

    private fun query(title: String): String = """
        {"query":"query(\$s:String){Page(perPage:1){media(search:\$s,type:ANIME,sort:SEARCH_MATCH){coverImage{extraLarge large}}}}","variables":{"s":"${title.replace("\\", "\\\\").replace("\"", "\\\"")}"}}
    """.trimIndent()

    /** Fetch HD poster URL for a title; null if not found. Suspend — call from a coroutine. */
    suspend fun fetch(title: String): String? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { fetchBlocking(title) }

    /** Blocking implementation. */
    private fun fetchBlocking(title: String): String? {
        if (title.isBlank()) return null
        synchronized(cache) { if (cache.containsKey(title)) return cache[title] }
        val deferred = synchronized(inFlight) {
            inFlight.getOrPut(title) {
                kotlinx.coroutines.GlobalScope.async(kotlinx.coroutines.Dispatchers.IO) {
                    val url = try {
                        val conn = java.net.URL(endpoint).openConnection() as HttpURLConnection
                        conn.connectTimeout = 8000
                        conn.readTimeout = 8000
                        conn.requestMethod = "POST"
                        conn.setRequestProperty("Content-Type", "application/json")
                        conn.setRequestProperty("Accept", "application/json")
                        conn.doOutput = true
                        conn.outputStream.use { it.write(query(title).toByteArray()) }
                        if (conn.responseCode !in 200..299) throw RuntimeException("HTTP ${conn.responseCode}")
                        val body = conn.inputStream.bufferedReader().use { it.readText() }
                        val obj = com.google.gson.JsonParser.parseString(body).asJsonObject
                        val media = obj.getAsJsonObject("data").getAsJsonObject("Page")
                            .getAsJsonArray("media")?.firstOrNull()?.asJsonObject
                        media?.getAsJsonObject("coverImage")
                            ?.get("extraLarge")?.takeIf { !it.isJsonNull }?.asString
                            ?: media?.getAsJsonObject("coverImage")?.get("large")?.takeIf { !it.isJsonNull }?.asString
                    } catch (_: Exception) { null }
                    synchronized(cache) { cache[title] = url }
                    synchronized(inFlight) { inFlight.remove(title) }
                    url
                }
            }
        }
        return runCatching { deferred.get() }.getOrNull()
    }

    /** Non-blocking peek: cached value or null (does not trigger a fetch). */
    fun cached(title: String): String? = synchronized(cache) { cache[title] }
}
