// BusTrackerView.swift
// SAETA Saldo App - Pantalla principal para ver colectivos en tiempo real

import SwiftUI

struct BusTrackerView: View {
    @State private var selectedMode: BusViewMode = .mapa
    @State private var isLoading: Bool = true
    @State private var reloadTrigger: Int = 0

    var body: some View {
        VStack(spacing: 0) {
            // Selector de modo (Mapa vs Cuándo Llega)
            Picker("Modo", selection: $selectedMode) {
                ForEach(BusViewMode.allCases) { mode in
                    Label(mode.title, systemImage: mode.icon)
                        .tag(mode)
                }
            }
            .pickerStyle(.segmented)
            .padding(.horizontal, 16)
            .padding(.top, 10)
            .padding(.bottom, 8)
            .background(Color(UIColor.systemBackground))

            // Banner informativo sutil
            HStack(spacing: 8) {
                Image(systemName: "location.fill")
                    .font(.caption2)
                    .foregroundColor(.saetaBlue)

                Text(selectedMode == .mapa
                     ? "Elegí el corredor o línea para ver las unidades en movimiento."
                     : "Seleccioná tu parada o línea para ver los tiempos de llegada.")
                    .font(.caption)
                    .foregroundColor(.secondary)

                Spacer()
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 6)
            .background(Color.saetaBlue.opacity(0.08))

            // Contenedor del Mapa / Vista Web con indicador de carga
            ZStack {
                SAETABusMapView(
                    mode: selectedMode,
                    isLoading: $isLoading,
                    reloadTrigger: reloadTrigger
                )

                // Overlay de carga nativo
                if isLoading {
                    ZStack {
                        Color(UIColor.systemGroupedBackground)
                            .ignoresSafeArea()

                        VStack(spacing: 14) {
                            ProgressView()
                                .scaleEffect(1.3)
                                .tint(.saetaBlue)

                            Text("Cargando mapa de unidades...")
                                .font(.subheadline.weight(.medium))
                                .foregroundColor(.primary)

                            Text("Conectando con el GPS de SAETA")
                                .font(.caption)
                                .foregroundColor(.secondary)
                        }
                        .padding(24)
                        .background(.regularMaterial)
                        .clipShape(RoundedRectangle(cornerRadius: 16))
                        .shadow(color: .black.opacity(0.06), radius: 10, y: 4)
                    }
                    .transition(.opacity)
                }
            }
        }
        .navigationTitle("Colectivos SAETA")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                Button {
                    reloadTrigger += 1
                } label: {
                    Image(systemName: "arrow.clockwise")
                        .foregroundColor(.saetaBlue)
                }
            }
        }
    }
}

#Preview {
    NavigationStack {
        BusTrackerView()
    }
}
