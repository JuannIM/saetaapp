// NFCService.swift
// SAETA Saldo App - Servicio de lectura NFC para tarjetas SAETA
//
// La tarjeta física SAETA usa tecnología NFC contactless (ISO 14443 / MIFARE).
// iOS no puede leer el saldo directamente del chip (está encriptado y protegido).
// Lo que SÍ podemos hacer: leer el UID del chip y usarlo para identificar la tarjeta.
//
// El flujo real es:
//   1. El usuario acerca la tarjeta al iPhone
//   2. La app lee el UID (identificador único del chip hardware)
//   3. Con ese UID (o el número de tarjeta impreso), se consulta el saldo en el portal SAETA
//
// NOTA TÉCNICA: No existe una relación pública documentada entre el UID NFC y el número
// de tarjeta impreso en el plástico. La app almacena el UID para reconocimiento futuro,
// pero el usuario debe asociar manualmente el número de tarjeta la primera vez.

import CoreNFC
import Combine

// MARK: - Resultado de lectura NFC
struct NFCReadResult {
    let uid: String           // UID en hexadecimal (ej: "A1B2C3D4")
    let tagType: NFCTagType
    let rawIdentifier: Data

    enum NFCTagType: String {
        case miFare = "MIFARE"
        case iso7816 = "ISO 7816"
        case iso15693 = "ISO 15693"
        case feliCa = "FeliCa"
        case unknown = "Desconocido"
    }
}

// MARK: - Servicio NFC
@MainActor
class NFCService: NSObject, ObservableObject {
    // Estado de la lectura NFC
    @Published var isScanning = false
    @Published var lastReadResult: NFCReadResult?
    @Published var error: SAETAError?

    // Sesión NFC activa
    private var session: NFCTagReaderSession?

    // Callback para comunicar el resultado al ViewModel
    private var completion: ((Result<NFCReadResult, SAETAError>) -> Void)?

    // MARK: - Verificación de soporte NFC
    static var isNFCAvailable: Bool {
        NFCTagReaderSession.readingAvailable
    }

    // MARK: - Iniciar lectura NFC
    /// Inicia una sesión de lectura NFC para identificar la tarjeta SAETA.
    /// El chip de la tarjeta SAETA es del tipo MIFARE (ISO 14443-A).
    func startReading(completion: @escaping (Result<NFCReadResult, SAETAError>) -> Void) {
        guard Self.isNFCAvailable else {
            completion(.failure(.nfcNotSupported))
            return
        }

        self.completion = completion
        self.isScanning = true

        // Crear sesión para leer tags ISO 14443 (incluye MIFARE usado por SAETA)
        session = NFCTagReaderSession(
            pollingOption: [.iso14443, .iso15693],
            delegate: self,
            queue: DispatchQueue.global(qos: .userInteractive)
        )

        // Mensaje que verá el usuario en el modal de iOS
        session?.alertMessage = "Acercá tu tarjeta SAETA a la parte trasera del iPhone"
        session?.begin()
    }

    // MARK: - Cancelar lectura
    func cancelReading() {
        session?.invalidate()
        session = nil
        isScanning = false
    }
}

// MARK: - Implementación del delegado NFCTagReaderSession
extension NFCService: NFCTagReaderSessionDelegate {

    // Método requerido: la sesión NFC se activó correctamente
    nonisolated func tagReaderSessionDidBecomeActive(_ session: NFCTagReaderSession) {
        // No se necesita acción aquí; la sesión ya está activa y buscando tags
    }

    // La sesión se invalidó (por error o al completarse)
    nonisolated func tagReaderSession(_ session: NFCTagReaderSession, didInvalidateWithError error: Error) {
        let nfcError = error as? NFCReaderError
        let errorCode = nfcError?.code

        Task { @MainActor in
            self.isScanning = false
            self.session = nil

            // Código .readerSessionInvalidationErrorUserCanceled = usuario canceló (no es error real)
            if errorCode == .readerSessionInvalidationErrorUserCanceled {
                self.completion?(.failure(.nfcReadFailed("Lectura cancelada por el usuario")))
            } else if errorCode == .readerSessionInvalidationErrorSessionTimeout {
                self.completion?(.failure(.nfcReadFailed("Tiempo de espera agotado. Acercá la tarjeta más rápido.")))
            } else {
                self.completion?(.failure(.nfcReadFailed(error.localizedDescription)))
            }
            self.completion = nil
        }
    }

    // Se detectaron tags NFC
    nonisolated func tagReaderSession(_ session: NFCTagReaderSession, didDetect tags: [NFCTag]) {
        guard let firstTag = tags.first else { return }

        // Conectarse al primer tag detectado
        session.connect(to: firstTag) { [weak self] error in
            guard let self = self else { return }

            if let error = error {
                session.invalidate(errorMessage: "Error de conexión: \(error.localizedDescription)")
                return
            }

            // Obtener el identificador (UID) según el tipo de tag
            // extractUID es nonisolated para poder llamarse desde este closure de background
            let result = self.extractUID(from: firstTag)

            Task { @MainActor in
                switch result {
                case .success(let nfcResult):
                    session.alertMessage = "✅ Tarjeta detectada correctamente"
                    session.invalidate()
                    self.lastReadResult = nfcResult
                    self.completion?(.success(nfcResult))
                    self.completion = nil
                case .failure(let nfcError):
                    session.invalidate(errorMessage: "No se pudo leer la tarjeta. Asegurate de acercarla bien.")
                    self.error = nfcError
                    self.completion?(.failure(nfcError))
                    self.completion = nil
                }
            }
        }
    }

    // MARK: - Extraer UID del tag
    // nonisolated: se llama desde el closure de session.connect que corre en background thread
    nonisolated private func extractUID(from tag: NFCTag) -> Result<NFCReadResult, SAETAError> {
        let uidData: Data
        let tagType: NFCReadResult.NFCTagType

        switch tag {
        case .miFare(let miFareTag):
            // MIFARE es el tipo más común en tarjetas de transporte argentinas
            uidData = miFareTag.identifier
            tagType = .miFare

        case .iso7816(let iso7816Tag):
            uidData = iso7816Tag.identifier
            tagType = .iso7816

        case .iso15693(let iso15693Tag):
            uidData = iso15693Tag.identifier
            tagType = .iso15693

        case .feliCa(let feliCaTag):
            // FeliCa es principalmente usado en Japón, poco probable en SAETA
            uidData = feliCaTag.currentIDm
            tagType = .feliCa

        @unknown default:
            return .failure(.nfcReadFailed("Tipo de tarjeta no compatible"))
        }

        // Convertir Data a string hexadecimal (formato estándar de UIDs NFC)
        let hexUID = uidData.map { String(format: "%02X", $0) }.joined()

        if hexUID.isEmpty {
            return .failure(.nfcReadFailed("No se pudo obtener el identificador de la tarjeta"))
        }

        let result = NFCReadResult(uid: hexUID, tagType: tagType, rawIdentifier: uidData)
        return .success(result)
    }
}
