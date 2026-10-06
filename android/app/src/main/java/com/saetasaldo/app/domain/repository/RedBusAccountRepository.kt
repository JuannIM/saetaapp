package com.saetasaldo.app.domain.repository

import com.saetasaldo.app.domain.model.PendingLoad
import com.saetasaldo.app.domain.model.RedBusAccountCard
import com.saetasaldo.app.domain.model.RedBusSessionState
import kotlinx.coroutines.flow.StateFlow

interface RedBusAccountRepository {
    val sessionState: StateFlow<RedBusSessionState>
    suspend fun checkSession(): Result<RedBusSessionState>
    suspend fun getLinkedCards(): Result<List<RedBusAccountCard>>
    suspend fun getPendingLoads(internalNumber: String): Result<List<PendingLoad>>
    suspend fun disconnect()
}
