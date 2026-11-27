package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

object DownloadStatus {
    const val QUEUED = "QUEUED"
    const val DOWNLOADING = "DOWNLOADING"
    const val COMPLETED = "COMPLETED"
    const val FAILED = "FAILED"
    const val CANCELLED = "CANCELLED"
}

@Entity(tableName = "downloaded_videos")
data class DownloadedVideoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val sourceUrl: String,
    val streamUrl: String,
    val localFilePath: String,
    val thumbnailUrl: String = "",
    val qualityLabel: String = "HD",
    val mimeType: String = "video/mp4",
    val fileSizeBytes: Long = 0L,
    val downloadedBytes: Long = 0L,
    val status: String = DownloadStatus.QUEUED,
    val subtitleSrtContent: String = "",
    val sourceLangCode: String = "auto",
    val targetLangCode: String = "ar",
    val errorMessage: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "translation_history")
data class TranslationHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceText: String,
    val translatedText: String,
    val sourceLangCode: String,
    val targetLangCode: String,
    val styleId: String,
    val isFavorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "subtitle_cache")
data class SubtitleCacheEntity(
    @PrimaryKey val cacheKey: String,
    val originalText: String,
    val translatedText: String,
    val targetLangCode: String,
    val createdAt: Long = System.currentTimeMillis()
)
