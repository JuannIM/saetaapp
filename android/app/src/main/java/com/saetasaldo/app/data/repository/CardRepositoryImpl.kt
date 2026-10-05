package com.saetasaldo.app.data.repository

import com.saetasaldo.app.data.local.dao.BalanceHistoryDao
import com.saetasaldo.app.data.local.dao.CardDao
import com.saetasaldo.app.data.local.entity.BalanceHistoryEntity
import com.saetasaldo.app.data.local.entity.CardEntity
import com.saetasaldo.app.data.remote.api.SaetaApiService
import com.saetasaldo.app.data.remote.dto.SaldoRequestDto
import com.saetasaldo.app.domain.model.BalanceRecord
import com.saetasaldo.app.domain.model.CardBalanceUpdate
import com.saetasaldo.app.domain.model.CardType
import com.saetasaldo.app.domain.model.SaetaCard
import com.saetasaldo.app.domain.repository.CardRepository
import com.saetasaldo.app.domain.usecase.SolveCaptchaUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class CardRepositoryImpl(
    private val cardDao: CardDao,
    private val balanceHistoryDao: BalanceHistoryDao,
    private val apiService: SaetaApiService,
    private val solveCaptchaUseCase: SolveCaptchaUseCase
) : CardRepository {

    override fun getAllCards(): Flow<List<SaetaCard>> =
        cardDao.getAllCardsFlow().map { list -> list.map { it.toDomain() } }

    override suspend fun getCardById(id: String): SaetaCard? =
        cardDao.getCardById(id)?.toDomain()

    override suspend fun getCardByNfcUid(uid: String): SaetaCard? =
        cardDao.getCardByNfcUid(uid)?.toDomain()

    override suspend fun getFavoriteCard(): SaetaCard? =
        cardDao.getFavoriteCard()?.toDomain()

    override suspend fun saveCard(card: SaetaCard) {
        cardDao.insertCard(CardEntity.fromDomain(card))
    }

    override suspend fun deleteCard(card: SaetaCard) {
        cardDao.deleteCard(CardEntity.fromDomain(card))
    }

    override suspend fun setFavorite(id: String) {
        cardDao.clearFavorites()
        cardDao.setFavorite(id)
    }

    override fun getHistoryForCard(cardId: String): Flow<List<BalanceRecord>> =
        balanceHistoryDao.getHistoryForCardFlow(cardId).map { list -> list.map { it.toDomain() } }

    override suspend fun refreshCardBalance(cardNumber: String, manualCaptcha: String?): Result<SaetaCard> {
        val captchaCode = manualCaptcha ?: solveCaptchaUseCase().getOrElse {
            return Result.failure(it)
        }

        return try {
            val response = apiService.queryBalance(
                SaldoRequestDto(cardNumber = cardNumber, captchaCode = captchaCode)
            )

            if (!response.isSuccessful) {
                response.errorBody()?.close()
                return Result.failure(IllegalStateException("Error de conexión con el servidor de RedBus"))
            }

            val body = response.body()
                ?: return Result.failure(IllegalStateException("Error de conexión con el servidor de RedBus"))

            when (body.error) {
                0 -> {
                    val amount = body.balances?.firstOrNull()?.amount ?: body.effectiveBalance
                    val state = body.cardState ?: "ACTIVA"
                    val type = body.cardType?.let { CardType.fromBackendString(it) }

                    Result.success(
                        applyBalanceUpdate(
                            CardBalanceUpdate(
                                cardNumber = cardNumber,
                                balance = amount,
                                cardType = type,
                                cardState = state,
                                suggestedName = null
                            )
                        )
                    )
                }
                1 -> Result.failure(IllegalArgumentException("Captcha incorrecto. Reintentá nuevamente."))
                2 -> Result.failure(IllegalArgumentException("El número de tarjeta no existe en el sistema."))
                else -> Result.failure(IllegalStateException(body.message ?: "Error desconocido en el portal"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun applyBalanceUpdate(update: CardBalanceUpdate): SaetaCard {
        val cardNumber = update.cardNumber.trim()
        require(cardNumber.isNotBlank()) { "Card number must not be blank" }
        require(update.balance.isFinite()) { "Balance must be finite" }

        val remoteState = update.cardState?.trim()?.takeIf { it.isNotEmpty() }
        val suggestedName = update.suggestedName?.trim()?.takeIf { it.isNotEmpty() }

        val existing = cardDao.getCardByNumber(cardNumber)
        val updated = if (existing != null) {
            existing.copy(
                currentBalance = update.balance,
                lastUpdated = System.currentTimeMillis(),
                cardState = remoteState ?: existing.cardState,
                type = update.cardType ?: existing.type
            )
        } else {
            CardEntity(
                name = suggestedName ?: "Tarjeta SAETA",
                cardNumber = cardNumber,
                type = update.cardType ?: CardType.AZUL_COMUN,
                currentBalance = update.balance,
                lastUpdated = System.currentTimeMillis(),
                cardState = remoteState
            )
        }

        cardDao.insertCard(updated)

        // Record balance history change only if balance changed or it's the initial record
        val lastRecord = balanceHistoryDao.getLatestBalanceRecord(updated.id)
        if (lastRecord == null || update.balance != lastRecord.balance) {
            val diff = if (lastRecord != null) update.balance - lastRecord.balance else 0.0
            balanceHistoryDao.insertRecord(
                BalanceHistoryEntity(
                    cardId = updated.id,
                    balance = update.balance,
                    difference = diff
                )
            )
        }

        return updated.toDomain()
    }
}
