package com.nusanime.app.data.db

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "watch_history")
data class WatchHistory(
    @PrimaryKey val episodeId: String,
    val animeId: String,
    val animeTitle: String,
    val poster: String?,
    val episodeTitle: String?,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
)

@Entity(tableName = "followed")
data class Followed(
    @PrimaryKey val animeId: String,
    val title: String,
    val poster: String?,
    val lastEpisodeId: String?,
    val followedAt: Long,
)

@Dao
interface HistoryDao {
    @Query("SELECT * FROM watch_history ORDER BY updatedAt DESC LIMIT 20")
    fun recent(): Flow<List<WatchHistory>>

    @Query("SELECT * FROM watch_history WHERE episodeId = :id LIMIT 1")
    suspend fun get(id: String): WatchHistory?

    @Query("SELECT * FROM watch_history WHERE animeId = :animeId ORDER BY updatedAt DESC LIMIT 1")
    suspend fun latestForAnime(animeId: String): WatchHistory?

    @Upsert
    suspend fun upsert(entry: WatchHistory)

    @Query("DELETE FROM watch_history")
    suspend fun clear()
}

@Dao
interface FollowedDao {
    @Query("SELECT * FROM followed ORDER BY followedAt DESC")
    fun all(): Flow<List<Followed>>

    @Query("SELECT * FROM followed WHERE animeId = :id LIMIT 1")
    suspend fun get(id: String): Followed?

    @Query("SELECT * FROM followed")
    suspend fun allOnce(): List<Followed>

    @Upsert
    suspend fun upsert(entry: Followed)

    @Query("DELETE FROM followed WHERE animeId = :id")
    suspend fun delete(id: String)
}

@Database(entities = [WatchHistory::class, Followed::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
    abstract fun followedDao(): FollowedDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext, AppDatabase::class.java, "nusanime.db"
                ).build().also { instance = it }
            }
    }
}
