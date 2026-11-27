package com.example.ui.components

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.ClosedCaptionOff
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.data.media.SubtitleCue
import com.example.data.media.SubtitleParser
import com.example.ui.theme.LiquidCyan
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
fun LiquidVideoPlayer(
    playUrl: String,
    useWebMode: Boolean,
    cues: List<SubtitleCue>,
    liveEmbeddedOriginal: String,
    liveEmbeddedTranslated: String,
    isLiveMicActive: Boolean,
    liveMicOriginal: String,
    liveMicTranslated: String,
    showDualSubtitles: Boolean,
    subtitleFontScale: Float,
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    onEmbeddedCueDetected: (String) -> Unit,
    onWebSniffedStream: (String) -> Unit,
    onPlaybackPositionUpdated: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    if (isFullscreen) {
        BackHandler { onToggleFullscreen() }
    }

    var subtitlesEnabled by remember { mutableStateOf(true) }
    var currentPosMs by remember { mutableLongStateOf(0L) }

    val activeFileCue = remember(cues, currentPosMs) {
        SubtitleParser.findActiveCue(cues, currentPosMs)
    }

    val displayTranslated = when {
        !subtitlesEnabled -> ""
        isLiveMicActive && liveMicTranslated.isNotBlank() -> liveMicTranslated
        activeFileCue != null -> activeFileCue.translatedText.ifBlank { activeFileCue.originalText }
        liveEmbeddedTranslated.isNotBlank() -> liveEmbeddedTranslated
        else -> ""
    }
    val displayOriginal = when {
        !subtitlesEnabled -> ""
        isLiveMicActive && liveMicOriginal.isNotBlank() -> liveMicOriginal
        activeFileCue != null -> activeFileCue.originalText
        liveEmbeddedOriginal.isNotBlank() -> liveEmbeddedOriginal
        else -> ""
    }

    val containerModifier = if (isFullscreen) {
        modifier
            .fillMaxSize()
            .background(Color.Black)
    } else {
        modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9.5f)
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF050811))
            .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(24.dp))
    }

    Box(modifier = containerModifier.testTag("video_player_container")) {
        if (useWebMode) {
            SmartWebVideoViewport(
                url = playUrl,
                onSniffedStream = onWebSniffedStream,
                modifier = Modifier.fillMaxSize()
            )
            // Compact fullscreen + subtitle toggle pill at top-end in Web mode
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .clip(CircleShape)
                    .background(Color(0xAA060A14))
                    .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { subtitlesEnabled = !subtitlesEnabled }) {
                    Icon(
                        imageVector = if (subtitlesEnabled) Icons.Rounded.ClosedCaption else Icons.Rounded.ClosedCaptionOff,
                        contentDescription = "تبديل الترجمة النصية",
                        tint = if (subtitlesEnabled) LiquidCyan else Color.White
                    )
                }
                IconButton(onClick = onToggleFullscreen) {
                    Icon(
                        imageVector = if (isFullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                        contentDescription = "ملء الشاشة",
                        tint = Color.White
                    )
                }
            }
        } else {
            ExoPlayerSurfaceWithGlassControls(
                playUrl = playUrl,
                subtitlesEnabled = subtitlesEnabled,
                isFullscreen = isFullscreen,
                onToggleSubtitles = { subtitlesEnabled = !subtitlesEnabled },
                onToggleFullscreen = onToggleFullscreen,
                onEmbeddedCueDetected = onEmbeddedCueDetected,
                onPositionChanged = { pos ->
                    currentPosMs = pos
                    onPlaybackPositionUpdated(pos)
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Bilingual / Translated Subtitle Overlay (Zero Dubbing)
        if (displayTranslated.isNotBlank() || displayOriginal.isNotBlank()) {
            DualSubtitleGlassOverlay(
                translatedText = displayTranslated,
                originalText = displayOriginal,
                showDual = showDualSubtitles,
                fontScale = subtitleFontScale,
                isLiveSpeech = isLiveMicActive,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(
                        start = 14.dp,
                        end = 14.dp,
                        bottom = if (useWebMode) 14.dp else 64.dp
                    )
            )
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun ExoPlayerSurfaceWithGlassControls(
    playUrl: String,
    subtitlesEnabled: Boolean,
    isFullscreen: Boolean,
    onToggleSubtitles: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onEmbeddedCueDetected: (String) -> Unit,
    onPositionChanged: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(true) }
    var playerError by remember { mutableStateOf<String?>(null) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var controlsVisible by remember { mutableStateOf(true) }
    var playbackSpeed by remember { mutableFloatStateOf(1f) }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
        }
    }

    LaunchedEffect(playUrl) {
        playerError = null
        isBuffering = true
        val item = MediaItem.fromUri(playUrl)
        exoPlayer.setMediaItem(item)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) {
                    durationMs = exoPlayer.duration.coerceAtLeast(0L)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                isBuffering = false
                playerError = "تعذر تشغيل هذا الرابط مباشرة (${error.errorCodeName}). جرّب وضع مشغل الويب الذكي."
            }

            override fun onCues(cueGroup: CueGroup) {
                val text = cueGroup.cues.joinToString("\n") { it.text?.toString().orEmpty() }.trim()
                onEmbeddedCueDetected(text)
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    LaunchedEffect(exoPlayer) {
        while (true) {
            val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
            val dur = exoPlayer.duration.coerceAtLeast(0L)
            positionMs = pos
            if (dur > 0L) durationMs = dur
            onPositionChanged(pos)
            delay(250L)
        }
    }

    LaunchedEffect(controlsVisible, isPlaying) {
        if (controlsVisible && isPlaying) {
            delay(4000L)
            controlsVisible = false
        }
    }

    Box(
        modifier = modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null
        ) { controlsVisible = !controlsVisible }
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    subtitleView?.visibility = android.view.View.GONE
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { view ->
                if (view.player !== exoPlayer) {
                    view.player = exoPlayer
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (isBuffering && playerError == null) {
            CircularProgressIndicator(
                color = LiquidCyan,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(40.dp)
            )
        }

        if (playerError != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(20.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xCC0A1020))
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = playerError.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White
                )
            }
        }

        AnimatedVisibility(
            visible = controlsVisible && playerError == null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0x88040812),
                                Color.Transparent,
                                Color(0xB8040812)
                            )
                        )
                    )
            ) {
                // Top-end quick pills (Speed, Subtitles, Fullscreen)
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .clip(CircleShape)
                        .background(Color(0x99081022))
                        .border(1.dp, Color.White.copy(alpha = 0.22f), CircleShape)
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable {
                                val nextSpeed = when (playbackSpeed) {
                                    1f -> 1.25f
                                    1.25f -> 1.5f
                                    1.5f -> 2f
                                    else -> 1f
                                }
                                playbackSpeed = nextSpeed
                                exoPlayer.setPlaybackSpeed(nextSpeed)
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Speed,
                            contentDescription = "سرعة التشغيل",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${playbackSpeed}x",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }
                    IconButton(onClick = onToggleSubtitles) {
                        Icon(
                            imageVector = if (subtitlesEnabled) Icons.Rounded.ClosedCaption else Icons.Rounded.ClosedCaptionOff,
                            contentDescription = "إظهار/إخفاء الترجمة",
                            tint = if (subtitlesEnabled) LiquidCyan else Color.White
                        )
                    }
                    IconButton(
                        onClick = onToggleFullscreen,
                        modifier = Modifier.testTag("player_fullscreen_button")
                    ) {
                        Icon(
                            imageVector = if (isFullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                            contentDescription = "ملء الشاشة",
                            tint = Color.White
                        )
                    }
                }

                // Center Transport Glass Pill
                Row(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .clip(CircleShape)
                        .background(Color(0x990A1224))
                        .border(1.dp, Color.White.copy(alpha = 0.28f), CircleShape)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    IconButton(onClick = {
                        exoPlayer.seekTo((exoPlayer.currentPosition - 10_000L).coerceAtLeast(0L))
                    }) {
                        Icon(
                            imageVector = Icons.Rounded.Replay10,
                            contentDescription = "ترجيع 10 ثوانٍ",
                            tint = Color.White
                        )
                    }
                    IconButton(
                        onClick = {
                            if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(LiquidCyan.copy(alpha = 0.90f))
                            .testTag("player_play_pause_button")
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (isPlaying) "إيقاف مؤقت" else "تشغيل",
                            tint = Color(0xFF041024),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    IconButton(onClick = {
                        val maxDur = if (durationMs > 0) durationMs else Long.MAX_VALUE
                        exoPlayer.seekTo((exoPlayer.currentPosition + 10_000L).coerceAtMost(maxDur))
                    }) {
                        Icon(
                            imageVector = Icons.Rounded.Forward10,
                            contentDescription = "تقديم 10 ثوانٍ",
                            tint = Color.White
                        )
                    }
                }

                // Bottom Scrubber Bar (always LTR for media timeline)
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = SubtitleParser.formatDisplayDuration(positionMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                        Slider(
                            value = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f,
                            onValueChange = { frac ->
                                if (durationMs > 0) {
                                    exoPlayer.seekTo((frac * durationMs).toLong())
                                }
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = LiquidCyan,
                                activeTrackColor = LiquidCyan,
                                inactiveTrackColor = Color.White.copy(alpha = 0.28f)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp)
                                .height(28.dp)
                        )
                        Text(
                            text = SubtitleParser.formatDisplayDuration(durationMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun SmartWebVideoViewport(
    url: String,
    onSniffedStream: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                webChromeClient = WebChromeClient()
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                        return false
                    }

                    override fun onLoadResource(view: WebView?, resourceUrl: String?) {
                        super.onLoadResource(view, resourceUrl)
                        if (resourceUrl != null &&
                            (resourceUrl.contains(".mp4", true) ||
                                resourceUrl.contains(".m3u8", true) ||
                                resourceUrl.contains(".webm", true))
                        ) {
                            post { onSniffedStream(resourceUrl) }
                        }
                    }
                }
                loadUrl(url)
            }
        },
        update = { webView ->
            if (webView.url != url && url.startsWith("http")) {
                webView.loadUrl(url)
            }
        },
        modifier = modifier
    )
}
