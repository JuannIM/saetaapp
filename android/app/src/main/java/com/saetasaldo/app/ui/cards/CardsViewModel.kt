package com.saetasaldo.app.ui.cards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saetasaldo.app.domain.model.SaetaCard
import com.saetasaldo.app.domain.repository.CardRepository
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
    private val refreshAllBalancesUseCase: RefreshAllBalancesUseCase
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
                val result = refreshAllBalancesUseCase(cards.value)
                if (result.failures.isNotEmpty()) {
                    _errorMessage.value = result.failures.values.first().message
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

    fun addNewCard(
        name: String,
        cardNumber: String,
        nfcUid: String? = null,
        onComplete: ((Result<SaetaCard>) -> Unit)? = null
    ) {
        viewModelScope.launch {
            _errorMessage.value = null
            try {
                val number = cardNumber.trim()
                val uid = nfcUid?.trim()?.uppercase()
                val existing = repository.getCardByNumber(number)
                val card = if (existing != null) {
                    existing.copy(
                        name = name.trim().ifBlank { existing.name },
                        nfcUid = uid ?: existing.nfcUid
                    )
                } else {
                    SaetaCard(
                        id = UUID.randomUUID().toString(),
                        name = name.trim().ifBlank { "Tarjeta SAETA" },
                        cardNumber = number,
                        nfcUid = uid,
                        isFavorite = cards.value.isEmpty()
                    )
                }
                repository.saveCard(card)
                onComplete?.invoke(Result.success(card))
            } catch (e: CancellationException) {
                throw e
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
