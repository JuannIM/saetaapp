package com.saetasaldo.app.domain.repository

import com.saetasaldo.app.domain.model.BalanceRecord
import com.saetasaldo.app.domain.model.CardBalanceUpdate
import com.saetasaldo.app.domain.model.SaetaCard
import kotlinx.coroutines.flow.Flow

interface CardRepository {
    fun getAllCards(): Flow<List<SaetaCard>>
    suspend fun getCardById(id: String): SaetaCard?
    suspend fun getCardByNfcUid(uid: String): SaetaCard?
    suspend fun getFavoriteCard(): SaetaCard?
    suspend fun saveCard(card: SaetaCard)
    suspend fun deleteCard(card: SaetaCard)
    suspend fun setFavorite(id: String)
    fun getHistoryForCard(cardId: String): Flow<List<BalanceRecord>>
    suspend fun refreshCardBalance(cardNumber: String, manualCaptcha: String? = null): Result<SaetaCard>
    suspend fun applyBalanceUpdate(update: CardBalanceUpdate): SaetaCard
}
