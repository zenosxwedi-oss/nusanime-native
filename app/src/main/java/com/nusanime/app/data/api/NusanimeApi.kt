package com.nusanime.app.data.api

import com.nusanime.app.data.model.*
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface NusanimeApi {

    @GET("samehadaku/home")
    suspend fun home(): retrofit2.Response<HomeData>

    @GET("samehadaku/ongoing")
    suspend fun ongoing(@Query("page") page: Int = 1): retrofit2.Response<ListData>

    @GET("samehadaku/completed")
    suspend fun completed(@Query("page") page: Int = 1): retrofit2.Response<ListData>

    @GET("samehadaku/schedule")
    suspend fun schedule(): retrofit2.Response<ScheduleData>

    @GET("samehadaku/search")
    suspend fun search(@Query("q") query: String): retrofit2.Response<ListData>

    /** Detail lives under /anime/{id} (NOT /detail/{id} which returns an error object). */
    @GET("samehadaku/anime/{animeId}")
    suspend fun detail(@Path("animeId", encoded = true) animeId: String): retrofit2.Response<DetailData>

    @GET("samehadaku/episode/{episodeId}")
    suspend fun episode(@Path("episodeId", encoded = true) episodeId: String): retrofit2.Response<EpisodeData>

    @GET("samehadaku/server/{serverId}")
    suspend fun server(@Path("serverId", encoded = true) serverId: String): retrofit2.Response<ServerResult>

    companion object {
        const val BASE_URL = "https://www.sankavollerei.web.id/anime/"
    }
}
