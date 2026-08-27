// SAETAColors.swift
// SAETA Saldo App - Paleta de colores y tema visual de la app
// Colores inspirados en la identidad visual de SAETA Salta (azul y blanco/verde)

import SwiftUI

// MARK: - Extensión de Color con colores personalizados SAETA
extension Color {
    // Colores primarios SAETA
    static let saetaBlue = Color(red: 0.09, green: 0.46, blue: 0.82)      // Azul institucional SAETA
    static let saetaLightBlue = Color(red: 0.23, green: 0.61, blue: 0.92) // Azul claro
    static let saetaGreen = Color(red: 0.18, green: 0.65, blue: 0.32)     // Verde para tarjeta de beneficios
    static let saetaLightGreen = Color(red: 0.30, green: 0.78, blue: 0.45)

    // Colores de estado
    static let balanceLow = Color(red: 0.90, green: 0.30, blue: 0.26)     // Rojo para saldo bajo
    static let balanceMedium = Color(red: 0.95, green: 0.65, blue: 0.10)  // Naranja para saldo medio
    static let balanceGood = Color(red: 0.18, green: 0.65, blue: 0.32)    // Verde para saldo OK

    // Fondo de la app
    static let appBackground = Color(UIColor.systemGroupedBackground)
    static let cardBackground = Color(UIColor.secondarySystemGroupedBackground)
}

// MARK: - Gradientes SAETA
struct SAETAGradient {
    // Gradiente principal para tarjeta azul
    static var blueCard: LinearGradient {
        LinearGradient(
            colors: [Color.saetaBlue, Color.saetaLightBlue],
            startPoint: .topLeading,
            endPoint: .bottomTrailing
        )
    }

    // Gradiente para tarjeta verde (beneficios)
    static var greenCard: LinearGradient {
        LinearGradient(
            colors: [Color.saetaGreen, Color.saetaLightGreen],
            startPoint: .topLeading,
            endPoint: .bottomTrailing
        )
    }

    // Gradiente gris para tarjeta desconocida
    static var grayCard: LinearGradient {
        LinearGradient(
            colors: [Color.gray, Color.gray.opacity(0.7)],
            startPoint: .topLeading,
            endPoint: .bottomTrailing
        )
    }

    static func gradient(for type: CardType) -> LinearGradient {
        switch type {
        case .azul:    return blueCard
        case .verde:   return greenCard
        case .unknown: return grayCard
        }
    }
}

// MARK: - Color de saldo según monto
extension Double {
    /// Retorna el color apropiado según el nivel de saldo
    var balanceColor: Color {
        switch self {
        case ..<0:        return .balanceLow
        case 0..<500:     return .balanceLow     // Menos de $500 = bajo
        case 500..<1500:  return .balanceMedium  // Entre $500 y $1500 = medio
        default:          return .balanceGood    // Más de $1500 = OK
        }
    }
}
