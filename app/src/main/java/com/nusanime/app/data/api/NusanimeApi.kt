package com.nusanime.app.data.api

import com.nusanime.app.data.model.*
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface NusanimeApi {

    @GET("samehadaku/home")
    suspend fun home(): Envelope<HomeData>

    @GET("samehadaku/ongoing")
    suspend fun ongoing(@Query("page") page: Int = 1): Envelope<ListData>

    @GET("samehadaku/completed")
    suspend fun completed(@Query("page") page: Int = 1): Envelope<ListData>

    @GET("samehadaku/schedule")
    suspend fun schedule(): Envelope<ScheduleData>

    @GET("samehadaku/search")
    suspend fun search(@Query("q") query: String): Envelope<ListData>

    /** Detail lives under /anime/{id} (NOT /detail/{id} which returns an error object). */
    @GET("samehadaku/anime/{animeId}")
    suspend fun detail(@Path("animeId", encoded = true) animeId: String): Envelope<DetailData>

    @GET("samehadaku/episode/{episodeId}")
    suspend fun episode(@Path("episodeId", encoded = true) episodeId: String): Envelope<EpisodeData>

    @GET("samehadaku/server/{serverId}")
    suspend fun server(@Path("serverId", encoded = true) serverId: String): Envelope<ServerResult>

    companion object {
        const val BASE_URL = "https://www.sankavollerei.web.id/anime/"
    }
}
