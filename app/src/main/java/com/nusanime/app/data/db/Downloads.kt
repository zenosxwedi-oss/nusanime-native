package com.nusanime.app.data.db

import androidx.room.*

@Entity(tableName = "downloads")
data class DownloadEntry(
    @PrimaryKey val episodeId: String,
    val animeId: String,
    val animeTitle: String,
    val episodeLabel: String,
    val poster: String?,
    val url: String,
    val quality: String?,
    /** QUEUED = 0, RUNNING = 1, DONE = 2, FAILED = 3 */
    val state: Int,
    val bytesDownloaded: Long,
    val bytesTotal: Long,
    val filePath: String?,
    val updatedAt: Long,
)

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY updatedAt DESC")
    fun all(): kotlinx.coroutines.flow.Flow<List<DownloadEntry>>

    @Query("SELECT * FROM downloads WHERE episodeId = :id")
    suspend fun get(id: String): DownloadEntry?

    @Query("SELECT * FROM downloads WHERE state IN (0,1)")
    suspend fun active(): List<DownloadEntry>

    @Upsert
    suspend fun upsert(entry: DownloadEntry)

    @Query("DELETE FROM downloads WHERE episodeId = :id")
    suspend fun delete(id: String)
}
