package com.example.ui.screens

import android.Manifest
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.core.LanguageCatalog
import com.example.core.LiveSpeechController
import com.example.core.TranslationStyle
import com.example.data.local.UserSettings
import com.example.data.media.SubtitleParser
import com.example.ui.components.FreeBadgePill
import com.example.ui.components.LanguagePickerBottomSheet
import com.example.ui.components.LiquidGlassCard
import com.example.ui.components.LiquidGlassPillButton
import com.example.ui.components.LiquidVideoPlayer
import com.example.ui.theme.CoralAlert
import com.example.ui.theme.EmeraldMint
import com.example.ui.theme.LiquidCyan
import com.example.ui.theme.LocalIsLiquidDark
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.VideoStudioUiState

@Composable
fun VideoStudioScreen(
    state: VideoStudioUiState,
    settings: UserSettings,
    viewModel: MainViewModel,
    contentPadding: PaddingValues
) {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val isDark = LocalIsLiquidDark.current

    var showSourcePicker by remember { mutableStateOf(false) }
    var showTargetPicker by remember { mutableStateOf(false) }
    var showSubtitleSettings by remember { mutableStateOf(false) }
    var micNotice by remember { mutableStateOf<String?>(null) }
    var currentVideoPosMs by remember { mutableLongStateOf(0L) }

    val speechController = remember {
        LiveSpeechController(
            context = context,
            onPartialOrFinalResult = { text, isFinal ->
                viewModel.onLiveSpeechRecognized(text, isFinal)
            },
            onErrorMessage = { msg ->
                micNotice = msg
                viewModel.setLiveMicActive(false)
            },
            onListeningChanged = { active ->
                viewModel.setLiveMicActive(active)
            }
        )
    }

    DisposableEffect(Unit) {
        onDispose { speechController.stop() }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            micNotice = null
            val bcp47 = if (state.sourceLang.code == LanguageCatalog.autoDetect.code) {
                "en-US"
            } else {
                state.sourceLang.bcp47
            }
            speechController.start(bcp47, continuous = true)
        } else {
            micNotice = "يلزم إذن الميكروفون للاستماع إلى صوت الفيديو وترجمته نصياً."
        }
    }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val name = resolveUriDisplayName(context, uri)
            viewModel.openLocalVideoUri(uri, name)
        }
    }

    val srtPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()?.let { text ->
                viewModel.loadExternalSubtitleContent(text)
            }
        }
    }

    // Fullscreen overlay mode
    if (state.isFullscreen && !state.activePlayUrl.isNullOrBlank()) {
        LiquidVideoPlayer(
            playUrl = state.activePlayUrl,
            useWebMode = state.useWebPlayerMode,
            cues = state.cues,
            liveEmbeddedOriginal = state.liveCueOriginal,
            liveEmbeddedTranslated = state.liveCueTranslated,
            isLiveMicActive = state.isLiveMicActive,
            liveMicOriginal = state.liveMicOriginal,
            liveMicTranslated = state.liveMicTranslated,
            showDualSubtitles = settings.showDualSubtitles,
            subtitleFontScale = settings.subtitleFontScale,
            isFullscreen = true,
            onToggleFullscreen = { viewModel.setFullscreen(false) },
            onEmbeddedCueDetected = viewModel::onEmbeddedPlayerCueText,
            onWebSniffedStream = viewModel::onWebViewSniffedStream,
            onPlaybackPositionUpdated = { currentVideoPosMs = it },
            modifier = Modifier.fillMaxSize()
        )
        return
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = contentPadding.calculateTopPadding() + 8.dp,
                bottom = contentPadding.calculateBottomPadding() + 96.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "ترجمان الفيديو",
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        FreeBadgePill()
                    }
                    IconButton(
                        onClick = viewModel::toggleTheme,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                            .testTag("theme_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (isDark) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                            contentDescription = "تبديل المظهر الفاتح/الداكن",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Link Input & Quick Tools Card
            item {
                LiquidGlassCard(accentGlow = LiquidCyan) {
                    Text(
                        text = "أحضر رابط فيديو من أي موقع أو تطبيق",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "الصق رابطاً أو شاركه مباشرة إلى ترجمان للمشاهدة بالترجمة النصية الفورية أو التنزيل السريع بجودة عالية.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = state.urlInput,
                        onValueChange = viewModel::updateVideoUrlInput,
                        placeholder = {
                            Text(
                                "https://... الصق رابط الفيديو هنا",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Link,
                                contentDescription = "رابط الفيديو",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (state.urlInput.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.updateVideoUrlInput("") }) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = "مسح الرابط"
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        val pasted = readClipboardText(context)
                                        if (!pasted.isNullOrBlank()) {
                                            viewModel.updateVideoUrlInput(pasted)
                                            viewModel.analyzeVideoLink(pasted)
                                        }
                                    },
                                    modifier = Modifier.testTag("paste_url_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ContentPaste,
                                        contentDescription = "لصق من الحافظة",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(
                            onGo = {
                                keyboard?.hide()
                                viewModel.analyzeVideoLink()
                            }
                        ),
                        shape = RoundedCornerShape(18.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("video_url_input")
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Language Direction Pill
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LanguageChip(
                            label = "${state.sourceLang.flag} ${state.sourceLang.arabicName}",
                            onClick = { showSourcePicker = true },
                            testTag = "video_source_lang_button",
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = viewModel::swapVideoLanguages,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f))
                                .testTag("video_swap_lang_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SwapHoriz,
                                contentDescription = "تبديل اللغتين",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        LanguageChip(
                            label = "${state.targetLang.flag} ${state.targetLang.arabicName}",
                            onClick = { showTargetPicker = true },
                            testTag = "video_target_lang_button",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        LiquidGlassPillButton(
                            text = if (state.isResolving) "جاري التحليل..." else "تحليل وتشغيل",
                            icon = Icons.Rounded.PlayArrow,
                            onClick = {
                                keyboard?.hide()
                                viewModel.analyzeVideoLink()
                            },
                            enabled = !state.isResolving,
                            isPrimary = true,
                            testTag = "analyze_video_button",
                            modifier = Modifier.weight(1f)
                        )
                        LiquidGlassPillButton(
                            text = "من الجهاز",
                            icon = Icons.Rounded.VideoLibrary,
                            onClick = {
                                videoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                )
                            },
                            isPrimary = false,
                            testTag = "pick_local_video_button"
                        )
                    }

                    if (state.isResolving) {
                        Spacer(modifier = Modifier.height(10.dp))
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(CircleShape),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (state.resolveError != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = state.resolveError,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // Active Video Player & Live Subtitle Controls
            val activeUrl = state.activePlayUrl
            val resolved = state.resolvedVideo
            if (!activeUrl.isNullOrBlank() && resolved != null) {
                item {
                    LiquidGlassCard(contentPadding = 12.dp) {
                        // Video Title + Thumbnail Info Bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (resolved.thumbnailUrl.isNotBlank()) {
                                AsyncImage(
                                    model = resolved.thumbnailUrl,
                                    contentDescription = resolved.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = resolved.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (state.useWebPlayerMode) {
                                        "وضع متصفح الفيديو الذكي"
                                    } else {
                                        "مشغل فائق السرعة • ${state.selectedStream?.qualityLabel ?: "HD"}"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(
                                onClick = { showSubtitleSettings = !showSubtitleSettings },
                                modifier = Modifier.testTag("subtitle_settings_toggle")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Subtitles,
                                    contentDescription = "إعدادات الترجمة",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        LiquidVideoPlayer(
                            playUrl = activeUrl,
                            useWebMode = state.useWebPlayerMode,
                            cues = state.cues,
                            liveEmbeddedOriginal = state.liveCueOriginal,
                            liveEmbeddedTranslated = state.liveCueTranslated,
                            isLiveMicActive = state.isLiveMicActive,
                            liveMicOriginal = state.liveMicOriginal,
                            liveMicTranslated = state.liveMicTranslated,
                            showDualSubtitles = settings.showDualSubtitles,
                            subtitleFontScale = settings.subtitleFontScale,
                            isFullscreen = false,
                            onToggleFullscreen = { viewModel.setFullscreen(true) },
                            onEmbeddedCueDetected = viewModel::onEmbeddedPlayerCueText,
                            onWebSniffedStream = viewModel::onWebViewSniffedStream,
                            onPlaybackPositionUpdated = { currentVideoPosMs = it }
                        )

                        // Subtitle Appearance Settings Drawer
                        AnimatedVisibility(visible = showSubtitleSettings) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    .padding(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "إظهار النص الأصلي مع الترجمة",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Switch(
                                        checked = settings.showDualSubtitles,
                                        onCheckedChange = { viewModel.toggleDualSubtitles() }
                                    )
                                }
                                Text(
                                    text = "حجم خط الترجمة",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Slider(
                                    value = settings.subtitleFontScale,
                                    onValueChange = viewModel::updateSubtitleFontScale,
                                    valueRange = 0.8f..1.5f
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Quality Selection Pills
                        if (resolved.streams.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                resolved.streams.forEachIndexed { idx, stream ->
                                    val selected = state.selectedStream?.url == stream.url && !state.useWebPlayerMode
                                    QualityChip(
                                        label = stream.qualityLabel,
                                        selected = selected,
                                        onClick = { viewModel.selectStreamOption(stream) },
                                        testTag = "stream_quality_$idx"
                                    )
                                }
                                if (resolved.originalUrl.startsWith("http")) {
                                    QualityChip(
                                        label = "متصفح الويب",
                                        selected = state.useWebPlayerMode,
                                        onClick = { viewModel.toggleWebPlayerMode(true) },
                                        icon = Icons.Rounded.Public,
                                        testTag = "web_mode_chip"
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        // Action Bar: Live Mic Subtitles + AI Subtitles + SRT File + Fast HD Download
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            LiquidGlassPillButton(
                                text = if (state.isLiveMicActive) "إيقاف التقاط الصوت" else "ترجمة الصوت حياً",
                                icon = if (state.isLiveMicActive) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                                onClick = {
                                    if (state.isLiveMicActive) {
                                        speechController.stop()
                                    } else {
                                        val granted = ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.RECORD_AUDIO
                                        ) == PackageManager.PERMISSION_GRANTED
                                        if (granted) {
                                            micNotice = null
                                            val bcp47 = if (state.sourceLang.code == LanguageCatalog.autoDetect.code) {
                                                "en-US"
                                            } else {
                                                state.sourceLang.bcp47
                                            }
                                            speechController.start(bcp47, continuous = true)
                                        } else {
                                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        }
                                    }
                                },
                                isPrimary = state.isLiveMicActive,
                                testTag = "live_mic_subtitle_button",
                                modifier = Modifier.weight(1f)
                            )

                            LiquidGlassPillButton(
                                text = "تنزيل سريع",
                                icon = Icons.Rounded.Download,
                                onClick = viewModel::downloadSelectedVideo,
                                isPrimary = true,
                                testTag = "download_video_button",
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            LiquidGlassPillButton(
                                text = if (state.isGeneratingSubtitles) "جاري الترجمة..." else "توليد ترجمة بالذكاء",
                                icon = Icons.Rounded.AutoAwesome,
                                onClick = viewModel::generateAiSubtitlesForActiveVideo,
                                enabled = !state.isGeneratingSubtitles,
                                isPrimary = false,
                                testTag = "generate_ai_subtitles_button",
                                modifier = Modifier.weight(1f)
                            )
                            LiquidGlassPillButton(
                                text = "ملف SRT/VTT",
                                icon = Icons.Rounded.Subtitles,
                                onClick = { srtPickerLauncher.launch(arrayOf("*/*")) },
                                isPrimary = false,
                                testTag = "import_srt_button"
                            )
                        }

                        if (state.isGeneratingSubtitles) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                                Text(
                                    text = state.subtitleStatusMessage ?: "جاري معالجة الترجمة...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        } else if (!state.subtitleStatusMessage.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = state.subtitleStatusMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (!micNotice.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = micNotice.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = CoralAlert
                            )
                        }

                        if (state.isLiveMicActive) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(EmeraldMint.copy(alpha = 0.14f))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.GraphicEq,
                                    contentDescription = null,
                                    tint = EmeraldMint,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "وضع الاستماع الحي نشط — تظهر الترجمة النصية فوق الفيديو فوراً دون دبلجة",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = EmeraldMint
                                )
                            }
                        }
                    }
                }
            } else {
                // First-launch / Empty State Guide Card
                item {
                    LiquidGlassCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Subtitles,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "كيف يعمل ترجمان؟",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "ترجمة نصية متزامنة بدون دبلجة صوتية",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        FeatureStepRow("1", "شارك أو الصق أي رابط فيديو من المتصفح أو أي تطبيق آخر.")
                        Spacer(modifier = Modifier.height(8.dp))
                        FeatureStepRow("2", "شاهد الفيديو مع ترجمة نصية فورية من أي لغة إلى العربية وبالعكس.")
                        Spacer(modifier = Modifier.height(8.dp))
                        FeatureStepRow("3", "نزّل الفيديو بجودة عالية وبأقصى سرعة للمشاهدة لاحقاً بدون إنترنت.")
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "أسلوب الترجمة:",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TranslationStyle.entries.forEach { style ->
                                QualityChip(
                                    label = style.arabicLabel,
                                    selected = state.translationStyle == style,
                                    onClick = { viewModel.setVideoTranslationStyle(style) },
                                    testTag = "video_style_${style.id}"
                                )
                            }
                        }
                    }
                }
            }

            // Subtitle Timeline Preview if cues are loaded
            if (state.cues.isNotEmpty()) {
                item {
                    Text(
                        text = "جدول الترجمة المتزامن (${state.cues.size} سطر)",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
                items(state.cues.take(40), key = { it.index }) { cue ->
                    val isActive = currentVideoPosMs in cue.startMs..cue.endMs
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = if (isActive) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        },
                        border = BorderStroke(
                            1.dp,
                            if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "${SubtitleParser.formatDisplayDuration(cue.startMs)} → ${SubtitleParser.formatDisplayDuration(cue.endMs)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = cue.translatedText.ifBlank { cue.originalText },
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (cue.translatedText.isNotBlank() && cue.originalText != cue.translatedText) {
                                Text(
                                    text = cue.originalText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSourcePicker) {
        LanguagePickerBottomSheet(
            title = "اختر لغة الفيديو الأصلية",
            options = LanguageCatalog.sourceOptions,
            selectedCode = state.sourceLang.code,
            onSelect = { viewModel.setVideoLanguages(it, state.targetLang) },
            onDismiss = { showSourcePicker = false }
        )
    }

    if (showTargetPicker) {
        LanguagePickerBottomSheet(
            title = "اختر لغة الترجمة المطلوبة",
            options = LanguageCatalog.supportedLanguages,
            selectedCode = state.targetLang.code,
            onSelect = { viewModel.setVideoLanguages(state.sourceLang, it) },
            onDismiss = { showTargetPicker = false }
        )
    }
}

@Composable
private fun LanguageChip(
    label: String,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            .clickable(onClick = onClick)
            .testTag(testTag)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.Language,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun QualityChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
            .border(
                1.dp,
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                CircleShape
            )
            .clickable(onClick = onClick)
            .testTag(testTag)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            ),
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun FeatureStepRow(number: String, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun readClipboardText(context: Context): String? {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
    if (!clipboard.hasPrimaryClip()) return null
    val desc = clipboard.primaryClipDescription ?: return null
    if (!desc.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) &&
        !desc.hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML)
    ) {
        return clipboard.primaryClip?.getItemAt(0)?.text?.toString()
    }
    return clipboard.primaryClip?.getItemAt(0)?.text?.toString()
}

private fun resolveUriDisplayName(context: Context, uri: android.net.Uri): String {
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (idx != -1 && cursor.moveToFirst()) {
            return cursor.getString(idx) ?: "فيديو محلي"
        }
    }
    return uri.lastPathSegment ?: "فيديو محلي"
}
