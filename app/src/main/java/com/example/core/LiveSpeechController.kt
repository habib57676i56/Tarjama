package com.example.core

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

class LiveSpeechController(
    private val context: Context,
    private val onPartialOrFinalResult: (text: String, isFinal: Boolean) -> Unit,
    private val onErrorMessage: (String) -> Unit,
    private val onListeningChanged: (Boolean) -> Unit
) {
    companion object {
        private const val TAG = "LiveSpeechController"
    }

    private var recognizer: SpeechRecognizer? = null
    private var continuousMode = false
    private var currentLanguageTag = "en-US"
    private var isStoppedByUser = true

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun start(languageTag: String, continuous: Boolean) {
        if (!isAvailable()) {
            onErrorMessage("التعرف على الصوت غير متاح على هذا الجهاز.")
            return
        }
        currentLanguageTag = languageTag
        continuousMode = continuous
        isStoppedByUser = false
        startInternal()
    }

    fun stop() {
        isStoppedByUser = true
        continuousMode = false
        runCatching {
            recognizer?.stopListening()
            recognizer?.destroy()
        }
        recognizer = null
        onListeningChanged(false)
    }

    private fun startInternal() {
        if (isStoppedByUser) return
        runCatching { recognizer?.destroy() }
        val sr = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = sr

        sr.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                onListeningChanged(true)
            }

            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}

            override fun onError(error: Int) {
                Log.w(TAG, "SpeechRecognizer error code: $error")
                if (continuousMode && !isStoppedByUser &&
                    (error == SpeechRecognizer.ERROR_NO_MATCH ||
                        error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)
                ) {
                    startInternal()
                    return
                }
                if (!continuousMode || error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                    onListeningChanged(false)
                    val msg = when (error) {
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "يرجى منح صلاحية الميكروفون."
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                            "تحقق من اتصال الإنترنت للتعرف على الصوت."
                        else -> "توقف الاستماع للصوت."
                    }
                    onErrorMessage(msg)
                } else if (!isStoppedByUser) {
                    startInternal()
                }
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val best = matches?.firstOrNull().orEmpty()
                if (best.isNotBlank()) {
                    onPartialOrFinalResult(best, true)
                }
                if (continuousMode && !isStoppedByUser) {
                    startInternal()
                } else {
                    onListeningChanged(false)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val best = matches?.firstOrNull().orEmpty()
                if (best.isNotBlank()) {
                    onPartialOrFinalResult(best, false)
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, currentLanguageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        sr.startListening(intent)
    }
}
