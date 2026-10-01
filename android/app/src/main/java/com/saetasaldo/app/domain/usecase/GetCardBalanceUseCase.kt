package com.saetasaldo.app.domain.usecase

import com.saetasaldo.app.domain.model.SaetaCard
import com.saetasaldo.app.domain.repository.CardRepository

class GetCardBalanceUseCase(
    private val repository: CardRepository
) {
    suspend operator fun invoke(cardNumber: String, manualCaptcha: String? = null): Result<SaetaCard> {
        return repository.refreshCardBalance(cardNumber, manualCaptcha)
    }
}
