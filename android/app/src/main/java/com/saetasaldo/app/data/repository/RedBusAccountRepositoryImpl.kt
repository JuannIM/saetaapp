package com.saetasaldo.app.data.repository

import com.saetasaldo.app.data.remote.RedBusContractException
import com.saetasaldo.app.data.remote.RedBusHttpException
import com.saetasaldo.app.data.remote.RedBusNetworkException
import com.saetasaldo.app.data.remote.RedBusNotConnectedException
import com.saetasaldo.app.data.remote.RedBusSessionExpiredException
import com.saetasaldo.app.data.remote.api.RedBusAccountApiService
import com.saetasaldo.app.data.remote.cookie.WebViewCookieJar
import com.saetasaldo.app.data.remote.dto.toDomainCards
import com.saetasaldo.app.domain.model.RedBusAccountCard
import com.saetasaldo.app.domain.model.RedBusSessionState
import com.saetasaldo.app.domain.repository.RedBusAccountRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException

class RedBusAccountRepositoryImpl(
    private val apiService: RedBusAccountApiService,
    private val cookieJar: WebViewCookieJar
) : RedBusAccountRepository {

    private val _sessionState = MutableStateFlow<RedBusSessionState>(RedBusSessionState.Unknown)
    override val sessionState: StateFlow<RedBusSessionState> = _sessionState.asStateFlow()

    override suspend fun checkSession(): Result<RedBusSessionState> {
        val priorState = when (_sessionState.value) {
            RedBusSessionState.Checking -> RedBusSessionState.Unknown
            else -> _sessionState.value
        }
        _sessionState.value = RedBusSessionState.Checking
        return try {
            val response = apiService.getLoggedUser()
            if (!response.isSuccessful) {
                response.errorBody()?.close()
                _sessionState.value = priorState
                Result.failure(RedBusHttpException(response.code()))
            } else {
                when (response.body()?.error) {
                    0 -> {
                        _sessionState.value = RedBusSessionState.Connected
                        Result.success(RedBusSessionState.Connected)
                    }
                    1, 99 -> {
                        _sessionState.value = RedBusSessionState.Disconnected
                        Result.success(RedBusSessionState.Disconnected)
                    }
                    else -> {
                        _sessionState.value = priorState
                        Result.failure(RedBusContractException())
                    }
                }
            }
        } catch (e: CancellationException) {
            _sessionState.value = priorState
            throw e
        } catch (e: IOException) {
            _sessionState.value = priorState
            Result.failure(RedBusNetworkException(e))
        } catch (e: Exception) {
            _sessionState.value = priorState
            Result.failure(RedBusContractException(e))
        }
    }

    override suspend fun getLinkedCards(): Result<List<RedBusAccountCard>> {
        if (_sessionState.value != RedBusSessionState.Connected) {
            return Result.failure(RedBusNotConnectedException())
        }
        return try {
            val response = apiService.getLinkedCards()
            if (!response.isSuccessful) {
                response.errorBody()?.close()
                Result.failure(RedBusHttpException(response.code()))
            } else {
                val body = response.body()
                when {
                    body == null -> Result.failure(RedBusContractException())
                    body.error == 1 || body.error == 99 -> {
                        _sessionState.value = RedBusSessionState.Disconnected
                        Result.failure(RedBusSessionExpiredException())
                    }
                    body.error == 0 && body.cards != null ->
                        Result.success(body.toDomainCards())
                    else -> Result.failure(RedBusContractException())
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Result.failure(RedBusNetworkException(e))
        } catch (e: Exception) {
            Result.failure(RedBusContractException(e))
        }
    }

    override suspend fun disconnect() {
        try {
            cookieJar.clear()
        } finally {
            _sessionState.value = RedBusSessionState.Disconnected
        }
    }
}
