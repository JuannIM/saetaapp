// BalanceService.swift
// SAETA Saldo App - Servicio de consulta de saldo vía portal SAETA/RedBus
//
// INFORMACIÓN TÉCNICA IMPORTANTE:
// ─────────────────────────────────────────────────────────────────────────────
// SAETA no tiene una API REST pública. El saldo se consulta a través del portal
// web de RedBus (salta.miredbus.com.ar) que es el proveedor tecnológico de SAETA.
//
// El sitio usa protección con captcha (imagen) para consultas anónimas.
// Esta implementación intenta hacer una consulta HTTP directa al endpoint que
// usa la web internamente. Si falla (captcha), se redirige al usuario a la web.
//
// Actualización del saldo: los datos en el portal se actualizan cada 24-48 horas
// hábiles, correspondiendo al último uso de la tarjeta en el colectivo.
//
// URL portal oficial: https://salta.miredbus.com.ar

import Foundation
import Combine

// MARK: - Servicio de consulta de saldo
actor BalanceService {

    // Base URL del portal RedBus de SAETA Salta
    private let baseURL = "https://salta.miredbus.com.ar"

    // URLSession configurada con User-Agent de navegador para evitar bloqueos básicos
    private let session: URLSession = {
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 15
        config.timeoutIntervalForResource = 30
        config.httpAdditionalHeaders = [
            "User-Agent": "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1",
            "Accept": "application/json, text/html, */*",
            "Accept-Language": "es-AR,es;q=0.9",
            "Referer": "https://salta.miredbus.com.ar/"
        ]
        return URLSession(configuration: config)
    }()

    // MARK: - Consulta de saldo principal
    /// Consulta el saldo de una tarjeta SAETA usando el número de tarjeta impreso.
    /// Retorna el saldo o lanza un SAETAError.
    func fetchBalance(for cardNumber: String) async throws -> BalanceQueryResult {
        // Validar formato del número de tarjeta
        let cleanedNumber = cardNumber.trimmingCharacters(in: .whitespacesAndNewlines)
        guard isValidCardNumber(cleanedNumber) else {
            throw SAETAError.invalidCardNumber
        }

        // Intentar consulta directa al API interno de RedBus
        do {
            return try await queryRedBusAPI(cardNumber: cleanedNumber)
        } catch SAETAError.captchaRequired {
            // Si requiere captcha, re-lanzar para que el ViewModel maneje la UI
            throw SAETAError.captchaRequired
        } catch {
            // Intentar método alternativo via web scraping del HTML
            return try await scrapeBalanceFromHTML(cardNumber: cleanedNumber)
        }
    }

    // MARK: - Consulta vía API interna de RedBus
    /// Intenta llamar al endpoint REST interno que usa la SPA de RedBus.
    /// Este endpoint puede requerir autenticación o cambiar en cualquier momento.
    private func queryRedBusAPI(cardNumber: String) async throws -> BalanceQueryResult {
        // Endpoint que usa la aplicación web de RedBus Salta internamente
        // (descubierto analizando el tráfico de red del portal)
        guard let url = URL(string: "\(baseURL)/rest/tarjeta/saldo/\(cardNumber)") else {
            throw SAETAError.networkError("URL inválida")
        }

        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue("\(baseURL)/", forHTTPHeaderField: "Referer")

        let (data, response) = try await session.data(for: request)

        guard let httpResponse = response as? HTTPURLResponse else {
            throw SAETAError.networkError("Respuesta inválida del servidor")
        }

        switch httpResponse.statusCode {
        case 200:
            return try parseAPIResponse(data: data, cardNumber: cardNumber)
        case 401, 403:
            throw SAETAError.captchaRequired
        case 404:
            throw SAETAError.cardNotFound
        case 400:
            throw SAETAError.invalidCardNumber
        default:
            throw SAETAError.serverError(httpResponse.statusCode)
        }
    }

    // MARK: - Parsear respuesta JSON de la API
    private func parseAPIResponse(data: Data, cardNumber: String) throws -> BalanceQueryResult {
        // Intentar parsear como JSON
        guard let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw SAETAError.balanceParseError
        }

        // El portal RedBus usa diferentes estructuras según la versión del API.
        // Intentamos varios campos conocidos:
        let possibleBalanceKeys = ["saldo", "balance", "saldoActual", "monto", "amount"]

        for key in possibleBalanceKeys {
            if let balanceValue = json[key] {
                var balance: Double?

                if let doubleVal = balanceValue as? Double {
                    balance = doubleVal
                } else if let intVal = balanceValue as? Int {
                    balance = Double(intVal)
                } else if let stringVal = balanceValue as? String,
                          let parsed = Double(stringVal.replacingOccurrences(of: ",", with: ".")) {
                    balance = parsed
                }

                if let finalBalance = balance {
                    return BalanceQueryResult(
                        cardNumber: cardNumber,
                        balance: finalBalance,
                        queryDate: Date(),
                        rawResponse: String(data: data, encoding: .utf8)
                    )
                }
            }
        }

        throw SAETAError.balanceParseError
    }

    // MARK: - Web scraping del HTML del portal
    /// Método de respaldo: carga la página web de consulta y extrae el saldo del HTML.
    private func scrapeBalanceFromHTML(cardNumber: String) async throws -> BalanceQueryResult {
        // La consulta de saldo en el portal web requiere:
        // 1. POST a un endpoint con el número de tarjeta
        // 2. Resolver un captcha de imagen
        // Dado que el captcha bloquea la automatización completa, este método
        // intenta un GET simple primero

        guard let url = URL(string: "\(baseURL)/consulta-saldo?tarjeta=\(cardNumber)") else {
            throw SAETAError.networkError("URL inválida")
        }

        let (data, response) = try await session.data(from: url)

        guard let httpResponse = response as? HTTPURLResponse,
              (200...299).contains(httpResponse.statusCode) else {
            throw SAETAError.networkError("El servidor de SAETA no respondió correctamente")
        }

        guard let html = String(data: data, encoding: .utf8) else {
            throw SAETAError.balanceParseError
        }

        return try parseBalanceFromHTML(html, cardNumber: cardNumber)
    }

    // MARK: - Parsear saldo del HTML
    private func parseBalanceFromHTML(_ html: String, cardNumber: String) throws -> BalanceQueryResult {
        // Buscar patrones comunes de saldo en el HTML del portal SAETA/RedBus
        // El saldo aparece típicamente como "$1234.56" o "1234,56"
        let patterns = [
            #"saldo[\"']?\s*[:=]\s*[\"']?\$?\s*([\d]+[.,][\d]{2})"#,
            #">\$\s*([\d]+[.,][\d]{2})<"#,
            #"balance[\"']?\s*[:=]\s*[\"']?([\d]+[.,][\d]{2})"#,
            #"([\d]+),([\d]{2})"#
        ]

        for pattern in patterns {
            if let range = html.range(of: pattern, options: .regularExpression),
               let match = extractFirstCapture(from: html[range]) {
                let normalized = match.replacingOccurrences(of: ",", with: ".")
                if let balance = Double(normalized) {
                    return BalanceQueryResult(
                        cardNumber: cardNumber,
                        balance: balance,
                        queryDate: Date(),
                        rawResponse: nil
                    )
                }
            }
        }

        // Si el HTML contiene referencias a captcha, informar al usuario
        if html.lowercased().contains("captcha") || html.lowercased().contains("recaptcha") {
            throw SAETAError.captchaRequired
        }

        throw SAETAError.balanceParseError
    }

    // MARK: - Regex helper
    private func extractFirstCapture(from substring: Substring) -> String? {
        let str = String(substring)
        let regex = try? NSRegularExpression(pattern: #"([\d]+[.,][\d]{2})"#)
        let range = NSRange(str.startIndex..., in: str)
        guard let match = regex?.firstMatch(in: str, range: range),
              let captureRange = Range(match.range(at: 1), in: str) else {
            return nil
        }
        return String(str[captureRange])
    }

    // MARK: - Validación de número de tarjeta
    /// Las tarjetas SAETA tienen un número numérico (generalmente 8-16 dígitos)
    func isValidCardNumber(_ number: String) -> Bool {
        let cleaned = number.filter { $0.isNumber }
        // Formato de tarjetas SAETA: números entre 6 y 16 dígitos
        return cleaned.count >= 6 && cleaned.count <= 16
    }

    // MARK: - URL para abrir el portal en Safari
    /// URL para que el usuario consulte manualmente el saldo
    func webPortalURL(for cardNumber: String? = nil) -> URL? {
        if let number = cardNumber, !number.isEmpty {
            return URL(string: "\(baseURL)/#/tarjetas")
        }
        return URL(string: baseURL)
    }
}
