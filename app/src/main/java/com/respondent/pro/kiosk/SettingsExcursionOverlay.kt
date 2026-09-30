package com.respondent.pro.kiosk

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.TextPaint
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import androidx.core.content.ContextCompat
import com.respondent.pro.MainActivity
import com.respondent.pro.R
import kotlin.math.ceil

/**
 * Вертикальный ярлык «RESPONDENT.PRO» поверх системных настроек (spec §6).
 * Плашка справа по центру экрана: фон primary (#178776), белый текст,
 * читается снизу вверх. Показывается только при разрешении SYSTEM_ALERT_WINDOW.
 */
object SettingsExcursionOverlay {

    private const val TAG = "ExcursionOverlay"
    private const val OVERLAY_TEXT = "RESPONDENT.PRO"

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

    /**
     * Рендерит горизонтальную плашку в bitmap и поворачивает на −90°
     * (текст снизу вверх). Поворот самой View обрезается окном WindowManager
     * по не-повёрнутым границам поверхности, поэтому текст готовим заранее —
     * окно создаётся ровно под размер итоговой плашки, мёртвых зон нет.
     */
    private fun renderVerticalTab(context: Context): Bitmap {
        val res = context.resources
        val density = res.displayMetrics.density
        val scale = res.displayMetrics.scaledDensity
        val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
            textSize = 15f * scale
            color = Color.WHITE
            typeface = Typeface.DEFAULT
        }
        val fm = paint.fontMetrics
        val padX = (14 * density)
        val padY = (10 * density)
        val w = ceil(paint.measureText(OVERLAY_TEXT)).toInt() + (2 * padX).toInt()
        val h = (fm.descent - fm.ascent).toInt() + (2 * padY).toInt()

        val src = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(src).apply {
            drawColor(ContextCompat.getColor(context, R.color.primary))
            drawText(
                OVERLAY_TEXT,
                padX,
                h / 2f - (fm.descent + fm.ascent) / 2f,
                paint
            )
        }
        val matrix = Matrix().apply { postRotate(-90f) }
        return Bitmap.createBitmap(src, 0, 0, w, h, matrix, true)
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
                // Справа, вертикальный центр экрана
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                x = (16 * density).toInt()
                y = 0
            }
            val view = ImageView(context).apply {
                setImageBitmap(renderVerticalTab(context))
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
