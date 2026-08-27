// Package.swift
// SAETA Saldo App - Configuración Swift Package Manager (alternativa a .xcodeproj)
//
// NOTA: Este archivo es solo de referencia. Para usar la app en iOS
// debes abrir el proyecto con Xcode y configurar el target iOS manualmente.
// El archivo .xcodeproj real se crea con Xcode al crear el proyecto.

// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "SAETASaldoApp",
    platforms: [
        .iOS(.v15)
    ],
    products: [
        .library(name: "SAETASaldoApp", targets: ["SAETASaldoApp"])
    ],
    dependencies: [
        // Sin dependencias externas - usa solo APIs nativas de Apple:
        // - CoreNFC (lectura de tarjetas NFC)
        // - SwiftUI (interfaz de usuario)
        // - Foundation (networking, JSON, persistencia)
        // - Combine (programación reactiva)
    ],
    targets: [
        .target(
            name: "SAETASaldoApp",
            dependencies: [],
            path: "SAETASaldoApp",
            resources: [
                .process("Resources/Info.plist")
            ]
        )
    ]
)
