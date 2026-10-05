package com.respondent.pro.cabinet

import retrofit2.HttpException
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

enum class PairOutcome { PAIRED, BUSY, INVALID_CODE, NETWORK_ERROR, REVOKED }

/**
 * Нормализация ввода: убирает пробелы/дефисы, верхний регистр.
 * Алфавит обязан совпадать с серверным (TabletsService.ALPHABET):
 * 2-9 A-H J K M N P-Z — без 0/1/I/L/O.
 */
fun normalizePairingCode(raw: String): String? {
    val cleaned = raw.filter { it.isLetterOrDigit() }.uppercase()
    if (cleaned.length != 8) return null
    val allowed = cleaned.all {
        it in '2'..'9' || it in 'A'..'H' || it in 'J'..'K' || it in 'M'..'N' || it in 'P'..'Z'
    }
    return if (allowed) cleaned else null
}

/**
 * Логика привязки без Android-зависимостей (тестируется юнитами).
 * Хранение и HTTP — инъекцией.
 */
class CabinetBinder(
    private val storage: BindingStorage,
    private val api: CabinetApi
) {
    @Volatile
    private var busy = false

    fun currentBinding(): StoredBinding? = storage.read()

    /** Привязка. Повторный вызов во время выполнения → BUSY (защита от двойного тапа). */
    suspend fun pair(rawCode: String): PairOutcome {
        if (busy) return PairOutcome.BUSY
        busy = true
        return try {
            val code = normalizePairingCode(rawCode)
                ?: return PairOutcome.INVALID_CODE
            val r = api.pair(PairRequest(code))
            storage.write(
                StoredBinding(r.apiKey, r.tabletId, r.organizationId, r.organizationName, r.pointName)
            )
            PairOutcome.PAIRED
        } catch (e: HttpException) {
            when (e.code()) {
                401, 403 -> { storage.clear(); PairOutcome.REVOKED }
                400, 404 -> PairOutcome.INVALID_CODE
                else -> PairOutcome.NETWORK_ERROR
            }
        } catch (e: IOException) {
            PairOutcome.NETWORK_ERROR
        } catch (e: CancellationException) {
            throw e // отмена корутины — не ошибка привязки
        } catch (e: Exception) {
            // Кривой ответ сервера (JsonSyntaxException и т.п.) не должен ронять киоск
            PairOutcome.NETWORK_ERROR
        } finally {
            busy = false
        }
    }

    /** Локальная отвязка (серверный revoke — в кабинете). */
    fun unbindLocal() {
        storage.clear()
    }

    /**
     * Разбор ошибки авторизованного (X-API-Key) запроса.
     * 401/403 → ключ отозван: чистим локальное состояние, возвращаем true.
     * Сетевые и прочие ошибки состоянию не мешают.
     */
    fun isRevoked(httpCode: Int): Boolean {
        if (httpCode == 401 || httpCode == 403) {
            storage.clear()
            return true
        }
        return false
    }
}
