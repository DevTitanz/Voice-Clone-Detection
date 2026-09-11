package com.sih.voxshield.ui

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.sih.voxshield.MainActivity
import com.sih.voxshield.service.CallShieldManager
import kotlinx.coroutines.*

/**
 * FloatingShieldOverlay:
 * Floating on-screen security badge displayed directly over the WhatsApp in-call screen.
 * Provides instant caller risk verification without leaving the WhatsApp app.
 */
object FloatingShieldOverlay {
    private const val TAG = "FloatingShieldOverlay"
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var coroutineScope: CoroutineScope? = null

    fun show(context: Context, callerName: String) {
        if (!Settings.canDrawOverlays(context)) {
            Log.w(TAG, "SYSTEM_ALERT_WINDOW permission not granted. Cannot show overlay.")
            return
        }

        if (overlayView != null) {
            updateCaller(callerName)
            return
        }

        val appContext = context.applicationContext
        windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 30
            y = 140
        }

        val container = LinearLayout(appContext).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 16, 24, 16)

            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#0F172A")) // Dark sleek container
                cornerRadius = 32f
                setStroke(3, Color.parseColor("#10B981")) // Green border initially
            }
            background = bg
        }

        val titleView = TextView(appContext).apply {
            text = "VoxShield AI: $callerName"
            setTextColor(Color.WHITE)
            textSize = 12f
            paint.isFakeBoldText = true
        }

        val riskBadge = TextView(appContext).apply {
            text = "AI RISK: 14% • SAFE"
            setTextColor(Color.parseColor("#10B981"))
            textSize = 10f
            paint.isFakeBoldText = true
            setPadding(0, 4, 0, 0)
        }

        val tapHint = TextView(appContext).apply {
            text = "Tap to open Call Shield"
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 8f
            setPadding(0, 2, 0, 0)
        }

        container.addView(titleView)
        container.addView(riskBadge)
        container.addView(tapHint)

        // Make overlay draggable across screen
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isClick = true

        container.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isClick = true
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = (event.rawX - initialTouchX).toInt()
                    val deltaY = (event.rawY - initialTouchY).toInt()
                    if (Math.abs(deltaX) > 10 || Math.abs(deltaY) > 10) {
                        isClick = false
                    }
                    layoutParams.x = initialX - deltaX
                    layoutParams.y = initialY + deltaY
                    try {
                        windowManager?.updateViewLayout(container, layoutParams)
                    } catch (e: Exception) {
                        // ignore
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (isClick) {
                        val intent = Intent(appContext, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            putExtra("IS_WHATSAPP_CALL", true)
                            putExtra("CALLER_NUMBER", "WhatsApp: $callerName")
                        }
                        appContext.startActivity(intent)
                    }
                    true
                }
                else -> false
            }
        }

        try {
            windowManager?.addView(container, layoutParams)
            overlayView = container
            Log.i(TAG, "Floating shield overlay successfully attached to window.")

            // Observe live risk changes from CallShieldManager
            coroutineScope?.cancel()
            val scope = CoroutineScope(Dispatchers.Main + Job())
            coroutineScope = scope

            scope.launch {
                CallShieldManager.uiState.collect { state ->
                    val isScam = state.currentRiskScore >= 70
                    val isScreening = state.clearVerdict.contains("SCREENING", ignoreCase = true)

                    val badgeColor = when {
                        isScam -> Color.parseColor("#EF4444")
                        isScreening -> Color.parseColor("#3B82F6")
                        else -> Color.parseColor("#10B981")
                    }

                    val badgeText = when {
                        isScam -> "AI CLONE: ${state.currentRiskScore.toInt()}% • THREAT"
                        isScreening -> state.clearVerdict
                        else -> "HEALTHY CALL: ${state.currentRiskScore.toInt()}% • SAFE"
                    }

                    riskBadge.text = badgeText
                    riskBadge.setTextColor(badgeColor)

                    val bg = container.background as? GradientDrawable
                    bg?.setStroke(3, badgeColor)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed adding overlay view: ${e.message}")
        }
    }

    fun updateCaller(callerName: String) {
        val container = overlayView as? LinearLayout ?: return
        val titleView = container.getChildAt(0) as? TextView
        titleView?.text = "VoxShield AI: $callerName"
    }

    fun dismiss(context: Context) {
        coroutineScope?.cancel()
        coroutineScope = null

        overlayView?.let { view ->
            try {
                windowManager?.removeView(view)
                Log.i(TAG, "Floating shield overlay removed.")
            } catch (e: Exception) {
                Log.w(TAG, "Error removing overlay: ${e.message}")
            }
        }
        overlayView = null
        windowManager = null
    }
}
