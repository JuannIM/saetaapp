package com.saetasaldo.app.domain.model

data class RedBusAccountCard(
    val cardNumber: String,
    val balance: Double,
    val cardType: CardType?,
    val cardState: String?,
    val suggestedName: String?
)
