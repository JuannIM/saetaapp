package com.saetasaldo.app.domain.model

data class BalanceRecord(
    val id: Long = 0,
    val cardId: String,
    val balance: Double,
    val difference: Double,
    val timestamp: Long
)
