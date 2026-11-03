package com.nusanime.app.data.api

import android.content.Context
import com.nusanime.app.data.model.AnimeItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** 18+ section (javsubindo.com). Gated by age confirmation in Settings. */
object AdultPrefs {
    private const val KEY = "adult_mode_18"
    fun enabled(ctx: Context): Boolean = ctx.getSharedPreferences("nusa", 0).getBoolean(KEY, false)
    fun set(ctx: Context, v: Boolean) = ctx.getSharedPreferences("nusa", 0).edit().putBoolean(KEY, v).apply()
}

object JavSource {
    private const val BASE = "https://javsubindo.com"
    private const val UA = "Mozilla/5.0 (Linux; Android 12) Mobile"

    private fun get(url: String): String? = try {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10000; conn.readTimeout = 15000
        conn.setRequestProperty("User-Agent", UA)
        conn.instanceFollowRedirects = true
        if (conn.responseCode !in 200..299) null
        else conn.inputStream.bufferedReader().use { it.readText() }
    } catch (_: Exception) { null }

    private val RESERVED = setOf(
        "wp-content", "wp-json", "wp-admin", "page", "feed", "comments", "categories",
        "tags", "actors", "author", "jav-subindo", "contact", "sitemap",
    )
    private val POST_RE = Regex("""href="$BASE/([a-z0-9][a-z0-9-]{4,})"""")
    private val IMG_RE = Regex("""src="(https://javsubindo\.com/wp-content/uploads/[^"]+?\.(?:jpg|jpeg|webp|png))"""")

    private fun parse(html: String): List<AnimeItem> {
        val items = LinkedHashMap<String, AnimeItem>()
        val imgs = IMG_RE.findAll(html).map { it.groupValues[1] }.distinct().toList()
        POST_RE.findAll(html).forEach { m ->
            val slug = m.groupValues[1]
            if (slug in RESERVED) return@forEach
            if (slug.contains("bokep-viral") && false) return@forEach
            if (items.containsKey(slug)) return@forEach
            val guess = imgs.getOrNull(items.size % imgs.size.coerceAtLeast(1))
            items[slug] = AnimeItem(
                title = slug.replace("-", " ").replaceFirstChar { it.uppercase() }.take(64),
                poster = guess,
                animeId = "jav:$slug",
                episodeId = null, episodes = null, releasedOn = null,
                type = "18+", score = null, statusField = null, estimation = null, genreList = null,
            )
        }
        return items.values.toList()
    }

    suspend fun latest(page: Int = 1): List<AnimeItem> = withContext(Dispatchers.IO) {
        val url = if (page <= 1) "$BASE/" else "$BASE/page/$page"
        get(url)?.let { parse(it) } ?: emptyList()
    }

    suspend fun search(q: String): List<AnimeItem> = withContext(Dispatchers.IO) {
        get("$BASE/?s=" + java.net.URLEncoder.encode(q, "UTF-8"))?.let { parse(it) } ?: emptyList()
    }

    suspend fun tag(tag: String): List<AnimeItem> = withContext(Dispatchers.IO) {
        get("$BASE/tag/$tag")?.let { parse(it) } ?: emptyList()
    }

    /** Curated 18+ "genres" (tags) + dynamic tag cloud. */
    suspend fun tags(): List<String> = withContext(Dispatchers.IO) {
        val curated = listOf(
            "uncensored", "censored", "jav-subtitle-indonesia", "abg", "tante", "jilbab",
            "bdsm", "harem", "threesome", "creampie", "guru", "perawat", "adik", "istri",
        )
        val html = get("$BASE/tags") ?: return@withContext curated
        val re = Regex("""href="$BASE/tag/([^"/]+)"[^>]*aria-label="([^"]+)"""")
        val dyn = re.findAll(html).mapNotNull { m ->
            val slug = m.groupValues[1]
            if (slug.all { it.isDigit() }) null else m.groupValues[2].substringBefore(" (")
        }.distinct().take(140)
        (curated + dyn).distinct()
    }

    /** Extract iframe embeds from a post page. */
    suspend fun embeds(slug: String): List<String> = withContext(Dispatchers.IO) {
        val html = get("$BASE/$slug") ?: return@withContext emptyList()
        val re = Regex("""<iframe[^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        re.findAll(html)
            .map { it.groupValues[1] }
            .filter { !it.contains("a-ads") && !it.contains("discordapp") && !it.contains("ads.") && !it.contains("adsterra") }
            .map { if (it.startsWith("//")) "https:$it" else it }
            .distinct()
            .toList()
    }
}
