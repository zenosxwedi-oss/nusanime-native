package com.nusanime.app.data.api

import android.content.Context
import com.google.gson.annotations.SerializedName
import com.nusanime.app.data.model.AnimeItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** 18+ section (Nekopoi via Sankavollerei). Gated by age confirmation in Settings. */
object AdultPrefs {
    private const val KEY = "adult_mode_18"
    fun enabled(ctx: Context): Boolean = ctx.getSharedPreferences("nusa", 0).getBoolean(KEY, false)
    fun set(ctx: Context, v: Boolean) = ctx.getSharedPreferences("nusa", 0).edit().putBoolean(KEY, v).apply()
}

object JavSource {
    data class JavItem(
        val title: String?,
        val slug: String?,
        val thumbnail: String?,
    )

    private data class JavEnvelope(
        val status: String?,
        val data: List<JavItem>?,
    )

    suspend fun latest(): List<AnimeItem> = withContext(Dispatchers.IO) {
        try {
            val conn = URL("https://www.sankavollerei.web.id/anime/nekopoi/latest-jav").openConnection() as HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 15000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 12)")
            if (conn.responseCode !in 200..299) return@withContext emptyList()
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val env = com.google.gson.Gson().fromJson(body, JavEnvelope::class.java)
            (env.data ?: emptyList()).map { j ->
                AnimeItem(
                    title = j.title,
                    poster = j.thumbnail,
                    animeId = "jav:" + (j.slug ?: ""),
                    episodeId = null,
                    episodes = null,
                    releasedOn = null,
                    type = "18+",
                    score = null,
                    statusField = null,
                    estimation = null,
                    genreList = null,
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
