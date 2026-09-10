package com.sih.voiceguard.service

import android.content.Context
import android.media.AudioManager
import android.util.Log
import com.sih.voiceguard.ai.OnDeviceDetectionResult
import com.sih.voiceguard.ai.OnDeviceSpeechAnalyzer
import com.sih.voiceguard.audio.OnDeviceCallMonitor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class CallShieldUiState(
    val isCallActive: Boolean = false,
    val callerNumber: String? = null,
    val isMonitoring: Boolean = false,
    val currentRiskScore: Double = 14.0,
    val classification: String = "LOW_RISK",
    val classificationLabel: String = "Natural voice patterns detected",
    val detectedEmotion: String = "Calm / Neutral",
    val emotionIncongruenceFlag: String? = null,
    val scamThreatCategory: String? = null,
    val threatLevel: String = "SAFE",
    val aiExplanation: String = "Normal conversational speech patterns.",
    val matchedKeywords: List<String> = emptyList(),
    val liveTranscript: String = "",
    val audioRmsDb: Float = 0f,
    val isAudioSignalDetected: Boolean = false,
    val highFreqRatio: Double = 0.04,
    val pitchJitter: Double = 0.28,
    val pitchVariance: Double = 34.0,
    val statusText: String = "Shield Standing By",
    val speakerphoneRecommended: Boolean = false,
    val selectedLanguage: OnDeviceSpeechAnalyzer.Language = OnDeviceSpeechAnalyzer.Language.ENGLISH
)

/**
 * CallShieldManager:
 * Unified Singleton orchestrating in-call on-device audio screening and speech intent analysis.
 * Eliminates microphone hardware conflicts between Foreground Services and Activities.
 */
object CallShieldManager {
    private const val TAG = "CallShieldManager"

    private val _uiState = MutableStateFlow(CallShieldUiState())
    val uiState: StateFlow<CallShieldUiState> = _uiState.asStateFlow()

    private var callMonitor: OnDeviceCallMonitor? = null
    val speechAnalyzer = OnDeviceSpeechAnalyzer()
    private var consecutiveSilenceTicks = 0

    @Synchronized
    fun startInCallShield(context: Context, callerNumber: String? = null) {
        Log.i(TAG, "startInCallShield requested for caller: $callerNumber")

        _uiState.update { current ->
            current.copy(
                isCallActive = true,
                callerNumber = callerNumber ?: current.callerNumber ?: "Active Call",
                isMonitoring = true,
                statusText = "In-Call Shield Active • Screening Call"
            )
        }

        // Set audio routing mode for communication
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            audioManager?.mode = AudioManager.MODE_IN_COMMUNICATION
        } catch (e: Exception) {
            Log.w(TAG, "Failed setting audio mode: ${e.message}")
        }

        if (callMonitor == null) {
            callMonitor = OnDeviceCallMonitor(
                onResult = { result ->
                    handleDetectionResult(result)
                },
                onStatusChanged = { status ->
                    _uiState.update { it.copy(statusText = status) }
                },
                onAudioLevelChanged = { rmsDb, isAudioPresent ->
                    if (!isAudioPresent && _uiState.value.isCallActive) {
                        consecutiveSilenceTicks++
                    } else {
                        consecutiveSilenceTicks = 0
                    }
                    val needSpeaker = consecutiveSilenceTicks > 12 // ~1.5 seconds silence during call
                    _uiState.update {
                        it.copy(
                            audioRmsDb = rmsDb,
                            isAudioSignalDetected = isAudioPresent,
                            speakerphoneRecommended = needSpeaker
                        )
                    }
                }
            )
            callMonitor?.startMonitoring()
        }
    }

    @Synchronized
    fun stopInCallShield() {
        Log.i(TAG, "stopInCallShield requested")
        callMonitor?.stopMonitoring()
        callMonitor = null
        speechAnalyzer.purgeMemory()
        consecutiveSilenceTicks = 0

        _uiState.update {
            it.copy(
                isCallActive = false,
                isMonitoring = false,
                currentRiskScore = 14.0,
                classification = "LOW_RISK",
                classificationLabel = "Natural voice patterns detected",
                detectedEmotion = "Calm / Neutral",
                emotionIncongruenceFlag = null,
                scamThreatCategory = null,
                threatLevel = "SAFE",
                aiExplanation = "Normal conversational speech patterns.",
                matchedKeywords = emptyList(),
                liveTranscript = "",
                audioRmsDb = 0f,
                isAudioSignalDetected = false,
                speakerphoneRecommended = false,
                statusText = "Shield Standing By • Volatile RAM Cleared"
            )
        }
    }

    fun processSpokenText(textChunk: String) {
        speechAnalyzer.appendTranscriptChunk(textChunk)
        val intent = speechAnalyzer.analyzeIntent()

        _uiState.update { current ->
            val isScam = intent.isScamSuspected
            val fusedRisk = if (isScam) {
                minOf(98.0, maxOf(current.currentRiskScore, intent.riskScoreBoost.coerceAtLeast(85.0)))
            } else {
                current.currentRiskScore
            }

            current.copy(
                liveTranscript = textChunk.trim(),
                scamThreatCategory = intent.detectedThreatCategory,
                threatLevel = intent.threatLevel,
                aiExplanation = intent.aiExplanation,
                matchedKeywords = intent.matchedKeywords,
                currentRiskScore = fusedRisk,
                classification = if (fusedRisk >= 70.0) "HIGH_RISK" else "LOW_RISK",
                classificationLabel = if (isScam) "Scam Intent: ${intent.detectedThreatCategory ?: "Urgent Coercion"}" else current.classificationLabel
            )
        }
    }

    private fun handleDetectionResult(result: OnDeviceDetectionResult) {
        val intent = speechAnalyzer.analyzeIntent()
        val fusedRisk = if (intent.isScamSuspected) {
            minOf(98.0, maxOf(result.riskScore, intent.riskScoreBoost.coerceAtLeast(85.0)))
        } else {
            result.riskScore
        }

        _uiState.update { current ->
            current.copy(
                highFreqRatio = result.features.highFreqRatio,
                pitchJitter = result.features.jitterFactor,
                pitchVariance = result.features.pitchVarianceHz,
                detectedEmotion = result.detectedEmotion,
                emotionIncongruenceFlag = result.emotionIncongruenceFlag,
                currentRiskScore = fusedRisk,
                classification = if (fusedRisk >= 70.0) "HIGH_RISK" else result.classification,
                classificationLabel = if (intent.isScamSuspected) "Scam Intent: ${intent.detectedThreatCategory ?: "Urgent Coercion"}" else result.classificationLabel,
                scamThreatCategory = intent.detectedThreatCategory,
                threatLevel = intent.threatLevel,
                aiExplanation = intent.aiExplanation,
                matchedKeywords = intent.matchedKeywords
            )
        }
    }

    fun purgeMemory() {
        speechAnalyzer.purgeMemory()
        _uiState.update {
            it.copy(
                liveTranscript = "",
                scamThreatCategory = null,
                threatLevel = "SAFE",
                aiExplanation = "Normal conversational speech patterns.",
                matchedKeywords = emptyList(),
                currentRiskScore = 14.0,
                classification = "LOW_RISK",
                classificationLabel = "Natural voice patterns detected"
            )
        }
    }

    fun setLanguage(language: OnDeviceSpeechAnalyzer.Language) {
        speechAnalyzer.currentLanguage = language
        _uiState.update { it.copy(selectedLanguage = language) }
    }
}
