package com.nusanime.app.data.api

import android.content.Context
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

/** 18+ source: multi-site (javsubindo.com, javsubindo.cloud, arenajav.com), embeds + native m3u8 resolve. */
object JavSource {
    data class Site(val base: String, val imgHost: String)
    private val SITES = listOf(
        Site("https://javsubindo.com", "https://javsubindo.com"),
        Site("https://javsubindo.cloud", "https://javsubindo.cloud"),
        Site("https://arenajav.com", "https://arenajav.com"),
    )
    const val UA = "Mozilla/5.0 (Linux; Android 12; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"

    private fun get(url: String, referer: String? = null): String? = try {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10000; conn.readTimeout = 15000
        conn.setRequestProperty("User-Agent", UA)
        referer?.let { conn.setRequestProperty("Referer", it) }
        conn.instanceFollowRedirects = true
        if (conn.responseCode !in 200..299) null
        else conn.inputStream.bufferedReader().use { it.readText() }
    } catch (_: Exception) { null }

    private val RESERVED = setOf(
        "wp-content", "wp-json", "wp-admin", "page", "feed", "comments", "categories",
        "tags", "actors", "abjad", "aktris", "author", "bokep-indo", "bookmark", "dmca",
        "contact", "sitemap", "jav-subindo", "genre", "category", "tag",
    )
    private val POST_RE = Regex(""""https?://[a-z0-9.-]+/([a-z0-9][a-z0-9-]{4,})/"""")
    private val IMG_RE = Regex(""""(https://[a-z0-9.-]+/wp-content/uploads/[^"]+?\.(?:jpg|jpeg|webp|png))"""")
    private val TAG_RE = Regex(""""https?://[a-z0-9.-]+/(?:tag|genre|categories)/([a-z0-9-]{3,30})/"""")
    private val TITLE_RE = Regex(""""([^"]{10,120})"""")
    private val IFRAME_RE = Regex(""""(https?://[^"]*(?:embed|iframe|player)[^"]*)"""")
    private val M3U8_RE = Regex(""""(https?://[^\s"']+\.m3u8[^\s"']*)""""")

    private fun parse(html: String, siteIdx: Int): List<AnimeItem> {
        val site = SITES[siteIdx]
        val items = LinkedHashMap<String, AnimeItem>()
        val imgs = IMG_RE.findAll(html).map { it.groupValues[1] }.distinct().toList()
        var i = 0
        POST_RE.findAll(html).forEach { m ->
            val slug = m.groupValues[1]
            if (slug !in RESERVED && !slug.contains("wp-") && !items.containsKey(slug)) {
                val img = imgs.getOrNull(i) ?: imgs.firstOrNull() ?: ""
                i++
                items[slug] = AnimeItem(
                    title = slug.replace("-", " ").replaceFirstChar { it.uppercase() },
                    poster = img, animeId = "jav:$slug", episodeId = null, episodes = null,
                    releasedOn = null, type = "jav", score = null, statusField = null,
                    estimation = null, genreList = null, siteIdx = siteIdx,
                )
            }
        }
        return items.values.toList()
    }

    suspend fun latest(page: Int = 1): List<AnimeItem> = withContext(Dispatchers.IO) {
        SITES.forEachIndexed { idx, s ->
            val h = get(if (page == 1) s.base else "\${s.base}/page/\$page")
            if (h != null) {
                val r = parse(h, idx)
                if (r.isNotEmpty()) return@withContext r
            }
        }
        emptyList()
    }

    suspend fun search(q: String): List<AnimeItem> = withContext(Dispatchers.IO) {
        SITES.forEachIndexed { idx, s ->
            val h = get("\${s.base}/?s=" + android.net.Uri.encode(q))
            if (h != null) {
                val r = parse(h, idx)
                if (r.isNotEmpty()) return@withContext r
            }
        }
        emptyList()
    }

    suspend fun tags(): List<String> = withContext(Dispatchers.IO) {
        val h = get(SITES[0].base) ?: return@withContext listOf("uncensored", "censored", "jilbab", "tante", "abg")
        TAG_RE.findAll(h).map { it.groupValues[1] }.distinct().take(30).toList()
            .ifEmpty { listOf("uncensored", "censored", "jilbab", "tante", "abg") }
    }

    suspend fun tag(t: String): List<AnimeItem> = withContext(Dispatchers.IO) {
        SITES.forEachIndexed { idx, s ->
            val h = get("\${s.base}/tag/\$t") ?: get("\${s.base}/genre/\$t") ?: get("\${s.base}/categories/\$t")
            if (h != null) {
                val r = parse(h, idx)
                if (r.isNotEmpty()) return@withContext r
            }
        }
        emptyList()
    }

    data class JavServer(val label: String, val url: String)

    /** Embeds on a post page (multi-server). */
    suspend fun embeds(slug: String, siteIdx: Int = 0): List<JavServer> = withContext(Dispatchers.IO) {
        val base = SITES[siteIdx.coerceIn(SITES.indices)].base
        val h = get("\$base/\$slug/", referer = base) ?: return@withContext emptyList()
        val out = LinkedHashMap<String, JavServer>()
        IFRAME_RE.findAll(h).forEach { m ->
            val u = m.groupValues[1]
            if (u.contains("nekovid") || u.contains("embed") && !u.contains("arenajav") && !u.contains(base)) {
                val host = try { java.net.URI(u).host ?: u } catch (_: Exception) { u }
                if (!out.containsKey(u)) out[u] = JavServer("Server \${out.size + 1}", u)
            }
        }
        // arenajav data-em base64 embeds
        Regex(""""data-em="([A-Za-z0-9+/=]{40,})""""").findAll(h).forEach { m ->
            try {
                val dec = String(android.util.Base64.decode(m.groupValues[1], android.util.Base64.DEFAULT))
                Regex(""""src="([^"]+)""""").find(dec)?.let { s ->
                    val u = s.groupValues[1]
                    if (!out.containsKey(u)) out[u] = JavServer("Server \${out.size + 1}", u)
                }
            } catch (_: Exception) {}
        }
        out.values.toList()
    }

    /** Resolve direct m3u8 from a nekovid-style embed (packed JS unpack + sources fetch). Native ExoPlayer => NO embed ads. */
    suspend fun resolveM3u8(embedUrl: String): String? = withContext(Dispatchers.IO) {
        val h = get(embedUrl, referer = "https://javsubindo.com/") ?: return@withContext null
        M3U8_RE.find(h)?.groupValues?.get(1) ?: run {
            // try unpack eval(function(p,a,c,k,e,d)...)
            val m = Regex("\\}\\('(.*)',(\\d+),(\\d+),'([^']*)'\\.split\\('\\|'\\)").find(h)
            if (m != null) {
                val p = m.groupValues[1]; val k = m.groupValues[4].split("|")
                val out = Regex("\\d+").replace(p) { mm ->
                    val n = mm.value.toInt(); if (n < k.size) k[n] else mm.value
                }
                M3U8_RE.find(out)?.groupValues?.get(1)
            } else null
        }
    }
}
