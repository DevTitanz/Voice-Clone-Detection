package com.sih.voxshield.audio

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

/**
 * OnDeviceSpeechRecognizerHelper:
 * Wraps Android's native SpeechRecognizer to transcribe live phone/mic speech on-device
 * into real-time text chunks without any external cloud APIs.
 *
 * Supported locales:
 * - English (India): "en-IN"
 * - Hindi: "hi-IN"
 * - Marathi: "mr-IN"
 */
class OnDeviceSpeechRecognizerHelper(
    private val context: Context,
    private val onSpeechChunkRecognized: (String) -> Unit
) {
    companion object {
        private const val TAG = "SpeechRecognizerHelper"
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isListening = false
    private var currentLanguageCode = "en-IN"

    fun setLanguage(languageCode: String) {
        currentLanguageCode = languageCode
        if (isListening) {
            stopListening()
            mainHandler.postDelayed({ startListening(currentLanguageCode) }, 300)
        }
    }

    fun startListening(languageCode: String = currentLanguageCode) {
        currentLanguageCode = languageCode
        mainHandler.post {
            try {
                if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                    Log.w(TAG, "SpeechRecognizer is not available on this device.")
                    return@post
                }

                stopListeningInternal()

                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) {}
                        override fun onBeginningOfSpeech() {}
                        override fun onRmsChanged(rmsdB: Float) {}
                        override fun onBufferReceived(buffer: ByteArray?) {}
                        override fun onEndOfSpeech() {}

                        override fun onError(error: Int) {
                            Log.d(TAG, "Speech recognition error code: $error")
                            // Auto-restart to maintain continuous live call screening
                            if (isListening) {
                                mainHandler.postDelayed({
                                    if (isListening) restartListening()
                                }, 800)
                            }
                        }

                        override fun onResults(results: Bundle?) {
                            processResults(results)
                            if (isListening) {
                                restartListening()
                            }
                        }

                        override fun onPartialResults(partialResults: Bundle?) {
                            processResults(partialResults)
                        }

                        override fun onEvent(eventType: Int, params: Bundle?) {}
                    })
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, currentLanguageCode)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    // Request on-device offline recognition when available
                    putExtra("android.speech.extra.PREFER_OFFLINE", true)
                }

                speechRecognizer?.startListening(intent)
                isListening = true
                Log.i(TAG, "Started speech recognition in language: $currentLanguageCode")
            } catch (e: Exception) {
                Log.e(TAG, "Error starting SpeechRecognizer: ${e.message}")
            }
        }
    }

    private fun processResults(bundle: Bundle?) {
        val matches = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (!matches.isNullOrEmpty()) {
            val recognizedText = matches[0]
            if (recognizedText.isNotBlank()) {
                Log.d(TAG, "Recognized speech chunk: $recognizedText")
                onSpeechChunkRecognized(recognizedText)
            }
        }
    }

    private fun restartListening() {
        if (!isListening) return
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, currentLanguageCode)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra("android.speech.extra.PREFER_OFFLINE", true)
            }
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error restarting SpeechRecognizer: ${e.message}")
        }
    }

    fun stopListening() {
        isListening = false
        mainHandler.post {
            stopListeningInternal()
        }
    }

    private fun stopListeningInternal() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
            speechRecognizer = null
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up SpeechRecognizer: ${e.message}")
        }
    }
}
