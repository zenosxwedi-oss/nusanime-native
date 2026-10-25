package com.nusanime.app.data

/** In-memory cache of poster/title per episodeId, filled by DetailScreen. */
object PosterCache {
    data class Info(val poster: String?, val animeId: String?, val animeTitle: String?)
    private val map = mutableMapOf<String, Info>()
    fun put(episodeId: String, info: Info) { map[episodeId] = info }
    fun get(episodeId: String): Info? = map[episodeId]
}
