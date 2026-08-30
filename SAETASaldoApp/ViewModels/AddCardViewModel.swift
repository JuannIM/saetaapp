// AddCardViewModel.swift
// SAETA Saldo App - ViewModel para agregar nueva tarjeta (manual o vía NFC)

import Foundation
import Combine
import SwiftUI

@MainActor
class AddCardViewModel: ObservableObject {

    // MARK: - Estado del formulario
    @Published var cardNumber: String = ""
    @Published var alias: String = ""
    @Published var selectedType: CardType = .azul
    @Published var isNominated: Bool = false

    // MARK: - Estado NFC
    @Published var isScanning: Bool = false
    @Published var nfcUID: String? = nil
    @Published var nfcDetected: Bool = false

    // MARK: - Estado general
    @Published var isLoading: Bool = false
    @Published var errorMessage: String? = nil
    @Published var showCaptchaAlert: Bool = false
    @Published var showNFCNotSupportedAlert: Bool = false

    // MARK: - Servicios
    private let nfcService = NFCService()
    private let balanceService = BalanceService()

    // Callback para notificar al padre cuando se crea una tarjeta
    var onCardCreated: ((SAETACard) -> Void)?

    // MARK: - Validación
    var isFormValid: Bool {
        !cardNumber.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty &&
        cardNumber.filter { $0.isNumber }.count >= 6
    }

    var cardNumberError: String? {
        guard !cardNumber.isEmpty else { return nil }
        let digits = cardNumber.filter { $0.isNumber }
        if digits.count < 6 {
            return "El número de tarjeta debe tener al menos 6 dígitos"
        }
        if digits.count > 16 {
            return "El número de tarjeta no puede tener más de 16 dígitos"
        }
        return nil
    }

    // MARK: - Iniciar escaneo NFC
    func startNFCScan() {
        guard NFCService.isNFCAvailable else {
            showNFCNotSupportedAlert = true
            return
        }

        isScanning = true
        errorMessage = nil

        nfcService.startReading { [weak self] result in
            Task { @MainActor in
                guard let self = self else { return }
                self.isScanning = false

                switch result {
                case .success(let nfcResult):
                    self.nfcUID = nfcResult.uid
                    self.nfcDetected = true
                    // Pre-llenar el alias si está vacío
                    if self.alias.isEmpty {
                        self.alias = "Mi Tarjeta SAETA"
                    }
                case .failure(let error):
                    // "Cancelado por usuario" no es un error que mostrar
                    if case .nfcReadFailed(let msg) = error, msg.contains("cancelada") {
                        return
                    }
                    self.errorMessage = error.errorDescription
                }
            }
        }
    }

    // MARK: - Guardar tarjeta
    func saveCard() async {
        guard isFormValid else { return }

        let cleanNumber = cardNumber.trimmingCharacters(in: .whitespacesAndNewlines)
        let finalAlias = alias.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            ? "Tarjeta \(cleanNumber.suffix(4))"
            : alias.trimmingCharacters(in: .whitespacesAndNewlines)

        isLoading = true
        errorMessage = nil

        // No intentamos consultar el saldo al agregar la tarjeta:
        // el portal requiere acción humana (captcha) y el endpoint REST no existe.
        // El usuario puede consultar el saldo desde la pantalla de detalle.

        let newCard = SAETACard(
            cardNumber: cleanNumber,
            nfcUID: nfcUID,
            alias: finalAlias,
            type: selectedType,
            balance: nil,
            lastUpdated: nil,
            isNominated: isNominated
        )

        isLoading = false
        onCardCreated?(newCard)
    }
}
