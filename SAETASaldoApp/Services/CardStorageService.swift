// CardStorageService.swift
// SAETA Saldo App - Servicio de persistencia local de tarjetas

import Foundation
import Combine

// MARK: - Servicio de almacenamiento de tarjetas
/// Guarda y carga las tarjetas del usuario usando UserDefaults (con codificación JSON).
/// Para una app de producción se recomendaría usar CoreData o el Keychain para datos sensibles.
class CardStorageService: ObservableObject {

    private let storageKey = "saeta_cards_v1"
    private let encoder = JSONEncoder()
    private let decoder = JSONDecoder()

    // MARK: - Guardar tarjetas
    func save(cards: [SAETACard]) {
        do {
            let data = try encoder.encode(cards)
            UserDefaults.standard.set(data, forKey: storageKey)
        } catch {
            print("Error al guardar tarjetas: \(error)")
        }
    }

    // MARK: - Cargar tarjetas
    func load() -> [SAETACard] {
        guard let data = UserDefaults.standard.data(forKey: storageKey) else {
            return []
        }
        do {
            return try decoder.decode([SAETACard].self, from: data)
        } catch {
            print("Error al cargar tarjetas: \(error)")
            return []
        }
    }

    // MARK: - Eliminar todas las tarjetas
    func clearAll() {
        UserDefaults.standard.removeObject(forKey: storageKey)
    }

    // MARK: - Guardar una tarjeta individual (actualizar o insertar)
    func upsert(card: SAETACard, in cards: inout [SAETACard]) {
        if let index = cards.firstIndex(where: { $0.id == card.id }) {
            cards[index] = card
        } else {
            cards.append(card)
        }
        save(cards: cards)
    }

    // MARK: - Eliminar una tarjeta
    func remove(card: SAETACard, from cards: inout [SAETACard]) {
        cards.removeAll { $0.id == card.id }
        save(cards: cards)
    }
}
