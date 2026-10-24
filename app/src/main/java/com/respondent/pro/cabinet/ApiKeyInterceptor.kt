package com.respondent.pro.cabinet

import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response

/** Ставит X-API-Key на все запросы API кабинета, если планшет привязан. */
class ApiKeyInterceptor(private val storage: BindingStorage) : Interceptor {
    companion object {
        private const val TAG = "ApiKeyInterceptor"
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val binding = storage.read()
        val request = chain.request().newBuilder()
        if (binding != null) {
            request.header("X-API-Key", binding.apiKey)
        }
        val response = chain.proceed(request.build())
        // Ключ погашен в кабинете («Сбросить доступ») или заменён: привязки
        // больше нет. Чистим локально, чтобы UI не показывал «Привязан», а
        // планшет вернулся в Free-режим — как и при ошибке отправки отзыва.
        // 403 НЕ трогаем: пауза биллинга и запреты сервера не отзывают ключ.
        if (binding != null && response.code == 401) {
            Log.w(TAG, "API key rejected (401) — unbinding")
            storage.clear()
        }
        return response
    }
}
