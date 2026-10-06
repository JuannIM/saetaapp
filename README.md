# SAETA Saldo - Apps para tarjetas SAETA Salta (iOS & Android)

Aplicaciones nativas para consultar el saldo de las tarjetas de transporte público **SAETA** (Salta, Argentina) utilizando tecnología sin contacto **NFC**.

---

## 🚀 Versión Android Nativa (Superando las Restricciones de iOS)

La versión para Android (`android/`) fue diseñada e implementada desde cero con **Kotlin 2.x** y **Jetpack Compose**, superando por completo las limitaciones de iOS (modales invasivos de CoreNFC, requisito de cuenta paga Apple Developer de $99/año, bloqueos SSL de ATS, y falta de widgets interactivos con sincronización en background).

### 🌟 Capacidades Clave de la Versión Android

| Característica | Versión iOS | Versión Android |
|---|---|---|
| **Lectura NFC** | Requiere modal invasivo del sistema iOS (`CoreNFC`), sesión explícita y cuenta paga Apple de $99/año | **Nativo y transparente**: Foreground Dispatch (`NfcAdapter`) con respuesta háptica instantánea y filtros en background (`ACTION_TECH_DISCOVERED`) sin modales ni costo. |
| **Resolución de Captcha** | Scraping manual / Redirige a Safari al fallar | **On-Device OCR con Google ML Kit**: Descarga `/captcha.png`, binariza la imagen y resuelve el código alfanumérico en ~50ms sin enviar datos a servidores externos, con 3 reintentos silenciosos y diálogo de fallback manual. |
| **Widget de Escritorio** | No disponible (limitado por sandbox de iOS) | **Jetpack Glance Widget**: Muestra el saldo actualizado directamente en la pantalla de inicio con botón de refresco de 1 toque. |
| **Conectividad con RedBus** | Bloqueos por App Transport Security (ATS) ante certificados del portal | **Network Security Config**: Configuración segura de certificados para `salta.miredbus.com.ar` y sesión `JSESSIONID` retenida en memoria. |
| **Persistencia y Métricas** | `UserDefaults` simple (solo último valor) | **Room Database**: Historial completo de variaciones de saldo, estimación inteligente de viajes restantes según la tarifa vigente y editor de tarifa. |

---

## 🏛️ Arquitectura del Proyecto Android (`android/`)

El proyecto Android sigue los principios de **Clean Architecture** y **Jetpack Recommended App Architecture**:

```
android/
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml                  # Tech filters NFC, permisos, widget provider
│   │   ├── res/
│   │   │   ├── xml/network_security_config.xml  # Configuración SSL y dominios confiables
│   │   │   ├── xml/nfc_tech_filter.xml          # Filtros NfcA, MifareClassic, IsoDep
│   │   │   └── xml/saeta_widget_info.xml        # Metadata del Widget Jetpack Glance
│   │   └── java/com/saetasaldo/app/
│   │       ├── MainActivity.kt                  # Single-Activity Compose host y despacho NFC
│   │       ├── domain/                          # Modelos de negocio y Casos de Uso puros
│   │       │   ├── model/                       # SaetaCard, CardType, BalanceRecord, TripEstimate
│   │       │   ├── repository/                  # CardRepository, RedBusAccountRepository (interfaces)
│   │       │   └── usecase/
│   │       │       ├── CalculateRemainingTripsUseCase.kt
│   │       │       ├── GetCardBalanceUseCase.kt
│   │       │       ├── ProcessNfcScanUseCase.kt
│   │       │       ├── RefreshAllBalancesUseCase.kt
│   │       │       ├── SolveCaptchaUseCase.kt
│   │       │       └── SyncRedBusCardsUseCase.kt
│   │       ├── data/                            # Implementación de datos y proveedores
│   │       │   ├── local/                       # Room DB: SaetaDatabase, DAOs, Entities, Converters
│   │       │   ├── remote/                      # Retrofit + OkHttp
│   │       │   │   ├── api/                     # SaetaApiService (anónimo), RedBusAccountApiService (cuenta)
│   │       │   │   ├── cookie/                  # SessionCookieJar (anónimo), WebViewCookieJar/WebCookieStore (cuenta)
│   │       │   │   ├── dto/                     # SaldoRequestDto/SaldoResponseDto, RedBusAccountDtos
│   │       │   │   ├── NetworkClient.kt         # Cliente OkHttp del flujo anónimo
│   │       │   │   └── RedBusAccountNetworkClient.kt  # Cliente OkHttp dedicado a la sesión de cuenta
│   │       │   ├── ocr/                         # Google ML Kit OCR & Preprocesador Binarizador
│   │       │   ├── nfc/                         # AndroidNfcManager (Foreground & Background dispatch)
│   │       │   └── repository/                  # CardRepositoryImpl, RedBusAccountRepositoryImpl
│   │       ├── ui/                              # Capa de presentación Jetpack Compose
│   │       │   ├── theme/                       # Material 3 tokens: Color (Azul/Verde SAETA, Oro), Theme
│   │       │   ├── account/                     # RedBusLoginScreen (WebView), RedBusAccountDialog, RedBusAccountViewModel
│   │       │   ├── cards/                       # CardsScreen, CardsViewModel, SaetaCardItem
│   │       │   ├── detail/                      # CardDetailScreen, CardDetailViewModel
│   │       │   ├── nfc/                         # NfcScanBottomSheet (onda radar animada)
│   │       │   └── dialogs/                     # FallbackCaptchaDialog (ingreso manual en contingencia)
│   │       └── widget/                          # Jetpack Glance Home Widget
│   │           ├── SaetaBalanceWidget.kt        # UI interactiva en Glance
│   │           ├── SaetaBalanceWidgetReceiver.kt# Receptor AppWidgetProvider
│   │           └── RefreshBalanceAction.kt      # Actualización en background con 1 toque
│   └── src/test/                                # 160 pruebas unitarias automatizadas
```

---

## 🛠️ Tecnologías y Dependencias Principales

- **Lenguaje:** Kotlin 2.1
- **UI Toolkit:** Jetpack Compose + Material 3 (BOM 2024.12.01)
- **Base de Datos:** AndroidX Room 2.6.1 con Coroutine Flow y TypeConverters
- **Conectividad:** Retrofit 2.11 + OkHttp 4.12 (con retención de cookies `JSESSIONID` y compresión GZIP)
- **Visión por Computadora (OCR):** Google ML Kit Text Recognition (`com.google.mlkit:text-recognition:16.0.1`)
- **Widgets de Escritorio:** Jetpack Glance 1.1.1
- **Testing:** JUnit 4, MockK 1.13.13, Kotlinx Coroutines Test 1.9.0

---

## 🔄 Contrato de API Revertido (RedBus / Bizland)

El portal `salta.miredbus.com.ar` no ofrece una API REST pública documentada. Durante la fase de investigación y benchmarking se identificó el endpoint interno utilizado por la plataforma:

1. **Obtención del Captcha:**
   - `GET https://salta.miredbus.com.ar/captcha.png`
   - Descarga la imagen captcha vinculada a la cookie de sesión `JSESSIONID`.
2. **Consulta de Saldo Directa (JSON):**
   - `POST https://salta.miredbus.com.ar/rest/tarjetaInternal/resultadoSaldo`
   - Headers requeridos: `Content-Type: application/json`, `User-Agent: Mozilla/5.0 ...`
   - **Regla fundamental:** No enviar el encabezado `X-Use-New-Captcha: true`. Al omitirlo, el backend valida el campo `verificacionCaptcha` contra la imagen servida en `/captcha.png`.
   - Payload:
     ```json
     {
       "nroExternoTarjeta": "12345678",
       "verificacionCaptcha": "4B8Y"
     }
     ```
   - Códigos de respuesta: `0 = Éxito`, `1 = Captcha inválido`, `2 = Tarjeta inexistente`.
   - **Modo Turnstile (preferido):** cuando no hay captcha manual, la app primero obtiene el sitekey público con `GET /rest/getTurnstileKeySite`, resuelve un token de Cloudflare Turnstile en un WebView fuera de pantalla sobre el dominio oficial y envía `X-Use-New-Captcha: true` con el token en `verificacionCaptcha`. Si el token falla o es rechazado (`error: 1`), se recurre automáticamente al captcha de imagen con OCR local y, como último recurso, al diálogo manual.

### Flujo autenticado opcional (cuenta RedBus)

Además del flujo anónimo, el usuario puede conectar de forma **opcional** su cuenta RedBus. El inicio de sesión se realiza en un **WebView endurecido** que carga el formulario oficial de `salta.miredbus.com.ar` (con Cloudflare Turnstile): la app no implementa un formulario nativo de credenciales, no inspecciona el DOM ni agrega puentes JavaScript (`addJavascriptInterface`). Las cookies de sesión se comparten únicamente a través de `CookieManager` hacia un cliente OkHttp dedicado, aislado del cliente anónimo.

Endpoints internos utilizados una vez autenticado:

- `GET https://salta.miredbus.com.ar/rest/loginInternal/usuarioLogeado`
  - Verificación de sesión: `error: 0` = sesión activa, `error: 1` = no autenticado.
- `GET https://salta.miredbus.com.ar/rest/tarjetaInternal/listaTarjetas`
  - Lista las tarjetas vinculadas a la cuenta con sus monederos; solo se importa el saldo del monedero **`Principal (Dinero)`** (nunca beneficios ni boletos gratuitos). Responde `error: 99` cuando no hay sesión autenticada.

La desconexión es **local**: elimina las cookies del `CookieManager` y conserva intactas las tarjetas guardadas. Estos endpoints son **internos y no documentados** por RedBus, por lo que pueden cambiar sin previo aviso; ante cualquier falla o cambio de contrato, la app recurre automáticamente al flujo anónimo de captcha/OCR.

---

## 📱 Pantallas de la Aplicación Android

1. **Mis Tarjetas (`CardsScreen`):**
   - Tarjetas diseñadas como plásticos físicos digitales con el gradiente azul institucional de SAETA (`#0D47A1` a `#1976D2`), sin pedir ni mostrar una categoría de usuario.
   - Tipografía grande de saldo (`$ 1.500,00`), número impreso y badges de tarjeta favorita y NFC vinculado.
   - Gesto Pull-to-Refresh para actualización masiva.
   - Botón de Acción Flotante (FAB) para escanear nueva tarjeta vía NFC.

2. **Detalle de Tarjeta (`CardDetailScreen`):**
   - Encabezado ampliado con acciones rápidas: fijar como tarjeta del Widget de escritorio y botón de refresco.
   - **Estimador Inteligente de Viajes:** Calcula cuántos boletos cubre el saldo actual con tarifa configurable (valor por defecto `1450,00`; el campo acepta coma o punto decimal). La estimación se basa estrictamente en el saldo disponible y marca aparte si la tarjeta quedó en saldo negativo.
   - **Historial de Variaciones:** Registro cronológico de variaciones de saldo con badges diferenciales (`+ $...` / `- $...`).
   - Edición de nombre y eliminación de tarjeta.

3. **Escanear NFC (`NfcScanBottomSheet`):**
   - Diálogo inferior con animación de radar de ondas concéntricas.
   - Detección del estado del adaptador NFC del equipo con botón directo a los ajustes del sistema en caso de estar desactivado.
   - Respuesta con vibración háptica al detectar un chip.

4. **Verificación Manual de Seguridad (`FallbackCaptchaDialog`):**
   - Diálogo modal de contingencia si el motor OCR local agota sus 3 reintentos silenciosos.
   - Presenta la imagen del captcha en pantalla con botón para regenerarlo y campo de texto con auto-capitalización.

5. **Cuenta RedBus Opcional (`RedBusLoginScreen` + `RedBusAccountDialog`):**
   - Inicio de sesión en el sitio oficial de RedBus dentro de un WebView endurecido (JavaScript y DOM storage para Turnstile, sin puente JavaScript, navegación principal limitada a HTTPS en el host exacto).
   - Sincronización de las tarjetas vinculadas a la cuenta con el saldo del monedero `Principal (Dinero)`, sin captcha por cada consulta.
   - El modo anónimo (captcha/OCR + NFC) permanece intacto como alternativa y como fallback automático ante sesiones expiradas o fallas del contrato.

---

## 🍎 Versión iOS Original (`SAETASaldoApp/`)

La versión para iOS original se encuentra en la raíz del repositorio, construida con **SwiftUI** y **CoreNFC** (`NFCTagReaderSession`).

### Limitaciones conocidas en iOS:
- Requiere tarjeta de crédito y cuenta paga en **Apple Developer Program ($99 USD/año)** para ejecutar en dispositivos físicos con capacidad NFC.
- CoreNFC despliega un modal obligatorio del sistema con animación circular que no se puede omitir ni personalizar.
- No dispone de widgets interactivos de escritorio con refresco en 1 toque sin abrir la app.
- Bloqueo de certificados autofirmados o intermedios en el portal de RedBus por políticas estrictas de ATS (App Transport Security).

---

## ⚖️ Aviso Legal

Esta aplicación es un proyecto independiente de código abierto, de carácter **no oficial**, y no está respaldada, afiliada ni asociada con SAETA (Sociedad Anónima de Transporte Automotor) ni con Bizland/RedBus. Los datos son consultados en tiempo real desde el portal web público de SAETA. La aplicación **no modifica ni puede modificar** el saldo de las tarjetas físicas.
