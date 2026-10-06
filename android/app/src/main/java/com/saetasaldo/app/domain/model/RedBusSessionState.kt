package com.saetasaldo.app.domain.model

sealed interface RedBusSessionState {
    data object Unknown : RedBusSessionState
    data object Checking : RedBusSessionState
    data object Disconnected : RedBusSessionState
    data object Connected : RedBusSessionState
}
