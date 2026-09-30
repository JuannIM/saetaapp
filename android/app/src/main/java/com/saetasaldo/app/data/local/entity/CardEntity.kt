package com.saetasaldo.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.saetasaldo.app.domain.model.CardType
import com.saetasaldo.app.domain.model.SaetaCard
import java.util.UUID

@Entity(tableName = "cards")
data class CardEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val cardNumber: String,
    val nfcUid: String? = null,
    val type: CardType = CardType.AZUL_COMUN,
    val currentBalance: Double? = null,
    val lastUpdated: Long? = null,
    val isFavorite: Boolean = false,
    val cardState: String? = null
) {
    fun toDomain(): SaetaCard = SaetaCard(
        id = id,
        name = name,
        cardNumber = cardNumber,
        nfcUid = nfcUid,
        type = type,
        currentBalance = currentBalance,
        lastUpdated = lastUpdated,
        isFavorite = isFavorite,
        cardState = cardState
    )

    companion object {
        fun fromDomain(card: SaetaCard): CardEntity = CardEntity(
            id = card.id,
            name = card.name,
            cardNumber = card.cardNumber,
            nfcUid = card.nfcUid,
            type = card.type,
            currentBalance = card.currentBalance,
            lastUpdated = card.lastUpdated,
            isFavorite = card.isFavorite,
            cardState = card.cardState
        )
    }
}
