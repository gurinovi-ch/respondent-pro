package com.respondent.pro.cabinet

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Мгновенный сигнал паузы биллинга для UI — живёт дольше, чем DataStore-запись. */
@Singleton
class BillingPauseSignal @Inject constructor() {
    private val _paused = MutableStateFlow(false)
    val paused: StateFlow<Boolean> = _paused
    fun set(value: Boolean) { _paused.value = value }
}
