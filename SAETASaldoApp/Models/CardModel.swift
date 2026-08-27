// CardModel.swift
// SAETA Saldo App - Modelos de datos para tarjetas SAETA

import Foundation

// MARK: - Tipo de tarjeta SAETA
enum CardType: String, Codable, CaseIterable {
    case azul = "Azul"    // Tarjeta común (pago estándar)
    case verde = "Verde"  // Tarjeta de beneficios (jubilados, estudiantes, discapacidad)
    case unknown = "Desconocida"

    var color: String {
        switch self {
        case .azul:    return "blue"
        case .verde:   return "green"
        case .unknown: return "gray"
        }
    }

    var description: String {
        switch self {
        case .azul:    return "Tarjeta Común"
        case .verde:   return "Tarjeta Beneficios"
        case .unknown: return "Tipo no identificado"
        }
    }

    var benefits: [String] {
        switch self {
        case .azul:
            return ["Transbordo universal (60 min)", "Saldo de emergencia (2 boletos)", "Recupero de saldo por extravío"]
        case .verde:
            return ["Gratuidad o descuento especial", "Transbordo con beneficio activo", "Para jubilados, estudiantes y discapacidad"]
        case .unknown:
            return []
        }
    }
}

// MARK: - Movimiento / Transacción
struct CardTransaction: Identifiable, Codable {
    let id: UUID
    let date: Date
    let description: String
    let amount: Double      // Positivo = recarga, Negativo = débito
    let lineNumber: String? // Número de línea de colectivo (si aplica)
    let type: TransactionType

    enum TransactionType: String, Codable {
        case debit    = "Débito"    // Pago de pasaje
        case recharge = "Recarga"   // Carga de saldo
        case transfer = "Transbordo" // Transbordo gratuito
        case emergency = "Emergencia" // Saldo de emergencia
    }

    var formattedAmount: String {
        let sign = amount >= 0 ? "+" : ""
        return "\(sign)$\(String(format: "%.2f", amount))"
    }

    var formattedDate: String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "es_AR")
        formatter.dateStyle = .medium
        formatter.timeStyle = .short
        return formatter.string(from: date)
    }
}

// MARK: - Modelo principal de tarjeta
struct SAETACard: Identifiable, Codable {
    let id: UUID
    var cardNumber: String       // Número impreso en la tarjeta (identificador del sistema SAETA)
    var nfcUID: String?          // UID NFC del chip (identificador hardware)
    var alias: String            // Nombre personalizado del usuario
    var type: CardType
    var balance: Double?         // Último saldo consultado (puede estar desactualizado 24-48h)
    var lastUpdated: Date?       // Fecha de la última consulta
    var transactions: [CardTransaction]
    var isNominated: Bool        // Si la tarjeta está nominada con DNI

    // Saldo formateado en pesos argentinos
    var formattedBalance: String {
        guard let balance = balance else { return "Sin datos" }
        let formatter = NumberFormatter()
        formatter.locale = Locale(identifier: "es_AR")
        formatter.numberStyle = .currency
        formatter.currencySymbol = "$"
        return formatter.string(from: NSNumber(value: balance)) ?? "$\(String(format: "%.2f", balance))"
    }

    // Tiempo transcurrido desde la última actualización
    var lastUpdatedDescription: String {
        guard let date = lastUpdated else { return "Nunca consultado" }
        let formatter = RelativeDateTimeFormatter()
        formatter.locale = Locale(identifier: "es_AR")
        formatter.unitsStyle = .full
        return "Actualizado \(formatter.localizedString(for: date, relativeTo: Date()))"
    }

    // Indicador de si el saldo puede estar desactualizado (> 48 horas)
    var balanceMightBeStale: Bool {
        guard let date = lastUpdated else { return true }
        let hoursSinceUpdate = Date().timeIntervalSince(date) / 3600
        return hoursSinceUpdate > 48
    }

    // Inicializador para tarjeta nueva
    init(
        id: UUID = UUID(),
        cardNumber: String,
        nfcUID: String? = nil,
        alias: String = "Mi Tarjeta",
        type: CardType = .unknown,
        balance: Double? = nil,
        lastUpdated: Date? = nil,
        transactions: [CardTransaction] = [],
        isNominated: Bool = false
    ) {
        self.id = id
        self.cardNumber = cardNumber
        self.nfcUID = nfcUID
        self.alias = alias
        self.type = type
        self.balance = balance
        self.lastUpdated = lastUpdated
        self.transactions = transactions
        self.isNominated = isNominated
    }
}

// MARK: - Resultado de consulta de saldo
struct BalanceQueryResult {
    let cardNumber: String
    let balance: Double
    let queryDate: Date
    let rawResponse: String?
}

// MARK: - Error personalizado de la app
enum SAETAError: LocalizedError {
    case nfcNotSupported
    case nfcReadFailed(String)
    case networkError(String)
    case balanceParseError
    case cardNotFound
    case captchaRequired
    case invalidCardNumber
    case serverError(Int)

    var errorDescription: String? {
        switch self {
        case .nfcNotSupported:
            return "Este dispositivo no soporta NFC"
        case .nfcReadFailed(let msg):
            return "Error al leer la tarjeta: \(msg)"
        case .networkError(let msg):
            return "Error de red: \(msg)"
        case .balanceParseError:
            return "No se pudo interpretar el saldo. El sitio web puede haber cambiado su formato."
        case .cardNotFound:
            return "Número de tarjeta no encontrado en el sistema de SAETA"
        case .captchaRequired:
            return "El sistema requiere verificación manual. Por favor consulta el saldo en saetasalta.com.ar"
        case .invalidCardNumber:
            return "El número de tarjeta ingresado no es válido"
        case .serverError(let code):
            return "Error del servidor SAETA (código \(code)). Intenta más tarde."
        }
    }

    var recoverySuggestion: String? {
        switch self {
        case .captchaRequired:
            return "El portal web de SAETA solicita una verificación de seguridad (captcha). Esto es normal y protege el sistema. Puedes consultar tu saldo manualmente en salta.miredbus.com.ar"
        case .balanceParseError:
            return "Intentá consultar el saldo directamente en salta.miredbus.com.ar"
        case .nfcNotSupported:
            return "Puedes ingresar el número de tu tarjeta manualmente"
        default:
            return nil
        }
    }
}
