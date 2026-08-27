// CardListViewModel.swift
// SAETA Saldo App - ViewModel principal de gestión de tarjetas

import Foundation
import Combine
import SwiftUI

@MainActor
class CardListViewModel: ObservableObject {

    // MARK: - Estado publicado
    @Published var cards: [SAETACard] = []
    @Published var isLoading = false
    @Published var errorMessage: String? = nil
    @Published var successMessage: String? = nil


    // MARK: - Servicios
    private let storageService = CardStorageService()
    private let balanceService = BalanceService()
    private let nfcService = NFCService()

    // MARK: - Inicialización
    init() {
        loadCards()
    }

    // MARK: - Carga inicial desde almacenamiento
    func loadCards() {
        cards = storageService.load()
    }

    // MARK: - Agregar nueva tarjeta
    func addCard(_ card: SAETACard) {
        storageService.upsert(card: card, in: &cards)
        successMessage = "Tarjeta agregada correctamente"
    }

    // MARK: - Eliminar tarjeta
    func removeCard(_ card: SAETACard) {
        storageService.remove(card: card, from: &cards)
    }

    // MARK: - Actualizar tarjeta existente
    func updateCard(_ card: SAETACard) {
        storageService.upsert(card: card, in: &cards)
    }

    // MARK: - Actualizar saldo de una tarjeta
    func refreshBalance(for card: SAETACard) async {
        isLoading = true
        errorMessage = nil

        do {
            let result = try await balanceService.fetchBalance(for: card.cardNumber)

            // Actualizar la tarjeta con el nuevo saldo
            var updatedCard = card
            updatedCard.balance = result.balance
            updatedCard.lastUpdated = result.queryDate
            storageService.upsert(card: updatedCard, in: &cards)

            successMessage = "Saldo actualizado: \(updatedCard.formattedBalance)"
        } catch let error as SAETAError {
            errorMessage = error.errorDescription

            // Para el caso de captcha, dar información adicional al usuario
            if case .captchaRequired = error {
                errorMessage = "El portal SAETA requiere verificación manual. Abrí el sitio web para consultar tu saldo."
            }
        } catch {
            errorMessage = "Error inesperado: \(error.localizedDescription)"
        }

        isLoading = false
    }

    // MARK: - Actualizar saldo de todas las tarjetas
    func refreshAllBalances() async {
        isLoading = true
        errorMessage = nil
        var errorCount = 0

        for card in cards {
            do {
                let result = try await balanceService.fetchBalance(for: card.cardNumber)
                var updatedCard = card
                updatedCard.balance = result.balance
                updatedCard.lastUpdated = result.queryDate
                storageService.upsert(card: updatedCard, in: &cards)
            } catch {
                errorCount += 1
            }
        }

        isLoading = false

        if errorCount > 0 {
            errorMessage = "\(errorCount) tarjeta(s) no pudieron actualizarse. Verificá tu conexión a internet."
        } else if !cards.isEmpty {
            successMessage = "Todos los saldos actualizados"
        }
    }

    // MARK: - URL para consulta web manual
    func webPortalURL(for card: SAETACard) -> URL? {
        return URL(string: "https://salta.miredbus.com.ar/#/tarjetas")
    }
}
