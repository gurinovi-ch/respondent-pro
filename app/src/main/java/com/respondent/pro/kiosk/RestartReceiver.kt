package com.respondent.pro.kiosk

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import com.respondent.pro.MainActivity

@EntryPoint
@InstallIn(SingletonComponent::class)
interface KioskEntryPoint {
    fun kioskManager(): KioskManager
}

/**
 * Приём alarm'ов стража и разового перезапуска (spec §3).
 * ACTION_WATCHDOG — всегда перепланирует следующий шаг (цепочка не рвётся),
 * и перезапускает приложение, если оно в фоне и не идёт excursion.
 */
class RestartReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "RestartReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            WatchdogScheduler.ACTION_WATCHDOG -> {
                WatchdogScheduler.scheduleNext(context)
                maybeRestart(context)
            }
            WatchdogScheduler.ACTION_RESTART -> maybeRestart(context)
        }
    }

    private fun maybeRestart(context: Context) {
        val kiosk = EntryPointAccessors
            .fromApplication(context.applicationContext, KioskEntryPoint::class.java)
            .kioskManager()
        if (!kiosk.shouldRestart()) {
            // R8: прошивка подавляет Log.d — verification-строки пишем через Log.i
            Log.i(TAG, "Skip restart (foreground=${kiosk.isForeground}, excursion=${kiosk.excursionActive})")
            return
        }
        // R8: прошивка подавляет Log.d — verification-строки пишем через Log.i
        Log.i(TAG, "Restarting MainActivity")
        try {
            val launch = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            context.startActivity(launch)
        } catch (e: Exception) {
            // Review Focus №1: на Android 10+ фоновый старт может быть заблокирован —
            // логируем; следующая попытка через 60 сек (цепочка alarm'ов переживает отказ)
            Log.e(TAG, "startActivity failed (background start restriction?)", e)
        }
    }
}
