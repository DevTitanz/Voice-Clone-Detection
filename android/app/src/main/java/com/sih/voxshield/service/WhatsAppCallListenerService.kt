package com.sih.voxshield.service

import android.app.Notification
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.sih.voxshield.MainActivity
import com.sih.voxshield.ui.FloatingShieldOverlay

/**
 * WhatsAppCallListenerService:
 * Official Android NotificationListenerService that intercepts incoming and ongoing
 * WhatsApp VoIP calls in real time.
 *
 * Supported packages:
 * - com.whatsapp (Standard WhatsApp)
 * - com.whatsapp.w4b (WhatsApp Business)
 */
class WhatsAppCallListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "WhatsAppCallListener"
        private val WHATSAPP_PACKAGES = setOf("com.whatsapp", "com.whatsapp.w4b")

        @Volatile
        var isWhatsAppCallActive: Boolean = false
            private set

        @Volatile
        var currentWhatsAppCaller: String? = null
            private set
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "WhatsAppCallListenerService connected and armed.")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val packageName = sbn.packageName
        if (packageName !in WHATSAPP_PACKAGES) return

        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString() ?: ""
        val category = notification.category

        val isCallCategory = category == Notification.CATEGORY_CALL
        val isCallKeywords = text.contains("call", ignoreCase = true) ||
                             text.contains("कॉल", ignoreCase = true) ||
                             text.contains("calling", ignoreCase = true) ||
                             text.contains("incoming", ignoreCase = true) ||
                             text.contains("ongoing", ignoreCase = true) ||
                             subText.contains("call", ignoreCase = true)

        if (isCallCategory || isCallKeywords) {
            val callerName = if (title.isNotBlank()) title else "WhatsApp Caller"
            Log.i(TAG, "WhatsApp Call Detected! Caller: $callerName, Text: $text")

            isWhatsAppCallActive = true
            currentWhatsAppCaller = callerName

            // 1. Activate CallShieldManager for on-device analysis
            CallShieldManager.startInCallShield(this, "WhatsApp: $callerName")

            // 2. Launch Floating Shield Overlay if overlay permission is granted
            try {
                FloatingShieldOverlay.show(this, callerName)
            } catch (e: Exception) {
                Log.w(TAG, "Could not show overlay: ${e.message}")
            }

            // 3. Bring MainActivity to foreground if desired
            val openAppIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("CALL_AUTO_TRIGGERED", true)
                putExtra("CALLER_NUMBER", "WhatsApp: $callerName")
                putExtra("IS_WHATSAPP_CALL", true)
            }
            startActivity(openAppIntent)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        // User requested: "dont remove overlay".
        // WhatsApp notifications are frequently updated, replaced, or dismissed when
        // a call transitions from ringing to active, or when screen state changes.
        // We keep the overlay active and persistent on screen so monitoring is uninterrupted.
        if (sbn == null) return
        val packageName = sbn.packageName
        if (packageName !in WHATSAPP_PACKAGES) return

        Log.d(TAG, "WhatsApp notification dismissed/updated, keeping FloatingShieldOverlay persistent.")
    }
}