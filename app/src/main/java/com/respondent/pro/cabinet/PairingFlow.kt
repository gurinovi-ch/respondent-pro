package com.respondent.pro.cabinet

/**
 * Привязка + немедленное применение параметров (включая PIN) после успеха.
 * Refresh — best-effort: его ошибка не отменяет уже сохранённую привязку.
 */
class PairingFlow(
    private val binder: CabinetBinder,
    private val onPaired: suspend () -> Unit = {},
) {
    suspend fun pair(rawCode: String): PairOutcome {
        val outcome = binder.pair(rawCode)
        if (outcome == PairOutcome.PAIRED) {
            runCatching { onPaired() }
        }
        return outcome
    }
}
