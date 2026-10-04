package com.respondent.pro.data.remote

import android.util.Log
import com.respondent.pro.cabinet.BindingStorage
import com.respondent.pro.data.local.FeedbackDao
import com.respondent.pro.data.model.Feedback
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Выбирает канал доставки по режиму планшета:
 * привязан к кабинету → только C web (сервер сам релейит в C msg);
 * не привязан → Telegram или e-mail (как раньше, спека §3 п.5).
 */
@Singleton
class FeedbackSender @Inject constructor(
    private val serverChannel: ServerChannel,
    private val localChannel: LocalChannel,
    private val bindingStorage: BindingStorage,
    private val feedbackDao: FeedbackDao,
) {
    companion object {
        private const val TAG = "FeedbackSender"
    }

    // Mutex: защита от конкурентной отправки (дубли при быстрых повторах)
    private val sendMutex = Mutex()

    private fun activeChannel(): FeedbackChannel =
        if (bindingStorage.read() != null) serverChannel else localChannel

    suspend fun send(feedback: Feedback): Boolean {
        return sendMutex.withLock {
            val pending = feedbackDao.getUnsent()
            if (pending.none { it.id == feedback.id }) {
                Log.d(TAG, "Feedback ${feedback.id} already delivered, skipping")
                return@withLock true
            }
            activeChannel().send(feedback)
        }
    }

    suspend fun sendUnsent(): Int {
        return sendMutex.withLock {
            val count = activeChannel().sendUnsent()
            Log.d(TAG, "Retry (${if (bindingStorage.read() != null) "cabinet" else "local"}): sent $count")
            count
        }
    }
}
