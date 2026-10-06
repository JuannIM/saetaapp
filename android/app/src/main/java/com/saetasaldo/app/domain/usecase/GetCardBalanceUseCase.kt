package com.saetasaldo.app.domain.usecase

import com.saetasaldo.app.domain.model.CardBalanceUpdate
import com.saetasaldo.app.domain.model.RedBusSessionState
import com.saetasaldo.app.domain.model.SaetaCard
import com.saetasaldo.app.domain.repository.CardRepository
import com.saetasaldo.app.domain.repository.RedBusAccountRepository
import kotlinx.coroutines.CancellationException

class GetCardBalanceUseCase(
    private val repository: CardRepository,
    private val accountRepository: RedBusAccountRepository? = null
) {
    suspend operator fun invoke(cardNumber: String, manualCaptcha: String? = null): Result<SaetaCard> {
        if (manualCaptcha != null) {
            return repository.refreshCardBalance(cardNumber, manualCaptcha)
        }
        if (accountRepository?.sessionState?.value == RedBusSessionState.Connected) {
            try {
                val match = accountRepository.getLinkedCards().getOrThrow()
                    .firstOrNull { it.cardNumber.trim() == cardNumber.trim() }
                if (match != null) {
                    return Result.success(
                        repository.applyBalanceUpdate(
                            CardBalanceUpdate(
                                cardNumber = cardNumber.trim(),
                                balance = match.balance,
                                cardType = match.cardType,
                                cardState = match.cardState,
                                suggestedName = match.suggestedName,
                                internalNumber = match.internalNumber,
                                wallets = match.wallets
                            )
                        )
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Any account-path failure degrades to the anonymous captcha query.
            }
        }
        return repository.refreshCardBalance(cardNumber, null)
    }
}
