// ContentView.swift
// SAETA Saldo App - Vista principal raíz de la aplicación

import SwiftUI

struct ContentView: View {
    @StateObject private var viewModel = CardListViewModel()

    var body: some View {
        TabView {
            // Tab 1: Mis Tarjetas
            NavigationStack {
                CardListView()
                    .environmentObject(viewModel)
            }
            .tabItem {
                Label("Mis Tarjetas", systemImage: "creditcard.fill")
            }

            // Tab 2: Colectivos en Vivo
            NavigationStack {
                BusTrackerView()
            }
            .tabItem {
                Label("Colectivos", systemImage: "bus.fill")
            }

            // Tab 3: Escanear
            NavigationStack {
                NFCScanView()
                    .environmentObject(viewModel)
            }
            .tabItem {
                Label("Escanear", systemImage: "wave.3.right")
            }

            // Tab 3: Info
            NavigationStack {
                InfoView()
            }
            .tabItem {
                Label("Información", systemImage: "info.circle.fill")
            }
        }
        .tint(.saetaBlue)
    }
}

#Preview {
    ContentView()
}
