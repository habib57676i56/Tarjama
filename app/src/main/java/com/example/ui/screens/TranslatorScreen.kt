package com.example.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.core.LanguageCatalog
import com.example.core.LiveSpeechController
import com.example.core.TranslationStyle
import com.example.data.local.TranslationHistoryEntity
import com.example.ui.components.FreeBadgePill
import com.example.ui.components.LanguagePickerBottomSheet
import com.example.ui.components.LiquidGlassCard
import com.example.ui.components.LiquidGlassPillButton
import com.example.ui.theme.CoralAlert
import com.example.ui.theme.LiquidCyan
import com.example.ui.viewmodel.InstantTranslateUiState
import com.example.ui.viewmodel.MainViewModel

@Composable
fun TranslatorScreen(
    state: InstantTranslateUiState,
    history: List<TranslationHistoryEntity>,
    viewModel: MainViewModel,
    contentPadding: PaddingValues
) {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current

    var showSourcePicker by remember { mutableStateOf(false) }
    var showTargetPicker by remember { mutableStateOf(false) }
    var isVoiceListening by remember { mutableStateOf(false) }
    var voiceError by remember { mutableStateOf<String?>(null) }
    var copiedFeedback by remember { mutableStateOf(false) }

    val speechController = remember {
        LiveSpeechController(
            context = context,
            onPartialOrFinalResult = { text, isFinal ->
                viewModel.updateTranslateInput(text)
                if (isFinal) {
                    viewModel.translateTextInstant()
                }
            },
            onErrorMessage = { msg ->
                voiceError = msg
                isVoiceListening = false
            },
            onListeningChanged = { active ->
                isVoiceListening = active
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
            voiceError = null
            val bcp47 = if (state.sourceLang.code == LanguageCatalog.autoDetect.code) {
                "ar-SA"
            } else {
                state.sourceLang.bcp47
            }
            speechController.start(bcp47, continuous = false)
        } else {
            voiceError = "يلزم إذن الميكروفون للإدخال الصوتي."
        }
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
            item {
                Column {
                    Text(
                        text = "الترجمة الفورية",
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    FreeBadgePill()
                }
            }

            // Language Switcher + Dialect Style Pills
            item {
                LiquidGlassCard(accentGlow = LiquidCyan) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
                                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                .clickable { showSourcePicker = true }
                                .testTag("translate_source_lang_button")
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${state.sourceLang.flag} ${state.sourceLang.arabicName}",
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        IconButton(
                            onClick = viewModel::swapTranslateLanguages,
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .testTag("translate_swap_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SwapHoriz,
                                contentDescription = "تبديل اللغتين",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
                                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                .clickable { showTargetPicker = true }
                                .testTag("translate_target_lang_button")
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${state.targetLang.flag} ${state.targetLang.arabicName}",
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TranslationStyle.entries.forEach { style ->
                            val selected = state.style == style
                            Row(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    )
                                    .border(
                                        1.dp,
                                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                        CircleShape
                                    )
                                    .clickable { viewModel.setTranslateStyle(style) }
                                    .testTag("translate_style_${style.id}")
                                    .padding(horizontal = 12.dp, vertical = 7.dp)
                            ) {
                                Text(
                                    text = style.arabicLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = state.inputText,
                        onValueChange = {
                            copiedFeedback = false
                            viewModel.updateTranslateInput(it)
                        },
                        placeholder = { Text("اكتب أو تحدّث للترجمة الفورية من وإلى العربية...") },
                        minLines = 3,
                        maxLines = 6,
                        shape = RoundedCornerShape(20.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        ),
                        trailingIcon = {
                            if (state.inputText.isNotEmpty()) {
                                IconButton(onClick = { viewModel.updateTranslateInput("") }) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "مسح النص"
                                    )
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("translate_text_input")
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        LiquidGlassPillButton(
                            text = if (state.isTranslating) "جاري الترجمة..." else "ترجم فوراً",
                            icon = Icons.Rounded.Translate,
                            onClick = {
                                keyboard?.hide()
                                copiedFeedback = false
                                viewModel.translateTextInstant()
                            },
                            enabled = !state.isTranslating,
                            isPrimary = true,
                            testTag = "translate_now_button",
                            modifier = Modifier.weight(1f)
                        )
                        LiquidGlassPillButton(
                            text = if (isVoiceListening) "إيقاف" else "صوت إلى نص",
                            icon = if (isVoiceListening) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                            onClick = {
                                if (isVoiceListening) {
                                    speechController.stop()
                                } else {
                                    val granted = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.RECORD_AUDIO
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (granted) {
                                        voiceError = null
                                        val bcp47 = if (state.sourceLang.code == LanguageCatalog.autoDetect.code) {
                                            "ar-SA"
                                        } else {
                                            state.sourceLang.bcp47
                                        }
                                        speechController.start(bcp47, continuous = false)
                                    } else {
                                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                }
                            },
                            isPrimary = isVoiceListening,
                            testTag = "translate_mic_button"
                        )
                    }

                    if (voiceError != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = voiceError.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = CoralAlert
                        )
                    }
                    if (state.errorMessage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = state.errorMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // Streaming Translation Output Card
            if (state.isTranslating || state.outputText.isNotBlank()) {
                item {
                    LiquidGlassCard(accentGlow = MaterialTheme.colorScheme.secondary) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${state.targetLang.flag} ${state.targetLang.arabicName}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (state.isTranslating) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    IconButton(
                                        onClick = {
                                            copyToClipboard(context, state.outputText)
                                            copiedFeedback = true
                                        },
                                        modifier = Modifier.testTag("copy_translation_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.ContentCopy,
                                            contentDescription = "نسخ الترجمة",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            val share = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_TEXT, state.outputText)
                                            }
                                            context.startActivity(Intent.createChooser(share, "مشاركة الترجمة"))
                                        },
                                        modifier = Modifier.testTag("share_translation_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Share,
                                            contentDescription = "مشاركة الترجمة",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = state.outputText.ifBlank { "..." },
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.testTag("translation_output_text")
                        )

                        if (copiedFeedback) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "تم النسخ إلى الحافظة",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                }
            }

            // Recent & Favorite Translations
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "السجل والمفضلة (${history.size})",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            if (history.isEmpty()) {
                item {
                    LiquidGlassCard {
                        Text(
                            text = "ستظهر ترجماتك الأخيرة والمحفوظة هنا تلقائياً على جهازك دون الحاجة لأي حساب.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(history, key = { it.id }) { item ->
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { viewModel.loadHistoryItemIntoTranslator(item) }
                            .testTag("history_item_${item.id}")
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${LanguageCatalog.findByCode(item.sourceLangCode).flag} → ${LanguageCatalog.findByCode(item.targetLangCode).flag}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Row {
                                    IconButton(onClick = { viewModel.toggleHistoryFavorite(item) }) {
                                        Icon(
                                            imageVector = if (item.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                            contentDescription = "تفضيل",
                                            tint = if (item.isFavorite) CoralAlert else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(onClick = { viewModel.deleteHistoryItem(item.id) }) {
                                        Icon(
                                            imageVector = Icons.Rounded.DeleteOutline,
                                            contentDescription = "حذف",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            Text(
                                text = item.sourceText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = item.translatedText,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }

    if (showSourcePicker) {
        LanguagePickerBottomSheet(
            title = "اللغة المصدر",
            options = LanguageCatalog.sourceOptions,
            selectedCode = state.sourceLang.code,
            onSelect = { viewModel.setTranslateLanguages(it, state.targetLang) },
            onDismiss = { showSourcePicker = false }
        )
    }

    if (showTargetPicker) {
        LanguagePickerBottomSheet(
            title = "اللغة الهدف",
            options = LanguageCatalog.supportedLanguages,
            selectedCode = state.targetLang.code,
            onSelect = { viewModel.setTranslateLanguages(state.sourceLang, it) },
            onDismiss = { showTargetPicker = false }
        )
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("Tarjuman Translation", text))
}
