// InfoView.swift
// SAETA Saldo App - Vista de información sobre SAETA y la app

import SwiftUI

struct InfoView: View {
    @Environment(\.openURL) var openURL

    var body: some View {
        List {
            // MARK: - Header
            Section {
                VStack(spacing: 12) {
                    Image(systemName: "bus.fill")
                        .font(.system(size: 48))
                        .foregroundColor(.saetaBlue)

                    Text("SAETA Saldo")
                        .font(.title)
                        .fontWeight(.bold)

                    Text("Consulta el saldo de tus tarjetas SAETA\nde forma rápida y sencilla")
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                        .multilineTextAlignment(.center)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 20)
            }

            // MARK: - Sobre SAETA
            Section("Sobre SAETA") {
                InfoLinkRow(
                    icon: "info.circle.fill",
                    iconColor: .saetaBlue,
                    title: "¿Qué es SAETA?",
                    description: "Sociedad Anónima de Transporte Automotor. Opera el transporte público urbano e interurbano de Salta Capital y Gran Salta.",
                    action: nil
                )

                InfoLinkRow(
                    icon: "creditcard.fill",
                    iconColor: .saetaGreen,
                    title: "Tarjeta Azul",
                    description: "Para el público en general. Incluye transbordo universal (60 min) y saldo de emergencia si está nominada.",
                    action: nil
                )

                InfoLinkRow(
                    icon: "star.fill",
                    iconColor: .saetaGreen,
                    title: "Tarjeta Verde",
                    description: "Para jubilados, pensionados, estudiantes y personas con discapacidad. Acceden a tarifas diferenciales o gratuidad.",
                    action: nil
                )
            }

            // MARK: - Cómo recargar
            Section("Cómo recargar tu tarjeta") {
                ForEach(rechargeOptions, id: \.title) { option in
                    HStack(spacing: 12) {
                        Image(systemName: option.icon)
                            .font(.title3)
                            .foregroundColor(.saetaBlue)
                            .frame(width: 28)

                        VStack(alignment: .leading, spacing: 2) {
                            Text(option.title)
                                .font(.subheadline)
                                .fontWeight(.medium)
                            Text(option.description)
                                .font(.caption)
                                .foregroundColor(.secondary)
                        }
                    }
                    .padding(.vertical, 4)
                }
            }

            // MARK: - Contacto SAETA
            Section("Contacto SAETA") {
                Button {
                    if let url = URL(string: "https://www.saetasalta.com.ar") { openURL(url) }
                } label: {
                    InfoLinkRow(
                        icon: "globe",
                        iconColor: .saetaBlue,
                        title: "Sitio Web Oficial",
                        description: "www.saetasalta.com.ar",
                        action: {}
                    )
                }
                .buttonStyle(PlainButtonStyle())

                Button {
                    if let url = URL(string: "https://salta.miredbus.com.ar") { openURL(url) }
                } label: {
                    InfoLinkRow(
                        icon: "creditcard.viewfinder",
                        iconColor: .saetaBlue,
                        title: "Portal de Tarjetas",
                        description: "salta.miredbus.com.ar – Consulta saldo y movimientos",
                        action: {}
                    )
                }
                .buttonStyle(PlainButtonStyle())

                InfoLinkRow(
                    icon: "phone.fill",
                    iconColor: .saetaBlue,
                    title: "Línea de atención",
                    description: "(0387) 423-8118",
                    action: {
                        if let url = URL(string: "tel:+543874238118") { openURL(url) }
                    }
                )

                InfoLinkRow(
                    icon: "message.fill",
                    iconColor: .green,
                    title: "WhatsApp",
                    description: "387-2280901",
                    action: {
                        if let url = URL(string: "https://wa.me/5493872280901") { openURL(url) }
                    }
                )
            }

            // MARK: - Centros de Atención
            Section("Centros de Atención (CAU)") {
                InfoLinkRow(
                    icon: "mappin.circle.fill",
                    iconColor: .red,
                    title: "Centro Principal",
                    description: "Pellegrini 824 – Lunes a viernes de 8:00 a 16:00",
                    action: {
                        if let url = URL(string: "https://maps.apple.com/?q=Pellegrini+824+Salta") { openURL(url) }
                    }
                )

                InfoLinkRow(
                    icon: "mappin.circle.fill",
                    iconColor: .red,
                    title: "Paseo Salta",
                    description: "Local 2020, 1° Piso (Ex Híper Libertad)",
                    action: {
                        if let url = URL(string: "https://maps.apple.com/?q=Paseo+Salta+Argentina") { openURL(url) }
                    }
                )
            }


            // MARK: - Sobre la App
            Section("Sobre esta App") {
                VStack(alignment: .leading, spacing: 8) {
                    Text("Aviso Legal")
                        .font(.subheadline)
                        .fontWeight(.semibold)

                    Text("Esta aplicación es una herramienta no oficial desarrollada por la comunidad. NO está afiliada, respaldada ni patrocinada por SAETA. Los datos de saldo se obtienen del portal oficial de SAETA (salta.miredbus.com.ar) y pueden tener un desfase de 24-48 horas hábiles.")
                        .font(.caption)
                        .foregroundColor(.secondary)

                    Text("La app NO modifica el saldo de tu tarjeta ni accede a datos privados. Solo consulta información pública del portal SAETA.")
                        .font(.caption)
                        .foregroundColor(.secondary)
                        .padding(.top, 4)
                }
                .padding(.vertical, 4)

                HStack {
                    Text("Versión")
                        .foregroundColor(.secondary)
                    Spacer()
                    Text("1.0.0")
                        .foregroundColor(.secondary)
                }
                .font(.subheadline)
            }
        }
        .navigationTitle("Información")
        .navigationBarTitleDisplayMode(.large)
    }

    private struct RechargeOption {
        let icon: String
        let title: String
        let description: String
    }

    private let rechargeOptions = [
        RechargeOption(icon: "iphone", title: "App oficial de SAETA",
                       description: "Disponible en Google Play Store"),
        RechargeOption(icon: "banknote.fill", title: "Banco Macro",
                       description: "Home Banking y app Macro Click"),
        RechargeOption(icon: "qrcode", title: "Mercado Pago",
                       description: "Desde la app o el sitio web"),
        RechargeOption(icon: "creditcard", title: "Naranja X / Ualá",
                       description: "Recarga desde las apps financieras"),
        RechargeOption(icon: "building.columns.fill", title: "Banelco / Pago 24",
                       description: "En cajeros automáticos habilitados"),
        RechargeOption(icon: "storefront.fill", title: "Puntos de venta físicos",
                       description: "Kioscos y centros habilitados en toda la ciudad")
    ]
}

// MARK: - Fila de información con link opcional
struct InfoLinkRow: View {
    let icon: String
    let iconColor: Color
    let title: String
    let description: String
    let action: (() -> Void)?

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: icon)
                .font(.title3)
                .foregroundColor(iconColor)
                .frame(width: 28)

            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.subheadline)
                    .fontWeight(.medium)
                    .foregroundColor(.primary)
                Text(description)
                    .font(.caption)
                    .foregroundColor(.secondary)
            }

            Spacer()

            if action != nil {
                Image(systemName: "chevron.right")
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
        }
        .contentShape(Rectangle())
        .padding(.vertical, 4)
        .onTapGesture {
            action?()
        }
    }
}
