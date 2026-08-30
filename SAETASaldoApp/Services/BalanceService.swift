// BalanceService.swift
// SAETA Saldo App - Servicio de consulta de saldo vía portal SAETA/RedBus
//
// INFORMACIÓN TÉCNICA REAL (verificada 2026-08):
// ─────────────────────────────────────────────────────────────────────────────
// • SAETA NO tiene API REST pública.
// • El portal https://salta.miredbus.com.ar usa un certificado SSL con CA
//   autofirmada en la cadena → iOS rechaza la conexión via URLSession/ATS.
// • La consulta de saldo funciona en el portal web (WKWebView puede
//   aceptar ese certificado con el delegate apropiado).
// • El saldo en el portal se actualiza cada 24-48 horas hábiles.
//
// ESTRATEGIA:
//   La consulta real se hace en SAETAWebView (WKWebView con JS injection).
//   Este actor solo coordina el estado y la lógica de validación.

import Foundation

// MARK: - Servicio de consulta de saldo
actor BalanceService {

    // URL del portal oficial RedBus/SAETA Salta
    static let portalURL = "https://salta.miredbus.com.ar"

    // MARK: - Construir URL para consulta directa con número de tarjeta
    /// Devuelve la URL del portal para que el WKWebView la cargue directamente.
    func webPortalURL(for cardNumber: String?) -> URL? {
        // El portal carga como SPA (Single Page App); el número se ingresa en la UI
        return URL(string: Self.portalURL)
    }

    // MARK: - Validación de número de tarjeta
    /// Las tarjetas SAETA tienen un número numérico (generalmente 6 a 16 dígitos)
    func isValidCardNumber(_ number: String) -> Bool {
        let cleaned = number.filter { $0.isNumber }
        return cleaned.count >= 6 && cleaned.count <= 16
    }
}
