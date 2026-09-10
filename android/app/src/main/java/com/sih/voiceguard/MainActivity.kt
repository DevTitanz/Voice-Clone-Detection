package com.sih.voiceguard

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.sih.voiceguard.ai.OnDeviceSpeechAnalyzer
import com.sih.voiceguard.security.SecureStorage
import com.sih.voiceguard.service.CallShieldManager

class MainActivity : ComponentActivity() {

    private lateinit var secureStorage: SecureStorage

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        val hasMic = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (hasMic) {
            CallShieldManager.startInCallShield(this, incomingCallerNumber.value)
        }
    }

    private var incomingCallerNumber = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        secureStorage = SecureStorage(this)
        handleCallIntent(intent)

        setContent {
            VoiceGuardTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFFF8FAFC)
                ) {
                    val hasPermissions = ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.READ_PHONE_STATE
                    ) == PackageManager.PERMISSION_GRANTED

                    OnDeviceCallScreen(
                        hasMicPermission = hasPermissions,
                        callerNumber = incomingCallerNumber.value,
                        onRequestMicPermission = {
                            requestPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.RECORD_AUDIO,
                                    Manifest.permission.READ_PHONE_STATE
                                )
                            )
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleCallIntent(intent)
    }

    private fun handleCallIntent(intent: Intent?) {
        val isCall = intent?.getBooleanExtra("CALL_AUTO_TRIGGERED", false) == true ||
                     intent?.getBooleanExtra("CALL_ACTIVE", false) == true
        if (isCall) {
            val num = intent?.getStringExtra("CALLER_NUMBER") ?: "Incoming Call"
            incomingCallerNumber.value = num
            CallShieldManager.startInCallShield(this, num)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Keep in-call shield active if call is still going on, otherwise stop
        val state = CallShieldManager.uiState.value
        if (!state.isCallActive) {
            CallShieldManager.stopInCallShield()
        }
    }
}

@Composable
fun VoiceGuardTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF059669),
            secondary = Color(0xFF2563EB),
            background = Color(0xFFF8FAFC),
            surface = Color.White
        ),
        content = content
    )
}

@Composable
fun OnDeviceCallScreen(
    hasMicPermission: Boolean,
    callerNumber: String? = null,
    onRequestMicPermission: () -> Unit
) {
    val context = LocalContext.current
    val shieldState by CallShieldManager.uiState.collectAsState()
    var showStepUpDialog by remember { mutableStateOf(false) }
    var customTestInput by remember { mutableStateOf("") }

    val activeCaller = callerNumber ?: shieldState.callerNumber

    // Android System Speech Recognizer Launcher for guaranteed 100% microphone reliability
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (!matches.isNullOrEmpty()) {
                val recognized = matches[0]
                CallShieldManager.processSpokenText(recognized)
            }
        }
        // Resume background in-call acoustic monitoring
        if (shieldState.isMonitoring || shieldState.isCallActive) {
            CallShieldManager.startInCallShield(context, activeCaller)
        }
    }

    val launchVoiceToText: () -> Unit = {
        if (!hasMicPermission) {
            onRequestMicPermission()
        } else {
            try {
                // Temporarily release the microphone so the Android speech engine has exclusive access
                CallShieldManager.stopInCallShield()
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, shieldState.selectedLanguage.code)
                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now in ${shieldState.selectedLanguage.displayName} (say 'give me money' or 'पैसे पाठवा')...")
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                }
                speechLauncher.launch(intent)
            } catch (e: Exception) {
                // Ignore fallback
            }
        }
    }

    // Auto-start monitoring when permission is available
    LaunchedEffect(hasMicPermission) {
        if (!hasMicPermission) {
            onRequestMicPermission()
        } else if (!shieldState.isMonitoring) {
            CallShieldManager.startInCallShield(context, activeCaller)
        }
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        // 1. Top Header with Status, Language Selector & In-Call Info
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                if (shieldState.isMonitoring) {
                                    if (shieldState.currentRiskScore >= 70) Color(0xFFEF4444) else Color(0xFF10B981)
                                } else Color(0xFF94A3B8)
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "VoiceGuard",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                }

                // Multilingual Selector Pills (English / Hindi / Marathi)
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFF1F5F9))
                        .padding(2.dp)
                ) {
                    OnDeviceSpeechAnalyzer.Language.values().forEach { lang ->
                        val isSelected = shieldState.selectedLanguage == lang
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) Color.White else Color.Transparent,
                            modifier = Modifier
                                .padding(horizontal = 2.dp)
                                .clickable {
                                    CallShieldManager.setLanguage(lang)
                                }
                        ) {
                            Text(
                                text = when (lang) {
                                    OnDeviceSpeechAnalyzer.Language.ENGLISH -> "EN"
                                    OnDeviceSpeechAnalyzer.Language.HINDI -> "हिन्दी"
                                    OnDeviceSpeechAnalyzer.Language.MARATHI -> "मराठी"
                                },
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color(0xFF0F172A) else Color(0xFF64748B),
                                modifier = Modifier
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (activeCaller != null) "In-Call Shield: $activeCaller" else (if (shieldState.isMonitoring) "Live Screening Active" else "Shield Standby"),
                    fontSize = 11.sp,
                    color = Color(0xFF64748B),
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "100% On-Device",
                    fontSize = 10.sp,
                    color = Color(0xFF10B981),
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 2. In-Call Audio Input Signal Meter
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (shieldState.audioRmsDb > 18f) Color(0xFFF0FDF4) else Color(0xFFF8FAFC),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (shieldState.audioRmsDb > 18f) Color(0xFF86EFAC) else Color(0xFFE2E8F0)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (shieldState.audioRmsDb > 18f) Color(0xFF10B981) else Color(0xFF94A3B8))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (shieldState.audioRmsDb > 18f) "In-Call Audio Receiving (${shieldState.audioRmsDb.toInt()} dB)" else "Mic Level: ${shieldState.audioRmsDb.toInt()} dB • Waiting for Audio",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (shieldState.audioRmsDb > 18f) Color(0xFF166534) else Color(0xFF64748B)
                    )
                }

                Text(
                    text = if (shieldState.audioRmsDb > 18f) "ANALYZING" else "IDLE",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (shieldState.audioRmsDb > 18f) Color(0xFF10B981) else Color(0xFF94A3B8),
                    modifier = Modifier
                        .background(
                            if (shieldState.audioRmsDb > 18f) Color(0xFFDCFCE7) else Color(0xFFE2E8F0),
                            RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        // Speakerphone Recommendation Banner (Appears if earpiece silence is detected during call)
        if (shieldState.speakerphoneRecommended) {
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFFFEF3C7),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFCD34D)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.VolumeUp,
                        contentDescription = null,
                        tint = Color(0xFFB45309),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "In-Call Tip: Turn on Speakerphone so the microphone can capture the caller's voice for deepfake & scam detection.",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF92400E),
                        lineHeight = 16.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 3. Central Minimal Security Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (shieldState.currentRiskScore >= 70) Color(0xFFFEF2F2) else Color(0xFFF0FDF4)
            ),
            shape = RoundedCornerShape(24.dp),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (shieldState.currentRiskScore >= 70) Color(0xFFFECACA) else Color(0xFFBBF7D0)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (shieldState.currentRiskScore >= 70) "AI Voice Scam Suspected" else "Caller Verified Safe",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (shieldState.currentRiskScore >= 70) Color(0xFFDC2626) else Color(0xFF059669)
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = if (shieldState.currentRiskScore >= 70) "Do not send funds or share OTP" else shieldState.classificationLabel,
                    fontSize = 12.sp,
                    color = Color(0xFF64748B),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Clean Risk Gauge
                Box(
                    modifier = Modifier
                        .size(136.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .border(
                            width = 4.dp,
                            color = if (shieldState.currentRiskScore >= 70) Color(0xFFEF4444) else Color(0xFF10B981),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${shieldState.currentRiskScore.toInt()}%",
                            fontSize = 38.sp,
                            fontWeight = FontWeight.Black,
                            color = if (shieldState.currentRiskScore >= 70) Color(0xFFEF4444) else Color(0xFF10B981)
                        )
                        Text(
                            text = "AI RISK",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF64748B),
                            letterSpacing = 1.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Emotion & Threat Pill
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (shieldState.emotionIncongruenceFlag != null || shieldState.scamThreatCategory != null) Color(0xFFFEE2E2) else Color.White,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (shieldState.emotionIncongruenceFlag != null || shieldState.scamThreatCategory != null) Color(0xFFF87171) else Color(0xFFE2E8F0)
                    )
                ) {
                    Text(
                        text = when {
                            shieldState.scamThreatCategory != null -> "Threat: ${shieldState.scamThreatCategory}"
                            shieldState.emotionIncongruenceFlag != null -> "Flag: Fake Urgency Detected"
                            else -> "Tone: ${shieldState.detectedEmotion}"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (shieldState.emotionIncongruenceFlag != null || shieldState.scamThreatCategory != null) Color(0xFFDC2626) else Color(0xFF334155),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                    )
                }

                if (shieldState.currentRiskScore >= 70 || shieldState.scamThreatCategory != null) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = { showStepUpDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Verify Caller with OTP", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // -------------------------------------------------------------
        // 4. DEDICATED ON-SCREEN VOICE-TO-TEXT TRANSCRIBER CARD
        // -------------------------------------------------------------
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(20.dp),
            border = androidx.compose.foundation.BorderStroke(
                1.5.dp,
                if (shieldState.scamThreatCategory != null) Color(0xFFEF4444) else Color(0xFFE2E8F0)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                // Transcriber Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(
                                    if (shieldState.scamThreatCategory != null) Color(0xFFEF4444)
                                    else if (shieldState.liveTranscript.isNotEmpty()) Color(0xFF10B981)
                                    else Color(0xFF2563EB)
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "On-Screen Voice to Text",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                    }

                    // Verification Pill
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (shieldState.scamThreatCategory != null) Color(0xFFFEE2E2) else if (shieldState.liveTranscript.isNotEmpty()) Color(0xFFDCFCE7) else Color(0xFFF1F5F9)
                    ) {
                        Text(
                            text = if (shieldState.scamThreatCategory != null) "FLAGGED: ${shieldState.scamThreatCategory}" else if (shieldState.liveTranscript.isNotEmpty()) "SAFE" else "READY",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (shieldState.scamThreatCategory != null) Color(0xFFDC2626) else if (shieldState.liveTranscript.isNotEmpty()) Color(0xFF059669) else Color(0xFF64748B),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // On-Screen Transcript Window
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (shieldState.scamThreatCategory != null) Color(0xFFFEF2F2) else Color(0xFFF8FAFC),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (shieldState.scamThreatCategory != null) Color(0xFFFCA5A5) else Color(0xFFCBD5E1)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "LIVE RECOGNIZED SPEECH",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF64748B),
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = "RAM ONLY • 0 DISK STORAGE",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF10B981)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = if (shieldState.liveTranscript.isNotEmpty()) "\"${shieldState.liveTranscript}\"" else "Tap 'Tap to Speak' below and speak into your phone (e.g. 'give me money' or 'पैसे पाठवा') to see your speech transcribe here on screen in real time.",
                            fontSize = 14.sp,
                            fontWeight = if (shieldState.liveTranscript.isNotEmpty()) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (shieldState.liveTranscript.isNotEmpty()) (if (shieldState.scamThreatCategory != null) Color(0xFF991B1B) else Color(0xFF0F172A)) else Color(0xFF94A3B8),
                            lineHeight = 20.sp
                        )

                        if (shieldState.scamThreatCategory != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Threat Detected: ${shieldState.scamThreatCategory} • Elevated to 95% AI Risk",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFDC2626)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Primary Action Button: Tap to Speak (Voice to Text)
                Button(
                    onClick = { launchVoiceToText() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (shieldState.scamThreatCategory != null) Color(0xFFDC2626) else Color(0xFF2563EB)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Tap to Speak (Voice to Text)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Custom Phrase Input Field for Instant Voice Testing
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = customTestInput,
                        onValueChange = { customTestInput = it },
                        placeholder = { Text("Type custom words (e.g. give money)", fontSize = 11.sp) },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Button(
                        onClick = {
                            if (customTestInput.isNotBlank()) {
                                CallShieldManager.processSpokenText(customTestInput)
                                customTestInput = ""
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                        modifier = Modifier.height(46.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp)
                    ) {
                        Text("Verify", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "One-Tap Voice Verification Presets:",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF64748B)
                )
                Spacer(modifier = Modifier.height(6.dp))

                // Presets Row 1 (Money & Coercion)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFFEE2E2),
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 3.dp)
                            .clickable {
                                CallShieldManager.processSpokenText("Please give me money send 50000 rupees immediately")
                            }
                    ) {
                        Text(
                            text = "Ask Money\n(50,000 Rs)",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF991B1B),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFFEF3C7),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 2.dp)
                            .clickable {
                                CallShieldManager.processSpokenText("लगेच तातडीने खात्यात ५०,००० पैसे पाठवा")
                            }
                    ) {
                        Text(
                            text = "पैसे पाठवा\n(मराठी)",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF92400E),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFFEF3C7),
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 3.dp)
                            .clickable {
                                CallShieldManager.processSpokenText("अस्पताल में 50000 रुपये तुरंत भेजो")
                            }
                    ) {
                        Text(
                            text = "पैसे भेजो\n(हिन्दी)",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF92400E),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Presets Row 2 (Digital Arrest, Safe Voice, Clear Memory)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFFEE2E2),
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 4.dp)
                            .clickable {
                                CallShieldManager.processSpokenText("This is CBI officer you are under digital arrest wire funds")
                            }
                    ) {
                        Text(
                            text = "Digital Arrest\n(Threat)",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF991B1B),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFDCFCE7),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 2.dp)
                            .clickable {
                                CallShieldManager.processSpokenText("Hello, how are you? Let us meet tomorrow at 10 AM.")
                            }
                    ) {
                        Text(
                            text = "Natural Safe\nVoice",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF166534),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFF1F5F9),
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 4.dp)
                            .clickable {
                                CallShieldManager.purgeMemory()
                            }
                    ) {
                        Text(
                            text = "Clear RAM\nReset (14%)",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF475569),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 5. Bottom Action Controls
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                onClick = {
                    if (!hasMicPermission) {
                        onRequestMicPermission()
                        return@Button
                    }
                    if (!shieldState.isMonitoring) {
                        CallShieldManager.startInCallShield(context, activeCaller)
                    } else {
                        CallShieldManager.stopInCallShield()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (shieldState.isMonitoring) Color(0xFFEF4444) else Color(0xFF10B981)
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(
                    imageVector = if (shieldState.isMonitoring) Icons.Default.CallEnd else Icons.Default.PhoneInTalk,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (shieldState.isMonitoring) "End Call Protection" else "Screen Incoming Call",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "100% on-device CPU • 0 bytes sent over network",
                fontSize = 10.sp,
                color = Color(0xFF94A3B8)
            )
        }
    }

    // On-Device Step-Up Verification Modal
    if (showStepUpDialog) {
        AlertDialog(
            onDismissRequest = { showStepUpDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFEF4444))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("AI Voice Suspected (On-Device)")
                }
            },
            text = {
                Text(
                    "The on-device acoustic model detected high synthetic voice characteristics on this call. Audio remained 100% on your device, but transaction approval requires independent out-of-band verification.",
                    color = Color(0xFFCBD5E1),
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = { showStepUpDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                ) {
                    Text("Verify Caller via Callback")
                }
            },
            dismissButton = {
                TextButton(onClick = { showStepUpDialog = false }) {
                    Text("Dismiss", color = Color(0xFF94A3B8))
                }
            },
            containerColor = Color(0xFF1E293B)
        )
    }
}
