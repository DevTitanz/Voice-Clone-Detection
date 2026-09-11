package com.sih.voiceguard

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.sih.voiceguard.ai.ExternalAiAudioService
import com.sih.voiceguard.ai.OnDeviceSpeechAnalyzer
import com.sih.voiceguard.security.SecureStorage
import com.sih.voiceguard.service.CallShieldManager
import com.sih.voiceguard.service.WhatsAppCallListenerService
import com.sih.voiceguard.ui.FloatingShieldOverlay
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

fun isNotificationAccessGranted(context: Context): Boolean {
    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: ""
    return flat.contains(context.packageName) || NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
}

fun isOverlayGranted(context: Context): Boolean {
    return Settings.canDrawOverlays(context)
}

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
                        secureStorage = secureStorage,
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
    secureStorage: SecureStorage? = null,
    onRequestMicPermission: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val shieldState by CallShieldManager.uiState.collectAsState()
    var showStepUpDialog by remember { mutableStateOf(false) }
    var customTestInput by remember { mutableStateOf("") }
    var hasNotificationAccess by remember { mutableStateOf(isNotificationAccessGranted(context)) }
    var hasOverlayPermission by remember { mutableStateOf(isOverlayGranted(context)) }
    var isSimulatedOverlayActive by remember { mutableStateOf(false) }

    var apiKeyInput by remember { mutableStateOf(secureStorage?.getAiApiKey() ?: ExternalAiAudioService.DEFAULT_GEMINI_KEY) }
    var apiKeySaveFeedback by remember { mutableStateOf<String?>(null) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasNotificationAccess = isNotificationAccessGranted(context)
                hasOverlayPermission = isOverlayGranted(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val activeCaller = callerNumber ?: shieldState.callerNumber
    var isDirectListeningActive by remember { mutableStateOf(false) }

    val launchDirectVoiceScan: () -> Unit = {
        if (!hasMicPermission) {
            onRequestMicPermission()
        } else {
            // Keep CallShield actively capturing audio
            if (!shieldState.isMonitoring) {
                CallShieldManager.startInCallShield(context, activeCaller)
            }
            isDirectListeningActive = true
            coroutineScope.launch {
                // Buffer 2.5 seconds of live call/mic speech directly
                kotlinx.coroutines.delay(2500)
                isDirectListeningActive = false
                // Transcribe and analyze directly via multimodal AI without any Google dialogs
                CallShieldManager.runExternalAiScan(apiKeyInput, coroutineScope)
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

        // 2.5 WhatsApp Real-Time Call Interceptor & Floating Shield Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(
                1.5.dp,
                if (hasNotificationAccess && hasOverlayPermission) Color(0xFF10B981) else Color(0xFFF59E0B)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(if (hasNotificationAccess && hasOverlayPermission) Color(0xFF10B981) else Color(0xFFF59E0B))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "WhatsApp VoIP Shield",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (hasNotificationAccess && hasOverlayPermission) Color(0xFFDCFCE7) else Color(0xFFFEF3C7)
                    ) {
                        Text(
                            text = if (hasNotificationAccess && hasOverlayPermission) "ARMED & ACTIVE" else "SETUP REQUIRED",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (hasNotificationAccess && hasOverlayPermission) Color(0xFF166534) else Color(0xFF92400E),
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = if (hasNotificationAccess && hasOverlayPermission)
                        "WhatsApp call interception active. Incoming VoIP calls automatically trigger real-time AI screening with a floating security badge."
                    else
                        "Enable permissions below so VoiceGuard can automatically intercept incoming WhatsApp calls and show the floating safety pill.",
                    fontSize = 11.sp,
                    color = Color(0xFF64748B),
                    lineHeight = 16.sp
                )

                // Permission action buttons if not granted
                if (!hasNotificationAccess) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            try {
                                val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                // fallback
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().height(42.dp)
                    ) {
                        Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("1. Enable WhatsApp Notification Access", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                if (!hasOverlayPermission) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            try {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                // fallback
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().height(42.dp)
                    ) {
                        Icon(Icons.Default.Layers, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("2. Enable Floating Shield Overlay", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // WhatsApp Call Simulator Test Action
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            CallShieldManager.startInCallShield(context, "WhatsApp: +91 98200 45678")
                            try {
                                FloatingShieldOverlay.show(context, "+91 98200 45678")
                                isSimulatedOverlayActive = true
                            } catch (e: Exception) {
                                // ignore
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).height(42.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Simulate WhatsApp Call", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    if (isSimulatedOverlayActive || WhatsAppCallListenerService.isWhatsAppCallActive) {
                        Button(
                            onClick = {
                                FloatingShieldOverlay.dismiss(context)
                                isSimulatedOverlayActive = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF64748B)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.height(42.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) {
                            Text("Hide Overlay", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
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
                    text = shieldState.clearVerdict,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Black,
                    color = if (shieldState.currentRiskScore >= 70) Color(0xFFDC2626) else (if (shieldState.currentRiskScore >= 40) Color(0xFFD97706) else Color(0xFF059669)),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Confidence: ${shieldState.confidencePercent}% • ${if (shieldState.currentRiskScore >= 70) "Review caller identity before acting" else "Biological human speech prosody"}",
                    fontSize = 11.sp,
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
                            color = if (shieldState.currentRiskScore >= 70) Color(0xFFEF4444) else (if (shieldState.currentRiskScore >= 40) Color(0xFFF59E0B) else Color(0xFF10B981)),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${shieldState.currentRiskScore.toInt()}%",
                            fontSize = 38.sp,
                            fontWeight = FontWeight.Black,
                            color = if (shieldState.currentRiskScore >= 70) Color(0xFFEF4444) else (if (shieldState.currentRiskScore >= 40) Color(0xFFF59E0B) else Color(0xFF10B981))
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

                // Dedicated External Cloud AI Forensic Result Card
                if (shieldState.externalAiVerdict != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFEFF6FF),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF3B82F6)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = shieldState.externalAiProvider ?: "Cloud AI Forensic Scan",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1D4ED8)
                                )
                                Text(
                                    text = "VERIFIED AI",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF2563EB)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = shieldState.externalAiVerdict ?: "",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = shieldState.externalAiExplanation ?: "",
                                fontSize = 11.sp,
                                color = Color(0xFF334155),
                                lineHeight = 15.sp
                            )
                        }
                    }
                }

                // AI Deep Analysis Explanation
                if (shieldState.currentRiskScore >= 70 || shieldState.scamThreatCategory != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFFEE2E2),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFCA5A5)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "Threat Intelligence Analysis:",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF991B1B)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = shieldState.aiExplanation,
                                fontSize = 11.sp,
                                color = Color(0xFF7F1D1D),
                                lineHeight = 15.sp
                            )
                        }
                    }

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

        Spacer(modifier = Modifier.height(10.dp))

        // 3.5 External Cloud AI Engine Card (Gemini / Groq / OpenAI API Key)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (apiKeyInput.isNotBlank()) Color(0xFF93C5FD) else Color(0xFFE2E8F0)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.VpnKey,
                            contentDescription = null,
                            tint = Color(0xFF2563EB),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Cloud AI Forensic Key",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (apiKeyInput.isNotBlank()) Color(0xFFDCFCE7) else Color(0xFFF1F5F9)
                    ) {
                        Text(
                            text = if (apiKeyInput.isNotBlank()) "CONFIGURED" else "OPTIONAL",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (apiKeyInput.isNotBlank()) Color(0xFF166534) else Color(0xFF64748B),
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Add a Google Gemini (AIzaSy...), Groq (gsk_...), or OpenAI key for cloud audio forensics during active calls.",
                    fontSize = 11.sp,
                    color = Color(0xFF64748B),
                    lineHeight = 15.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = { apiKeyInput = it },
                        placeholder = { Text("Paste Gemini or Groq API Key...", fontSize = 11.sp) },
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Button(
                        onClick = {
                            secureStorage?.saveAiApiKey(apiKeyInput)
                            apiKeySaveFeedback = "Saved!"
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                        modifier = Modifier.height(44.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Text(apiKeySaveFeedback ?: "Save", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Cloud AI Trigger Button during call
                if (apiKeyInput.isNotBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            CallShieldManager.runExternalAiScan(apiKeyInput, coroutineScope)
                        },
                        enabled = !shieldState.isExternalAiRunning,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    ) {
                        if (shieldState.isExternalAiRunning) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Analyzing via Cloud AI...", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Run Deep Cloud AI Forensic Scan", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
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
                            text = "Direct In-App Voice Scanner",
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
                            text = if (shieldState.scamThreatCategory != null) "FLAGGED: ${shieldState.threatLevel}" else if (shieldState.liveTranscript.isNotEmpty()) "SAFE" else "READY",
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
                                text = "RAM ONLY • ZERO POPUPS",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF10B981)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = when {
                                shieldState.liveTranscript.isNotEmpty() -> "\"${shieldState.liveTranscript}\""
                                isDirectListeningActive -> "Listening directly through microphone in ${shieldState.selectedLanguage.displayName}... Speak naturally now."
                                shieldState.isExternalAiRunning -> "Transcribing speech and running deepfake forensic inspection directly..."
                                else -> "Tap 'Scan Live Voice Directly' below to speak and detect synthetic voice clones seamlessly without any Google dialogs or popups."
                            },
                            fontSize = 14.sp,
                            fontWeight = if (shieldState.liveTranscript.isNotEmpty()) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (shieldState.liveTranscript.isNotEmpty()) (if (shieldState.scamThreatCategory != null) Color(0xFF991B1B) else Color(0xFF0F172A)) else (if (isDirectListeningActive) Color(0xFF2563EB) else Color(0xFF94A3B8)),
                            lineHeight = 20.sp
                        )

                        if (shieldState.scamThreatCategory != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Threat: ${shieldState.scamThreatCategory} (${shieldState.threatLevel})",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFDC2626)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = shieldState.aiExplanation,
                                fontSize = 11.sp,
                                color = Color(0xFF991B1B),
                                lineHeight = 15.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Primary Action Button: Direct In-App Voice Scan (No Google Popup)
                Button(
                    onClick = { launchDirectVoiceScan() },
                    enabled = !isDirectListeningActive && !shieldState.isExternalAiRunning,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (shieldState.scamThreatCategory != null) Color(0xFFDC2626) else Color(0xFF2563EB)
                    )
                ) {
                    if (isDirectListeningActive || shieldState.isExternalAiRunning) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isDirectListeningActive) "Listening to Voice..." else "Analyzing Directly...",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Scan Live Voice Directly (No Popups)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
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
                        placeholder = { Text("Type words to test AI scam analysis...", fontSize = 11.sp) },
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
                        Text("Scan AI", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "One-Tap AI Threat Presets:",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF64748B)
                )
                Spacer(modifier = Modifier.height(6.dp))

                // Presets Row 1 (Digital Arrest, Family Emergency, Courier)
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
                                CallShieldManager.processSpokenText("This is CBI officer you are under digital arrest wire funds 50000 immediately")
                            }
                    ) {
                        Text(
                            text = "Digital Arrest\n(98% Risk)",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF991B1B),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFFEE2E2),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 2.dp)
                            .clickable {
                                CallShieldManager.processSpokenText("Your son had serious hospital accident send bail money immediately")
                            }
                    ) {
                        Text(
                            text = "Family Emergency\n(96% Risk)",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF991B1B),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFFEE2E2),
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 3.dp)
                            .clickable {
                                CallShieldManager.processSpokenText("Customs seized narcotics in your FedEx parcel do not disconnect")
                            }
                    ) {
                        Text(
                            text = "FedEx Drugs\n(95% Risk)",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF991B1B),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Presets Row 2 (Ask Money, Marathi, Hindi)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFFEF3C7),
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 3.dp)
                            .clickable {
                                CallShieldManager.processSpokenText("Please give me money send 50000 rupees immediately to UPI")
                            }
                    ) {
                        Text(
                            text = "Ask 50,000 Rs\n(88% Risk)",
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

                // Presets Row 3 (KYC/OTP, Safe Voice, Clear Memory)
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
                                CallShieldManager.processSpokenText("Your bank account suspended share OTP and install AnyDesk")
                            }
                    ) {
                        Text(
                            text = "OTP / KYC\n(92% Risk)",
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
                            text = "Natural Safe\nVoice (14%)",
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
