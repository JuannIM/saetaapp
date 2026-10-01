package com.saetasaldo.app.domain.usecase

import com.saetasaldo.app.domain.model.SaetaCard
import com.saetasaldo.app.domain.repository.CardRepository

sealed class NfcScanResult {
    data class ExistingCardFound(val card: SaetaCard) : NfcScanResult()
    data class NewCardDiscovered(val nfcUid: String) : NfcScanResult()
}

class ProcessNfcScanUseCase(
    private val repository: CardRepository
) {
    suspend operator fun invoke(nfcUid: String): NfcScanResult {
        val existing = repository.getCardByNfcUid(nfcUid)
        return if (existing != null) {
            NfcScanResult.ExistingCardFound(existing)
        } else {
            NfcScanResult.NewCardDiscovered(nfcUid)
        }
    }

    suspend operator fun invoke(rawUid: ByteArray): NfcScanResult {
        val hex = rawUid.joinToString("") { "%02X".format(it) }
        return invoke(hex)
    }
}
