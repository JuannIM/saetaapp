package com.saetasaldo.app.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saetasaldo.app.domain.model.BalanceRecord
import com.saetasaldo.app.domain.model.SaetaCard
import com.saetasaldo.app.domain.model.TripEstimate
import com.saetasaldo.app.domain.repository.CardRepository
import com.saetasaldo.app.domain.usecase.CalculateRemainingTripsUseCase
import com.saetasaldo.app.domain.usecase.GetCardBalanceUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CardDetailViewModel(
    val cardId: String,
    private val repository: CardRepository,
    private val getCardBalanceUseCase: GetCardBalanceUseCase,
    private val calculateRemainingTripsUseCase: CalculateRemainingTripsUseCase = CalculateRemainingTripsUseCase()
) : ViewModel() {

    val card: StateFlow<SaetaCard?> = repository.getAllCards()
        .map { list -> list.firstOrNull { it.id == cardId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val history: StateFlow<List<BalanceRecord>> = repository.getHistoryForCard(cardId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _fare = MutableStateFlow(690.0)
    val fare: StateFlow<Double> = _fare.asStateFlow()

    val tripEstimate: StateFlow<TripEstimate?> = combine(card, fare) { currentCard, currentFare ->
        currentCard?.currentBalance?.let { balance ->
            calculateRemainingTripsUseCase(balance, currentFare, currentCard.type)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun updateFare(newFare: Double) {
        if (newFare > 0.0) {
            _fare.value = newFare
        }
    }

    fun refreshBalance(manualCaptcha: String? = null) {
        val currentCard = card.value ?: return
        viewModelScope.launch {
            _isRefreshing.value = true
            _errorMessage.value = null
            try {
                val result = getCardBalanceUseCase(currentCard.cardNumber, manualCaptcha)
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

    fun setAsFavorite() {
        viewModelScope.launch {
            repository.setFavorite(cardId)
        }
    }

    fun deleteCard(onDeleted: () -> Unit) {
        val currentCard = card.value ?: return
        viewModelScope.launch {
            repository.deleteCard(currentCard)
            onDeleted()
        }
    }

    fun updateCardName(newName: String) {
        val currentCard = card.value ?: return
        val trimmed = newName.trim()
        if (trimmed.isNotBlank()) {
            viewModelScope.launch {
                repository.saveCard(currentCard.copy(name = trimmed))
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
