# SAETA Saldo - App iOS para tarjetas SAETA Salta

App para iOS que permite consultar el saldo de las tarjetas de transporte público **SAETA** (Salta, Argentina) usando tecnología **NFC**.

## ¿Qué es SAETA?

**SAETA** (Sociedad Anónima de Transporte Automotor) es la empresa de transporte público urbano e interurbano de la ciudad de Salta y el Gran Salta, Argentina. Utiliza tarjetas sin contacto (NFC/MIFARE) para el pago de pasajes.

### Tipos de tarjetas SAETA

| Tarjeta | Destinatarios | Beneficios |
|---------|--------------|------------|
| **Azul** | Público general | Transbordo universal (60 min), saldo de emergencia (2 boletos si está nominada) |
| **Verde** | Jubilados, pensionados, estudiantes, discapacitados | Tarifa diferencial o gratuidad |

---

## Cómo funciona la App

### 1. Lectura NFC
La app usa **CoreNFC** para leer el **UID (identificador único)** del chip de la tarjeta SAETA al acercarla al iPhone. Este UID permite identificar la tarjeta en futuras consultas sin necesidad de ingresar el número manualmente.

> **Nota técnica:** iOS no puede leer el saldo directamente del chip (está encriptado y protegido con claves que solo SAETA tiene). El saldo se consulta vía el portal web oficial.

### 2. Consulta de Saldo
El saldo se obtiene del **portal oficial de SAETA** (`salta.miredbus.com.ar`) operado por **RedBus/Bizland**. 

⚠️ **Importante:** Los datos en el portal se actualizan cada **24-48 horas hábiles** y corresponden al último uso de la tarjeta en el colectivo.

### 3. Almacenamiento local
Las tarjetas se guardan localmente en el dispositivo usando `UserDefaults` (sin servidores externos, sin datos personales).

---

## Estructura del Proyecto

```
SAETASaldoApp/
├── SAETASaldoApp.swift          ← Punto de entrada (@main)
├── Models/
│   └── CardModel.swift          ← SAETACard, CardType, SAETAError
├── Services/
│   ├── NFCService.swift         ← CoreNFC: leer UID de tarjetas
│   ├── BalanceService.swift     ← Consulta saldo portal SAETA
│   └── CardStorageService.swift ← Persistencia local
├── ViewModels/
│   ├── CardListViewModel.swift  ← Gestión lista de tarjetas
│   └── AddCardViewModel.swift   ← Lógica agregar tarjeta
├── Views/
│   ├── ContentView.swift        ← TabView raíz
│   ├── CardListView.swift       ← Tab: Mis Tarjetas
│   ├── CardDetailView.swift     ← Detalle de tarjeta individual
│   ├── AddCardView.swift        ← Formulario + NFC scan
│   ├── NFCScanView.swift        ← Tab: Escanear con NFC
│   └── InfoView.swift           ← Tab: Info SAETA
├── Utilities/
│   └── SAETAColors.swift        ← Paleta de colores SAETA
└── Resources/
    ├── Info.plist               ← Permisos NFC y configuración
    └── SAETASaldoApp.entitlements ← Entitlement NFC
```

---

## Requisitos Técnicos

| Requisito | Detalle |
|-----------|---------|
| **iOS mínimo** | iOS 15.0 |
| **Xcode** | 15.0 o superior |
| **Swift** | 5.9+ |
| **NFC compatible** | iPhone 7 o superior |
| **Apple Developer** | Cuenta paga ($99/año) para NFC en dispositivo físico |

### Frameworks utilizados (todos nativos de iOS)
- **CoreNFC** — Lectura de tags NFC (`NFCTagReaderSession`)
- **SwiftUI** — Interfaz de usuario
- **Foundation** — Networking, JSON, UserDefaults
- **Combine** — Programación reactiva (ObservableObject)

---

## Configuración en Xcode

### 1. Crear el proyecto
```
File → New → Project → iOS → App
Product Name: SAETASaldoApp
Bundle ID: com.saetasaldo.app
Interface: SwiftUI | Language: Swift | Min iOS: 15.0
```

### 2. Habilitar NFC
```
Target → Signing & Capabilities → + Capability
→ "Near Field Communication Tag Reading"
```

### 3. Info.plist (campos requeridos)
```xml
<!-- Descripción requerida por Apple -->
<key>NFCReaderUsageDescription</key>
<string>SAETA Saldo usa NFC para identificar tu tarjeta...</string>

<!-- Habilitar lectura de tags físicos -->
<key>com.apple.developer.nfc.readersession.formats</key>
<array>
    <string>TAG</string>
</array>
```

### 4. Entitlements (generado automáticamente por Xcode)
```xml
<key>com.apple.developer.nfc.readersession.formats</key>
<array>
    <string>TAG</string>
</array>
```

---

## Pantallas de la App

### Tab 1: Mis Tarjetas
- Lista todas las tarjetas guardadas
- Muestra el saldo con gradiente según tipo (azul/verde)
- Pull-to-refresh para actualizar todos los saldos
- Swipe o menú contextual para eliminar

### Tab 2: Escanear
- Animación de ondas NFC en tiempo real
- Detecta tarjetas SAETA al acercarlas al iPhone
- Vincula el UID NFC a tarjetas existentes o crea nuevas
- Instrucciones paso a paso para el usuario

### Tab 3: Información
- Información sobre SAETA y tipos de tarjetas
- Cómo recargar (Mercado Pago, Banco Macro, Naranja X, etc.)
- Contacto SAETA (teléfono, WhatsApp, CAU)
- Links directos al portal web y app de Android

---

## Limitaciones Conocidas

1. **Saldo desactualizado:** El portal de SAETA actualiza los datos cada 24-48h hábiles. El saldo que muestra la app puede no reflejar el estado real en tiempo real.

2. **Captcha:** El portal SAETA puede solicitar verificación CAPTCHA para consultas automáticas. En ese caso, la app abre el portal web en Safari.

3. **Sin API oficial:** SAETA no tiene una API REST pública. La consulta de saldo depende del scraping del portal web, que puede cambiar en cualquier momento.

4. **NFC solo en dispositivo físico:** El simulador de iOS no soporta NFC. Se requiere iPhone 7 o superior para pruebas.

5. **UID ≠ Número de tarjeta:** No hay documentación pública que relacione el UID del chip con el número impreso en el plástico. El usuario debe ingresar el número manualmente la primera vez.

---

## Información de Contacto SAETA

- **Web oficial:** https://www.saetasalta.com.ar
- **Portal de tarjetas:** https://salta.miredbus.com.ar
- **Teléfono:** (0387) 423-8118
- **WhatsApp:** 387-2280901
- **CAU Principal:** Pellegrini 824, Lunes a viernes 8:00-16:00
- **CAU Paseo Salta:** Local 2020, 1° Piso

---

## Aviso Legal

Esta aplicación es **no oficial** y no está afiliada, respaldada ni patrocinada por SAETA. Los datos se obtienen del portal público de SAETA. La app **no modifica** el saldo de ninguna tarjeta.
