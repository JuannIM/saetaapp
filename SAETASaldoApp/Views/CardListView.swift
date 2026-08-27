// CardListView.swift
// SAETA Saldo App - Vista principal que lista las tarjetas del usuario

import SwiftUI

struct CardListView: View {
    @EnvironmentObject var viewModel: CardListViewModel
    @State private var showingAddCard = false
    @State private var cardToDelete: SAETACard?
    @State private var showingDeleteAlert = false

    var body: some View {
        ZStack {
            Color.appBackground.ignoresSafeArea()

            if viewModel.cards.isEmpty {
                EmptyCardsView(onAddCard: { showingAddCard = true })
            } else {
                ScrollView {
                    VStack(spacing: 16) {
                        // Header de saldo total
                        TotalBalanceHeaderView()
                            .environmentObject(viewModel)

                        // Lista de tarjetas
                        VStack(spacing: 12) {
                            ForEach(viewModel.cards) { card in
                                NavigationLink(destination: CardDetailView(card: card).environmentObject(viewModel)) {
                                    CardRowView(card: card)
                                        .environmentObject(viewModel)
                                }
                                .buttonStyle(PlainButtonStyle())
                                .contextMenu {
                                    Button {
                                        Task { await viewModel.refreshBalance(for: card) }
                                    } label: {
                                        Label("Actualizar saldo", systemImage: "arrow.clockwise")
                                    }

                                    Button(role: .destructive) {
                                        cardToDelete = card
                                        showingDeleteAlert = true
                                    } label: {
                                        Label("Eliminar tarjeta", systemImage: "trash")
                                    }
                                }
                            }
                        }
                        .padding(.horizontal)
                    }
                    .padding(.vertical)
                }
                .refreshable {
                    await viewModel.refreshAllBalances()
                }
            }
        }
        .navigationTitle("SAETA Saldo")
        .navigationBarTitleDisplayMode(.large)
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                Button {
                    showingAddCard = true
                } label: {
                    Image(systemName: "plus.circle.fill")
                        .font(.title2)
                        .foregroundColor(.saetaBlue)
                }
            }

            if !viewModel.cards.isEmpty {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button {
                        Task { await viewModel.refreshAllBalances() }
                    } label: {
                        Image(systemName: "arrow.clockwise")
                            .foregroundColor(.saetaBlue)
                    }
                    .disabled(viewModel.isLoading)
                }
            }
        }
        .sheet(isPresented: $showingAddCard) {
            AddCardView { newCard in
                viewModel.addCard(newCard)
            }
        }
        .alert("Eliminar tarjeta", isPresented: $showingDeleteAlert, presenting: cardToDelete) { card in
            Button("Eliminar", role: .destructive) {
                viewModel.removeCard(card)
            }
            Button("Cancelar", role: .cancel) {}
        } message: { card in
            Text("¿Eliminar la tarjeta '\(card.alias)'? Esta acción no se puede deshacer.")
        }
        .overlay {
            if viewModel.isLoading {
                LoadingOverlayView()
            }
        }
        .overlay(alignment: .bottom) {
            if let message = viewModel.errorMessage {
                MessageBannerView(message: message, type: .error) {
                    viewModel.errorMessage = nil
                }
                .transition(.move(edge: .bottom).combined(with: .opacity))
                .animation(.spring(), value: viewModel.errorMessage)
            } else if let message = viewModel.successMessage {
                MessageBannerView(message: message, type: .success) {
                    viewModel.successMessage = nil
                }
                .transition(.move(edge: .bottom).combined(with: .opacity))
                .animation(.spring(), value: viewModel.successMessage)
            }
        }
    }
}

// MARK: - Vista de estado vacío
struct EmptyCardsView: View {
    let onAddCard: () -> Void

    var body: some View {
        VStack(spacing: 24) {
            Spacer()

            // Ícono animado
            ZStack {
                Circle()
                    .fill(Color.saetaBlue.opacity(0.1))
                    .frame(width: 120, height: 120)

                Image(systemName: "creditcard.fill")
                    .font(.system(size: 50))
                    .foregroundColor(.saetaBlue)
            }

            VStack(spacing: 8) {
                Text("No tenés tarjetas")
                    .font(.title2)
                    .fontWeight(.semibold)

                Text("Agregá tu tarjeta SAETA para consultar el saldo rápidamente")
                    .font(.subheadline)
                    .foregroundColor(.secondary)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 40)
            }

            Button(action: onAddCard) {
                HStack {
                    Image(systemName: "plus.circle.fill")
                    Text("Agregar Tarjeta")
                }
                .font(.headline)
                .foregroundColor(.white)
                .padding(.horizontal, 30)
                .padding(.vertical, 14)
                .background(Color.saetaBlue)
                .clipShape(Capsule())
                .shadow(color: .saetaBlue.opacity(0.4), radius: 8, y: 4)
            }

            Spacer()
        }
    }
}

// MARK: - Encabezado de saldo total
struct TotalBalanceHeaderView: View {
    @EnvironmentObject var viewModel: CardListViewModel

    private var totalBalance: Double? {
        let balances = viewModel.cards.compactMap { $0.balance }
        guard !balances.isEmpty else { return nil }
        return balances.reduce(0, +)
    }

    var body: some View {
        if let total = totalBalance {
            HStack {
                VStack(alignment: .leading, spacing: 4) {
                    Text("Saldo total")
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                    Text(formatCurrency(total))
                        .font(.largeTitle)
                        .fontWeight(.bold)
                        .foregroundColor(.primary)
                }
                Spacer()

                VStack(alignment: .trailing, spacing: 4) {
                    Text("\(viewModel.cards.count) tarjeta\(viewModel.cards.count == 1 ? "" : "s")")
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
            }
            .padding()
            .background(Color.cardBackground)
            .clipShape(RoundedRectangle(cornerRadius: 16))
            .padding(.horizontal)
        }
    }

    private func formatCurrency(_ value: Double) -> String {
        let formatter = NumberFormatter()
        formatter.locale = Locale(identifier: "es_AR")
        formatter.numberStyle = .currency
        formatter.currencySymbol = "$"
        return formatter.string(from: NSNumber(value: value)) ?? "$\(value)"
    }
}

// MARK: - Fila de tarjeta en la lista
struct CardRowView: View {
    let card: SAETACard
    @EnvironmentObject var viewModel: CardListViewModel
    @State private var isRefreshing = false

    var body: some View {
        ZStack {
            // Fondo con gradiente según tipo de tarjeta
            SAETAGradient.gradient(for: card.type)
                .clipShape(RoundedRectangle(cornerRadius: 20))

            VStack(alignment: .leading, spacing: 0) {
                // Header de la tarjeta
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(card.alias)
                            .font(.headline)
                            .fontWeight(.semibold)
                            .foregroundColor(.white)

                        Text(card.type.description)
                            .font(.caption)
                            .foregroundColor(.white.opacity(0.8))
                    }

                    Spacer()

                    // Logo SAETA simplificado
                    VStack {
                        Image(systemName: "bus.fill")
                            .font(.title2)
                            .foregroundColor(.white.opacity(0.9))
                        Text("SAETA")
                            .font(.caption2)
                            .fontWeight(.bold)
                            .foregroundColor(.white.opacity(0.9))
                    }
                }
                .padding(.horizontal, 20)
                .padding(.top, 20)

                Spacer()

                // Saldo principal
                HStack(alignment: .bottom) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("SALDO DISPONIBLE")
                            .font(.system(size: 9, weight: .medium))
                            .foregroundColor(.white.opacity(0.7))
                            .tracking(1.5)

                        if let balance = card.balance {
                            Text(card.formattedBalance)
                                .font(.system(size: 32, weight: .bold, design: .rounded))
                                .foregroundColor(.white)
                        } else {
                            Text("Sin consultar")
                                .font(.title2)
                                .fontWeight(.semibold)
                                .foregroundColor(.white.opacity(0.7))
                        }
                    }

                    Spacer()

                    // Botón de actualizar
                    Button {
                        Task {
                            isRefreshing = true
                            await viewModel.refreshBalance(for: card)
                            isRefreshing = false
                        }
                    } label: {
                        Image(systemName: "arrow.clockwise.circle.fill")
                            .font(.title2)
                            .foregroundColor(.white.opacity(0.9))
                            .rotationEffect(.degrees(isRefreshing ? 360 : 0))
                            .animation(isRefreshing ? .linear(duration: 1).repeatForever(autoreverses: false) : .default, value: isRefreshing)
                    }
                }
                .padding(.horizontal, 20)

                // Número de tarjeta (parcialmente oculto por privacidad)
                HStack {
                    Text("N° \(maskedCardNumber(card.cardNumber))")
                        .font(.system(size: 12, design: .monospaced))
                        .foregroundColor(.white.opacity(0.7))

                    Spacer()

                    // Indicador de NFC
                    if card.nfcUID != nil {
                        HStack(spacing: 4) {
                            Image(systemName: "wave.3.right")
                                .font(.system(size: 10))
                            Text("NFC")
                                .font(.system(size: 10, weight: .medium))
                        }
                        .foregroundColor(.white.opacity(0.7))
                    }
                }
                .padding(.horizontal, 20)

                // Última actualización
                if card.lastUpdated != nil {
                    Text(card.lastUpdatedDescription)
                        .font(.system(size: 10))
                        .foregroundColor(.white.opacity(0.6))
                        .padding(.horizontal, 20)
                        .padding(.bottom, 4)
                }

                // Alerta de saldo posiblemente desactualizado
                if card.balanceMightBeStale && card.balance != nil {
                    HStack(spacing: 4) {
                        Image(systemName: "exclamationmark.triangle.fill")
                            .font(.system(size: 10))
                        Text("Datos pueden estar desactualizados (+48h)")
                            .font(.system(size: 10))
                    }
                    .foregroundColor(.yellow.opacity(0.9))
                    .padding(.horizontal, 20)
                    .padding(.bottom, 4)
                }

                Color.clear.frame(height: 16)
            }
        }
        .frame(height: 180)
        .shadow(color: .black.opacity(0.15), radius: 10, y: 5)
    }

    /// Enmascara el número de tarjeta mostrando solo los últimos 4 dígitos
    private func maskedCardNumber(_ number: String) -> String {
        let digits = number.filter { $0.isNumber }
        guard digits.count >= 4 else { return number }
        let last4 = String(digits.suffix(4))
        let masked = String(repeating: "•", count: max(0, digits.count - 4))
        return masked + last4
    }
}

// MARK: - Overlay de carga
struct LoadingOverlayView: View {
    var body: some View {
        ZStack {
            Color.black.opacity(0.3)
                .ignoresSafeArea()

            VStack(spacing: 16) {
                ProgressView()
                    .scaleEffect(1.5)
                    .tint(.white)

                Text("Consultando saldo...")
                    .font(.subheadline)
                    .foregroundColor(.white)
                    .fontWeight(.medium)
            }
            .padding(30)
            .background(Color.black.opacity(0.7))
            .clipShape(RoundedRectangle(cornerRadius: 16))
        }
    }
}

// MARK: - Banner de mensaje (error/éxito)
struct MessageBannerView: View {
    enum MessageType { case error, success }

    let message: String
    let type: MessageType
    let onDismiss: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: type == .error ? "exclamationmark.circle.fill" : "checkmark.circle.fill")
                .font(.title3)
                .foregroundColor(type == .error ? .red : .green)

            Text(message)
                .font(.subheadline)
                .foregroundColor(.primary)
                .fixedSize(horizontal: false, vertical: true)

            Spacer()

            Button(action: onDismiss) {
                Image(systemName: "xmark.circle.fill")
                    .foregroundColor(.secondary)
            }
        }
        .padding()
        .background(Color.cardBackground)
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .shadow(color: .black.opacity(0.1), radius: 8, y: 4)
        .padding()
        .onAppear {
            // Auto-dismiss después de 4 segundos
            DispatchQueue.main.asyncAfter(deadline: .now() + 4) {
                onDismiss()
            }
        }
    }
}
