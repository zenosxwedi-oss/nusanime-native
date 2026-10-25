package com.nusanime.app.data.api

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL

// ===== Models (mirrors animein.net API shape, same as AnimeX) =====

data class AxMovie(
    val id: String? = null,
    val title: String? = null,
    val synopsis: String? = null,
    @SerializedName("image_poster") val imagePoster: String? = null,
    @SerializedName("image_cover") val imageCover: String? = null,
    val type: String? = null,
    val year: String? = null,
    val status: String? = null,
    val genre: String? = null,
    val studio: String? = null,
    val views: String? = null,
    val rating: String? = null,
    val day: String? = null,
    val time: String? = null,
    @SerializedName("episode_index") val episodeIndex: String? = null,
    @SerializedName("episode_id") val episodeId: String? = null,
    @SerializedName("episode_title") val episodeTitle: String? = null,
    val season: String? = null,
) {
    val posterUrl: String? get() = imagePoster ?: imageCover
    val epLabel: String? get() = episodeIndex?.takeIf { it.isNotBlank() }
}

data class AxEpisode(
    val id: String? = null,
    val index: String? = null,
    val title: String? = null,
    val image: String? = null,
    val views: String? = null,
    @SerializedName("id_movie") val idMovie: String? = null,
) {
    val label: String
        get() = title ?: ("Episode " + (index ?: "?"))
}

data class AxServer(
    val id: String? = null,
    val link: String? = null,
    val quality: String? = null,
    val name: String? = null,
    val type: String? = null,
) {
    val isDirect: Boolean
        get() {
            val l = link.orEmpty().lowercase()
            return type.equals("direct", true) || ".mp4" in l || ".m3u8" in l
        }
    val qualityValue: Int get() = quality.orEmpty().filter(Char::isDigit).toIntOrNull() ?: 0
}

data class AxHomeData(
    val update: List<AxMovie>? = null,
    val hot: List<AxMovie>? = null,
    val new: List<AxMovie>? = null,
    val today: List<AxMovie>? = null,
    val popular: List<AxMovie>? = null,
    val random: List<AxMovie>? = null,
    val waiting: List<AxMovie>? = null,
)

data class AxDistributor(
    val movie: List<AxMovie>? = null,
)

data class AxDistributor2(
    val episode: List<AxEpisode>? = null,
)

data class AxDistributor3(
    val server: List<AxServer>? = null,
)

data class AxDistributor4(
    val movie: AxMovie? = null,
    val season: List<AxMovie>? = null,
)

// ===== Client =====

private data class AxEnvelope<T>(
    val status: Int? = null,
    val error: Boolean? = null,
    val data: T? = null,
    val message: String? = null,
)

object AnimeInApi {
    const val GATE_URL = "https://gate.nextanimelist.com/"
    const val DEFAULT_BASE = "https://xyz-api.animein.net/"
    private const val UA = "okhttp/4.12.0"

    private val gson = Gson()

    @Volatile
    var baseUrl: String = ""
        private set

    fun normalizeBase(raw: String): String {
        var b = raw.trim()
        when {
            b.startsWith("//") -> b = "https:$b"
            !b.contains("://") -> b = "https://$b"
        }
        return b.trimEnd('/') + "/"
    }

    /** Resolve real API base via gate (data/setup/data -> data.domain_api.value). */
    suspend fun resolveBase(): String = withContext(Dispatchers.IO) {
        if (baseUrl.isNotBlank()) return@withContext baseUrl
        try {
            val json = rawGet(GATE_URL, "data/setup/data", emptyMap())
            val root = JsonParser.parseString(json).asJsonObject
            val v = root.getAsJsonObject("data")?.getAsJsonObject("domain_api")?.get("value")?.asString
            if (!v.isNullOrBlank()) baseUrl = normalizeBase(v)
        } catch (_: Exception) {
        }
        if (baseUrl.isBlank()) baseUrl = DEFAULT_BASE
        baseUrl
    }

    private suspend fun rawGet(base: String, path: String, params: Map<String, String>): String =
        withContext(Dispatchers.IO) {
            val sb = StringBuilder(base.trimEnd('/') + "/" + path.trimStart('/'))
            if (params.isNotEmpty()) {
                sb.append('?')
                params.entries.forEachIndexed { i, (k, v) ->
                    if (i > 0) sb.append('&')
                    sb.append(URLEncoder.encode(k, "UTF-8")).append('=')
                    sb.append(URLEncoder.encode(v, "UTF-8"))
                }
            }
            val conn = URL(sb.toString()).openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 20000
            conn.setRequestProperty("User-Agent", UA)
            conn.setRequestProperty("Accept", "*/*")
            try {
                val code = conn.responseCode
                if (code != 200) throw RuntimeException("HTTP $code")
                conn.inputStream.bufferedReader().use { it.readText() }
            } finally {
                conn.disconnect()
            }
        }

    private suspend fun <T> get(
        path: String,
        cls: Class<T>,
        params: Map<String, String> = emptyMap(),
    ): T? = withContext(Dispatchers.IO) {
        val base = resolveBase()
        val json = rawGet(base, path, params)
        val root = JsonParser.parseString(json).asJsonObject
        val err = root.get("error")
        val isError = when {
            err == null -> false
            err.isJsonPrimitive -> err.asString.equals("true", true)
            else -> false
        }
        if (isError) throw RuntimeException(root.get("message")?.asString ?: "API error")
        val d = root.get("data")?.takeIf { it.isJsonObject }?.asJsonObject ?: return@withContext null
        gson.fromJson(d, cls)
    }

    private fun JsonObject.movieArray(): List<AxMovie> {
        for (key in listOf("movie", "movies", "list", "items", "results")) {
            val el = get(key) ?: continue
            if (el.isJsonArray) {
                val arr = gson.fromJson(el, Array<AxMovie>::class.java)?.toList()
                if (!arr.isNullOrEmpty()) return arr
            }
        }
        return emptyList()
    }

    // ===== Endpoints =====

    suspend fun home(limit: Int = 100): AxHomeData? =
        try { get("data/home/list", AxHomeData::class.java, mapOf("limit" to "$limit")) } catch (_: Exception) { null }

    suspend fun newEpisodes(limit: Int = 30, page: Int = 0): List<AxMovie> =
        try {
            val p = mutableMapOf("limit" to "$limit")
            if (page > 0) p["page"] = "$page"
            get("data/home/list_new_episode", AxDistributor::class.java, p)?.movie.orEmpty()
        } catch (_: Exception) { emptyList() }

    suspend fun schedule(day: String): List<AxMovie> =
        try {
            get("3/2/schedule/data", AxDistributor::class.java, mapOf("day" to day, "page" to "1", "limit" to "100"))
                ?.movie.orEmpty()
        } catch (_: Exception) { emptyList() }

    suspend fun detail(id: String): AxMovie? =
        try { get("3/2/movie/detail/$id", AxDistributor4::class.java)?.movie } catch (_: Exception) { null }

    suspend fun seasons(id: String): List<AxMovie> =
        try { get("3/2/movie/detail/$id", AxDistributor4::class.java)?.season.orEmpty() } catch (_: Exception) { emptyList() }

    suspend fun episodes(id: String, page: Int? = null): List<AxEpisode> =
        try {
            val p = if (page != null && page > 0) mapOf("page" to "$page") else emptyMap()
            get("3/2/movie/episode/$id", AxDistributor2::class.java, p)?.episode.orEmpty()
        } catch (_: Exception) { emptyList() }

    /** Stream servers for one episode. Returns direct links when available. */
    suspend fun servers(episodeId: String): List<AxServer> =
        try {
            get("3/2/episode/streamnew/$episodeId", AxDistributor3::class.java)
                ?.server.orEmpty()
                .filter { !it.link.isNullOrBlank() }
        } catch (_: Exception) { emptyList() }

    suspend fun search(q: String, page: Int = 0): List<AxMovie> {
        val query = q.trim()
        if (query.isBlank()) return emptyList()
        val p = page.coerceAtLeast(0)
        // primary endpoint first, fall back to alternates like AnimeX does
        try {
            val r = get("3/2/explore/movie", AxDistributor::class.java, mapOf("keyword" to query, "page" to "$p", "sort" to "views"))?.movie.orEmpty()
            if (r.isNotEmpty()) return r
        } catch (_: Exception) {}
        for (key in listOf("query", "q", "search", "title", "name")) {
            try {
                val r = get("3/2/explore/movie", AxDistributor::class.java, mapOf(key to query, "page" to "$p", "sort" to "views"))?.movie.orEmpty()
                if (r.isNotEmpty()) return r
            } catch (_: Exception) {}
        }
        try {
            val r = get("data/movie/find", AxDistributor::class.java, mapOf("keyword" to query, "page" to "$p"))?.movie.orEmpty()
            if (r.isNotEmpty()) return r
        } catch (_: Exception) {}
        return emptyList()
    }

    suspend fun explore(limit: Int = 5000): Map<String, List<String>> =
        try {
            val d = get("3/2/explore/data", JsonObject::class.java, mapOf("limit" to "$limit"))
            val out = mutableMapOf<String, List<String>>()
            for (key in listOf("tipe", "type", "genre", "studio", "year", "tahun")) {
                val el = d?.get(key) ?: continue
                val names = mutableListOf<String>()
                when {
                    el.isJsonArray ->
                        gson.fromJson(el, Array<JsonObject>::class.java)?.forEach { o ->
                            val n = o.get("name")?.asString ?: o.get("title")?.asString ?: o.get("label")?.asString
                            if (!n.isNullOrBlank()) names.add(n)
                        }
                    el.isJsonObject ->
                        el.asJsonObject.entrySet().forEach { (k, v) ->
                            try {
                                val o = v.asJsonObject
                                val n = o.get("name")?.asString ?: o.get("title")?.asString ?: o.get("label")?.asString
                                if (!n.isNullOrBlank()) names.add(n) else names.add(k)
                            } catch (_: Exception) {}
                        }
                }
                if (names.isNotEmpty()) out[key] = names
            }
            out
        } catch (_: Exception) { emptyMap() }

    suspend fun exploreBy(kind: String, id: String, page: Int = 0): List<AxMovie> {
        val path = when (kind) {
            "genre" -> "3/2/explore/movie_genre" to "id_genre"
            "type", "tipe" -> "3/2/explore/movie_type" to "type"
            "studio" -> "3/2/explore/movie_studio" to "studio"
            "year", "tahun" -> "3/2/explore/movie_year" to "year"
            else -> return emptyList()
        }
        return try {
            get(path.first, AxDistributor::class.java, mapOf(path.second to id, "page" to "$page", "limit" to "60"))?.movie.orEmpty()
        } catch (_: Exception) { emptyList() }
    }
}
