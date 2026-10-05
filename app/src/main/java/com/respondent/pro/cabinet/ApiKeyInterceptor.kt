package com.respondent.pro.cabinet

import okhttp3.Interceptor
import okhttp3.Response

/** Ставит X-API-Key на все запросы API кабинета, если планшет привязан. */
class ApiKeyInterceptor(private val storage: BindingStorage) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val binding = storage.read()
        val request = chain.request().newBuilder()
        if (binding != null) {
            request.header("X-API-Key", binding.apiKey)
        }
        return chain.proceed(request.build())
    }
}
