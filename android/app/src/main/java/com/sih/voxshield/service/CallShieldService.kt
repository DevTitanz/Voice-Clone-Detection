package com.sih.voxshield.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.sih.voxshield.MainActivity
import kotlinx.coroutines.*

/**
 * CallShieldService:
 * High-priority Foreground Service that activates automatically during incoming phone calls.
 * Delegates 100% on-device acoustic feature extraction to CallShieldManager.
 *
 * PRIVACY GUARANTEE:
 * Audio capture and transcript processing run exclusively in volatile RAM.
 * Upon onDestroy() (call disconnect), memory is completely wiped and audio capture stops.
 */
class CallShieldService : Service() {

    companion object {
        const val CHANNEL_ID = "voxshield_call_shield"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START_CALL_SHIELD = "com.sih.voxshield.action.START_SHIELD"
        const val ACTION_STOP_CALL_SHIELD = "com.sih.voxshield.action.STOP_SHIELD"
        const val EXTRA_PHONE_NUMBER = "extra_phone_number"

        @Volatile
        var isServiceRunning: Boolean = false
            private set
    }

    private var serviceJob: Job? = null
    private var serviceScope: CoroutineScope? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_CALL_SHIELD -> {
                stopShield()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START_CALL_SHIELD, null -> {
                val phoneNumber = intent?.getStringExtra(EXTRA_PHONE_NUMBER) ?: "Unknown Caller"
                startForegroundShield(phoneNumber)
            }
        }
        return START_STICKY
    }

    private fun startForegroundShield(phoneNumber: String) {
        isServiceRunning = true

        val notificationIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("CALL_ACTIVE", true)
            putExtra("CALLER_NUMBER", phoneNumber)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("VoxShield AI: Shield Active")
            .setContentText("Screening incoming call ($phoneNumber) for AI deepfakes • 100% on-device")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        startForeground(NOTIFICATION_ID, notification)

        // Launch unified in-call shield
        CallShieldManager.startInCallShield(this, phoneNumber)

        // Observe detection state and update notification
        serviceJob?.cancel()
        val job = SupervisorJob()
        serviceJob = job
        serviceScope = CoroutineScope(Dispatchers.Main + job)
        serviceScope?.launch {
            CallShieldManager.uiState.collect { state ->
                if (state.isCallActive) {
                    val alertText = if (state.scamThreatCategory != null) {
                        "ALERT: ${state.scamThreatCategory} Detected (${state.currentRiskScore.toInt()}%)"
                    } else {
                        "Screening call: ${state.classificationLabel} (${state.currentRiskScore.toInt()}%)"
                    }

                    val updatedNotification = NotificationCompat.Builder(this@CallShieldService, CHANNEL_ID)
                        .setContentTitle(if (state.currentRiskScore >= 70) "AI Voice Scam Suspected!" else "VoxShield AI: Shield Active")
                        .setContentText(alertText)
                        .setSmallIcon(android.R.drawable.ic_lock_lock)
                        .setContentIntent(pendingIntent)
                        .setOngoing(true)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .build()

                    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    notificationManager.notify(NOTIFICATION_ID, updatedNotification)
                }
            }
        }
    }

    private fun stopShield() {
        isServiceRunning = false
        serviceJob?.cancel()
        serviceJob = null
        CallShieldManager.stopInCallShield()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopShield()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "VoxShield Live Call Shield",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Active while screening incoming calls on-device"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}
