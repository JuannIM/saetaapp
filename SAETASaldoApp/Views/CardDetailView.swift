// CardDetailView.swift
// SAETA Saldo App - Vista de detalle de una tarjeta individual

import SwiftUI

struct CardDetailView: View {
    let card: SAETACard
    @EnvironmentObject var viewModel: CardListViewModel
    @Environment(\.openURL) var openURL
    @State private var isRefreshing = false
    @State private var showingCaptchaSolver = false


    // Card actualizada desde el ViewModel
    private var currentCard: SAETACard {
        viewModel.cards.first { $0.id == card.id } ?? card
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 20) {

                // MARK: - Tarjeta virtual (hero)
                CardHeroView(card: currentCard)
                    .padding(.horizontal)

                // MARK: - Información de saldo
                BalanceInfoSection(card: currentCard)
                    .padding(.horizontal)

                // MARK: - Beneficios de la tarjeta
                if !currentCard.type.benefits.isEmpty {
                    BenefitsSection(card: currentCard)
                        .padding(.horizontal)
                }

                // MARK: - Datos técnicos
                TechnicalInfoSection(card: currentCard)
                    .padding(.horizontal)

                // MARK: - Acciones
                ActionsSection(card: currentCard, onRefresh: {
                    Task {
                        isRefreshing = true
                        await viewModel.refreshBalance(for: currentCard)
                        isRefreshing = false
                        if viewModel.errorMessage?.contains("verificación manual") == true {
                            showingCaptchaSolver = true
                        }
                    }
                }, onOpenWeb: {
                    if let url = viewModel.webPortalURL(for: currentCard) {
                        openURL(url)
                    }
                })
                .padding(.horizontal)

                // Aviso legal importante
                DisclaimerView()
                    .padding(.horizontal)

                Color.clear.frame(height: 20)
            }
            .padding(.top)
        }
        .background(Color.appBackground)
        .navigationTitle(currentCard.alias)
        .navigationBarTitleDisplayMode(.inline)
        .sheet(isPresented: $showingCaptchaSolver) {
            NavigationView {
                if let url = viewModel.webPortalURL(for: currentCard) {
                    SAETAWebView(url: url) { balance in
                        // Cuando el JS encuentra el saldo, actualizamos y cerramos
                        var updatedCard = currentCard
                        updatedCard.balance = balance
                        updatedCard.lastUpdated = Date()
                        viewModel.updateCard(updatedCard)
                        viewModel.successMessage = "¡Saldo extraído con éxito!"
                        showingCaptchaSolver = false
                    }
                    .navigationTitle("Resolver Captcha")
                    .navigationBarTitleDisplayMode(.inline)
                    .toolbar {
                        ToolbarItem(placement: .navigationBarLeading) {
                            Button("Cerrar") {
                                showingCaptchaSolver = false
                            }
                        }
                    }
                }
            }
        }
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                Button {
                    Task {
                        isRefreshing = true
                        await viewModel.refreshBalance(for: currentCard)
                        isRefreshing = false
                    }
                } label: {
                    Image(systemName: "arrow.clockwise")
                        .rotationEffect(.degrees(isRefreshing ? 360 : 0))
                        .animation(isRefreshing ? .linear(duration: 1).repeatForever(autoreverses: false) : .default, value: isRefreshing)
                }
                .disabled(isRefreshing)
            }
        }
    }
}

// MARK: - Vista hero de la tarjeta
struct CardHeroView: View {
    let card: SAETACard

    var body: some View {
        ZStack {
            SAETAGradient.gradient(for: card.type)
                .clipShape(RoundedRectangle(cornerRadius: 24))
                .shadow(color: .black.opacity(0.2), radius: 15, y: 8)

            VStack(alignment: .leading, spacing: 0) {
                // Header
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("SAETA")
                            .font(.system(size: 22, weight: .black))
                            .foregroundColor(.white)
                        Text("Salta · \(card.type.description)")
                            .font(.caption)
                            .foregroundColor(.white.opacity(0.8))
                    }

                    Spacer()

                    // Chip NFC
                    ZStack {
                        RoundedRectangle(cornerRadius: 8)
                            .fill(Color.white.opacity(0.15))
                            .frame(width: 50, height: 38)

                        VStack(spacing: 2) {
                            Image(systemName: "wave.3.right")
                                .font(.system(size: 14))
                                .foregroundColor(.white)
                            Text("NFC")
                                .font(.system(size: 8, weight: .bold))
                                .foregroundColor(.white.opacity(0.9))
                        }
                    }
                }
                .padding(.horizontal, 24)
                .padding(.top, 24)

                Spacer()

                // Saldo
                VStack(alignment: .leading, spacing: 4) {
                    Text("SALDO DISPONIBLE")
                        .font(.system(size: 10, weight: .medium))
                        .foregroundColor(.white.opacity(0.7))
                        .tracking(2)

                    if let balance = card.balance {
                        HStack(alignment: .firstTextBaseline, spacing: 4) {
                            Text("$")
                                .font(.system(size: 24, weight: .bold))
                                .foregroundColor(.white.opacity(0.9))
                            Text(String(format: "%.2f", balance))
                                .font(.system(size: 48, weight: .bold, design: .rounded))
                                .foregroundColor(.white)
                        }
                    } else {
                        Text("Consultar saldo →")
                            .font(.title2)
                            .fontWeight(.semibold)
                            .foregroundColor(.white.opacity(0.8))
                    }
                }
                .padding(.horizontal, 24)

                Spacer()

                // Footer con número
                HStack {
                    Text("N° \(formatCardNumber(card.cardNumber))")
                        .font(.system(size: 14, design: .monospaced))
                        .foregroundColor(.white.opacity(0.75))

                    Spacer()

                    if card.isNominated {
                        Label("Nominada", systemImage: "checkmark.seal.fill")
                            .font(.caption2)
                            .foregroundColor(.white.opacity(0.8))
                    }
                }
                .padding(.horizontal, 24)
                .padding(.bottom, 24)
            }
        }
        .frame(height: 220)
    }

    private func formatCardNumber(_ number: String) -> String {
        let digits = number.filter { $0.isNumber }
        var formatted = ""
        for (index, char) in digits.enumerated() {
            if index > 0 && index % 4 == 0 { formatted += " " }
            formatted += String(char)
        }
        return formatted
    }
}

// MARK: - Sección de información de saldo
struct BalanceInfoSection: View {
    let card: SAETACard

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            SectionHeader(title: "Información del Saldo", icon: "dollarsign.circle.fill")

            VStack(spacing: 0) {
                InfoRow(label: "Saldo actual",
                        value: card.balance != nil ? card.formattedBalance : "Sin datos",
                        isHighlighted: true)

                Divider().padding(.leading)

                InfoRow(label: "Última consulta",
                        value: card.balance != nil ? card.lastUpdatedDescription : "Nunca")

                if card.balanceMightBeStale && card.balance != nil {
                    Divider().padding(.leading)

                    HStack(spacing: 8) {
                        Image(systemName: "exclamationmark.triangle.fill")
                            .foregroundColor(.orange)
                            .font(.caption)
                        Text("El saldo en el portal de SAETA se actualiza cada 24-48 horas hábiles. El dato puede estar desactualizado.")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                    .padding()
                }
            }
            .background(Color.cardBackground)
            .clipShape(RoundedRectangle(cornerRadius: 12))
        }
    }
}

// MARK: - Sección de beneficios
struct BenefitsSection: View {
    let card: SAETACard

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            SectionHeader(title: "Beneficios", icon: "star.fill")

            VStack(alignment: .leading, spacing: 0) {
                ForEach(Array(card.type.benefits.enumerated()), id: \.offset) { index, benefit in
                    HStack(spacing: 12) {
                        Image(systemName: "checkmark.circle.fill")
                            .foregroundColor(card.type == .azul ? .saetaBlue : .saetaGreen)
                            .font(.subheadline)

                        Text(benefit)
                            .font(.subheadline)
                            .foregroundColor(.primary)
                    }
                    .padding()

                    if index < card.type.benefits.count - 1 {
                        Divider().padding(.leading, 44)
                    }
                }
            }
            .background(Color.cardBackground)
            .clipShape(RoundedRectangle(cornerRadius: 12))
        }
    }
}

// MARK: - Sección de información técnica
struct TechnicalInfoSection: View {
    let card: SAETACard

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            SectionHeader(title: "Datos de la Tarjeta", icon: "info.circle.fill")

            VStack(spacing: 0) {
                InfoRow(label: "Número de tarjeta", value: card.cardNumber)

                if let uid = card.nfcUID {
                    Divider().padding(.leading)
                    InfoRow(label: "UID NFC", value: uid, isMonospaced: true)
                }

                Divider().padding(.leading)
                InfoRow(label: "Tipo", value: card.type.description)

                Divider().padding(.leading)
                InfoRow(label: "Estado", value: card.isNominated ? "Nominada (con DNI)" : "Anónima")
            }
            .background(Color.cardBackground)
            .clipShape(RoundedRectangle(cornerRadius: 12))
        }
    }
}

// MARK: - Sección de acciones
struct ActionsSection: View {
    let card: SAETACard
    let onRefresh: () -> Void
    let onOpenWeb: () -> Void

    var body: some View {
        VStack(spacing: 12) {
            // Botón principal: Actualizar saldo
            Button(action: onRefresh) {
                HStack {
                    Image(systemName: "arrow.clockwise")
                    Text("Actualizar Saldo")
                    Spacer()
                    Image(systemName: "chevron.right")
                        .font(.caption)
                        .foregroundColor(.white.opacity(0.7))
                }
                .font(.headline)
                .foregroundColor(.white)
                .padding()
                .background(Color.saetaBlue)
                .clipShape(RoundedRectangle(cornerRadius: 12))
            }

            // Botón secundario: Abrir portal web
            Button(action: onOpenWeb) {
                HStack {
                    Image(systemName: "safari.fill")
                    Text("Ver en Portal SAETA")
                    Spacer()
                    Image(systemName: "arrow.up.right.square")
                        .font(.caption)
                }
                .font(.subheadline)
                .foregroundColor(.saetaBlue)
                .padding()
                .background(Color.saetaBlue.opacity(0.1))
                .clipShape(RoundedRectangle(cornerRadius: 12))
            }
        }
    }
}

// MARK: - Aviso importante
struct DisclaimerView: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Label("Aviso importante", systemImage: "info.circle")
                .font(.caption.weight(.semibold))
                .foregroundColor(.secondary)

            Text("El saldo se consulta en el portal oficial de SAETA (salta.miredbus.com.ar). Los datos pueden tener un desfase de 24 a 48 horas hábiles con respecto al saldo real de la tarjeta, ya que se actualizan al usar el colectivo. Esta app NO modifica el saldo de tu tarjeta.")
                .font(.caption2)
                .foregroundColor(.secondary)
                .lineSpacing(3)
        }
        .padding()
        .background(Color.orange.opacity(0.08))
        .clipShape(RoundedRectangle(cornerRadius: 10))
        .overlay(
            RoundedRectangle(cornerRadius: 10)
                .stroke(Color.orange.opacity(0.2), lineWidth: 1)
        )
    }
}

// MARK: - Componentes reutilizables
struct SectionHeader: View {
    let title: String
    let icon: String

    var body: some View {
        HStack(spacing: 8) {
            Image(systemName: icon)
                .foregroundColor(.saetaBlue)
            Text(title)
                .font(.headline)
        }
    }
}

struct InfoRow: View {
    let label: String
    let value: String
    var isHighlighted: Bool = false
    var isMonospaced: Bool = false

    var body: some View {
        HStack {
            Text(label)
                .font(.subheadline)
                .foregroundColor(.secondary)

            Spacer()

            Text(value)
                .font(isMonospaced ? .system(.subheadline, design: .monospaced) : .subheadline)
                .fontWeight(isHighlighted ? .semibold : .regular)
                .foregroundColor(isHighlighted ? .primary : .secondary)
        }
        .padding()
    }
}
