package com.nusanime.app.data.api

import com.nusanime.app.data.model.AnimeItem
import com.nusanime.app.data.model.DetailData
import com.nusanime.app.data.model.Envelope
import com.nusanime.app.data.model.EpisodeData
import com.nusanime.app.data.model.EpisodeItem
import com.nusanime.app.data.model.HomeData
import com.nusanime.app.data.model.ListData
import com.nusanime.app.data.model.ScheduleData
import com.nusanime.app.data.model.ServerData
import com.nusanime.app.data.model.ServerResult

/**
 * Adapter: serves the same model types the UI already consumes (sankavollerei shapes),
 * but backed by the animein.net API (same backend as AnimeX-App).
 * UI code stays untouched.
 */
object AxSource {

    private fun AxMovie.toItem(): AnimeItem = AnimeItem(
        title = title,
        poster = posterUrl,
        animeId = id,
        episodeId = episodeId,
        episodes = episodeIndex,
        releasedOn = year,
        type = type,
        score = rating,
        statusField = status,
        estimation = null,
        genreList = genre?.split(",")?.mapNotNull { g ->
            val t = g.trim()
            if (t.isEmpty()) null else com.nusanime.app.data.model.Genre(title = t, genreId = t)
        },
    )

    private fun AxEpisode.toItem(): EpisodeItem = EpisodeItem(
        title = title ?: ("Episode " + (index ?: "?")),
        episodeId = id,
        date = null,
        releasedOn = null,
    )

    suspend fun home(): Envelope<HomeData> {
        val h = AnimeInApi.home() ?: throw RuntimeException("Gagal memuat beranda")
        fun rows(l: List<AxMovie>?) = HomeData.RecentData(animeList = l?.map { it.toItem() })
        val data = HomeData(
            recent = rows(h.update ?: h.new),
            top10 = rows(h.popular ?: h.hot),
            batch = rows(h.today),
            movie = rows(h.random ?: h.waiting),
        )
        return Envelope(status = "success", data = data)
    }

    suspend fun ongoing(page: Int = 1): Envelope<ListData> {
        // Primary: explore by type id 6 (SERIES); fallback: latest list filtered; last resort: home update list
        var r = AnimeInApi.exploreBy("type", "6", page - 1)
        if (r.isEmpty()) r = AnimeInApi.exploreLatest(page - 1, typeFilter = "series")
        if (r.isEmpty() && page == 1) {
            val h = AnimeInApi.home()
            r = (h?.update ?: h?.new).orEmpty()
        }
        return Envelope(status = "success", data = ListData(animeList = r.map { it.toItem() }))
    }

    suspend fun completed(page: Int = 1): Envelope<ListData> {
        var r = AnimeInApi.exploreBy("type", "2", page - 1)
        if (r.isEmpty()) r = AnimeInApi.exploreLatest(page - 1, statusFilter = "completed")
        if (r.isEmpty() && page == 1) {
            val h = AnimeInApi.home()
            r = (h?.popular ?: h?.hot).orEmpty()
        }
        return Envelope(status = "success", data = ListData(animeList = r.map { it.toItem() }))
    }

    suspend fun schedule(): Envelope<ScheduleData> {
        val days = listOf("Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu", "Minggu")
        val out = mutableListOf<ScheduleData.ScheduleDay>()
        days.forEach { d ->
            val list = AnimeInApi.schedule(d.lowercase())
            if (list.isNotEmpty()) out.add(ScheduleData.ScheduleDay(day = d, animeList = list.map { it.toItem() }))
        }
        return Envelope(status = "success", data = ScheduleData(days = out))
    }

    suspend fun search(q: String): Envelope<ListData> {
        val r = AnimeInApi.search(q)
        return Envelope(status = "success", data = ListData(animeList = r.map { it.toItem() }))
    }

    /** Episode metadata + server list flattened into "Server 1..N" per quality. */
    suspend fun detail(animeId: String): Envelope<DetailData> {
        // Fallback: samehadaku detail (slug ids from genre list / search may not exist on animein)
        try {
            val m = AnimeInApi.detail(animeId)
            if (m == null) throw RuntimeException("not on animein")
            val eps = AnimeInApi.episodes(animeId)
        val data = DetailData(
            title = m.title,
            poster = m.posterUrl,
            synopsis = m.synopsis,
            status = m.status,
            type = m.type,
            duration = null,
            season = m.season ?: m.year,
            studios = m.studio,
            score = m.rating,
            genreList = m.genre?.split(",")?.mapNotNull { g ->
                val t = g.trim()
                if (t.isEmpty()) null else com.nusanime.app.data.model.Genre(title = t, genreId = t)
            },
            episodeList = eps.map { it.toItem() },
        )
        return Envelope(status = "success", data = data)
        } catch (_: Exception) {}

        // Otakudesu (sankavollerei) — DAFTAR EPISODE LENGKAP sampai episode 1
        try {
            val od = odDetail(animeId)
            if (od != null) return Envelope(status = "success", data = od)
        } catch (_: Exception) {}

        // samehadaku direct detail (body is an ENVELOPE {status, data:{...}} — parse .data, not the envelope)
        val body = rawJson("anime/" + animeId) ?: throw RuntimeException("Anime tidak ditemukan")
        val obj = try { com.google.gson.JsonParser.parseString(body).asJsonObject } catch (_: Exception) { null }
            ?: throw RuntimeException("Anime tidak ditemukan")
        val dataEl = obj.getAsJsonObject("data") ?: obj
        val fb = com.google.gson.Gson().fromJson(dataEl, DetailData::class.java)
            ?: throw RuntimeException("Anime tidak ditemukan")
        if (fb.title == null) throw RuntimeException("Anime tidak ditemukan")
        return Envelope(status = "success", data = fb)
    }

    suspend fun episode(episodeId: String): Envelope<EpisodeData> {
        // PRIMARY: Otakudesu (sankavollerei) — semua episode punya stream
        try {
            val od = odEpisode(episodeId)
            if (od != null && (!od.defaultStreamingUrl.isNullOrBlank() || !od.server?.qualities.isNullOrEmpty())) {
                return Envelope(status = "success", data = od)
            }
        } catch (_: Exception) {}
        // streamnew is flaky: retry a few times before giving up
        var servers = AnimeInApi.servers(episodeId)
        var tries = 0
        while (servers.isEmpty() && tries < 3) {
            tries++
            kotlinx.coroutines.delay(800L * tries)
            servers = AnimeInApi.servers(episodeId)
        }

        // group by quality, keep order; build ServerData shape
        // FALLBACK (like v3.4.x era): sankavollerei direct episode data when animein has no streams
        if (servers.isEmpty()) {
            try {
                val fb = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val conn = java.net.URL("https://www.sankavollerei.web.id/anime/samehadaku/episode/" + episodeId).openConnection() as java.net.HttpURLConnection
                    conn.connectTimeout = 12000
                    conn.readTimeout = 15000
                    conn.setRequestProperty("User-Agent", "okhttp/4.12.0")
                    conn.setRequestProperty("Accept", "application/json")
                    if (conn.responseCode !in 200..299) throw RuntimeException("HTTP " + conn.responseCode)
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val obj = try { com.google.gson.JsonParser.parseString(body).asJsonObject } catch (_: Exception) { null }
                    val dataEl = obj?.getAsJsonObject("data") ?: obj
                    com.google.gson.Gson().fromJson(dataEl, EpisodeData::class.java)
                }
                if (fb != null && (!fb.defaultStreamingUrl.isNullOrBlank() || !fb.server?.qualities.isNullOrEmpty())) {
                    return Envelope(status = "success", data = fb)
                }
            } catch (_: Exception) {}
        }

        val groups = LinkedHashMap<String, MutableList<ServerData.ServerItem>>()
        servers.forEachIndexed { i, s ->
            val q = s.quality?.takeIf { it.isNotBlank() } ?: "Default"
            groups.getOrPut(q) { mutableListOf() }.add(
                ServerData.ServerItem(title = "Server ${i + 1}", serverId = s.link),
            )
        }
        val qualities = groups.map { (q, list) -> ServerData.QualityGroup(title = q, serverList = list) }
        val first = servers.firstOrNull()?.link
        val data = EpisodeData(
            title = null,
            animeId = null,
            poster = null,
            releasedOn = null,
            defaultStreamingUrl = first,
            hasPrevEpisode = null,
            prevEpisode = null,
            hasNextEpisode = null,
            nextEpisode = null,
            server = if (qualities.isEmpty()) null else ServerData(qualities = qualities),
            downloadUrl = null,
        )
        return Envelope(status = "success", data = data)
    }


    // ---------- Otakudesu via sankavollerei (FULL episode list) ----------
    private const val OD_BASE = "https://www.sankavollerei.web.id/anime"

    // Lewat PROXY VPS (javapi) — HP sering diblok CF kalau akses langsung ke sankavollerei
    private fun odGet(path: String): String? {
        return try {
            val proxied = when {
                path.startsWith("/anime/anime/") -> "/od/detail?slug=" + java.net.URLEncoder.encode(path.removePrefix("/anime/anime/"), "UTF-8")
                path.startsWith("/anime/episode/") -> "/od/episode?id=" + java.net.URLEncoder.encode(path.removePrefix("/anime/episode/"), "UTF-8")
                path.startsWith("/anime/server/") -> "/od/server?id=" + java.net.URLEncoder.encode(path.removePrefix("/anime/server/"), "UTF-8")
                path.startsWith("/anime/search/") -> "/od/search?q=" + java.net.URLEncoder.encode(path.removePrefix("/anime/search/"), "UTF-8")
                else -> null
            } ?: return null
            val conn = java.net.URL("https://vpn.nusanime.web.id/javapi" + proxied).openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            conn.setRequestProperty("Accept", "application/json")
            if (conn.responseCode !in 200..299) null
            else conn.inputStream.bufferedReader().use { it.readText() }
        } catch (_: Exception) { null }
    }

    /** Cari anime di Otakudesu lalu ambil detail + daftar episode LENGKAP. */
    suspend fun odDetail(animeId: String): DetailData? =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { odDetailIO(animeId) }

    private fun odDetailIO(animeId: String): DetailData? {
        // animeId may be an otakudesu id already (e.g. "1piece-sub-indo") or a search term
        var body = odGet("/anime/anime/" + java.net.URLEncoder.encode(animeId, "UTF-8"))
        var det = parseOdDetail(body)
        if (det != null) return det
        // fallback: search then take first result
        val sbody = odGet("/anime/search/" + java.net.URLEncoder.encode(animeId, "UTF-8"))
        if (sbody != null) {
            try {
                val arr = com.google.gson.JsonParser.parseString(sbody).asJsonObject
                    .getAsJsonObject("data").getAsJsonArray("animeList")
                val firstId = arr.firstOrNull()?.asJsonObject?.get("animeId")?.asString
                if (firstId != null) {
                    body = odGet("/anime/anime/" + java.net.URLEncoder.encode(firstId, "UTF-8"))
                    det = parseOdDetail(body)
                    if (det != null) return det
                }
            } catch (_: Exception) {}
        }
        return null
    }

    private fun parseOdDetail(body: String?): DetailData? {
        if (body == null) return null
        return try {
            val dataEl = com.google.gson.JsonParser.parseString(body).asJsonObject.getAsJsonObject("data")
            if (dataEl == null || dataEl.isJsonNull) null
            else {
                val d = com.google.gson.Gson().fromJson(dataEl, DetailData::class.java)
                if (d?.title == null) null else d
            }
        } catch (_: Exception) { null }
    }

    /** Episode Otakudesu: serverId perlu di-resolve via /anime/server/:id. */
    suspend fun odEpisode(episodeId: String): EpisodeData? =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { odEpisodeIO(episodeId) }

    private fun odEpisodeIO(episodeId: String): EpisodeData? {
        val body = odGet("/anime/episode/" + java.net.URLEncoder.encode(episodeId, "UTF-8")) ?: return null
        return try {
            val dataEl = com.google.gson.JsonParser.parseString(body).asJsonObject.getAsJsonObject("data")
            if (dataEl == null || dataEl.isJsonNull) null
            else com.google.gson.Gson().fromJson(dataEl, EpisodeData::class.java)
        } catch (_: Exception) { null }
    }

    /** Resolve serverId (otakudesu) → embed URL. */
    suspend fun odServer(serverId: String): String? =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { odServerIO(serverId) }

    private fun odServerIO(serverId: String): String? {
        val body = odGet("/anime/server/" + java.net.URLEncoder.encode(serverId, "UTF-8")) ?: return null
        return try {
            com.google.gson.JsonParser.parseString(body).asJsonObject
                .getAsJsonObject("data").get("url")?.asString
        } catch (_: Exception) { null }
    }

    /** serverId: direct link (animein) or otakudesu server id needing resolution. */
    suspend fun server(serverId: String): Envelope<ServerResult> {
        if (!serverId.startsWith("http")) {
            try {
                val resolved = odServer(serverId)
                if (!resolved.isNullOrBlank()) return Envelope(status = "success", data = ServerResult(url = resolved))
            } catch (_: Exception) {}
        }
        return Envelope(status = "success", data = ServerResult(url = serverId))
    }

    private suspend fun rawJson(path: String): String? =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val conn = java.net.URL("https://www.sankavollerei.web.id/anime/samehadaku/" + path).openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 12000
                conn.readTimeout = 15000
                conn.setRequestProperty("User-Agent", "okhttp/4.12.0")
                conn.setRequestProperty("Accept", "application/json")
                if (conn.responseCode !in 200..299) return@withContext null
                conn.inputStream.bufferedReader().use { it.readText() }
            } catch (_: Exception) { null }
        }

    /** Genre list from sankavollerei: [{title, genreId}] */
    suspend fun genreList(): List<com.nusanime.app.data.model.Genre> {
        val body = rawJson("genres") ?: return emptyList()
        return try {
            val parsed = com.google.gson.JsonParser.parseString(body).asJsonObject
                .getAsJsonObject("data").getAsJsonArray("genreList")
            parsed.mapNotNull { el ->
                val o = el.asJsonObject
                val id = o.get("genreId")?.asString ?: return@mapNotNull null
                com.nusanime.app.data.model.Genre(title = o.get("title")?.asString ?: id, genreId = id)
            }
        } catch (_: Exception) { emptyList() }
    }

    /** Anime list for a genre from sankavollerei. */
    suspend fun genreAnime(genreId: String): Envelope<ListData> {
        val body = rawJson("genres/" + genreId)
        val list = if (body != null) {
            try {
                val parsed = com.google.gson.JsonParser.parseString(body).asJsonObject
                    .getAsJsonObject("data").getAsJsonArray("animeList")
                parsed.mapNotNull { el ->
                    val o = el.asJsonObject
                    fun s(k: String) = o.get(k)?.takeIf { !it.isJsonNull }?.asString
                    AnimeItem(
                        title = s("title"),
                        poster = s("poster"),
                        animeId = s("animeId") ?: s("href")?.substringAfterLast('/')?.removeSuffix("/"),
                        episodeId = s("episodeId") ?: s("href")?.substringAfterLast('/')?.removeSuffix("/"),
                        episodes = s("episode") ?: s("episodes"),
                        releasedOn = s("releasedOn") ?: s("release"),
                        type = s("type"),
                        score = s("rating") ?: s("score"),
                        statusField = s("status"),
                        estimation = null,
                        genreList = null,
                    )
                }
            } catch (_: Exception) { emptyList() }
        } else emptyList()
        return Envelope(status = if (list.isEmpty()) "empty" else "success", data = ListData(animeList = list))
    }
}
