package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.AppLanguage
import com.example.core.LanguageCatalog
import com.example.core.TranslationStyle
import com.example.data.ai.GeminiTranslationRepository
import com.example.data.local.AppDatabase
import com.example.data.local.DownloadedVideoEntity
import com.example.data.local.TranslationHistoryEntity
import com.example.data.local.UserPreferencesRepository
import com.example.data.local.UserSettings
import com.example.data.media.FastVideoDownloader
import com.example.data.media.ResolvedVideoInfo
import com.example.data.media.SubtitleCue
import com.example.data.media.SubtitleParser
import com.example.data.media.VideoLinkResolver
import com.example.data.media.VideoStreamOption
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

enum class AppTab(val route: String) {
    VIDEO_STUDIO("video_studio"),
    INSTANT_TRANSLATE("instant_translate"),
    DOWNLOADS("downloads")
}

data class VideoStudioUiState(
    val urlInput: String = "",
    val isResolving: Boolean = false,
    val resolveError: String? = null,
    val resolvedVideo: ResolvedVideoInfo? = null,
    val selectedStream: VideoStreamOption? = null,
    val activePlayUrl: String? = null,
    val useWebPlayerMode: Boolean = false,
    val isFullscreen: Boolean = false,
    val sourceLang: AppLanguage = LanguageCatalog.autoDetect,
    val targetLang: AppLanguage = LanguageCatalog.arabic,
    val translationStyle: TranslationStyle = TranslationStyle.NATURAL,
    val cues: List<SubtitleCue> = emptyList(),
    val liveCueOriginal: String = "",
    val liveCueTranslated: String = "",
    val isLiveMicActive: Boolean = false,
    val liveMicOriginal: String = "",
    val liveMicTranslated: String = "",
    val isGeneratingSubtitles: Boolean = false,
    val subtitleStatusMessage: String? = null,
    val playingDownloadId: Long? = null,
    val snackMessage: String? = null
)

data class InstantTranslateUiState(
    val inputText: String = "",
    val outputText: String = "",
    val isTranslating: Boolean = false,
    val errorMessage: String? = null,
    val sourceLang: AppLanguage = LanguageCatalog.autoDetect,
    val targetLang: AppLanguage = LanguageCatalog.arabic,
    val style: TranslationStyle = TranslationStyle.NATURAL
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val TAG = "MainViewModel"
        private const val MAX_INLINE_MEDIA_BYTES = 18 * 1024 * 1024 // 18 MB safe limit under 20 MB
    }

    private val db = AppDatabase.getInstance(application)
    private val prefsRepo = UserPreferencesRepository(application)
    private val linkResolver = VideoLinkResolver()
    private val downloader = FastVideoDownloader(application, db.downloadedVideoDao())
    private val aiRepo = GeminiTranslationRepository(db.subtitleCacheDao())

    private val _selectedTab = MutableStateFlow(AppTab.VIDEO_STUDIO)
    val selectedTab: StateFlow<AppTab> = _selectedTab.asStateFlow()

    val settings: StateFlow<UserSettings> = prefsRepo.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserSettings())

    val downloads: StateFlow<List<DownloadedVideoEntity>> = db.downloadedVideoDao()
        .observeAllDownloads()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val downloadSpeeds: StateFlow<Map<Long, Long>> = downloader.speedMap

    val translationHistory: StateFlow<List<TranslationHistoryEntity>> = db.translationHistoryDao()
        .observeHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _videoState = MutableStateFlow(VideoStudioUiState())
    val videoState: StateFlow<VideoStudioUiState> = _videoState.asStateFlow()

    private val _translateState = MutableStateFlow(InstantTranslateUiState())
    val translateState: StateFlow<InstantTranslateUiState> = _translateState.asStateFlow()

    private var translateJob: Job? = null
    private var liveCueJob: Job? = null
    private var liveMicJob: Job? = null

    fun selectTab(tab: AppTab) {
        _selectedTab.value = tab
    }

    fun toggleTheme() {
        viewModelScope.launch {
            prefsRepo.setDarkGlass(!settings.value.isDarkGlass)
        }
    }

    fun toggleDualSubtitles() {
        viewModelScope.launch {
            prefsRepo.setShowDualSubtitles(!settings.value.showDualSubtitles)
        }
    }

    fun updateSubtitleFontScale(scale: Float) {
        viewModelScope.launch {
            prefsRepo.setSubtitleFontScale(scale)
        }
    }

    fun clearSnackMessage() {
        _videoState.update { it.copy(snackMessage = null) }
    }

    // ---- Incoming Shared / Pasted Links ----

    fun handleIncomingSharedText(sharedText: String) {
        val extracted = VideoLinkResolver.extractFirstUrl(sharedText) ?: sharedText.trim()
        if (extracted.isBlank()) return
        _selectedTab.value = AppTab.VIDEO_STUDIO
        _videoState.update { it.copy(urlInput = extracted, resolveError = null) }
        analyzeVideoLink(extracted)
    }

    fun updateVideoUrlInput(url: String) {
        _videoState.update { it.copy(urlInput = url, resolveError = null) }
    }

    fun analyzeVideoLink(overrideUrl: String? = null) {
        val raw = (overrideUrl ?: _videoState.value.urlInput).trim()
        if (raw.isEmpty()) {
            _videoState.update { it.copy(resolveError = "يرجى لصق رابط الفيديو أولاً.") }
            return
        }

        viewModelScope.launch {
            _videoState.update {
                it.copy(
                    isResolving = true,
                    resolveError = null,
                    cues = emptyList(),
                    liveCueOriginal = "",
                    liveCueTranslated = "",
                    playingDownloadId = null
                )
            }
            val result = linkResolver.resolve(raw)
            result.onSuccess { info ->
                val bestStream = info.streams.firstOrNull()
                _videoState.update {
                    it.copy(
                        isResolving = false,
                        resolvedVideo = info,
                        selectedStream = bestStream,
                        activePlayUrl = bestStream?.url ?: info.originalUrl,
                        useWebPlayerMode = info.requiresWebViewFallback || bestStream == null,
                        subtitleStatusMessage = when {
                            info.subtitleTracks.isNotEmpty() -> "تم العثور على ترجمة مرفقة — جاري تجهيز الترجمة الفورية..."
                            info.requiresWebViewFallback -> "تم فتح مشغل الويب الذكي — فعّل الميكروفون أو الترجمة النصية الحية"
                            else -> "جاهز للمشاهدة — فعّل الترجمة الفورية بالذكاء الاصطناعي أو حمّل ملف ترجمة"
                        }
                    )
                }

                // Auto-load and translate first subtitle track if webpage provided one!
                val firstTrack = info.subtitleTracks.firstOrNull()
                if (firstTrack != null) {
                    fetchAndTranslateTrack(firstTrack.url)
                }
            }.onFailure { err ->
                _videoState.update {
                    it.copy(
                        isResolving = false,
                        resolveError = err.localizedMessage ?: "تعذر تحليل الرابط. تأكد من الاتصال بالإنترنت."
                    )
                }
            }
        }
    }

    fun openLocalVideoUri(uri: Uri, displayName: String) {
        val uriString = uri.toString()
        val info = ResolvedVideoInfo(
            originalUrl = uriString,
            title = displayName.ifBlank { "فيديو من الجهاز" },
            streams = listOf(
                VideoStreamOption(
                    url = uriString,
                    qualityLabel = "جودة أصلية (محلي)",
                    isDownloadableDirect = false
                )
            )
        )
        _videoState.update {
            it.copy(
                urlInput = displayName,
                resolvedVideo = info,
                selectedStream = info.streams.first(),
                activePlayUrl = uriString,
                useWebPlayerMode = false,
                cues = emptyList(),
                liveCueOriginal = "",
                liveCueTranslated = "",
                playingDownloadId = null,
                resolveError = null,
                subtitleStatusMessage = "فيديو محلي جاهز — اضغط توليد ترجمة ذكية لاستخراج الترجمة الفورية تلقائياً!"
            )
        }
    }

    fun selectStreamOption(stream: VideoStreamOption) {
        _videoState.update {
            it.copy(
                selectedStream = stream,
                activePlayUrl = stream.url,
                useWebPlayerMode = false
            )
        }
    }

    fun toggleWebPlayerMode(useWeb: Boolean) {
        val info = _videoState.value.resolvedVideo ?: return
        _videoState.update {
            it.copy(
                useWebPlayerMode = useWeb,
                activePlayUrl = if (useWeb) info.originalUrl else (it.selectedStream?.url ?: info.originalUrl)
            )
        }
    }

    fun onWebViewSniffedStream(streamUrl: String) {
        val current = _videoState.value.resolvedVideo ?: return
        if (current.streams.any { it.url == streamUrl }) return
        val isHls = streamUrl.contains(".m3u8", ignoreCase = true)
        val newOption = VideoStreamOption(
            url = streamUrl,
            qualityLabel = if (isHls) "HLS مكتشف تلقائياً" else "MP4 مكتشف من الصفحة",
            mimeType = if (isHls) "application/x-mpegURL" else "video/mp4",
            isHls = isHls,
            isDownloadableDirect = !isHls
        )
        _videoState.update {
            it.copy(
                resolvedVideo = current.copy(streams = listOf(newOption) + current.streams),
                selectedStream = it.selectedStream ?: newOption,
                snackMessage = "تم التقاط رابط فيديو عالي الجودة من الصفحة! يمكنك تنزيله أو تشغيله الآن."
            )
        }
    }

    fun setFullscreen(fullscreen: Boolean) {
        _videoState.update { it.copy(isFullscreen = fullscreen) }
    }

    fun setVideoLanguages(source: AppLanguage, target: AppLanguage) {
        _videoState.update { it.copy(sourceLang = source, targetLang = target) }
        val existingCues = _videoState.value.cues
        if (existingCues.isNotEmpty()) {
            translateExistingCues(existingCues)
        }
    }

    fun swapVideoLanguages() {
        val current = _videoState.value
        val newSource = current.targetLang
        val newTarget = if (current.sourceLang.code == LanguageCatalog.autoDetect.code) {
            if (newSource.code == LanguageCatalog.arabic.code) LanguageCatalog.english else LanguageCatalog.arabic
        } else {
            current.sourceLang
        }
        setVideoLanguages(newSource, newTarget)
    }

    fun setVideoTranslationStyle(style: TranslationStyle) {
        _videoState.update { it.copy(translationStyle = style) }
    }

    // ---- Subtitle Tracks & Live Translation (NO Dubbing) ----

    private fun fetchAndTranslateTrack(trackUrl: String) {
        viewModelScope.launch {
            _videoState.update { it.copy(isGeneratingSubtitles = true) }
            linkResolver.fetchSubtitleText(trackUrl).onSuccess { raw ->
                val parsed = SubtitleParser.parse(raw)
                if (parsed.isNotEmpty()) {
                    translateExistingCues(parsed)
                } else {
                    _videoState.update {
                        it.copy(isGeneratingSubtitles = false, subtitleStatusMessage = "ملف الترجمة المرفق فارغ.")
                    }
                }
            }.onFailure {
                _videoState.update {
                    it.copy(isGeneratingSubtitles = false, subtitleStatusMessage = "تعذر تحميل الترجمة المرفقة تلقائياً.")
                }
            }
        }
    }

    fun loadExternalSubtitleContent(rawContent: String) {
        val parsed = SubtitleParser.parse(rawContent)
        if (parsed.isEmpty()) {
            _videoState.update { it.copy(snackMessage = "تعذر قراءة ملف الترجمة (.srt أو .vtt).") }
            return
        }
        translateExistingCues(parsed)
    }

    private fun translateExistingCues(parsed: List<SubtitleCue>) {
        val state = _videoState.value
        viewModelScope.launch {
            _videoState.update {
                it.copy(
                    cues = parsed,
                    isGeneratingSubtitles = true,
                    subtitleStatusMessage = "جاري ترجمة ${parsed.size} سطر ترجمة عبر الذكاء الاصطناعي..."
                )
            }
            val result = aiRepo.translateSubtitleBatch(
                cues = parsed,
                sourceLang = state.sourceLang,
                targetLang = state.targetLang,
                style = state.translationStyle
            )
            result.onSuccess { translated ->
                _videoState.update {
                    it.copy(
                        cues = translated,
                        isGeneratingSubtitles = false,
                        subtitleStatusMessage = "الترجمة الفورية جاهزة (${translated.size} سطر • بدون دبلجة)"
                    )
                }
                persistSubtitlesIfDownloaded(translated)
            }.onFailure { err ->
                _videoState.update {
                    it.copy(
                        isGeneratingSubtitles = false,
                        subtitleStatusMessage = err.localizedMessage ?: "حدث خطأ أثناء ترجمة النصوص."
                    )
                }
            }
        }
    }

    fun onEmbeddedPlayerCueText(rawCue: String) {
        val clean = rawCue.trim()
        if (clean.isEmpty()) {
            _videoState.update { it.copy(liveCueOriginal = "", liveCueTranslated = "") }
            return
        }
        if (clean == _videoState.value.liveCueOriginal) return

        _videoState.update { it.copy(liveCueOriginal = clean) }
        liveCueJob?.cancel()
        liveCueJob = viewModelScope.launch {
            val state = _videoState.value
            aiRepo.translateInstantCached(
                text = clean,
                sourceLang = state.sourceLang,
                targetLang = state.targetLang,
                style = state.translationStyle
            ).onSuccess { translated ->
                _videoState.update { it.copy(liveCueTranslated = translated) }
            }
        }
    }

    fun setLiveMicActive(active: Boolean) {
        _videoState.update {
            it.copy(
                isLiveMicActive = active,
                liveMicOriginal = if (active) it.liveMicOriginal else "",
                liveMicTranslated = if (active) it.liveMicTranslated else ""
            )
        }
    }

    fun onLiveSpeechRecognized(spokenText: String, isFinal: Boolean) {
        val clean = spokenText.trim()
        if (clean.isEmpty()) return
        _videoState.update { it.copy(liveMicOriginal = clean) }

        if (!isFinal && clean.length < 18) return

        liveMicJob?.cancel()
        liveMicJob = viewModelScope.launch {
            val state = _videoState.value
            aiRepo.translateInstantCached(
                text = clean,
                sourceLang = state.sourceLang,
                targetLang = state.targetLang,
                style = state.translationStyle
            ).onSuccess { translated ->
                _videoState.update { it.copy(liveMicTranslated = translated) }
            }
        }
    }

    fun generateAiSubtitlesForActiveVideo() {
        val state = _videoState.value
        val playUrl = state.activePlayUrl ?: return

        viewModelScope.launch {
            _videoState.update {
                it.copy(
                    isGeneratingSubtitles = true,
                    subtitleStatusMessage = "جاري تحليل الصوت في الفيديو وتوليد ترجمة نصية متزامنة..."
                )
            }
            try {
                val mediaBytesAndMime = withContext(Dispatchers.IO) {
                    readMediaBytesForAi(playUrl)
                }
                if (mediaBytesAndMime == null) {
                    _videoState.update {
                        it.copy(
                            isGeneratingSubtitles = false,
                            subtitleStatusMessage = "الفيديو كبير جداً أو بث مباشر — استخدم وضع (الميكروفون الحي) للترجمة الفورية أثناء المشاهدة!"
                        )
                    }
                    return@launch
                }

                val (bytes, mimeType) = mediaBytesAndMime
                val result = aiRepo.generateSubtitlesFromMediaBytes(
                    mediaBytes = bytes,
                    mimeType = mimeType,
                    sourceLang = state.sourceLang,
                    targetLang = state.targetLang,
                    style = state.translationStyle
                )
                result.onSuccess { generated ->
                    _videoState.update {
                        it.copy(
                            cues = generated,
                            isGeneratingSubtitles = false,
                            subtitleStatusMessage = if (generated.isNotEmpty()) {
                                "تم توليد ${generated.size} سطر ترجمة فورية متزامنة!"
                            } else {
                                "لم يُرصد حوار صوتي واضح في هذا المقطع."
                            }
                        )
                    }
                    if (generated.isNotEmpty()) {
                        persistSubtitlesIfDownloaded(generated)
                    }
                }.onFailure { err ->
                    _videoState.update {
                        it.copy(
                            isGeneratingSubtitles = false,
                            subtitleStatusMessage = err.localizedMessage ?: "تعذر توليد الترجمة الذكية."
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "AI subtitle generation error", e)
                _videoState.update {
                    it.copy(
                        isGeneratingSubtitles = false,
                        subtitleStatusMessage = e.localizedMessage ?: "حدث خطأ غير متوقع."
                    )
                }
            }
        }
    }

    private fun readMediaBytesForAi(urlOrUri: String): Pair<ByteArray, String>? {
        return try {
            when {
                urlOrUri.startsWith("content://") -> {
                    val uri = Uri.parse(urlOrUri)
                    val resolver = getApplication<Application>().contentResolver
                    val mime = resolver.getType(uri) ?: "video/mp4"
                    resolver.openInputStream(uri)?.use { stream ->
                        val bytes = stream.readBytesLimited(MAX_INLINE_MEDIA_BYTES) ?: return null
                        bytes to mime
                    }
                }
                urlOrUri.startsWith("file://") || urlOrUri.startsWith("/") -> {
                    val path = if (urlOrUri.startsWith("file://")) Uri.parse(urlOrUri).path!! else urlOrUri
                    val file = File(path)
                    if (!file.exists() || file.length() > MAX_INLINE_MEDIA_BYTES) return null
                    file.readBytes() to "video/mp4"
                }
                urlOrUri.startsWith("http") && !urlOrUri.contains(".m3u8", true) -> {
                    val client = OkHttpClient.Builder()
                        .connectTimeout(15, TimeUnit.SECONDS)
                        .readTimeout(25, TimeUnit.SECONDS)
                        .build()
                    val req = Request.Builder().url(urlOrUri).build()
                    client.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) return null
                        val contentLen = resp.body?.contentLength() ?: -1L
                        if (contentLen > MAX_INLINE_MEDIA_BYTES) return null
                        val mime = resp.header("Content-Type")?.substringBefore(';') ?: "video/mp4"
                        val bytes = resp.body?.byteStream()?.readBytesLimited(MAX_INLINE_MEDIA_BYTES) ?: return null
                        bytes to mime
                    }
                }
                else -> null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unable to read media bytes", e)
            null
        }
    }

    private fun java.io.InputStream.readBytesLimited(maxBytes: Int): ByteArray? {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val read = read(buf)
            if (read == -1) break
            total += read
            if (total > maxBytes) return null
            out.write(buf, 0, read)
        }
        return out.toByteArray()
    }

    private fun persistSubtitlesIfDownloaded(cues: List<SubtitleCue>) {
        val dlId = _videoState.value.playingDownloadId ?: return
        val srt = SubtitleParser.exportToSrt(cues, useTranslated = true)
        viewModelScope.launch {
            db.downloadedVideoDao().updateSubtitles(dlId, srt, _videoState.value.targetLang.code)
        }
    }

    // ---- High-Speed Video Downloads ----

    fun downloadSelectedVideo() {
        val state = _videoState.value
        val info = state.resolvedVideo ?: return
        val stream = state.selectedStream
        if (stream == null || !stream.isDownloadableDirect) {
            _videoState.update {
                it.copy(
                    snackMessage = "هذا الرابط عبارة عن بث متدفق (HLS/Web). شغّله الآن في المشغل مع الترجمة الفورية، أو اختر رابط MP4 مباشر للتنزيل."
                )
            }
            return
        }

        val srtContent = if (state.cues.isNotEmpty()) {
            SubtitleParser.exportToSrt(state.cues, useTranslated = true)
        } else {
            ""
        }

        downloader.startDownload(
            title = info.title,
            sourceUrl = info.originalUrl,
            streamUrl = stream.url,
            thumbnailUrl = info.thumbnailUrl,
            qualityLabel = stream.qualityLabel,
            mimeType = stream.mimeType,
            subtitleSrtContent = srtContent,
            sourceLangCode = state.sourceLang.code,
            targetLangCode = state.targetLang.code
        )
        _videoState.update {
            it.copy(snackMessage = "بدأ التنزيل السريع (${stream.qualityLabel})! تابع التقدم من تبويب التنزيلات.")
        }
    }

    fun retryDownload(entity: DownloadedVideoEntity) {
        downloader.retryDownload(entity)
    }

    fun cancelDownload(id: Long) {
        downloader.cancelDownload(id)
    }

    fun deleteDownload(entity: DownloadedVideoEntity) {
        downloader.deleteDownload(entity)
    }

    fun watchDownloadedVideo(entity: DownloadedVideoEntity) {
        val localFile = File(entity.localFilePath)
        val playUri = if (localFile.exists() && localFile.length() > 0L) {
            Uri.fromFile(localFile).toString()
        } else {
            entity.streamUrl
        }
        val savedCues = if (entity.subtitleSrtContent.isNotBlank()) {
            SubtitleParser.parse(entity.subtitleSrtContent).map {
                it.copy(translatedText = it.originalText)
            }
        } else {
            emptyList()
        }
        val info = ResolvedVideoInfo(
            originalUrl = entity.sourceUrl,
            title = entity.title,
            thumbnailUrl = entity.thumbnailUrl,
            streams = listOf(
                VideoStreamOption(
                    url = playUri,
                    qualityLabel = entity.qualityLabel,
                    mimeType = entity.mimeType,
                    isDownloadableDirect = false
                )
            )
        )
        _videoState.update {
            it.copy(
                urlInput = entity.title,
                resolvedVideo = info,
                selectedStream = info.streams.first(),
                activePlayUrl = playUri,
                useWebPlayerMode = false,
                cues = savedCues,
                playingDownloadId = entity.id,
                subtitleStatusMessage = if (savedCues.isNotEmpty()) {
                    "تم تحميل الترجمة المحفوظة (${savedCues.size} سطر)"
                } else {
                    "فيديو محفوظ بدون إنترنت — اضغط توليد ترجمة ذكية أو فعّل الميكروفون"
                }
            )
        }
        _selectedTab.value = AppTab.VIDEO_STUDIO
    }

    // ---- Instant Bidirectional Text Translator ----

    fun updateTranslateInput(text: String) {
        _translateState.update { it.copy(inputText = text, errorMessage = null) }
    }

    fun setTranslateLanguages(source: AppLanguage, target: AppLanguage) {
        _translateState.update { it.copy(sourceLang = source, targetLang = target) }
    }

    fun swapTranslateLanguages() {
        val current = _translateState.value
        val newSource = current.targetLang
        val newTarget = if (current.sourceLang.code == LanguageCatalog.autoDetect.code) {
            if (newSource.code == LanguageCatalog.arabic.code) LanguageCatalog.english else LanguageCatalog.arabic
        } else {
            current.sourceLang
        }
        val swappedInput = current.outputText.ifBlank { current.inputText }
        _translateState.update {
            it.copy(
                sourceLang = newSource,
                targetLang = newTarget,
                inputText = swappedInput,
                outputText = ""
            )
        }
        if (swappedInput.isNotBlank()) {
            translateTextInstant()
        }
    }

    fun setTranslateStyle(style: TranslationStyle) {
        _translateState.update { it.copy(style = style) }
    }

    fun translateTextInstant() {
        val current = _translateState.value
        val text = current.inputText.trim()
        if (text.isEmpty()) {
            _translateState.update { it.copy(errorMessage = "أدخل نصاً للترجمة الفورية.") }
            return
        }

        translateJob?.cancel()
        translateJob = viewModelScope.launch {
            _translateState.update {
                it.copy(isTranslating = true, errorMessage = null, outputText = "")
            }
            try {
                var finalOutput = ""
                aiRepo.translateStream(
                    text = text,
                    sourceLang = current.sourceLang,
                    targetLang = current.targetLang,
                    style = current.style
                ).collect { streamed ->
                    finalOutput = streamed
                    _translateState.update { it.copy(outputText = streamed) }
                }

                _translateState.update { it.copy(isTranslating = false) }

                if (finalOutput.isNotBlank()) {
                    db.translationHistoryDao().insert(
                        TranslationHistoryEntity(
                            sourceText = text,
                            translatedText = finalOutput,
                            sourceLangCode = current.sourceLang.code,
                            targetLangCode = current.targetLang.code,
                            styleId = current.style.id
                        )
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Instant text translation failed", e)
                _translateState.update {
                    it.copy(
                        isTranslating = false,
                        errorMessage = e.localizedMessage ?: "فشلت الترجمة. تحقق من الاتصال بالإنترنت."
                    )
                }
            }
        }
    }

    fun loadHistoryItemIntoTranslator(item: TranslationHistoryEntity) {
        _translateState.update {
            it.copy(
                inputText = item.sourceText,
                outputText = item.translatedText,
                sourceLang = LanguageCatalog.findByCode(item.sourceLangCode),
                targetLang = LanguageCatalog.findByCode(item.targetLangCode)
            )
        }
    }

    fun toggleHistoryFavorite(item: TranslationHistoryEntity) {
        viewModelScope.launch {
            db.translationHistoryDao().setFavorite(item.id, !item.isFavorite)
        }
    }

    fun deleteHistoryItem(id: Long) {
        viewModelScope.launch {
            db.translationHistoryDao().deleteById(id)
        }
    }
}
