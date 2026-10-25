package com.nusanime.app.data.model

import com.google.gson.annotations.SerializedName

/** Generic API envelope. `data` is validated manually because its shape varies per endpoint. */
data class ApiEnvelope(
    @SerializedName("status") val status: String?,
    @SerializedName("message") val message: String?,
)

data class AnimeItem(
    @SerializedName("title") val title: String?,
    @SerializedName("poster") val poster: String?,
    @SerializedName("animeId") val animeId: String?,
    @SerializedName("episodeId") val episodeId: String?,
    @SerializedName("episodes") val episodes: String?,
    @SerializedName("releasedOn") val releasedOn: String?,
    @SerializedName("type") val type: String?,
    @SerializedName("score") val score: Any?,
    @SerializedName("status") val statusField: String?,
    @SerializedName("estimation") val estimation: String?,
    @SerializedName("genreList") val genreList: List<Genre>?,
) {
    /** score can be a String ("★ 8.7") or an object {value, users}. */
    fun scoreText(): String = when (score) {
        is String -> score
        is Map<*, *> -> (score["value"] as? String)?.let { "★ $it" }
        else -> null
    } ?: ""
}

class ScoreDto(
    @SerializedName("value") val value: String?,
    @SerializedName("users") val users: String?,
)

data class Genre(
    @SerializedName("title") val title: String?,
    @SerializedName("genreId") val genreId: String?,
)

data class HomeData(
    @SerializedName("recent") val recent: RecentData?,
    @SerializedName("top10") val top10: RecentData?,
    @SerializedName("batch") val batch: RecentData?,
    @SerializedName("movie") val movie: RecentData?,
) {
    data class RecentData(
        @SerializedName("animeList") val animeList: List<AnimeItem>?,
    )
}

data class ListData(
    @SerializedName("animeList") val animeList: List<AnimeItem>?,
)

data class ScheduleData(
    @SerializedName("days") val days: List<ScheduleDay>?,
) {
    data class ScheduleDay(
        @SerializedName("day") val day: String?,
        @SerializedName("animeList") val animeList: List<AnimeItem>?,
    )
}

data class DetailData(
    @SerializedName("title") val title: String?,
    @SerializedName("poster") val poster: String?,
    @SerializedName("synopsis") val synopsis: Any?,
    @SerializedName("status") val status: String?,
    @SerializedName("type") val type: String?,
    @SerializedName("duration") val duration: String?,
    @SerializedName("season") val season: String?,
    @SerializedName("studios") val studios: String?,
    @SerializedName("score") val score: Any?,
    @SerializedName("genreList") val genreList: List<Genre>?,
    @SerializedName("episodeList") val episodeList: List<EpisodeItem>?,
) {
    /** synopsis can be a String or {paragraphs:[...]}. */
    fun synopsisText(): String = when (synopsis) {
        is String -> synopsis
        is Map<*, *> -> (synopsis["paragraphs"] as? List<*>)?.filterIsInstance<String>()?.joinToString("\n\n")
        else -> null
    }.orEmpty()
}

class SynopsisDto(
    @SerializedName("paragraphs") val paragraphs: List<String>?,
)

data class EpisodeItem(
    @SerializedName("title") val title: Any?,
    @SerializedName("episodeId") val episodeId: String?,
    @SerializedName("date") val date: String?,
    @SerializedName("releasedOn") val releasedOn: String?,
) {
    /** title can be a number or string. */
    fun titleText(): String = when (title) {
        is Number -> "Episode $title"
        is String -> title
        else -> "Episode"
    }
}

data class EpisodeData(
    @SerializedName("title") val title: String?,
    @SerializedName("animeId") val animeId: String?,
    @SerializedName("poster") val poster: String?,
    @SerializedName("releasedOn") val releasedOn: String?,
    @SerializedName("defaultStreamingUrl") val defaultStreamingUrl: String?,
    @SerializedName("hasPrevEpisode") val hasPrevEpisode: Boolean?,
    @SerializedName("prevEpisode") val prevEpisode: EpisodeRef?,
    @SerializedName("hasNextEpisode") val hasNextEpisode: Boolean?,
    @SerializedName("nextEpisode") val nextEpisode: EpisodeRef?,
    @SerializedName("server") val server: ServerData?,
    @SerializedName("downloadUrl") val downloadUrl: DownloadData?,
) {
    data class EpisodeRef(
        @SerializedName("title") val title: Any?,
        @SerializedName("episodeId") val episodeId: String?,
    ) {
        fun titleText(): String = when (title) {
            is Number -> "Episode $title"
            is String -> title
            else -> "Episode"
        }
    }
}

data class ServerData(
    @SerializedName("qualities") val qualities: List<QualityGroup>?,
) {
    data class QualityGroup(
        @SerializedName("title") val title: String?,
        @SerializedName("serverList") val serverList: List<ServerItem>?,
    )

    data class ServerItem(
        @SerializedName("title") val title: String?,
        @SerializedName("serverId") val serverId: String?,
    )
}

data class ServerResult(
    @SerializedName("url") val url: String?,
)

data class DownloadData(
    @SerializedName("formats") val formats: List<DownloadFormat>?,
) {
    data class DownloadFormat(
        @SerializedName("title") val title: String?,
        @SerializedName("qualities") val qualities: List<DownloadQuality>?,
    )

    data class DownloadQuality(
        @SerializedName("title") val title: String?,
        @SerializedName("urls") val urls: List<DownloadUrl>?,
    )

    data class DownloadUrl(
        @SerializedName("title") val title: String?,
        @SerializedName("url") val url: String?,
    )
}
