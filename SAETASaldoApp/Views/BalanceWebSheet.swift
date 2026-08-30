// BalanceWebSheet.swift
// SAETA Saldo App - Sheet de consulta de saldo con aspecto nativo

import SwiftUI

/// Sheet que envuelve el SAETAWebView con:
/// - Barra superior nativa de la app (no del portal)
/// - Indicador de carga animado mientras el WebView inicializa
/// - Mensaje de éxito automático al detectar el saldo
/// - Instrucciones claras para el usuario
struct BalanceWebSheet: View {
    let card: SAETACard
    let onClose: () -> Void
    let onBalanceFound: (Double) -> Void

    @State private var isLoading = true
    @State private var balanceDetected = false
    @State private var detectedBalance: Double = 0

    var body: some View {
        NavigationView {
            ZStack {
                // WebView (siempre presente debajo)
                SAETAWebView(cardNumber: card.cardNumber) { balance in
                    detectedBalance = balance
                    balanceDetected = true
                    // Pequeño delay para que el usuario vea el éxito
                    DispatchQueue.main.asyncAfter(deadline: .now() + 1.2) {
                        onBalanceFound(balance)
                    }
                }
                .opacity(isLoading ? 0 : 1)
                .onAppear {
                    // Dar tiempo a que el WebView cargue Vue
                    DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) {
                        withAnimation(.easeIn(duration: 0.3)) {
                            isLoading = false
                        }
                    }
                }

                // Indicador de carga inicial
                if isLoading {
                    VStack(spacing: 20) {
                        ProgressView()
                            .scaleEffect(1.5)
                            .tint(.saetaBlue)

                        Text("Cargando portal SAETA...")
                            .font(.subheadline)
                            .foregroundColor(.secondary)

                        Text("Tarjeta N° \(card.cardNumber)")
                            .font(.caption)
                            .foregroundColor(.secondary.opacity(0.7))
                            .padding(.horizontal, 8)
                            .padding(.vertical, 4)
                            .background(Color.secondary.opacity(0.08))
                            .clipShape(Capsule())
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .background(Color.appBackground)
                }

                // Banner de éxito al detectar el saldo
                if balanceDetected {
                    VStack {
                        HStack(spacing: 12) {
                            Image(systemName: "checkmark.circle.fill")
                                .foregroundColor(.green)
                                .font(.title2)

                            VStack(alignment: .leading, spacing: 2) {
                                Text("¡Saldo detectado!")
                                    .font(.subheadline.weight(.semibold))
                                Text("$\(String(format: "%.2f", detectedBalance))")
                                    .font(.caption)
                                    .foregroundColor(.secondary)
                            }
                            Spacer()
                        }
                        .padding()
                        .background(.regularMaterial)
                        .clipShape(RoundedRectangle(cornerRadius: 14))
                        .padding()

                        Spacer()
                    }
                    .transition(.move(edge: .top).combined(with: .opacity))
                }
            }
            .animation(.spring(response: 0.3), value: balanceDetected)
            .navigationTitle("Consultar Saldo")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button {
                        onClose()
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .foregroundStyle(.secondary)
                            .font(.title3)
                    }
                }

                ToolbarItem(placement: .principal) {
                    HStack(spacing: 6) {
                        Image(systemName: "creditcard.fill")
                            .foregroundColor(.saetaBlue)
                            .font(.caption)
                        Text("Portal SAETA")
                            .font(.subheadline.weight(.semibold))
                    }
                }
            }
            // Instrucciones debajo de la toolbar (solo cuando carga)
            .safeAreaInset(edge: .top) {
                if !isLoading && !balanceDetected {
                    HStack(spacing: 8) {
                        Image(systemName: "info.circle.fill")
                            .foregroundColor(.saetaBlue)
                            .font(.caption)
                        Text("Completá el captcha y tocá Consultar. El saldo se guardará automáticamente.")
                            .font(.caption2)
                            .foregroundColor(.secondary)
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 10)
                    .background(.regularMaterial)
                }
            }
        }
    }
}
