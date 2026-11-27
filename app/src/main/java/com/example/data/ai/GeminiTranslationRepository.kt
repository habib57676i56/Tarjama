package com.example.data.ai

import android.util.Log
import com.example.core.AppLanguage
import com.example.core.LanguageCatalog
import com.example.core.TranslationStyle
import com.example.data.local.SubtitleCacheDao
import com.example.data.local.SubtitleCacheEntity
import com.example.data.media.SubtitleCue
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.security.MessageDigest

class GeminiTranslationRepository(
    private val subtitleCacheDao: SubtitleCacheDao
) {
    companion object {
        private const val TAG = "GeminiTranslationRepo"
        private const val DEFAULT_MODEL = "gemini-3.8-flash"
        private const val TIMEOUT_MS = 60_000L
    }

    private val streamModel by lazy {
        Firebase.ai.generativeModel(
            modelName = DEFAULT_MODEL,
            generationConfig = generationConfig {
                temperature = 0.2f
            }
        )
    }

    private val subtitleBatchSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "items" to Schema.array(
                    items = Schema.obj(
                        properties = mapOf(
                            "index" to Schema.integer(description = "Original cue index"),
                            "translated" to Schema.string(description = "Translated subtitle text")
                        )
                    ),
                    description = "Translated subtitle cues matching input order"
                )
            )
        )
    }

    private val generatedSubtitlesSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "cues" to Schema.array(
                    items = Schema.obj(
                        properties = mapOf(
                            "startMs" to Schema.integer(description = "Start timestamp in milliseconds"),
                            "endMs" to Schema.integer(description = "End timestamp in milliseconds"),
                            "original" to Schema.string(description = "Spoken original text"),
                            "translated" to Schema.string(description = "Translated subtitle text")
                        )
                    ),
                    description = "Timed subtitle cues extracted and translated from the video/audio"
                )
            )
        )
    }

    private val batchJsonModel by lazy {
        Firebase.ai.generativeModel(
            modelName = DEFAULT_MODEL,
            generationConfig = generationConfig {
                temperature = 0.15f
                responseMimeType = "application/json"
                responseSchema = subtitleBatchSchema
            }
        )
    }

    private val mediaSubtitlesModel by lazy {
        Firebase.ai.generativeModel(
            modelName = DEFAULT_MODEL,
            generationConfig = generationConfig {
                temperature = 0.2f
                responseMimeType = "application/json"
                responseSchema = generatedSubtitlesSchema
            }
        )
    }

    fun translateStream(
        text: String,
        sourceLang: AppLanguage,
        targetLang: AppLanguage,
        style: TranslationStyle
    ): Flow<String> = flow {
        val cleanText = text.trim()
        if (cleanText.isEmpty()) return@flow

        val prompt = buildTranslationPrompt(cleanText, sourceLang, targetLang, style)
        val accumulated = StringBuilder()
        try {
            withTimeout(TIMEOUT_MS) {
                streamModel.generateContentStream(prompt).collect { chunk ->
                    val piece = chunk.text.orEmpty()
                    if (piece.isNotEmpty()) {
                        accumulated.append(piece)
                        emit(accumulated.toString().trim())
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Streaming translation timed out", e)
            throw IllegalStateException("انتهت مهلة الترجمة (60 ثانية). حاول مرة أخرى.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Streaming translation failed", e)
            throw e
        }
    }

    suspend fun translateInstantCached(
        text: String,
        sourceLang: AppLanguage,
        targetLang: AppLanguage,
        style: TranslationStyle = TranslationStyle.NATURAL
    ): Result<String> {
        val cleanText = text.trim()
        if (cleanText.isEmpty()) return Result.success("")

        val cacheKey = sha256("${cleanText}|${targetLang.code}|${style.id}")
        subtitleCacheDao.getCached(cacheKey)?.let { cached ->
            return Result.success(cached.translatedText)
        }

        return try {
            val prompt = buildTranslationPrompt(cleanText, sourceLang, targetLang, style)
            val response = withTimeout(TIMEOUT_MS) {
                streamModel.generateContent(prompt)
            }
            val translated = response.text?.trim()
                ?: return Result.failure(IllegalStateException("لم يُرجع النموذج أي نص."))

            subtitleCacheDao.putCached(
                SubtitleCacheEntity(
                    cacheKey = cacheKey,
                    originalText = cleanText,
                    translatedText = translated,
                    targetLangCode = targetLang.code
                )
            )
            Result.success(translated)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Instant translation timed out", e)
            Result.failure(IllegalStateException("انتهت مهلة الترجمة الفورية."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Instant translation failed", e)
            Result.failure(e)
        }
    }

    suspend fun translateSubtitleBatch(
        cues: List<SubtitleCue>,
        sourceLang: AppLanguage,
        targetLang: AppLanguage,
        style: TranslationStyle
    ): Result<List<SubtitleCue>> {
        if (cues.isEmpty()) return Result.success(emptyList())
        return try {
            val resultCues = cues.toMutableList()
            val chunkSize = 25
            for (chunkStart in cues.indices step chunkSize) {
                val chunkEnd = minOf(chunkStart + chunkSize, cues.size)
                val slice = cues.subList(chunkStart, chunkEnd)

                val linesPayload = slice.joinToString("\n") { cue ->
                    "[${cue.index}] ${cue.originalText.replace("\n", " ")}"
                }
                val sourceHint = if (sourceLang.code == LanguageCatalog.autoDetect.code) {
                    "auto-detected source language"
                } else {
                    "${sourceLang.englishName} (${sourceLang.arabicName})"
                }
                val prompt = """
                    Translate these video subtitle lines from $sourceHint into ${targetLang.englishName} (${targetLang.arabicName}).
                    ${style.promptInstruction}
                    Keep subtitle lines concise, natural for reading on screen, and return the exact index for every item.
                    Lines:
                    $linesPayload
                """.trimIndent()

                val response = withTimeout(TIMEOUT_MS) {
                    batchJsonModel.generateContent(prompt)
                }
                val rawJson = response.text
                    ?: throw IllegalStateException("استجابة ترجمة الترجمة النصية فارغة.")
                val json = JSONObject(rawJson)
                val items = json.getJSONArray("items")
                val translatedByIndex = mutableMapOf<Int, String>()
                for (i in 0 until items.length()) {
                    val obj = items.getJSONObject(i)
                    translatedByIndex[obj.getInt("index")] = obj.getString("translated")
                }

                for (pos in chunkStart until chunkEnd) {
                    val cue = resultCues[pos]
                    val translated = translatedByIndex[cue.index]
                    if (!translated.isNullOrBlank()) {
                        resultCues[pos] = cue.copy(translatedText = translated)
                    }
                }
            }
            Result.success(resultCues)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Batch subtitle translation timed out", e)
            Result.failure(IllegalStateException("انتهت مهلة ترجمة ملف الترجمة."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Batch subtitle translation failed", e)
            Result.failure(e)
        }
    }

    suspend fun generateSubtitlesFromMediaBytes(
        mediaBytes: ByteArray,
        mimeType: String,
        sourceLang: AppLanguage,
        targetLang: AppLanguage,
        style: TranslationStyle
    ): Result<List<SubtitleCue>> {
        return try {
            val sourceHint = if (sourceLang.code == LanguageCatalog.autoDetect.code) {
                "automatically detect the spoken language"
            } else {
                "transcribe from ${sourceLang.englishName}"
            }
            val prompt = """
                Listen carefully to all spoken dialogue in this media clip.
                1) $sourceHint accurately into short subtitle segments.
                2) Translate each segment into ${targetLang.englishName} (${targetLang.arabicName}).
                ${style.promptInstruction}
                Return accurately timed cues with startMs, endMs, original, and translated fields.
            """.trimIndent()

            val response = withTimeout(TIMEOUT_MS) {
                mediaSubtitlesModel.generateContent(
                    content {
                        inlineData(mediaBytes, mimeType)
                        text(prompt)
                    }
                )
            }
            val rawJson = response.text
                ?: return Result.failure(IllegalStateException("تعذر استخراج ترجمة من الفيديو."))
            val json = JSONObject(rawJson)
            val cuesJson = json.getJSONArray("cues")
            val parsed = mutableListOf<SubtitleCue>()
            for (i in 0 until cuesJson.length()) {
                val obj = cuesJson.getJSONObject(i)
                val start = obj.optLong("startMs", i * 2500L)
                val end = obj.optLong("endMs", start + 2500L)
                val orig = obj.optString("original", "").trim()
                val trans = obj.optString("translated", "").trim()
                if (orig.isNotEmpty() || trans.isNotEmpty()) {
                    parsed.add(
                        SubtitleCue(
                            index = i + 1,
                            startMs = start,
                            endMs = maxOf(end, start + 600L),
                            originalText = orig.ifEmpty { trans },
                            translatedText = trans.ifEmpty { orig }
                        )
                    )
                }
            }
            Result.success(parsed.sortedBy { it.startMs })
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Media subtitle generation timed out", e)
            Result.failure(IllegalStateException("انتهت مهلة تحليل الصوت في الفيديو."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Media subtitle generation failed", e)
            Result.failure(e)
        }
    }

    private fun buildTranslationPrompt(
        text: String,
        sourceLang: AppLanguage,
        targetLang: AppLanguage,
        style: TranslationStyle
    ): String {
        val sourceClause = if (sourceLang.code == LanguageCatalog.autoDetect.code) {
            "Automatically detect the source language of the text"
        } else {
            "Translate from ${sourceLang.englishName} (${sourceLang.arabicName})"
        }
        return """
            You are an expert instant translator.
            $sourceClause into ${targetLang.englishName} (${targetLang.arabicName}).
            ${style.promptInstruction}
            Output ONLY the final translated text with zero explanations, zero quotes, and zero extra notes.
            Text:
            $text
        """.trimIndent()
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
