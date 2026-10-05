package com.respondent.pro.kiosk

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * Цепочка alarm'ов: каждую минуту RestartReceiver проверяет — на экране ли приложение,
 * иначе перезапускает (spec §3). Точность: exact, при отказе (API 31+) — неточный.
 */
object WatchdogScheduler {

    private const val TAG = "WatchdogScheduler"

    const val ACTION_WATCHDOG = "com.respondent.pro.kiosk.ACTION_WATCHDOG"
    const val ACTION_RESTART = "com.respondent.pro.kiosk.ACTION_RESTART"

    const val PERIOD_MS = 60_000L
    const val CRASH_DELAY_MS = 1_000L

    private const val REQUEST_WATCHDOG = 1001
    private const val REQUEST_RESTART = 1002

    enum class AlarmMode { EXACT, INEXACT }

    /** Решалка (TDD): exact-alarm разрешён только с API 31 и только если не отозван. */
    fun decide(sdkInt: Int, canScheduleExact: Boolean): AlarmMode =
        if (sdkInt >= 31 && !canScheduleExact) AlarmMode.INEXACT else AlarmMode.EXACT

    private fun mode(context: Context): AlarmMode {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        // Short-circuit = guard для Android 7-10 (canScheduleExactAlarms появился в API 31)
        val canExact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        return decide(Build.VERSION.SDK_INT, canExact)
    }

    private fun pendingIntent(context: Context, action: String, requestCode: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, RestartReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    /** Следующий шаг периодического стража (вызывается при старте приложения и из receiver'а). */
    fun scheduleNext(context: Context) {
        try {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val trigger = System.currentTimeMillis() + PERIOD_MS
            val pi = pendingIntent(context, ACTION_WATCHDOG, REQUEST_WATCHDOG)
            val alarmMode = mode(context)
            if (alarmMode == AlarmMode.EXACT) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
            }
            // R8: прошивка подавляет Log.d — verification-строки пишем через Log.i
            Log.i(TAG, "Watchdog armed ($alarmMode)")
        } catch (e: Exception) {
            Log.e(TAG, "scheduleNext failed", e)
        }
    }

    /** Разовый перезапуск после крэша (spec §3: CrashHandler → ~1 сек). */
    fun scheduleRestart(context: Context, delayMs: Long = CRASH_DELAY_MS) {
        try {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val trigger = System.currentTimeMillis() + delayMs
            val pi = pendingIntent(context, ACTION_RESTART, REQUEST_RESTART)
            if (mode(context) == AlarmMode.EXACT) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
            }
            // R8: прошивка подавляет Log.d — verification-строки пишем через Log.i
            Log.i(TAG, "Restart alarm in ${delayMs}ms")
        } catch (e: Exception) {
            Log.e(TAG, "scheduleRestart failed", e)
        }
    }
}
