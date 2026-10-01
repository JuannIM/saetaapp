package com.saetasaldo.app.domain.model

enum class CardType(val displayName: String) {
    AZUL_COMUN("Público General (Azul)"),
    VERDE_BENEFICIARIO("Beneficiario (Verde)");

    companion object {
        fun fromBackendString(raw: String?): CardType {
            if (raw == null) return AZUL_COMUN
            val upper = raw.uppercase()
            return when {
                upper.contains("VERDE") ||
                upper.contains("BENEFICIARIO") ||
                upper.contains("ESTUDIANTIL") ||
                upper.contains("JUBILADO") ||
                upper.contains("ABONO") -> VERDE_BENEFICIARIO
                else -> AZUL_COMUN
            }
        }
    }
}
