package com.nusanime.app.data.db

import androidx.room.*

@Entity(tableName = "bookmarks")
data class Bookmark(
    @PrimaryKey val animeId: String,
    val title: String,
    val poster: String?,
    val addedAt: Long,
)

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks ORDER BY addedAt DESC")
    fun all(): kotlinx.coroutines.flow.Flow<List<Bookmark>>

    @Query("SELECT * FROM bookmarks WHERE animeId = :id")
    suspend fun get(id: String): Bookmark?

    @Query("SELECT * FROM bookmarks")
    suspend fun allOnce(): List<Bookmark>

    @Upsert
    suspend fun upsert(entry: Bookmark)

    @Query("DELETE FROM bookmarks WHERE animeId = :id")
    suspend fun delete(id: String)
}
