package com.sih.voiceguard.ai

import kotlin.math.*

data class OnDeviceDetectionResult(
    val riskScore: Double,
    val classification: String,
    val classificationLabel: String,
    val confidence: Double,
    val verificationRequired: Boolean,
    val features: OnDeviceFeatureExtractor.AcousticFeatures,
    val detectedEmotion: String,
    val emotionIncongruenceFlag: String? = null,
    val isSpeechPresent: Boolean = true
)

/**
 * OnDeviceVoiceDetector:
 * Evaluates acoustic vocoder biomarkers and prosodic emotional incongruence on device
 * to detect synthetic speech, fake urgency, and cloned voices with ZERO server reliance.
 */
class OnDeviceVoiceDetector {

    private val extractor = OnDeviceFeatureExtractor()
    val modelVersion = "voiceguard-ondevice-v1.4-stabilized"

    fun analyzeBuffer(samples: FloatArray, sampleRate: Int = 16000): OnDeviceDetectionResult {
        val features = extractor.extract(samples, sampleRate)

        // Noise floor gate: If energy is too low, caller is not speaking (silence or ambient room tone)
        if (features.energyRms < 0.025f) {
            return OnDeviceDetectionResult(
                riskScore = 14.0,
                classification = "LOW_RISK",
                classificationLabel = "Natural voice baseline",
                confidence = 0.88,
                verificationRequired = false,
                features = features,
                detectedEmotion = "Listening...",
                emotionIncongruenceFlag = null,
                isSpeechPresent = false
            )
        }

        // 1. Vocoder high frequency ratio (neural vocoders often introduce high-frequency band anomaly)
        val hfScore = min(35.0, features.highFreqRatio * 280.0)

        // 2. Spectral flatness (vocoder phase noise)
        val flatnessScore = min(20.0, features.spectralFlatness * 220.0)

        // 3. Robotic pitch regularity or erratic jitter anomaly
        val jitter = features.jitterFactor
        val jitterScore = when {
            jitter < 0.05 -> 22.0 // Unnaturally robotic pitch regularity (TTS artifact)
            jitter > 0.85 -> 18.0 // Vocoder phase glitching
            else -> max(0.0, (0.4 - abs(jitter - 0.25)) * 20.0)
        }

        // 4. Zero-crossing rate anomaly
        val zcrScore = min(12.0, features.zeroCrossingRate * 90.0)

        // 5. Emotion Classification from Acoustic Prosody
        val f0 = features.pitchFundamentalF0
        val pVar = features.pitchVarianceHz
        val energy = features.energyRms

        val detectedEmotion = when {
            energy > 0.12 && f0 > 240.0 -> "Panic / Extreme Urgency"
            energy > 0.08 && f0 > 200.0 -> "High Pressure / Coercion"
            energy > 0.08 && pVar > 40.0 -> "Agitated / Distressed"
            pVar < 14.0 && energy > 0.05 -> "Monotone / Flat Affect"
            energy < 0.03 -> "Whisper / Low Energy"
            else -> "Calm / Neutral"
        }

        // 6. Fake Emotion / Emotional Incongruence Detection
        // Hallmark of AI voice scams: Caller pretends urgency/panic (high volume/F0),
        // but neural vocoder/TTS generates rigidly flat pitch variance (pVar < 16 Hz).
        var emotionIncongruenceFlag: String? = null
        var emotionAnomalyBonus = 0.0

        if ((detectedEmotion.contains("Panic") || detectedEmotion.contains("Pressure")) && pVar < 16.0) {
            emotionIncongruenceFlag = "Fake Urgency Detected: Monotone pitch with forced urgency (AI Scam Signature)"
            emotionAnomalyBonus = 22.0
        } else if (features.highFreqRatio > 0.10 && detectedEmotion.contains("Monotone")) {
            emotionIncongruenceFlag = "Synthetic Emotional Disconnect: Flat prosody with neural vocoder noise"
            emotionAnomalyBonus = 18.0
        } else if (pVar < 8.0 && energy > 0.04) {
            emotionIncongruenceFlag = "Robotic Prosody Anomaly: Unnatural pitch rigidity"
            emotionAnomalyBonus = 14.0
        }

        val rawRisk = hfScore + flatnessScore + jitterScore + zcrScore + emotionAnomalyBonus
        // Continuous probabilistic risk bounded strictly between 5.0 and 98.0
        val riskScore = round(rawRisk.coerceIn(5.0, 98.0) * 10.0) / 10.0

        val verificationRequired = riskScore >= 70.0
        val classification = when {
            riskScore >= 70.0 -> "HIGH_RISK"
            riskScore >= 40.0 -> "MEDIUM_RISK"
            else -> "LOW_RISK"
        }

        val label = when (classification) {
            "HIGH_RISK" -> if (emotionIncongruenceFlag != null) "AI Voice Scam Suspected (Fake Emotion)" else "AI-generated voice suspected"
            "MEDIUM_RISK" -> "Suspicious acoustic characteristics"
            else -> "Natural voice patterns detected"
        }

        val durationSec = samples.size.toDouble() / sampleRate
        val confidence = round((0.72 + min(0.24, durationSec * 0.05)).coerceIn(0.72, 0.96) * 100.0) / 100.0

        return OnDeviceDetectionResult(
            riskScore = riskScore,
            classification = classification,
            classificationLabel = label,
            confidence = confidence,
            verificationRequired = verificationRequired,
            features = features,
            detectedEmotion = detectedEmotion,
            emotionIncongruenceFlag = emotionIncongruenceFlag
        )
    }
}
