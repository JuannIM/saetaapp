// NFCScanView.swift
// SAETA Saldo App - Vista dedicada al escaneo NFC de tarjetas (Tab 2)
//
// Esta vista permite al usuario escanear su tarjeta SAETA con NFC
// y luego vincularla a una tarjeta existente o crear una nueva.

import SwiftUI
import CoreNFC

struct NFCScanView: View {
    @EnvironmentObject var viewModel: CardListViewModel
    @StateObject private var nfcService = NFCService()
    @State private var scanState: ScanState = .idle
    @State private var detectedUID: String? = nil
    @State private var matchedCard: SAETACard? = nil
    @State private var showingAddCard = false
    @State private var errorMessage: String? = nil

    enum ScanState {
        case idle
        case scanning
        case detected
        case notFound
        case error
    }

    // Animación de las ondas NFC
    @State private var waveScale: CGFloat = 1.0


    var body: some View {
        ScrollView {
            VStack(spacing: 30) {
                Spacer(minLength: 20)

                // MARK: - Ícono de escaneo animado
                ZStack {
                    // Ondas de NFC animadas
                    ForEach(0..<3) { i in
                        RoundedRectangle(cornerRadius: 20)
                            .stroke(stateColor.opacity(scanState == .scanning ? 0.6 - Double(i) * 0.15 : 0.1),
                                    lineWidth: 2)
                            .frame(width: 100 + CGFloat(i * 30), height: 140 + CGFloat(i * 30))
                            .scaleEffect(scanState == .scanning ? waveScale + CGFloat(i) * 0.1 : 1.0)
                            .animation(
                                scanState == .scanning ?
                                    .easeOut(duration: 1.5).repeatForever(autoreverses: false).delay(Double(i) * 0.5) :
                                    .default,
                                value: waveScale
                            )
                    }

                    // Imagen del iPhone con tarjeta
                    ZStack {
                        RoundedRectangle(cornerRadius: 18)
                            .fill(stateColor.opacity(0.1))
                            .frame(width: 100, height: 140)

                        VStack(spacing: 8) {
                            Image(systemName: stateIcon)
                                .font(.system(size: 44))
                                .foregroundColor(stateColor)

                            Image(systemName: "creditcard.fill")
                                .font(.title3)
                                .foregroundColor(stateColor.opacity(0.7))
                        }
                    }
                }
                .frame(height: 220)
                .onAppear {
                    withAnimation(.easeInOut(duration: 1.5).repeatForever(autoreverses: true)) {
                        waveScale = 1.15
                    }
                }

                // MARK: - Texto de estado
                VStack(spacing: 8) {
                    Text(stateTitle)
                        .font(.title2)
                        .fontWeight(.bold)
                        .multilineTextAlignment(.center)

                    Text(stateDescription)
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 40)
                }

                // MARK: - Resultado de detección
                if let uid = detectedUID {
                    VStack(spacing: 8) {
                        Text("UID Detectado:")
                            .font(.caption)
                            .foregroundColor(.secondary)

                        Text(uid)
                            .font(.system(.body, design: .monospaced))
                            .fontWeight(.medium)
                            .padding(.horizontal, 16)
                            .padding(.vertical, 8)
                            .background(Color.saetaBlue.opacity(0.1))
                            .clipShape(RoundedRectangle(cornerRadius: 8))
                    }
                }

                // MARK: - Tarjeta encontrada
                if let card = matchedCard {
                    VStack(spacing: 4) {
                        Text("Tarjeta identificada:")
                            .font(.caption)
                            .foregroundColor(.secondary)

                        NavigationLink(destination: CardDetailView(card: card).environmentObject(viewModel)) {
                            HStack {
                                Image(systemName: "creditcard.fill")
                                    .foregroundColor(.saetaBlue)
                                Text(card.alias)
                                    .font(.headline)
                                    .foregroundColor(.primary)
                                Spacer()
                                Image(systemName: "chevron.right")
                                    .foregroundColor(.secondary)
                                    .font(.caption)
                            }
                            .padding()
                            .background(Color.cardBackground)
                            .clipShape(RoundedRectangle(cornerRadius: 12))
                            .padding(.horizontal)
                        }
                        .buttonStyle(PlainButtonStyle())
                    }
                }

                // MARK: - Botón de acción
                actionButton

                // MARK: - Instrucciones
                if scanState == .idle {
                    InstructionsView()
                        .padding(.horizontal)
                }

                // MARK: - Aviso dispositivos no compatibles
                if !NFCService.isNFCAvailable {
                    NFCUnavailableView()
                        .padding(.horizontal)
                }

                Spacer(minLength: 20)
            }
        }
        .navigationTitle("Escanear Tarjeta")
        .navigationBarTitleDisplayMode(.large)
        .background(Color.appBackground)
        .sheet(isPresented: $showingAddCard) {
            if let uid = detectedUID {
                AddCardView { newCard in
                    viewModel.addCard(newCard)
                }
            }
        }
    }

    // MARK: - Colores e íconos según estado
    private var stateColor: Color {
        switch scanState {
        case .idle:     return .saetaBlue
        case .scanning: return .saetaBlue
        case .detected: return .saetaGreen
        case .notFound: return .orange
        case .error:    return .red
        }
    }

    private var stateIcon: String {
        switch scanState {
        case .idle:     return "wave.3.right"
        case .scanning: return "antenna.radiowaves.left.and.right"
        case .detected: return "checkmark.circle.fill"
        case .notFound: return "questionmark.circle.fill"
        case .error:    return "exclamationmark.triangle.fill"
        }
    }

    private var stateTitle: String {
        switch scanState {
        case .idle:     return "Listo para escanear"
        case .scanning: return "Buscando tarjeta..."
        case .detected: return "¡Tarjeta detectada!"
        case .notFound: return "Tarjeta nueva"
        case .error:    return "Error de lectura"
        }
    }

    private var stateDescription: String {
        switch scanState {
        case .idle:
            return NFCService.isNFCAvailable ?
                "Tocá el botón y acercá tu tarjeta SAETA a la parte trasera del iPhone" :
                "Tu dispositivo no soporta NFC. Ingresá el número manualmente en 'Mis Tarjetas'."
        case .scanning:
            return "Acercá la tarjeta SAETA al lomo del iPhone hasta escuchar el sonido"
        case .detected:
            return matchedCard != nil ? "La tarjeta ya está registrada en la app" : "Tarjeta SAETA detectada correctamente"
        case .notFound:
            return "Esta tarjeta no está guardada aún. ¿Querés agregarla?"
        case .error:
            return errorMessage ?? "Ocurrió un error al leer la tarjeta. Intentá de nuevo."
        }
    }

    // MARK: - Botón de acción contextual
    @ViewBuilder
    private var actionButton: some View {
        switch scanState {
        case .idle, .error:
            Button(action: startScan) {
                HStack {
                    Image(systemName: "wave.3.right.circle.fill")
                    Text(NFCService.isNFCAvailable ? "Iniciar Escaneo NFC" : "NFC no disponible")
                }
                .font(.headline)
                .foregroundColor(.white)
                .frame(maxWidth: .infinity)
                .padding()
                .background(NFCService.isNFCAvailable ? Color.saetaBlue : Color.gray)
                .clipShape(Capsule())
                .shadow(color: Color.saetaBlue.opacity(0.3), radius: 8, y: 4)
            }
            .disabled(!NFCService.isNFCAvailable)
            .padding(.horizontal, 40)

        case .scanning:
            HStack {
                ProgressView()
                    .tint(.saetaBlue)
                Text("Leyendo tarjeta...")
                    .foregroundColor(.saetaBlue)
            }

        case .detected:
            VStack(spacing: 12) {
                if matchedCard == nil {
                    Button {
                        showingAddCard = true
                    } label: {
                        HStack {
                            Image(systemName: "plus.circle.fill")
                            Text("Agregar esta Tarjeta")
                        }
                        .font(.headline)
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .padding()
                        .background(Color.saetaGreen)
                        .clipShape(Capsule())
                    }
                    .padding(.horizontal, 40)
                }

                Button(action: resetScan) {
                    Text("Escanear otra tarjeta")
                        .font(.subheadline)
                        .foregroundColor(.saetaBlue)
                }
            }

        case .notFound:
            VStack(spacing: 12) {
                Button {
                    showingAddCard = true
                } label: {
                    HStack {
                        Image(systemName: "plus.circle.fill")
                        Text("Agregar Tarjeta")
                    }
                    .font(.headline)
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .padding()
                    .background(Color.orange)
                    .clipShape(Capsule())
                }
                .padding(.horizontal, 40)

                Button(action: resetScan) {
                    Text("Cancelar")
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                }
            }
        }
    }

    // MARK: - Iniciar escaneo NFC
    private func startScan() {
        scanState = .scanning
        errorMessage = nil
        detectedUID = nil
        matchedCard = nil

        Task { @MainActor in
            await withCheckedContinuation { continuation in
                nfcService.startReading { result in
                    // Todas las mutaciones de @State se hacen en el MainActor
                    Task { @MainActor in
                        switch result {
                        case .success(let nfcResult):
                            detectedUID = nfcResult.uid

                            // Buscar si ya existe una tarjeta con ese UID
                            matchedCard = viewModel.cards.first { $0.nfcUID == nfcResult.uid }

                            if matchedCard != nil {
                                scanState = .detected
                            } else {
                                scanState = .notFound
                            }

                        case .failure(let error):
                            if case .nfcReadFailed(let msg) = error, msg.contains("cancelada") {
                                scanState = .idle
                            } else {
                                scanState = .error
                                errorMessage = error.errorDescription
                            }
                        }
                        continuation.resume()
                    }
                }
            }
        }
    }

    private func resetScan() {
        withAnimation(.spring()) {
            scanState = .idle
            detectedUID = nil
            matchedCard = nil
            errorMessage = nil
        }
    }
}

// MARK: - Instrucciones de uso NFC
struct InstructionsView: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("¿Cómo funciona?")
                .font(.headline)

            ForEach(instructions, id: \.step) { item in
                HStack(alignment: .top, spacing: 12) {
                    ZStack {
                        Circle()
                            .fill(Color.saetaBlue.opacity(0.15))
                            .frame(width: 28, height: 28)
                        Text(item.step)
                            .font(.caption)
                            .fontWeight(.bold)
                            .foregroundColor(.saetaBlue)
                    }

                    VStack(alignment: .leading, spacing: 2) {
                        Text(item.title)
                            .font(.subheadline)
                            .fontWeight(.medium)
                        Text(item.description)
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                }
            }
        }
        .padding()
        .background(Color.cardBackground)
        .clipShape(RoundedRectangle(cornerRadius: 14))
    }

    private struct Instruction {
        let step: String
        let title: String
        let description: String
    }

    private let instructions = [
        Instruction(step: "1", title: "Tocá 'Iniciar Escaneo NFC'",
                    description: "El iPhone activará el lector NFC"),
        Instruction(step: "2", title: "Acercá tu tarjeta SAETA",
                    description: "Apoyá la tarjeta en la parte trasera superior del iPhone"),
        Instruction(step: "3", title: "Esperá el sonido de confirmación",
                    description: "La app detectará automáticamente tu tarjeta"),
        Instruction(step: "4", title: "Consulta tu saldo",
                    description: "El saldo se consulta en el portal oficial de SAETA")
    ]
}

// MARK: - Vista cuando NFC no está disponible
struct NFCUnavailableView: View {
    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: "exclamationmark.triangle.fill")
                .foregroundColor(.orange)

            VStack(alignment: .leading, spacing: 2) {
                Text("NFC no disponible")
                    .font(.subheadline)
                    .fontWeight(.semibold)

                Text("Tu iPhone no soporta lectura NFC o el NFC está desactivado. Podés agregar tu tarjeta manualmente en 'Mis Tarjetas' ingresando el número impreso en el plástico.")
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
        }
        .padding()
        .background(Color.orange.opacity(0.1))
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }
}
