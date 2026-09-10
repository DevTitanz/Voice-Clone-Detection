package com.sih.voiceguard.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.TelephonyManager
import com.sih.voiceguard.MainActivity
import com.sih.voiceguard.service.CallShieldService

/**
 * CallStateReceiver:
 * BroadcastReceiver triggered by Android OS when phone call state changes:
 * - EXTRA_STATE_RINGING: An incoming call is arriving.
 * - EXTRA_STATE_OFFHOOK: Call answered / active. Immediately starts the on-device AI shield.
 * - EXTRA_STATE_IDLE: Call ended / hung up. Immediately terminates shield and purges volatile RAM.
 */
class CallStateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
            val stateStr = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
            val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER) ?: "Incoming Call"

            when (stateStr) {
                TelephonyManager.EXTRA_STATE_RINGING -> {
                    // Incoming call ringing - arm the shield
                }
                TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                    // Call is answered and ongoing:
                    com.sih.voiceguard.service.CallShieldManager.startInCallShield(context, incomingNumber)

                    // 1. Launch CallShieldService
                    val serviceIntent = Intent(context, CallShieldService::class.java).apply {
                        action = CallShieldService.ACTION_START_CALL_SHIELD
                        putExtra(CallShieldService.EXTRA_PHONE_NUMBER, incomingNumber)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }

                    // 2. Open MainActivity to display the real-time AI risk score card
                    val activityIntent = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        putExtra("CALL_AUTO_TRIGGERED", true)
                        putExtra("CALLER_NUMBER", incomingNumber)
                    }
                    context.startActivity(activityIntent)
                }
                TelephonyManager.EXTRA_STATE_IDLE -> {
                    // Call ended / hung up: stop shield and purge volatile RAM
                    com.sih.voiceguard.service.CallShieldManager.stopInCallShield()

                    val stopIntent = Intent(context, CallShieldService::class.java).apply {
                        action = CallShieldService.ACTION_STOP_CALL_SHIELD
                    }
                    context.startService(stopIntent)
                }
            }
        }
    }
}
