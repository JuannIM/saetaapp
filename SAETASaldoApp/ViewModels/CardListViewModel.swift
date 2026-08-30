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
    /// Controla si se debe mostrar el WebView de consulta de saldo
    @Published var cardPendingWebBalance: SAETACard? = nil

    // MARK: - Servicios
    private let storageService = CardStorageService()
    private let balanceService = BalanceService()

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

    // MARK: - Actualizar saldo (vía WebView)
    /// El portal SAETA usa un certificado SSL con CA autofirmada que iOS rechaza
    /// en URLSession/ATS. La única forma confiable de leer el saldo es usando
    /// WKWebView con un delegate que acepte ese certificado.
    /// Este método señaliza al CardDetailView que abra el WebView integrado.
    func refreshBalance(for card: SAETACard) async {
        errorMessage = nil
        cardPendingWebBalance = card
    }

    // MARK: - Guardar saldo leído desde el WebView
    func saveBalance(_ balance: Double, for card: SAETACard) {
        var updatedCard = card
        updatedCard.balance = balance
        updatedCard.lastUpdated = Date()
        storageService.upsert(card: updatedCard, in: &cards)
        cardPendingWebBalance = nil
        successMessage = "Saldo actualizado: \(updatedCard.formattedBalance)"
    }

    // MARK: - Actualizar saldo de todas las tarjetas
    func refreshAllBalances() async {
        // Para todas las tarjetas: pedir al usuario que consulte cada una
        // individualmente desde el detalle (el portal requiere acción humana para el captcha)
        if cards.isEmpty { return }
        successMessage = "Consultá el saldo de cada tarjeta en su pantalla de detalle."
    }

    // MARK: - URL para consulta web manual
    func webPortalURL(for card: SAETACard) -> URL? {
        return URL(string: BalanceService.portalURL)
    }
}
