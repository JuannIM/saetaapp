// MARK: ═══════════════════════════════════════════════════════════════
// project.pbxproj - Archivo de proyecto Xcode para SAETA Saldo App
// ════════════════════════════════════════════════════════════════════
//
// INSTRUCCIONES DE CONFIGURACIÓN EN XCODE:
// ─────────────────────────────────────────
// Este archivo representa la estructura del proyecto Xcode.
// Para crear el proyecto real en Xcode, seguí estos pasos:
//
//  1. Abrir Xcode → "Create New Project"
//  2. Seleccionar "iOS" → "App"
//  3. Configurar:
//       Product Name:       SAETASaldoApp
//       Bundle Identifier:  com.saetasaldo.app
//       Interface:          SwiftUI
//       Language:           Swift
//       Minimum Deployment: iOS 15.0
//  4. Agregar todos los archivos .swift de las carpetas:
//       Models/, Services/, ViewModels/, Views/, Utilities/
//  5. En el Target → Signing & Capabilities:
//       + Capability → "Near Field Communication Tag Reading"
//  6. En el archivo Info.plist agregar:
//       Key:   NFCReaderUsageDescription
//       Value: "SAETA Saldo usa NFC para identificar tu tarjeta SAETA..."
//       Key:   com.apple.developer.nfc.readersession.formats
//       Type:  Array → Item: TAG
//  7. Revisar que el .entitlements tenga:
//       com.apple.developer.nfc.readersession.formats = [TAG]
//
// ════════════════════════════════════════════════════════════════════
// ESTRUCTURA DEL PROYECTO:
//
//  SAETASaldoApp/
//  ├── SAETASaldoApp.swift              (App entry point - @main)
//  ├── Models/
//  │   └── CardModel.swift              (SAETACard, CardType, CardTransaction, SAETAError)
//  ├── Services/
//  │   ├── NFCService.swift             (CoreNFC - leer UID de tarjetas)
//  │   ├── BalanceService.swift         (Consulta saldo via portal SAETA)
//  │   └── CardStorageService.swift     (Persistencia local UserDefaults)
//  ├── ViewModels/
//  │   ├── CardListViewModel.swift      (Lista y gestión de tarjetas)
//  │   └── AddCardViewModel.swift       (Agregar nueva tarjeta)
//  ├── Views/
//  │   ├── ContentView.swift            (TabView raíz)
//  │   ├── CardListView.swift           (Tab 1: Mis Tarjetas)
//  │   ├── CardDetailView.swift         (Detalle de tarjeta)
//  │   ├── AddCardView.swift            (Formulario: agregar tarjeta)
//  │   ├── NFCScanView.swift            (Tab 2: Escanear NFC)
//  │   └── InfoView.swift              (Tab 3: Información SAETA)
//  ├── Utilities/
//  │   └── SAETAColors.swift            (Paleta de colores y gradientes)
//  └── Resources/
//      ├── Info.plist                   (Permisos y configuración)
//      └── SAETASaldoApp.entitlements   (NFC entitlement)
//
// ════════════════════════════════════════════════════════════════════
// FRAMEWORK REQUIREMENTS (todos incluidos en iOS SDK - sin dependencias externas):
//
//  Framework        | Uso
//  ─────────────────|────────────────────────────────────────────────
//  CoreNFC          | Lectura de tarjetas NFC (NFCTagReaderSession)
//  SwiftUI          | Interfaz de usuario declarativa
//  Foundation       | URLSession, JSONDecoder, UserDefaults
//  Combine          | @Published, ObservableObject, reactive state
//  UIKit            | UIApplication (abrir URLs externas)
//
// ════════════════════════════════════════════════════════════════════
