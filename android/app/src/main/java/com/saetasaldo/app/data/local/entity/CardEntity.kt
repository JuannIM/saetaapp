package com.saetasaldo.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.saetasaldo.app.domain.model.CardType
import com.saetasaldo.app.domain.model.CardWallet
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
    val cardState: String? = null,
    val colorArgb: Int? = null,
    val internalNumber: String? = null,
    val walletsJson: String? = null
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
        cardState = cardState,
        colorArgb = colorArgb,
        internalNumber = internalNumber,
        wallets = decodeWallets(walletsJson)
    )

    companion object {
        private val gson = Gson()
        private val walletListType = object : TypeToken<List<CardWallet>>() {}.type

        fun encodeWallets(wallets: List<CardWallet>?): String? =
            wallets?.takeIf { it.isNotEmpty() }?.let { gson.toJson(it) }

        fun decodeWallets(json: String?): List<CardWallet> {
            if (json.isNullOrBlank()) return emptyList()
            return runCatching {
                gson.fromJson<List<CardWallet>>(json, walletListType)
            }.getOrNull().orEmpty()
        }

        fun fromDomain(card: SaetaCard): CardEntity = CardEntity(
            id = card.id,
            name = card.name,
            cardNumber = card.cardNumber,
            nfcUid = card.nfcUid,
            type = card.type,
            currentBalance = card.currentBalance,
            lastUpdated = card.lastUpdated,
            isFavorite = card.isFavorite,
            cardState = card.cardState,
            colorArgb = card.colorArgb,
            internalNumber = card.internalNumber,
            walletsJson = encodeWallets(card.wallets)
        )
    }
}
