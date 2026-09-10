package com.sih.voiceguard.ai

/**
 * OnDeviceSpeechAnalyzer:
 * Multilingual on-device Speech-to-Text intent scanner supporting:
 * - Marathi (मराठी)
 * - Hindi (हिन्दी)
 * - English
 *
 * PRIVACY GUARANTEE:
 * Transcripts are held STRICTLY in an ephemeral volatile RAM buffer (StringBuilder)
 * while a call is active. The moment purgeMemory() is called upon call termination,
 * the buffer is completely cleared from memory. ZERO text is written to disk or database.
 */
class OnDeviceSpeechAnalyzer {

    enum class Language(val code: String, val displayName: String) {
        ENGLISH("en-IN", "English"),
        HINDI("hi-IN", "हिन्दी"),
        MARATHI("mr-IN", "मराठी")
    }

    data class ScamMatch(
        val category: String,
        val matchedKeyword: String,
        val severity: String // "CRITICAL", "HIGH", "MODERATE"
    )

    data class IntentResult(
        val isScamSuspected: Boolean,
        val detectedThreatCategory: String?,
        val matchedKeywords: List<String>,
        val currentTranscriptSnippet: String,
        val language: Language,
        val riskScoreBoost: Double = 0.0
    )

    private val ephemeralBuffer = StringBuilder()
    var currentLanguage: Language = Language.ENGLISH

    // Localized scam keyword dictionaries
    private val marathiScamKeywords = mapOf(
        // Money Demands & Extortion
        "पैसे" to "Urgent Money Demand",
        "पैसा" to "Urgent Money Demand",
        "रुपये" to "Urgent Money Demand",
        "रुपया" to "Urgent Money Demand",
        "पैसे पाठवा" to "Urgent Money Demand",
        "पैसे द्या" to "Urgent Money Demand",
        "पैसे दे" to "Urgent Money Demand",
        "पैसे हवेत" to "Urgent Money Demand",
        "पैसे मागितले" to "Urgent Money Demand",
        "पैसे ट्रान्सफर" to "Urgent Wire Transfer",
        "खात्यात पाठवा" to "Urgent Wire Transfer",
        "गुगल पे" to "Digital Payment Extortion",
        "फोन पे" to "Digital Payment Extortion",
        // Digital Arrest & Police Extortion
        "अटक" to "Digital Arrest / Police Extortion",
        "डिजिटल अटक" to "Digital Arrest Fraud",
        "पोलीस ठाणे" to "Police Impersonation",
        "सीबीआय" to "CBI Impersonation",
        "गुन्हे शाखा" to "Crime Branch Impersonation",
        "तातडीने" to "Forced Urgency Coercion",
        "लगेच" to "Forced Urgency Coercion",
        "ओटीपी" to "OTP / Credential Theft",
        "पासवर्ड" to "Credential Theft",
        "बँक खाते बंद" to "Account Freeze Threat"
    )

    private val hindiScamKeywords = mapOf(
        // Money Demands & Extortion
        "पैसे" to "Urgent Money Demand",
        "पैसा" to "Urgent Money Demand",
        "रुपये" to "Urgent Money Demand",
        "रुपया" to "Urgent Money Demand",
        "पैसे दो" to "Urgent Money Demand",
        "पैसे चाहिए" to "Urgent Money Demand",
        "पैसे मांग" to "Urgent Money Demand",
        "पैसे भेजो" to "Urgent Money Demand",
        "पैसे ट्रांसफर" to "Coerced Money Transfer",
        "कैश" to "Cash Demand",
        "खाते में भेजो" to "Account Transfer Demand",
        "गूगल पे" to "Digital Payment Extortion",
        "फोन पे" to "Digital Payment Extortion",
        "पेटीएम" to "Digital Payment Extortion",
        // Digital Arrest & Coercion
        "डिजिटल अरेस्ट" to "Digital Arrest Fraud",
        "सीबीआई" to "CBI Impersonation",
        "पुलिस" to "Police Impersonation",
        "अरेस्ट वारंट" to "Fake Arrest Warrant",
        "कस्टम्स" to "Customs Department Threat",
        "पार्सल में ड्रग्स" to "Customs Contraband Extortion",
        "ओटीपी" to "OTP Theft",
        "ओटीपी शेयर" to "OTP Theft",
        "अकाउंट ब्लॉक" to "Banking Freeze Scam",
        "तुरंत" to "High-Pressure Coercion",
        "जल्दी" to "High-Pressure Coercion"
    )

    private val englishScamKeywords = mapOf(
        // Money Demands & Extortion (Handles "asking money", "send money", etc.)
        "money" to "Urgent Money Demand",
        "asking money" to "Urgent Money Demand",
        "ask money" to "Urgent Money Demand",
        "ask for money" to "Urgent Money Demand",
        "send money" to "Urgent Money Demand",
        "send me money" to "Urgent Money Demand",
        "give me money" to "Urgent Money Demand",
        "give money" to "Urgent Money Demand",
        "need money" to "Urgent Money Demand",
        "pay money" to "Urgent Money Demand",
        "pay me" to "Urgent Money Demand",
        "transfer money" to "Wire Transfer Coercion",
        "transfer funds" to "Wire Transfer Coercion",
        "wire funds" to "Wire Transfer Coercion",
        "wire money" to "Wire Transfer Coercion",
        "cash" to "Urgent Cash Demand",
        "rupees" to "Currency Demand",
        "send cash" to "Urgent Cash Demand",
        "emergency funds" to "Emergency Bail Extortion",
        "bail money" to "Emergency Bail Extortion",
        "gpay" to "Digital Payment Extortion",
        "phonepe" to "Digital Payment Extortion",
        "paytm" to "Digital Payment Extortion",
        "upi" to "Digital Payment Extortion",
        // Digital Arrest & Legal Threats
        "digital arrest" to "Digital Arrest Fraud",
        "cbi" to "CBI Impersonation",
        "cbi officer" to "CBI Impersonation",
        "police" to "Police Impersonation",
        "police warrant" to "Fake Arrest Warrant",
        "customs parcel" to "Customs Extortion",
        "contraband" to "Contraband / Narcotics Threat",
        "narcotics" to "Contraband / Narcotics Threat",
        "drugs in parcel" to "Contraband / Narcotics Threat",
        "share otp" to "Credential / OTP Theft",
        "otp" to "Credential / OTP Theft",
        "bank verification" to "Banking Fraud",
        "account suspended" to "Account Freeze Threat",
        "immediately" to "High-Pressure Urgency",
        "right now" to "High-Pressure Urgency",
        "urgent" to "High-Pressure Urgency"
    )

    /**
     * Appends live text chunk to ephemeral RAM buffer
     */
    fun appendTranscriptChunk(chunk: String) {
        ephemeralBuffer.append(" ").append(chunk)
    }

    /**
     * Scans the ephemeral transcript for scam patterns on-device
     */
    fun analyzeIntent(): IntentResult {
        val text = ephemeralBuffer.toString().lowercase()
        val matches = mutableListOf<String>()
        var primaryThreat: String? = null

        val dictionary = when (currentLanguage) {
            Language.MARATHI -> marathiScamKeywords
            Language.HINDI -> hindiScamKeywords
            Language.ENGLISH -> englishScamKeywords
        }

        for ((keyword, category) in dictionary) {
            if (text.contains(keyword.lowercase())) {
                matches.add(keyword)
                if (primaryThreat == null) {
                    primaryThreat = category
                }
            }
        }

        // Also cross-check English loanwords commonly used in Indian calls
        if (currentLanguage != Language.ENGLISH) {
            for ((keyword, category) in englishScamKeywords) {
                if (text.contains(keyword.lowercase()) && !matches.contains(keyword)) {
                    matches.add(keyword)
                    if (primaryThreat == null) primaryThreat = category
                }
            }
        }

        val snippet = if (text.length > 80) "..." + text.takeLast(80) else text

        // Calculate substantial risk boost so money demands and coercion immediately elevate the app risk score
        val boost = when {
            matches.any {
                it.contains("money") || it.contains("पैसे") || it.contains("पैसा") ||
                it.contains("रुपये") || it.contains("रुपया") || it.contains("cash") ||
                it.contains("कैश") || it.contains("कॅश") || it.contains("wire") ||
                it.contains("transfer") || it.contains("gpay") || it.contains("phonepe") ||
                it.contains("paytm") || it.contains("upi")
            } -> 85.0
            matches.any { it.contains("arrest") || it.contains("अटक") || it.contains("cbi") || it.contains("police") || it.contains("otp") } -> 80.0
            matches.isNotEmpty() -> 75.0
            else -> 0.0
        }

        return IntentResult(
            isScamSuspected = matches.isNotEmpty(),
            detectedThreatCategory = primaryThreat,
            matchedKeywords = matches,
            currentTranscriptSnippet = snippet.trim(),
            language = currentLanguage,
            riskScoreBoost = boost
        )
    }

    /**
     * ZERO STORAGE GUARANTEE:
     * Completely wipe the ephemeral transcript from memory
     */
    fun purgeMemory() {
        ephemeralBuffer.setLength(0)
    }
}
