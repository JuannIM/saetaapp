package com.saetasaldo.app.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saetasaldo.app.domain.model.BalanceRecord
import com.saetasaldo.app.domain.model.PendingLoad
import com.saetasaldo.app.domain.model.RedBusSessionState
import com.saetasaldo.app.domain.model.SaetaCard
import com.saetasaldo.app.domain.model.TripEstimate
import com.saetasaldo.app.domain.repository.CardRepository
import com.saetasaldo.app.domain.repository.RedBusAccountRepository
import com.saetasaldo.app.domain.usecase.CalculateRemainingTripsUseCase
import com.saetasaldo.app.domain.usecase.GetCardBalanceUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.saetasaldo.app.data.remote.NetworkClient
import com.saetasaldo.app.data.remote.api.SaetaApiService

class CardDetailViewModel(
    val cardId: String,
    private val repository: CardRepository,
    private val getCardBalanceUseCase: GetCardBalanceUseCase,
    private val calculateRemainingTripsUseCase: CalculateRemainingTripsUseCase = CalculateRemainingTripsUseCase(),
    private val apiService: SaetaApiService? = null,
    private val accountRepository: RedBusAccountRepository? = null
) : ViewModel() {

    private val _pendingLoads = MutableStateFlow<List<PendingLoad>?>(null)
    val pendingLoads: StateFlow<List<PendingLoad>?> = _pendingLoads.asStateFlow()

    val card: StateFlow<SaetaCard?> = repository.getAllCards()
        .map { list -> list.firstOrNull { it.id == cardId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        // Pending virtual loads only exist for account-linked cards.
        if (accountRepository != null) {
            viewModelScope.launch {
                combine(
                    card.map { it?.internalNumber },
                    accountRepository.sessionState
                ) { number, state -> number to state }
                    .distinctUntilChanged()
                    .collect { (number, state) ->
                        when {
                            number == null -> _pendingLoads.value = null
                            state == RedBusSessionState.Connected ->
                                _pendingLoads.value = accountRepository.getPendingLoads(number).getOrNull()
                        }
                    }
            }
        }
    }

    val history: StateFlow<List<BalanceRecord>> = repository.getHistoryForCard(cardId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _fare = MutableStateFlow(1450.0)
    val fare: StateFlow<Double> = _fare.asStateFlow()

    val tripEstimate: StateFlow<TripEstimate?> = combine(card, fare) { currentCard, currentFare ->
        currentCard?.currentBalance?.let { balance ->
            calculateRemainingTripsUseCase(balance, currentFare)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _showFallbackCaptchaDialog = MutableStateFlow(false)
    val showFallbackCaptchaDialog: StateFlow<Boolean> = _showFallbackCaptchaDialog.asStateFlow()

    private val _captchaBitmap = MutableStateFlow<Bitmap?>(null)
    val captchaBitmap: StateFlow<Bitmap?> = _captchaBitmap.asStateFlow()

    fun updateFare(newFare: Double) {
        if (newFare > 0.0) {
            _fare.value = newFare
        }
    }

    fun triggerManualCaptcha() {
        _showFallbackCaptchaDialog.value = true
        loadCaptchaBitmap()
    }

    fun dismissCaptchaDialog() {
        _showFallbackCaptchaDialog.value = false
    }

    fun loadCaptchaBitmap() {
        viewModelScope.launch {
            _captchaBitmap.value = null
            try {
                val service = apiService ?: NetworkClient.apiService
                val response = service.getCaptchaImage()
                if (response.isSuccessful) {
                    response.body()?.byteStream()?.use { stream ->
                        _captchaBitmap.value = BitmapFactory.decodeStream(stream)
                    }
                } else {
                    response.errorBody()?.close()
                }
            } catch (_: Exception) {}
        }
    }

    fun submitManualCaptcha(code: String) {
        _showFallbackCaptchaDialog.value = false
        refreshBalance(code)
    }

    fun refreshBalance(manualCaptcha: String? = null) {
        val currentCard = card.value ?: return
        viewModelScope.launch {
            _isRefreshing.value = true
            _errorMessage.value = null
            try {
                val result = getCardBalanceUseCase(currentCard.cardNumber, manualCaptcha)
                if (result.isFailure) {
                    val errorMsg = result.exceptionOrNull()?.localizedMessage ?: "Error al actualizar saldo"
                    _errorMessage.value = errorMsg
                    if (manualCaptcha == null && (errorMsg.contains("captcha", ignoreCase = true) || errorMsg.contains("reintentos", ignoreCase = true))) {
                        triggerManualCaptcha()
                    }
                }
            } catch (e: Exception) {
                val errorMsg = e.localizedMessage ?: "Error al actualizar saldo"
                _errorMessage.value = errorMsg
                if (manualCaptcha == null && (errorMsg.contains("captcha", ignoreCase = true) || errorMsg.contains("reintentos", ignoreCase = true))) {
                    triggerManualCaptcha()
                }
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

    fun updateCard(newName: String, colorArgb: Int?) {
        val currentCard = card.value ?: return
        viewModelScope.launch {
            repository.saveCard(
                currentCard.copy(
                    name = newName.trim().ifBlank { currentCard.name },
                    colorArgb = colorArgb
                )
            )
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
