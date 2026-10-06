package com.saetasaldo.app.domain.usecase

import com.saetasaldo.app.domain.model.RedBusSessionState
import com.saetasaldo.app.domain.model.SaetaCard
import com.saetasaldo.app.domain.repository.CardRepository
import com.saetasaldo.app.domain.repository.RedBusAccountRepository
import kotlinx.coroutines.CancellationException

data class RefreshAllBalancesResult(
    val updatedCount: Int,
    val failures: Map<String, Throwable>
)

class RefreshAllBalancesUseCase(
    private val cardRepository: CardRepository,
    private val redBusAccountRepository: RedBusAccountRepository,
    private val syncRedBusCardsUseCase: SyncRedBusCardsUseCase,
    private val delayBetweenAnonymousRequestsMillis: Long = 800L,
    private val delay: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) }
) {
    suspend operator fun invoke(localCards: List<SaetaCard>): RefreshAllBalancesResult {
        val syncedNumbers: Set<String> =
            if (redBusAccountRepository.sessionState.value == RedBusSessionState.Connected) {
                try {
                    redBusAccountRepository.getLinkedCards().getOrNull()
                        ?.let { syncRedBusCardsUseCase(it).syncedCardNumbers }
                        ?: emptySet()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Any account-path failure degrades to the anonymous captcha queries.
                    emptySet()
                }
            } else {
                emptySet()
            }

        var updatedCount = syncedNumbers.size
        val failures = linkedMapOf<String, Throwable>()
        var anonymousRequests = 0
        for (card in localCards) {
            if (card.cardNumber.trim() in syncedNumbers) continue
            if (anonymousRequests > 0) {
                delay(delayBetweenAnonymousRequestsMillis)
            }
            anonymousRequests++
            try {
                cardRepository.refreshCardBalance(card.cardNumber)
                    .onSuccess { updatedCount++ }
                    .onFailure {
                        if (it is CancellationException) throw it
                        failures[card.cardNumber] = it
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A card that fails unexpectedly is reported without aborting the rest.
                failures[card.cardNumber] = e
            }
        }
        return RefreshAllBalancesResult(updatedCount = updatedCount, failures = failures)
    }
}
