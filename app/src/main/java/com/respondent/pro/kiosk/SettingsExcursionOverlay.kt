package com.respondent.pro.kiosk

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.respondent.pro.MainActivity

/**
 * Плавающая кнопка «◀ В RESPONDENT.PRO» поверх системных настроек (spec §6).
 * Показывается только при наличии разрешения SYSTEM_ALERT_WINDOW.
 */
object SettingsExcursionOverlay {

    private const val TAG = "ExcursionOverlay"
    private const val OVERLAY_TEXT = "◀ В RESPONDENT.PRO"

    private var currentView: View? = null

    fun canDraw(context: Context): Boolean = Settings.canDrawOverlays(context)

    /** Открывает системный экран выдачи разрешения «Поверх других окон». */
    fun openPermissionScreen(context: Context) {
        try {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "openPermissionScreen failed", e)
        }
    }

    fun show(context: Context) {
        if (currentView != null) return
        if (!canDraw(context)) {
            Log.i(TAG, "Overlay permission not granted — button not shown")
            return
        }
        try {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val density = context.resources.displayMetrics.density
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= 26) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
                },
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.END
                x = (16 * density).toInt()
                y = (96 * density).toInt() // выше системной панели навигации
            }
            val view = TextView(context).apply {
                text = OVERLAY_TEXT
                setBackgroundColor(0xE6000000.toInt())
                setTextColor(Color.WHITE)
                setPadding(
                    (14 * density).toInt(), (10 * density).toInt(),
                    (14 * density).toInt(), (10 * density).toInt()
                )
                textSize = 15f
                setOnClickListener {
                    try {
                        val back = Intent(context, MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        }
                        context.startActivity(back)
                    } catch (e: Exception) {
                        Log.e(TAG, "return to app failed", e)
                    }
                    hide(context)
                }
            }
            wm.addView(view, params)
            currentView = view
            Log.i(TAG, "Overlay shown")
        } catch (e: Exception) {
            Log.e(TAG, "show failed", e)
        }
    }

    fun hide(context: Context) {
        val view = currentView ?: return
        try {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            wm.removeView(view)
        } catch (e: Exception) {
            Log.e(TAG, "hide failed", e)
        }
        currentView = null
        Log.i(TAG, "Overlay hidden")
    }
}
