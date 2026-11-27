package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Database(
    entities = [
        DownloadedVideoEntity::class,
        TranslationHistoryEntity::class,
        SubtitleCacheEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun downloadedVideoDao(): DownloadedVideoDao
    abstract fun translationHistoryDao(): TranslationHistoryDao
    abstract fun subtitleCacheDao(): SubtitleCacheDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "tarjuman_local.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "tarjuman_prefs")

data class UserSettings(
    val isDarkGlass: Boolean = true,
    val showDualSubtitles: Boolean = true,
    val subtitleFontScale: Float = 1.0f,
    val defaultSourceLang: String = "auto",
    val defaultTargetLang: String = "ar",
    val translationStyleId: String = "natural"
)

class UserPreferencesRepository(private val context: Context) {
    private object Keys {
        val IS_DARK_GLASS = booleanPreferencesKey("is_dark_glass")
        val SHOW_DUAL_SUBTITLES = booleanPreferencesKey("show_dual_subtitles")
        val SUBTITLE_FONT_SCALE = floatPreferencesKey("subtitle_font_scale")
        val DEFAULT_SOURCE_LANG = stringPreferencesKey("default_source_lang")
        val DEFAULT_TARGET_LANG = stringPreferencesKey("default_target_lang")
        val TRANSLATION_STYLE = stringPreferencesKey("translation_style")
    }

    val settingsFlow: Flow<UserSettings> = context.dataStore.data.map { prefs ->
        UserSettings(
            isDarkGlass = prefs[Keys.IS_DARK_GLASS] ?: true,
            showDualSubtitles = prefs[Keys.SHOW_DUAL_SUBTITLES] ?: true,
            subtitleFontScale = prefs[Keys.SUBTITLE_FONT_SCALE] ?: 1.0f,
            defaultSourceLang = prefs[Keys.DEFAULT_SOURCE_LANG] ?: "auto",
            defaultTargetLang = prefs[Keys.DEFAULT_TARGET_LANG] ?: "ar",
            translationStyleId = prefs[Keys.TRANSLATION_STYLE] ?: "natural"
        )
    }

    suspend fun setDarkGlass(dark: Boolean) {
        context.dataStore.edit { it[Keys.IS_DARK_GLASS] = dark }
    }

    suspend fun setShowDualSubtitles(show: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_DUAL_SUBTITLES] = show }
    }

    suspend fun setSubtitleFontScale(scale: Float) {
        context.dataStore.edit { it[Keys.SUBTITLE_FONT_SCALE] = scale.coerceIn(0.75f, 1.6f) }
    }

    suspend fun setLanguages(sourceCode: String, targetCode: String) {
        context.dataStore.edit {
            it[Keys.DEFAULT_SOURCE_LANG] = sourceCode
            it[Keys.DEFAULT_TARGET_LANG] = targetCode
        }
    }

    suspend fun setTranslationStyle(styleId: String) {
        context.dataStore.edit { it[Keys.TRANSLATION_STYLE] = styleId }
    }
}
