package com.saetasaldo.app.data.remote.dto

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName
import com.saetasaldo.app.domain.model.CardType
import com.saetasaldo.app.domain.model.CardWallet
import com.saetasaldo.app.domain.model.PendingLoad
import com.saetasaldo.app.domain.model.RedBusAccountCard

private val NUMBER_TOKEN_REGEX = Regex("""-?\s*\d[\d.,]*""")

data class RedBusSessionDto(
    @SerializedName("error")
    val error: Int?
)

data class RedBusCardListDto(
    @SerializedName("error")
    val error: Int?,
    @SerializedName("tarjetas")
    val cards: List<RedBusCardDto>?
)

data class RedBusCardDto(
    @SerializedName("nroInterno")
    val internalNumber: String?,
    @SerializedName("relationship")
    val relationship: String?,
    @SerializedName("description")
    val description: String?,
    @SerializedName("estadoTarjeta")
    val cardState: String?,
    @SerializedName("tarjetaDatosAdicionales")
    val additionalData: RedBusCardAdditionalDataDto?
)

data class RedBusCardAdditionalDataDto(
    @SerializedName("saldos")
    val balances: List<RedBusAccountBalanceDto>?,
    @SerializedName("tipoTarjeta")
    val cardType: String?,
    @SerializedName("estadoTarjeta")
    val cardState: String?,
    @SerializedName("codExterno")
    val externalCode: JsonElement?
)

data class RedBusAccountBalanceDto(
    @SerializedName("saldo")
    val balance: JsonElement?,
    @SerializedName("monedero")
    val wallet: RedBusWalletDto?
)

data class RedBusWalletDto(
    @SerializedName("id")
    val id: Int?,
    @SerializedName("nombre")
    val name: String?,
    @SerializedName("unidadPasajes")
    val isPassageUnit: Boolean?,
    @SerializedName("prefijoSaldo")
    val balancePrefix: String?,
    @SerializedName("sufijoSaldo")
    val balanceSuffix: String?
)

data class RedBusPendingLoadsDto(
    @SerializedName("error")
    val error: Int?,
    @SerializedName("mensaje")
    val message: String?,
    @SerializedName("cargasPendientes")
    val pendingLoads: List<RedBusPendingLoadDto>?
)

/**
 * Shape inferred from the portal contract; every field is optional because the
 * verified capture only contained an empty list.
 */
data class RedBusPendingLoadDto(
    @SerializedName("monto")
    val amount: JsonElement?,
    @SerializedName("descripcion")
    val description: String?,
    @SerializedName("fecha")
    val date: String?
)

fun RedBusCardListDto.toDomainCards(): List<RedBusAccountCard> {
    if (error != 0) return emptyList()
    return cards.orEmpty().mapNotNull { it.toDomainCard() }
}

private fun RedBusCardDto.toDomainCard(): RedBusAccountCard? {
    val additional = additionalData ?: return null

    val cardNumber = additional.externalCode
        ?.takeIf { it.isJsonPrimitive }
        ?.asJsonPrimitive
        ?.takeIf { it.isNumber || it.isString }
        ?.asString
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: return null

    val wallets = additional.balances.orEmpty().mapNotNull { it.toDomainWallet() }
    val balance = wallets.firstOrNull {
        it.name.equals(CardWallet.PRINCIPAL_WALLET_NAME, ignoreCase = true)
    }?.balance ?: return null

    return RedBusAccountCard(
        cardNumber = cardNumber,
        balance = balance,
        cardType = additional.cardType.trimToNull()?.let { CardType.fromBackendString(it) },
        cardState = additional.cardState.trimToNull() ?: cardState.trimToNull(),
        suggestedName = description?.trim()?.takeIf { it.isNotEmpty() },
        internalNumber = internalNumber.trimToNull(),
        wallets = wallets
    )
}

private fun RedBusAccountBalanceDto.toDomainWallet(): CardWallet? {
    val amount = parseBalanceAmount(balance) ?: return null
    return CardWallet(
        id = wallet?.id,
        name = wallet?.name.trimToNull(),
        balance = amount,
        isPassageUnit = wallet?.isPassageUnit == true,
        prefix = wallet?.balancePrefix.orEmpty(),
        suffix = wallet?.balanceSuffix.orEmpty()
    )
}

fun RedBusPendingLoadsDto.toDomainLoads(): List<PendingLoad> {
    if (error != 0) return emptyList()
    return pendingLoads.orEmpty().map {
        PendingLoad(
            amount = parseBalanceAmount(it.amount),
            description = it.description.trimToNull(),
            date = it.date.trimToNull()
        )
    }
}

private fun parseBalanceAmount(element: JsonElement?): Double? {
    if (element == null || !element.isJsonPrimitive) return null
    val primitive = element.asJsonPrimitive
    val amount = when {
        primitive.isNumber -> primitive.asDouble
        primitive.isString -> {
            val raw = primitive.asString
            if (NUMBER_TOKEN_REGEX.find(raw) == null) return null
            SaldoResponseDto.parseAmount(raw)
        }
        else -> return null
    }
    return amount.takeIf { it.isFinite() }
}

private fun String?.trimToNull(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
