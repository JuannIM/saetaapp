package com.saetasaldo.app.domain.usecase

import com.saetasaldo.app.domain.model.CardBalanceUpdate
import com.saetasaldo.app.domain.model.RedBusAccountCard
import com.saetasaldo.app.domain.repository.CardRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

data class RedBusSyncResult(
    val syncedCardNumbers: Set<String>,
    val importedCount: Int
)

class SyncRedBusCardsUseCase(private val cardRepository: CardRepository) {
    suspend operator fun invoke(cards: List<RedBusAccountCard>): RedBusSyncResult {
        val existingNumbers = cardRepository.getAllCards().first()
            .map { it.cardNumber.trim() }
            .toSet()

        val synced = mutableSetOf<String>()
        var imported = 0
        for (card in cards.distinctBy { it.cardNumber.trim() }) {
            val number = card.cardNumber.trim()
            try {
                cardRepository.applyBalanceUpdate(
                    CardBalanceUpdate(
                        cardNumber = number,
                        balance = card.balance,
                        cardType = card.cardType,
                        cardState = card.cardState,
                        suggestedName = card.suggestedName
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A card that fails to persist is skipped without aborting the rest.
                continue
            }
            synced += number
            if (number !in existingNumbers) imported++
        }
        return RedBusSyncResult(syncedCardNumbers = synced, importedCount = imported)
    }
}
