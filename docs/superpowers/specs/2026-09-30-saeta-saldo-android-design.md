# Especificación de Diseño: SAETA Saldo para Android

**Fecha:** 2026-09-30  
**Proyecto:** SAETA Saldo Android (`com.saetasaldo.app`)  
**Estado:** Aprobado para Planificación  
**Autor:** Antigravity & Juancito  

---

## 1. Contexto y Motivación

La aplicación original **SAETASaldoApp** fue desarrollada para iOS (Swift / SwiftUI / CoreNFC). Debido a las restricciones inherentes del ecosistema de Apple, la experiencia de usuario se vio severamente limitada:
1. **Restricciones de CoreNFC en iOS**:
   - `NFCTagReaderSession` impone un modal invasivo a nivel de sistema (*"Listo para escanear"*).
   - Apple exige una cuenta de pago de Apple Developer ($99 USD/año) para permitir la lectura de tags físicos en dispositivos reales.
   - Es imposible realizar escaneo pasivo o despertar la aplicación al apoyar la tarjeta contra el teléfono con la pantalla de inicio o la app cerrada.
2. **Restricciones de Seguridad de Red (ATS)**:
   - El portal oficial de RedBus/Bizland (`salta.miredbus.com.ar`) utiliza una autoridad certificadora intermedia autofirmada en la cadena SSL. iOS bloquea estas conexiones por defecto en `URLSession`, obligando a la app de iOS a usar un `WKWebView` con inyección de scripts para interactuar con la página.
3. **Imposibilidad de Widgets y Consultas en Segundo Plano**:
   - Al depender de inyección DOM en un WebView para resolver el formulario y el captcha, era inviable actualizar el saldo en segundo plano o mediante un widget de escritorio.

### Objetivos en Android
La versión nativa de Android se diseña desde cero para aprovechar al máximo las capacidades abiertas del sistema operativo:
- **NFC Nativo sin Restricciones**: Lectura instantánea del hardware UID con vibración háptica suave y sin popups del sistema. Soporte para despertar la aplicación mediante `IntentFilter` al apoyar la tarjeta en la parte trasera del teléfono.
- **Resolución Automática de Captcha On-Device**: Consumo directo de la API REST descubierta de RedBus, descargando `/captcha.png` y resolviéndolo en ~50 ms mediante Google ML Kit Text Recognition localmente, de forma 100% gratuita y sin conexión a servicios de terceros.
- **Widget Interactivo de Escritorio (Jetpack Glance)**: Widget moderno en pantalla de inicio con actualización de saldo de un solo toque y diseño Material You.
- **Persistencia Local y Estadísticas (Room Database)**: Registro de variaciones de saldo, estimación automática de pasajes restantes según la tarifa vigente y funcionamiento offline.

---

## 2. Ingeniería Inversa de la API de RedBus / Bizland

A través del análisis del portal oficial `https://salta.miredbus.com.ar`, se identificaron los siguientes endpoints y mecanismos de autenticación y sesión:

### 2.1. Endpoints Clave
* `GET /captcha.png`: Genera una imagen PNG de 190x50 píxeles con 4-6 caracteres alfanuméricos asociados a la sesión `JSESSIONID`.
* `POST /rest/tarjetaInternal/resultadoSaldo`: Consulta de saldo directa sin interfaz gráfica.
* `GET /rest/getTurnstileKeySite`: Retorna la clave del widget de Cloudflare Turnstile (`0x4AAAAAAE2vQc2RkurVvWK8`).

### 2.2. Manejo de Sesión y Cookies
RedBus asocia la solución del captcha a la sesión HTTP del cliente. El cliente HTTP de Android (`OkHttp`) debe usar un `SessionCookieJar` en memoria que:
1. Almacene las cookies `JSESSIONID` y `SERVER_USED` recibidas al solicitar `GET /captcha.png`.
2. Reenvíe automáticamente estas cookies en la llamada posterior a `POST /rest/tarjetaInternal/resultadoSaldo`.

### 2.3. Contrato de Consulta de Saldo
* **Headers requeridos**:
  - `Content-Type: application/json`
  - `User-Agent: Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36`
  - *(Nota)*: **NO** incluir el header `X-Use-New-Captcha: true`. Omitir este header fuerza al backend a validar el campo contra el captcha de imagen de `/captcha.png`.
* **Request Body (JSON)**:
  ```json
  {
    "verificacionCaptcha": "4B8Y",
    "nroExternoTarjeta": "12345678"
  }
  ```
* **Response Body (JSON)**:
  - **Éxito (`error == 0`)**:
    ```json
    {
      "error": 0,
      "numeroTarjeta": "12345678",
      "tipoTarjeta": "COMUN",
      "estadoTarjeta": "ACTIVA",
      "saldos": [
        {
          "monto": 2450.00,
          "fecha": "2026-09-30 18:30:00"
        }
      ],
      "fechaSaldo": "2026-09-30 18:30:00"
    }
    ```
  - **Error de Captcha (`error == 1`)**:
    ```json
    {
      "error": 1,
      "mensaje": null,
      "numeroTarjeta": null,
      "tipoTarjeta": null,
      "estadoTarjeta": null,
      "saldos": null,
      "fechaSaldo": null
    }
    ```
  - **Tarjeta Inválida o Inexistente (`error == 2`)**:
    Indica que el número de tarjeta no se encuentra en el padrón de SAETA.

---

## 3. Arquitectura del Sistema

La aplicación sigue los principios de **Clean Architecture** y **Flujo Unidireccional de Datos (UDF)** dentro de un proyecto único de Android.

```
app/src/main/java/com/saetasaldo/app/
├── data/
│   ├── local/
│   │   ├── dao/
│   │   │   ├── CardDao.kt
│   │   │   └── BalanceHistoryDao.kt
│   │   ├── entity/
│   │   │   ├── CardEntity.kt
│   │   │   └── BalanceHistoryEntity.kt
│   │   └── SaetaDatabase.kt
│   ├── remote/
│   │   ├── api/
│   │   │   └── SaetaApiService.kt
│   │   ├── cookie/
│   │   │   └── SessionCookieJar.kt
│   │   └── dto/
│   │       ├── SaldoRequestDto.kt
│   │       └── SaldoResponseDto.kt
│   ├── nfc/
│   │   └── AndroidNfcManager.kt
│   ├── ocr/
│   │   └── MlKitCaptchaSolver.kt
│   └── repository/
│       └── CardRepositoryImpl.kt
├── domain/
│   ├── model/
│   │   ├── SaetaCard.kt
│   │   ├── CardType.kt
│   │   ├── BalanceRecord.kt
│   │   └── TripEstimate.kt
│   ├── repository/
│   │   └── CardRepository.kt
│   └── usecase/
│       ├── GetCardBalanceUseCase.kt
│       ├── SolveCaptchaUseCase.kt
│       ├── ProcessNfcScanUseCase.kt
│       └── CalculateRemainingTripsUseCase.kt
├── ui/
│   ├── cards/
│   │   ├── CardsScreen.kt
│   │   ├── CardsViewModel.kt
│   │   └── components/
│   │       └── SaetaCardItem.kt
│   ├── detail/
│   │   ├── CardDetailScreen.kt
│   │   ├── CardDetailViewModel.kt
│   │   └── components/
│   │       ├── TripEstimatorCard.kt
│   │       └── BalanceHistoryList.kt
│   ├── nfc/
│   │   └── NfcScanBottomSheet.kt
│   ├── dialogs/
│   │   └── FallbackCaptchaDialog.kt
│   ├── theme/
│   │   ├── Color.kt
│   │   ├── Theme.kt
│   │   └── Type.kt
│   └── navigation/
│       ├── NavRoutes.kt
│       └── SaetaNavHost.kt
└── widget/
    ├── SaetaBalanceWidget.kt
    ├── SaetaBalanceWidgetReceiver.kt
    └── RefreshBalanceAction.kt
```

---

## 4. Componentes de Bajo Nivel y Servicios

### 4.1. Configuración de Red y Seguridad SSL
Se añade `app/src/main/res/xml/network_security_config.xml` para autorizar las conexiones al dominio `salta.miredbus.com.ar` sin rebajar la seguridad general de la aplicación:
```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <domain-config>
        <domain includeSubdomains="true">salta.miredbus.com.ar</domain>
        <trust-anchors>
            <certificates src="system" />
            <certificates src="user" />
        </trust-anchors>
    </domain-config>
</network-security-config>
```

### 4.2. Motor de OCR On-Device (`MlKitCaptchaSolver`)
* **Pipeline de Procesamiento de Imagen**:
  1. Recibe el `ByteArray` de `/captcha.png` y lo convierte a `Bitmap`.
  2. Convierte el `Bitmap` a escala de grises y aplica un filtro de umbralización binarizado (blanco/negro) para suprimir artefactos y sombras.
  3. Escala la imagen 2x mediante interpolación bilineal.
  4. Pasa el `InputImage` al detector `TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)`.
* **Estrategia de Reintento Silencioso**:
  - Si el texto reconocido contiene menos de 4 caracteres o el servidor retorna `error: 1`, el caso de uso `SolveCaptchaUseCase` reintenta automáticamente hasta 3 veces consecutivas con una nueva imagen de `/captcha.png`.
  - Si tras 3 intentos no se logra validar, se activa el evento `CaptchaFallbackRequired`, desplegando `FallbackCaptchaDialog` en la interfaz.

### 4.3. Subsistema NFC (`AndroidNfcManager`)
* **Detección y Mapeo**:
  - Tarjetas SAETA utilizan chips compatibles con ISO/IEC 14443 Type A (MIFARE Classic 1K o MIFARE Ultralight).
  - Al recibir un `Tag`, se extrae `tag.id` y se formatea a string hexadecimal en mayúsculas (ej: `04A1B2C3D4E5F6`).
* **Foreground Dispatch**:
  - En `MainActivity.onResume()`, se activa `nfcAdapter.enableForegroundDispatch()`.
  - Al detectar la tarjeta mientras la app está abierta, se emite una vibración háptica suave (`VibrationEffect.createOneShot(50, DEFAULT_AMPLITUDE)`).
  - Si el UID ya existe en la base de datos, se actualiza el saldo de la tarjeta inmediatamente.
  - Si el UID es nuevo, se abre la pantalla para registrar la tarjeta con el UID precargado.
* **Background Intent Filter**:
  - Declarado en `AndroidManifest.xml` con `res/xml/nfc_tech_filter.xml` (`NfcA`, `MifareClassic`, `IsoDep`).
  - Apoyar la tarjeta con la app cerrada lanza la actividad directamente en la vista de detalle de la tarjeta.

---

## 5. Persistencia Local (Room) y Modelo de Datos

### 5.1. Entidad `CardEntity`
```kotlin
@Entity(tableName = "cards")
data class CardEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val cardNumber: String,
    val nfcUid: String? = null,
    val type: CardType = CardType.AZUL_COMUN,
    val currentBalance: Double? = null,
    val lastUpdated: Long? = null,
    val isFavorite: Boolean = false,
    val cardState: String? = null
)
```

### 5.2. Entidad `BalanceHistoryEntity`
```kotlin
@Entity(
    tableName = "balance_history",
    foreignKeys = [
        ForeignKey(
            entity = CardEntity::class,
            parentColumns = ["id"],
            childColumns = ["cardId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("cardId")]
)
data class BalanceHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cardId: String,
    val balance: Double,
    val difference: Double,
    val timestamp: Long = System.currentTimeMillis()
)
```

### 5.3. Estimación de Pasajes (`CalculateRemainingTripsUseCase`)
* Tarifa plana de SAETA guardada en `DataStore` (ej: `$690.00`).
* Cálculo:
  $$\text{Viajes Restantes} = \left\lfloor \frac{\text{saldo}}{\text{tarifa}} \right\rfloor$$
* Si `type == CardType.AZUL_COMUN`, se indica que cuenta con 2 viajes adicionales de saldo de emergencia autorizados si la tarjeta está nominada.

---

## 6. Widget de Escritorio con Jetpack Glance

* **Librerías**: `androidx.glance:glance-appwidget` y `androidx.glance:glance-material3`.
* **Diseño del Widget**:
  - Compatible con tamaños 2x1, 2x2 y expandible.
  - Muestra el nombre de la tarjeta favorita, el saldo destacado (`$ 2.450,00`), la cantidad de viajes aproximados y la hora de la última sincronización.
* **Acción de Refresco (`RefreshBalanceAction`)**:
  - Implementa `ActionCallback` de Glance.
  - Al pulsar el botón de recarga en el widget:
    1. El widget cambia su estado visual a *"Actualizando..."*.
    2. Ejecuta `GetCardBalanceUseCase` en una corrutina de fondo.
    3. Al actualizarse la entidad en Room, se invoca `SaetaBalanceWidget().update(context, glanceId)`.

---

## 7. Interfaz de Usuario y Experiencia (Jetpack Compose)

* **Paleta de Colores Oficial SAETA**:
  - `SaetaBluePrimary`: `#0D47A1`
  - `SaetaBlueSecondary`: `#1976D2`
  - `SaetaGreenPrimary`: `#1B5E20`
  - `SaetaGreenSecondary`: `#388E3C`
  - Soporte dinámico para temas claros y oscuros.
* **Pantallas**:
  1. `CardsScreen`: Muestra las tarjetas con estilo de billetera física digital, gradientes distintivos según sean generales (Azul) o con beneficio (Verde), gesto Pull-to-Refresh y botón flotante para escanear/agregar.
  2. `CardDetailScreen`: Detalle ampliado, badge de viajes restantes con editor rápido de tarifa, gráfico/historial de variaciones de saldo y botón para establecer como tarjeta de widget.
  3. `NfcScanBottomSheet`: Diálogo animado con indicador de radar NFC y retroalimentación háptica.
  4. `FallbackCaptchaDialog`: Diálogo modal nativo en caso de agotamiento de los reintentos automáticos de OCR.

---

## 8. Estrategia de Testing

* **Pruebas Unitarias (`test/`)**:
  - `CalculateRemainingTripsUseCaseTest`: Verificación de casos con saldo exacto, saldo con decimales y saldo negativo de emergencia.
  - `SolveCaptchaUseCaseTest`: Simulación con mocks de respuestas exitosas y de fallo (`error: 1`) para comprobar que el reintento se ejecuta exactamente 3 veces antes de activar el fallback.
  - `CardRepositoryTest`: Validación del guardado de historial al detectar cambios en el saldo.
* **Pruebas de Instrumentación (`androidTest/`)**:
  - `CardDaoTest`: Inserción, actualización y borrado en cascada con base de datos en memoria.
  - `MlKitOcrIntegrationTest`: Evaluación de precisión con muestras reales precargadas de `/captcha.png`.
  - `GlanceWidgetTest`: Verificación de renderizado del widget y despacho del callback de actualización.

---

## 9. Próximos Pasos

Una vez validada esta especificación por el usuario:
1. Confirmación formal del documento de diseño.
2. Invocación de la habilidad `writing-plans` para desglosar la implementación en fases y tareas incrementales ejecutables.
