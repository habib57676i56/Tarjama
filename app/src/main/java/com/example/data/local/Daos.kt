package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadedVideoDao {
    @Query("SELECT * FROM downloaded_videos ORDER BY createdAt DESC")
    fun observeAllDownloads(): Flow<List<DownloadedVideoEntity>>

    @Query("SELECT * FROM downloaded_videos WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): DownloadedVideoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: DownloadedVideoEntity): Long

    @Query(
        "UPDATE downloaded_videos SET downloadedBytes = :downloadedBytes, fileSizeBytes = :totalBytes, status = :status, errorMessage = :errorMessage WHERE id = :id"
    )
    suspend fun updateProgress(
        id: Long,
        downloadedBytes: Long,
        totalBytes: Long,
        status: String,
        errorMessage: String = ""
    )

    @Query("UPDATE downloaded_videos SET subtitleSrtContent = :srtContent, targetLangCode = :targetLang WHERE id = :id")
    suspend fun updateSubtitles(id: Long, srtContent: String, targetLang: String)

    @Query("DELETE FROM downloaded_videos WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface TranslationHistoryDao {
    @Query("SELECT * FROM translation_history ORDER BY isFavorite DESC, createdAt DESC LIMIT 100")
    fun observeHistory(): Flow<List<TranslationHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: TranslationHistoryEntity): Long

    @Query("UPDATE translation_history SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun setFavorite(id: Long, isFavorite: Boolean)

    @Query("DELETE FROM translation_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM translation_history WHERE isFavorite = 0")
    suspend fun clearNonFavorites()
}

@Dao
interface SubtitleCacheDao {
    @Query("SELECT * FROM subtitle_cache WHERE cacheKey = :key LIMIT 1")
    suspend fun getCached(key: String): SubtitleCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putCached(entity: SubtitleCacheEntity)
}
