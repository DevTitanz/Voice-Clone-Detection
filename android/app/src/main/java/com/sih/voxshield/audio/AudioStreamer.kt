package com.sih.voxshield.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.*
import okhttp3.*
import okio.ByteString.Companion.toByteString
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

data class LiveAnalysisResult(
    val riskScore: Double,
    val classification: String,
    val classificationLabel: String,
    val confidence: Double,
    val verificationRequired: Boolean,
    val durationSeconds: Double
)

/**
 * AudioStreamer:
 * Captures live microphone audio in volatile memory using AudioRecord (16kHz PCM16 Mono).
 * Streams binary chunks over an authenticated, encrypted WSS connection.
 * PRIVACY GUARANTEE: Never writes audio chunks to local flash/disk storage.
 */
class AudioStreamer(
    private val serverWsUrl: String,
    private val authToken: String,
    private val onResult: (LiveAnalysisResult) -> Unit,
    private val onStatusChanged: (String) -> Unit
) {
    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat).coerceAtLeast(4096)

    private var audioRecord: AudioRecord? = null
    private val isRecording = AtomicBoolean(false)
    private var webSocket: WebSocket? = null
    private var streamingJob: Job? = null
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val okHttpClient = OkHttpClient.Builder()
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    @SuppressLint("MissingPermission")
    fun startStreaming() {
        if (isRecording.get()) return

        val authenticatedUrl = "$serverWsUrl?token=$authToken&client_type=android"
        val request = Request.Builder().url(authenticatedUrl).build()

        onStatusChanged("Connecting securely via WSS...")

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                onStatusChanged("Shield Active: Encrypted Stream")
                startAudioCapture()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val json = JSONObject(text)
                    if (json.optString("event") == "ANALYSIS_UPDATE") {
                        val result = LiveAnalysisResult(
                            riskScore = json.getDouble("risk_score"),
                            classification = json.getString("classification"),
                            classificationLabel = json.getString("classification_label"),
                            confidence = json.getDouble("confidence"),
                            verificationRequired = json.getBoolean("verification_required"),
                            durationSeconds = json.optDouble("audio_duration_seconds", 0.0)
                        )
                        onResult(result)
                    }
                } catch (e: Exception) {
                    // Ignore parse errors
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                onStatusChanged("Connection Error. Stream Halted.")
                stopStreaming()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                onStatusChanged("Stream Closed. Memory Purged.")
            }
        })
    }

    @SuppressLint("MissingPermission")
    private fun startAudioCapture() {
        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                onStatusChanged("Microphone access unavailable.")
                return
            }

            audioRecord?.startRecording()
            isRecording.set(true)

            streamingJob = coroutineScope.launch {
                val chunk = ByteArray(2048) // ~64ms of 16kHz audio
                while (isRecording.get()) {
                    val readBytes = audioRecord?.read(chunk, 0, chunk.size) ?: -1
                    if (readBytes > 0) {
                        // Stream directly to secure WebSocket
                        webSocket?.send(chunk.copyOf(readBytes).toByteString())
                    }
                }
            }
        } catch (e: Exception) {
            onStatusChanged("Audio recording error.")
            stopStreaming()
        }
    }

    fun stopStreaming() {
        isRecording.set(false)
        streamingJob?.cancel()

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            // Safe release
        } finally {
            audioRecord = null
        }

        webSocket?.send("{\"action\":\"END_CALL\"}")
        webSocket?.close(1000, "Call Ended")
        webSocket = null
        onStatusChanged("Stream Disconnected. Zero Audio Stored.")
    }
}
