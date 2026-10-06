package com.saetasaldo.app.ui.cards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saetasaldo.app.domain.model.SaetaCard
import com.saetasaldo.app.domain.repository.CardRepository
import com.saetasaldo.app.domain.usecase.GetCardBalanceUseCase
import com.saetasaldo.app.domain.usecase.RefreshAllBalancesUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class CardsViewModel(
    private val repository: CardRepository,
    private val getCardBalanceUseCase: GetCardBalanceUseCase,
    private val refreshAllBalancesUseCase: RefreshAllBalancesUseCase? = null
) : ViewModel() {

    val cards: StateFlow<List<SaetaCard>> = repository.getAllCards()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun refreshAllBalances() {
        viewModelScope.launch {
            _isRefreshing.value = true
            _errorMessage.value = null
            try {
                val coordinator = refreshAllBalancesUseCase
                if (coordinator != null) {
                    val result = coordinator(cards.value)
                    if (result.failures.isNotEmpty()) {
                        _errorMessage.value = result.failures.values.first().message
                    }
                } else {
                    // Legacy path until the coordinator is wired in (Task 11).
                    val currentCards = cards.value
                    for ((index, card) in currentCards.withIndex()) {
                        if (index > 0) {
                            kotlinx.coroutines.delay(800) // Polite pacing to prevent bot-flagging
                        }
                        val result = getCardBalanceUseCase(card.cardNumber)
                        if (result.isFailure) {
                            _errorMessage.value = result.exceptionOrNull()?.localizedMessage
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errorMessage.value = e.localizedMessage ?: "Error al actualizar saldos"
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun refreshCard(cardNumber: String) {
        viewModelScope.launch {
            _isRefreshing.value = true
            _errorMessage.value = null
            try {
                val result = getCardBalanceUseCase(cardNumber)
                if (result.isFailure) {
                    _errorMessage.value = result.exceptionOrNull()?.localizedMessage
                }
            } catch (e: Exception) {
                _errorMessage.value = e.localizedMessage ?: "Error al actualizar saldo"
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun addNewCard(
        name: String,
        cardNumber: String,
        nfcUid: String? = null,
        onComplete: ((Result<SaetaCard>) -> Unit)? = null
    ) {
        viewModelScope.launch {
            _errorMessage.value = null
            try {
                val isFirstCard = cards.value.isEmpty()
                val newCard = SaetaCard(
                    id = UUID.randomUUID().toString(),
                    name = name.ifBlank { "Tarjeta SAETA" },
                    cardNumber = cardNumber.trim(),
                    nfcUid = nfcUid?.trim()?.uppercase(),
                    isFavorite = isFirstCard
                )
                repository.saveCard(newCard)

                // Refresh balance immediately
                val balanceResult = getCardBalanceUseCase(newCard.cardNumber)
                if (balanceResult.isSuccess) {
                    onComplete?.invoke(balanceResult)
                } else {
                    onComplete?.invoke(Result.success(newCard))
                    _errorMessage.value = balanceResult.exceptionOrNull()?.localizedMessage
                }
            } catch (e: Exception) {
                val errorMsg = e.localizedMessage ?: "Error al guardar tarjeta"
                _errorMessage.value = errorMsg
                onComplete?.invoke(Result.failure(e))
            }
        }
    }

    fun deleteCard(card: SaetaCard) {
        viewModelScope.launch {
            repository.deleteCard(card)
        }
    }

    fun setFavorite(cardId: String) {
        viewModelScope.launch {
            repository.setFavorite(cardId)
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
