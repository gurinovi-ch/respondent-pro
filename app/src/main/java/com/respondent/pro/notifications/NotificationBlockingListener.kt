package com.respondent.pro.notifications

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

/**
 * Блокировка всех уведомлений (системных и других приложений: СМС, обновления).
 * Device Owner не имеет API запрета уведомлений — отменяем каждое сразу после
 * публикации. Доступ выдаётся разово: `cmd notification allow_listener …`
 * (команда есть в списке ADB-настроек киоска).
 */
class NotificationBlockingListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        try {
            cancelNotification(sbn.key)
            Log.i(TAG, "cancelled: ${sbn.packageName}/${sbn.id}")
        } catch (e: Exception) {
            Log.e(TAG, "cancel failed: ${sbn.packageName}", e)
        }
    }

    companion object {
        private const val TAG = "NotificationBlocker"
    }
}
