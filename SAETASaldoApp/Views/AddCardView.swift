// AddCardView.swift
// SAETA Saldo App - Vista para agregar una nueva tarjeta (manual o NFC)

import SwiftUI

struct AddCardView: View {
    @Environment(\.dismiss) var dismiss
    @StateObject private var viewModel = AddCardViewModel()
    let onSave: (SAETACard) -> Void

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 20) {

                    // MARK: - Preview de la tarjeta
                    CardPreviewView(
                        cardNumber: viewModel.cardNumber,
                        alias: viewModel.alias,
                        type: viewModel.selectedType,
                        nfcUID: viewModel.nfcUID
                    )

                    // MARK: - Escaneo NFC
                    NFCScanSection(viewModel: viewModel)

                    // MARK: - Formulario
                    CardFormSection(viewModel: viewModel)

                    // MARK: - Tipo de tarjeta
                    CardTypeSection(viewModel: viewModel)

                    // MARK: - Botón guardar
                    Button {
                        Task { await saveCard() }
                    } label: {
                        HStack {
                            if viewModel.isLoading {
                                ProgressView()
                                    .tint(.white)
                                    .scaleEffect(0.9)
                                Text("Consultando saldo...")
                            } else {
                                Image(systemName: "checkmark.circle.fill")
                                Text("Guardar Tarjeta")
                            }
                        }
                        .font(.headline)
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .padding()
                        .background(viewModel.isFormValid ? Color.saetaBlue : Color.gray)
                        .clipShape(RoundedRectangle(cornerRadius: 14))
                    }
                    .disabled(!viewModel.isFormValid || viewModel.isLoading)
                    .padding(.horizontal)

                    Color.clear.frame(height: 20)
                }
                .padding(.top)
            }
            .background(Color.appBackground)
            .navigationTitle("Agregar Tarjeta")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Cancelar") { dismiss() }
                        .foregroundColor(.saetaBlue)
                }
            }
            .alert("Verificación manual requerida", isPresented: $viewModel.showCaptchaAlert) {
                Button("Entendido") {
                    // Guardar sin saldo si hay captcha
                    saveAndDismiss()
                }
                Button("Abrir sitio web") {
                    if let url = URL(string: "https://salta.miredbus.com.ar") {
                        UIApplication.shared.open(url)
                    }
                    saveAndDismiss()
                }
            } message: {
                Text("El portal de SAETA requiere una verificación de seguridad (captcha) para consultar el saldo. La tarjeta se guardará sin saldo inicial. Podés consultarlo en el sitio web oficial.")
            }
            .alert("NFC no disponible", isPresented: $viewModel.showNFCNotSupportedAlert) {
                Button("OK", role: .cancel) {}
            } message: {
                Text("Este dispositivo no soporta lectura NFC. Podés ingresar el número de tarjeta manualmente (está impreso en el plástico).")
            }
        }
        .onAppear {
            viewModel.onCardCreated = { card in
                onSave(card)
                dismiss()
            }
        }
    }

    private func saveCard() async {
        await viewModel.saveCard()
    }

    private func saveAndDismiss() {
        let cleanNumber = viewModel.cardNumber.trimmingCharacters(in: .whitespacesAndNewlines)
        let finalAlias = viewModel.alias.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            ? "Tarjeta \(cleanNumber.suffix(4))"
            : viewModel.alias

        let newCard = SAETACard(
            cardNumber: cleanNumber,
            nfcUID: viewModel.nfcUID,
            alias: finalAlias,
            type: viewModel.selectedType,
            balance: nil,
            isNominated: viewModel.isNominated
        )
        onSave(newCard)
        dismiss()
    }
}

// MARK: - Preview de la tarjeta mientras se edita
struct CardPreviewView: View {
    let cardNumber: String
    let alias: String
    let type: CardType
    let nfcUID: String?

    var body: some View {
        ZStack {
            SAETAGradient.gradient(for: type)
                .clipShape(RoundedRectangle(cornerRadius: 20))
                .shadow(color: .black.opacity(0.15), radius: 10, y: 5)

            VStack(alignment: .leading, spacing: 0) {
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(alias.isEmpty ? "Mi Tarjeta SAETA" : alias)
                            .font(.headline)
                            .fontWeight(.bold)
                            .foregroundColor(.white)
                        Text(type.description)
                            .font(.caption)
                            .foregroundColor(.white.opacity(0.8))
                    }
                    Spacer()
                    Image(systemName: "bus.fill")
                        .font(.title2)
                        .foregroundColor(.white.opacity(0.85))
                }
                .padding(.horizontal, 20)
                .padding(.top, 20)

                Spacer()

                VStack(alignment: .leading, spacing: 2) {
                    Text("NÚMERO DE TARJETA")
                        .font(.system(size: 9, weight: .medium))
                        .foregroundColor(.white.opacity(0.6))
                        .tracking(1.5)

                    Text(cardNumber.isEmpty ? "•••• •••• ••••" : formatNumber(cardNumber))
                        .font(.system(size: 18, design: .monospaced))
                        .fontWeight(.medium)
                        .foregroundColor(.white)
                }
                .padding(.horizontal, 20)
                .padding(.bottom, 20)
            }
        }
        .frame(height: 130)
        .padding(.horizontal)
        .animation(.spring(response: 0.4), value: type)
        .animation(.spring(response: 0.4), value: alias)
    }

    private func formatNumber(_ number: String) -> String {
        let digits = number.filter { $0.isNumber }
        var result = ""
        for (i, char) in digits.prefix(16).enumerated() {
            if i > 0 && i % 4 == 0 { result += " " }
            result += String(char)
        }
        return result
    }
}

// MARK: - Sección de escaneo NFC
struct NFCScanSection: View {
    @ObservedObject var viewModel: AddCardViewModel

    var body: some View {
        VStack(spacing: 12) {
            // Botón NFC principal
            Button(action: viewModel.startNFCScan) {
                VStack(spacing: 12) {
                    ZStack {
                        Circle()
                            .fill(viewModel.nfcDetected ? Color.saetaGreen.opacity(0.15) : Color.saetaBlue.opacity(0.1))
                            .frame(width: 80, height: 80)

                        if viewModel.isScanning {
                            // Animación de escaneo
                            ZStack {
                                ForEach(0..<3) { i in
                                    Circle()
                                        .stroke(Color.saetaBlue.opacity(0.5 - Double(i) * 0.15), lineWidth: 2)
                                        .scaleEffect(viewModel.isScanning ? 1.0 + Double(i) * 0.3 : 1.0)
                                        .animation(.easeOut(duration: 1.5).repeatForever(autoreverses: false).delay(Double(i) * 0.4), value: viewModel.isScanning)
                                        .frame(width: 80, height: 80)
                                }
                            }
                        }

                        Image(systemName: viewModel.nfcDetected ? "checkmark.circle.fill" : "wave.3.right.circle.fill")
                            .font(.system(size: 36))
                            .foregroundColor(viewModel.nfcDetected ? .saetaGreen : .saetaBlue)
                    }

                    VStack(spacing: 4) {
                        Text(viewModel.nfcDetected ? "¡Tarjeta detectada!" :
                             viewModel.isScanning ? "Acercá tu tarjeta..." : "Escanear con NFC")
                            .font(.headline)
                            .foregroundColor(viewModel.nfcDetected ? .saetaGreen : .saetaBlue)

                        Text(viewModel.nfcDetected ?
                             "UID: \(viewModel.nfcUID?.prefix(12) ?? "")..." :
                             "Acercá la tarjeta SAETA a la parte trasera del iPhone")
                            .font(.caption)
                            .foregroundColor(.secondary)
                            .multilineTextAlignment(.center)
                    }
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 20)
                .background(Color.cardBackground)
                .clipShape(RoundedRectangle(cornerRadius: 16))
            }
            .disabled(viewModel.isScanning)
            .padding(.horizontal)

            // Separador "o"
            HStack {
                Rectangle().fill(Color.secondary.opacity(0.3)).frame(height: 1)
                Text("O ingresá manualmente")
                    .font(.caption)
                    .foregroundColor(.secondary)
                    .padding(.horizontal, 8)
                Rectangle().fill(Color.secondary.opacity(0.3)).frame(height: 1)
            }
            .padding(.horizontal)
        }
    }
}

// MARK: - Sección del formulario
struct CardFormSection: View {
    @ObservedObject var viewModel: AddCardViewModel

    var body: some View {
        VStack(spacing: 0) {
            // Número de tarjeta
            VStack(alignment: .leading, spacing: 6) {
                HStack {
                    Text("Número de tarjeta *")
                        .font(.subheadline)
                        .fontWeight(.medium)
                    Spacer()
                    Text("Impreso en el plástico")
                        .font(.caption2)
                        .foregroundColor(.secondary)
                }
                .padding(.horizontal)

                TextField("Ej: 123456789", text: $viewModel.cardNumber)
                    .keyboardType(.numberPad)
                    .font(.system(.body, design: .monospaced))
                    .padding()
                    .background(Color.cardBackground)
                    .clipShape(RoundedRectangle(cornerRadius: 10))
                    .overlay(
                        RoundedRectangle(cornerRadius: 10)
                            .stroke(viewModel.cardNumberError != nil ? Color.red.opacity(0.5) : Color.clear, lineWidth: 1.5)
                    )
                    .padding(.horizontal)

                if let error = viewModel.cardNumberError {
                    Text(error)
                        .font(.caption)
                        .foregroundColor(.red)
                        .padding(.horizontal)
                }
            }

            Spacer(minLength: 12)

            // Alias
            VStack(alignment: .leading, spacing: 6) {
                Text("Nombre / Alias")
                    .font(.subheadline)
                    .fontWeight(.medium)
                    .padding(.horizontal)

                TextField("Ej: Mi tarjeta del trabajo", text: $viewModel.alias)
                    .padding()
                    .background(Color.cardBackground)
                    .clipShape(RoundedRectangle(cornerRadius: 10))
                    .padding(.horizontal)
            }

            Spacer(minLength: 12)

            // Nominada
            Toggle(isOn: $viewModel.isNominated) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Tarjeta nominada")
                        .font(.subheadline)
                        .fontWeight(.medium)
                    Text("Asociada a tu DNI (permite recupero de saldo)")
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
            }
            .tint(.saetaBlue)
            .padding()
            .background(Color.cardBackground)
            .clipShape(RoundedRectangle(cornerRadius: 10))
            .padding(.horizontal)
        }
    }
}

// MARK: - Sección de tipo de tarjeta
struct CardTypeSection: View {
    @ObservedObject var viewModel: AddCardViewModel

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Tipo de tarjeta")
                .font(.subheadline)
                .fontWeight(.medium)
                .padding(.horizontal)

            HStack(spacing: 12) {
                ForEach(CardType.allCases.filter { $0 != .unknown }, id: \.self) { type in
                    Button {
                        viewModel.selectedType = type
                    } label: {
                        VStack(spacing: 8) {
                            ZStack {
                                RoundedRectangle(cornerRadius: 10)
                                    .fill(type == .azul ? Color.saetaBlue : Color.saetaGreen)
                                    .frame(height: 50)

                                Image(systemName: type == .azul ? "creditcard.fill" : "star.circle.fill")
                                    .font(.title2)
                                    .foregroundColor(.white)
                            }

                            Text(type.rawValue)
                                .font(.caption)
                                .fontWeight(.medium)
                                .foregroundColor(.primary)

                            Text(type == .azul ? "Común" : "Beneficios")
                                .font(.caption2)
                                .foregroundColor(.secondary)
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 8)
                        .background(Color.cardBackground)
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                        .overlay(
                            RoundedRectangle(cornerRadius: 12)
                                .stroke(viewModel.selectedType == type ?
                                        (type == .azul ? Color.saetaBlue : Color.saetaGreen) : Color.clear,
                                        lineWidth: 2.5)
                        )
                        .shadow(color: viewModel.selectedType == type ? .black.opacity(0.08) : .clear, radius: 4)
                    }
                    .buttonStyle(PlainButtonStyle())
                }
            }
            .padding(.horizontal)
        }
    }
}
