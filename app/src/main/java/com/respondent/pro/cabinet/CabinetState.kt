package com.respondent.pro.cabinet

enum class FailKind { INVALID_CODE, NETWORK, REVOKED }

/** Состояние экрана «Привязка к кабинету» (UI + ViewModel). */
sealed class CabinetState {
    object Unbound : CabinetState()
    object Binding : CabinetState()
    data class Bound(val organizationName: String, val pointName: String?) : CabinetState()
    data class Failed(val kind: FailKind) : CabinetState()
}
