package com.saetasaldo.app.domain.model

/**
 * A virtual reload ("carga pendiente") that was paid but not yet credited to
 * the card. Field names in the response are only known for the empty case, so
 * every property is best-effort parsed and nullable.
 */
data class PendingLoad(
    val amount: Double? = null,
    val description: String? = null,
    val date: String? = null
)
