package com.saetasaldo.app.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saetasaldo.app.domain.model.RedBusSessionState
import com.saetasaldo.app.domain.repository.RedBusAccountRepository
import com.saetasaldo.app.domain.usecase.RedBusSyncResult
import com.saetasaldo.app.domain.usecase.SyncRedBusCardsUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RedBusAccountUiState(
    val sessionState: RedBusSessionState = RedBusSessionState.Unknown,
    val isSyncing: Boolean = false,
    val message: String? = null
)

/**
 * Connection state for the optional RedBus account. Only mapped session state is
 * retained: never a username, document, cookie, or raw portal response.
 */
class RedBusAccountViewModel(
    private val accountRepository: RedBusAccountRepository,
    private val syncRedBusCardsUseCase: SyncRedBusCardsUseCase
) : ViewModel() {

    private val _isSyncing = MutableStateFlow(false)
    private val _message = MutableStateFlow<String?>(null)

    val uiState: StateFlow<RedBusAccountUiState> = combine(
        accountRepository.sessionState,
        _isSyncing,
        _message
    ) { sessionState, isSyncing, message ->
        RedBusAccountUiState(sessionState = sessionState, isSyncing = isSyncing, message = message)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RedBusAccountUiState())

    private var verifyJob: Job? = null

    /** Silent one-shot check at app start: refreshes session state, never syncs. */
    fun checkExistingSession() {
        viewModelScope.launch {
            try {
                accountRepository.checkSession()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // A missing or broken session is normal on startup; nothing to surface.
            }
        }
    }

    /** Verifies a WebView login and runs the first sync. Coalesced while in flight. */
    fun verifyLoginAndSync() {
        if (verifyJob?.isActive == true) return
        verifyJob = viewModelScope.launch {
            _isSyncing.value = true
            _message.value = null
            try {
                val sessionResult = accountRepository.checkSession()
                if (sessionResult.getOrNull() != RedBusSessionState.Connected) {
                    // A clean Disconnected is expected while the user is still on
                    // the login page: stay silent and let the next page
                    // completion re-verify. Only surface real check failures.
                    sessionResult.exceptionOrNull()?.let { error ->
                        _message.value = error.message ?: "No se encontró una sesión activa."
                    }
                } else {
                    fetchAndSyncCards { result ->
                        val count = result.syncedCardNumbers.size
                        if (count == 0) {
                            "Sesión verificada."
                        } else {
                            "Sesión verificada. ${syncedCountText(count)}."
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _message.value = e.message ?: "No se encontró una sesión activa."
            } finally {
                _isSyncing.value = false
            }
        }
    }

    /** Manual re-sync of linked cards; only runs while the session is connected. */
    fun sync() {
        if (accountRepository.sessionState.value != RedBusSessionState.Connected) {
            _message.value = "No hay una cuenta de RedBus conectada."
            return
        }
        viewModelScope.launch {
            _isSyncing.value = true
            _message.value = null
            try {
                fetchAndSyncCards { result ->
                    val count = result.syncedCardNumbers.size
                    if (count == 0) {
                        "Sincronización completa: no se encontraron tarjetas."
                    } else {
                        "Sincronización completa: ${syncedCountText(count)}" +
                            "${importedCountText(result.importedCount)}."
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _message.value = e.message ?: "No se pudo completar la sincronización."
            } finally {
                _isSyncing.value = false
            }
        }
    }

    /** Drops the local session; stored cards and history stay untouched. */
    fun disconnect() {
        viewModelScope.launch {
            try {
                accountRepository.disconnect()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // The local session is dropped either way; still confirm to the user.
            }
            _message.value = "Sesión de RedBus cerrada."
        }
    }

    /** Marks the current one-shot message as consumed by the UI. */
    fun consumeMessage() {
        _message.value = null
    }

    private suspend fun fetchAndSyncCards(successMessage: (RedBusSyncResult) -> String) {
        val cards = accountRepository.getLinkedCards().getOrElse { error ->
            // Repository failures already carry user-safe Spanish messages.
            _message.value = error.message ?: "No se pudo sincronizar con RedBus."
            return
        }
        val result = syncRedBusCardsUseCase(cards)
        _message.value = successMessage(result)
    }

    private fun syncedCountText(count: Int): String =
        if (count == 1) "1 tarjeta sincronizada" else "$count tarjetas sincronizadas"

    private fun importedCountText(importedCount: Int): String = when {
        importedCount == 1 -> ", 1 importada"
        importedCount > 1 -> ", $importedCount importadas"
        else -> ""
    }
}
