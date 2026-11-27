package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.configureAppCheck
import com.example.data.local.DownloadStatus
import com.example.ui.components.LiquidGlassBackground
import com.example.ui.components.LiquidGlassFloatingDock
import com.example.ui.screens.DownloadsScreen
import com.example.ui.screens.TranslatorScreen
import com.example.ui.screens.VideoStudioScreen
import com.example.ui.theme.TarjumanTheme
import com.example.ui.viewmodel.AppTab
import com.example.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure Firebase App Check with launch intent before any UI or Firebase calls
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        handleIncomingIntent(intent)

        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
            val videoState by viewModel.videoState.collectAsStateWithLifecycle()
            val translateState by viewModel.translateState.collectAsStateWithLifecycle()
            val history by viewModel.translationHistory.collectAsStateWithLifecycle()
            val downloads by viewModel.downloads.collectAsStateWithLifecycle()
            val speedMap by viewModel.downloadSpeeds.collectAsStateWithLifecycle()

            val snackbarHostState = remember { SnackbarHostState() }

            LaunchedEffect(videoState.snackMessage) {
                val msg = videoState.snackMessage
                if (!msg.isNullOrBlank()) {
                    snackbarHostState.showSnackbar(msg)
                    viewModel.clearSnackMessage()
                }
            }

            val activeDownloadCount = downloads.count {
                it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED
            }

            TarjumanTheme(
                darkTheme = settings.isDarkGlass,
                rtlLayout = true
            ) {
                BackHandler(enabled = selectedTab != AppTab.VIDEO_STUDIO && !videoState.isFullscreen) {
                    viewModel.selectTab(AppTab.VIDEO_STUDIO)
                }
                LiquidGlassBackground {
                    Scaffold(
                        containerColor = Color.Transparent,
                        contentWindowInsets = WindowInsets.safeDrawing,
                        snackbarHost = {
                            SnackbarHost(
                                hostState = snackbarHostState,
                                modifier = Modifier.padding(bottom = 84.dp)
                            )
                        }
                    ) { innerPadding ->
                        Box(modifier = Modifier.fillMaxSize()) {
                            when (selectedTab) {
                                AppTab.VIDEO_STUDIO -> VideoStudioScreen(
                                    state = videoState,
                                    settings = settings,
                                    viewModel = viewModel,
                                    contentPadding = innerPadding
                                )

                                AppTab.INSTANT_TRANSLATE -> TranslatorScreen(
                                    state = translateState,
                                    history = history,
                                    viewModel = viewModel,
                                    contentPadding = innerPadding
                                )

                                AppTab.DOWNLOADS -> DownloadsScreen(
                                    downloads = downloads,
                                    speedMap = speedMap,
                                    viewModel = viewModel,
                                    contentPadding = innerPadding
                                )
                            }

                            if (!videoState.isFullscreen) {
                                LiquidGlassFloatingDock(
                                    selectedTab = selectedTab,
                                    activeDownloadCount = activeDownloadCount,
                                    onSelectTab = viewModel::selectTab,
                                    modifier = Modifier.align(Alignment.BottomCenter)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            Intent.ACTION_SEND -> {
                val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                if (!sharedText.isNullOrBlank()) {
                    viewModel.handleIncomingSharedText(sharedText)
                }
            }
            Intent.ACTION_VIEW -> {
                val dataUrl = intent.dataString
                if (!dataUrl.isNullOrBlank()) {
                    viewModel.handleIncomingSharedText(dataUrl)
                }
            }
        }
    }
}
