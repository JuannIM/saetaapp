# Política de Privacidad / Privacy Policy — SAETA Saldo Android

**Última actualización / Last updated:** 7 de Octubre de 2026\
**Aplicación / Application:** SAETA Saldo (com.saetasaldo.app)\
**Desarrollador / Developer:** Juan Ignacio Mercado (Proyecto de Código Abierto / Open Source Project)\
**Repositorio / Repository:** [https://github.com/JuannIM/saetaapp](https://github.com/JuannIM/saetaapp)

---

## 1. Declaración de Principios y Arquitectura Local-First

**SAETA Saldo** es una aplicación móvil de código abierto desarrollada con el único propósito de permitir a los usuarios del transporte público metropolitano de Salta (SAETA) consultar el saldo y viajes disponibles de sus tarjetas de transporte de forma rápida, accesible y transparente.

La aplicación opera bajo el principio estricto de **Privacidad por Diseño (Privacy by Design)** y **Almacenamiento Local Exclusivo (Local-First)**:
- **Cero Telemetría / Zero Trackers:** No contiene SDKs de análisis, publicidad, rastreo de comportamiento ni analítica (Google Analytics, Firebase Analytics, Crashlytics, Mixpanel, etc.).
- **Cuenta RedBus Opcional / Optional Account:** La conexión con una cuenta RedBus es opcional; sin ella la app funciona igual (modo anónimo con captcha). La aplicación no posee registro propio: no solicita correo electrónico, nombre, número de teléfono ni vinculación con redes sociales. Si eliges conectar tu cuenta, el inicio de sesión se realiza en el sitio oficial de RedBus (ver §4.1).
- **Sin Servidores Propios:** No disponemos de servidores intermedios, bases de datos en la nube ni servicios de retransmisión (relays). La aplicación no recopila ni centraliza información de sus usuarios.

---

## 2. Datos Tratados y Finalidad

| Dato | Dónde se origina | Dónde se almacena | Se transmite a terceros | Finalidad |
| :--- | :--- | :--- | :--- | :--- |
| **Número de Tarjeta SAETA** | Ingresado manualmente, leído por NFC o sincronizado desde tu cuenta RedBus (opcional) | 100% localmente en el dispositivo (SQLite Room) | Exclusivamente al portal oficial de MiRedBus Salta vía HTTPS (modo anónimo con captcha, o sesión autenticada opcional) | Consultar el saldo oficial |
| **Alias de la Tarjeta** | Ingresado por el usuario (ej. "Mi Tarjeta") | 100% localmente en el dispositivo | **Nunca** | Identificación visual en la UI |
| **Historial de Saldos** | Calculado tras cada consulta | 100% localmente en el dispositivo | **Nunca** | Mostrar la evolución del saldo al usuario |
| **Tarifa de Referencia** | Configurada por el usuario en el detalle de la tarjeta (por defecto $1.450) | 100% localmente en el dispositivo (SharedPreferences) | **Nunca** | Calcular viajes restantes disponibles |
| **Imágenes de Captcha** | Descargadas temporalmente del portal de RedBus | En memoria RAM volátil | **Nunca** | Resolución automática del captcha en el equipo |
| **Sesión RedBus (cookies)** | Portal oficial | CookieManager privado de la app | Solo a salta.miredbus.com.ar | Consultar tarjetas vinculadas sin captcha |
| **Monederos, número interno, descripción y cargas pendientes** (cuenta RedBus opcional) | Portal oficial, a través de tu sesión | Localmente en el dispositivo (SQLite Room); las cargas pendientes, solo en memoria | **Nunca** | Mostrar el saldo de cada monedero y avisar cargas pendientes de acreditación |
| **Resumen de la tarjeta favorita** (alias, saldo, viajes estimados, hora de actualización) | Calculado en el teléfono | Teléfono y reloj Wear OS vinculado | Solo a tu propio reloj, mediante Google Play Services (Wearable Data Layer) | Mostrar el saldo en el reloj |
| **Ubicación aproximada (coarse)** | GPS/red del dispositivo, solo si el usuario pulsa el botón "Mi ubicación" del mapa | Solo en memoria volátil; se descarta al instante | **Nunca** | Centrar el mapa de buses en la posición del usuario |

---

## 3. Procesamiento de Captcha en el Dispositivo (On-Device OCR)

Para automatizar la consulta de saldo sin requerir intervención manual constante, la aplicación utiliza la biblioteca **Google ML Kit Text Recognition** (`com.google.mlkit:text-recognition`, con el modelo incluido en la app).

- El procesamiento óptico de caracteres (OCR) se ejecuta **completamente de forma local en el procesador del dispositivo (on-device)**.
- El mapa de bits (bitmap) del captcha se procesa en memoria volátil y se libera inmediatamente una vez obtenido el texto.
- **Ninguna imagen ni resultado OCR se envía a servidores de Google ni a ningún otro servicio en la nube**.

---

## 4. Comunicaciones de Red y Conectividad

La aplicación realiza conexiones de red salientes hacia los servidores oficiales de consulta de transporte y hacia el servicio anti-bots que usa el propio portal:
- **Destino:** Servidor oficial de RedBus Salta (`https://salta.miredbus.com.ar`).
- **Seguridad:** Todas las comunicaciones se realizan de forma obligatoria mediante **cifrado TLS 1.2 / TLS 1.3 (HTTPS)**. El tráfico en texto plano (`cleartext HTTP`) está expresamente deshabilitado a nivel del sistema operativo mediante [`network_security_config.xml`](android/app/src/main/res/xml/network_security_config.xml).
- **Carga Útil (modo anónimo):** La solicitud únicamente envía los parámetros técnicos requeridos por el servicio de consulta: número de tarjeta y texto del captcha resuelto. **No se envían identificadores del dispositivo (Android ID, IMEI, IMSI, dirección MAC ni ID de Publicidad de Google)**.
- **Desafío Turnstile (modo anónimo):** Para resolver la verificación automáticamente, la app puede ejecutar un desafío de Cloudflare Turnstile dentro de un WebView interno fuera de pantalla sobre el dominio oficial `salta.miredbus.com.ar` — el mismo desafío que el portal ejecuta en un navegador. El desafío lo provee Cloudflare (`challenges.cloudflare.com`), que procesa datos técnicos de la conexión y del navegador (como la dirección IP) para distinguir personas de bots, según su propia política de privacidad. No intervienen credenciales; si el desafío falla se recurre al captcha de imagen con OCR local.
- **Mapa de buses en tiempo real:** La pantalla de mapa consulta al mismo portal oficial los recorridos, paradas y posiciones de buses en circulación, que el servicio de RedBus/SAETA publica de forma pública y anónima. El mapa usa los servidores de teselas (mapas) que el propio servicio anuncia en su configuración (`mapmoblrj.red-bus.com.ar` y `b.tile.openstreetmap.org`, ambos por HTTPS), que pueden registrar la dirección IP como cualquier descarga de mapas web. Ninguna consulta envía datos del usuario ni del dispositivo.

### 4.1 Conexión Opcional con Cuenta RedBus (Inicio de Sesión Web)

Si decides conectar tu cuenta, la aplicación abre la página oficial de inicio de sesión de RedBus (`https://salta.miredbus.com.ar/login`) dentro de un WebView endurecido:

- **Tus credenciales van directamente al portal oficial.** SAETA Saldo no incluye formulario de contraseña propio, no lee el contenido de la página (DOM) ni los campos que completas, y no utiliza puente JavaScript (`addJavascriptInterface`) ni ningún mecanismo para inspeccionar la página.
- **Navegación restringida:** solo se permite navegar dentro del host exacto `salta.miredbus.com.ar` por HTTPS; cualquier otra dirección se bloquea.
- **Errores TLS cancelan la carga:** ante un error de certificado la navegación se cancela en lugar de continuar.
- **Cookies de sesión:** tras el inicio de sesión, las cookies del portal se conservan únicamente en el `CookieManager` privado de la aplicación y se envían solo a `salta.miredbus.com.ar` para consultar tus tarjetas vinculadas (número de tarjeta, saldo principal y de los demás monederos, tipo, estado, número interno, descripción y cargas pendientes de acreditación). Nunca se guardan en archivos, bases de datos ni registros (logs) del desarrollador.
- **La aplicación nunca recibe ni almacena tu contraseña de RedBus.**

### 4.2 Reloj Wear OS (Opcional)

Si tienes un reloj Wear OS vinculado con SAETA Saldo instalado, el teléfono le envía el alias, el saldo, la estimación de viajes y la hora de la última actualización de tu tarjeta favorita mediante la API **Wearable Data Layer de Google Play Services**, que sincroniza esos datos entre tus dispositivos. El reloj no se conecta al portal: solo muestra lo que recibe del teléfono y puede pedirle que actualice el saldo.

---

## 5. Permisos de la Aplicación y Justificación

La aplicación solicita los siguientes permisos (algunos los agrega automáticamente una biblioteca de Android Jetpack):

1. **`android.permission.NFC`**:
   - **Justificación:** Se utiliza de manera interactiva en primer plano (foreground dispatch) para leer el UID del chip contactless de la tarjeta SAETA física cuando el usuario la apoya en el sensor NFC del teléfono.
   - **Uso:** El escaneo solo ocurre si el usuario abre la aplicación y acerca la tarjeta; no se realiza escaneo pasivo ni en segundo plano.
2. **`android.permission.INTERNET`**:
   - **Justificación:** Necesario para emitir la petición HTTP segura al servidor de MiRedBus y obtener el saldo.
3. **`android.permission.ACCESS_NETWORK_STATE`**:
   - **Justificación:** La requiere WorkManager, la biblioteca de Android Jetpack que usa el widget de escritorio. La app no la usa directamente.
4. **`android.permission.VIBRATE`**:
   - **Justificación:** Vibración breve (respuesta háptica) al detectar la tarjeta por NFC.
5. **`android.permission.WAKE_LOCK`, `android.permission.RECEIVE_BOOT_COMPLETED` y `android.permission.FOREGROUND_SERVICE`**:
   - **Justificación:** Las agrega automáticamente WorkManager para el trabajo en segundo plano del widget. La app no las usa directamente.
6. **`android.permission.ACCESS_COARSE_LOCATION`**:
   - **Justificación:** Se solicita en contexto únicamente cuando el usuario pulsa el botón "Mi ubicación" del mapa de buses, para centrar el mapa en su posición.
   - **Uso:** Solo ubicación aproximada (nunca precisa ni en segundo plano): la última posición conocida y una única lectura puntual; no hay rastreo continuo. La ubicación jamás se almacena, se comparte ni se transmite — se usa solo en el dispositivo para mover la cámara del mapa. Si el permiso se niega, el mapa sigue funcionando con normalidad.

---

## 6. Control del Usuario, Modificación y Eliminación de Datos

De acuerdo con la **Ley Argentina N° 25.326 de Protección de los Datos Personales** y las directrices globales de privacidad:

- **Modificación y Edición:** Puedes modificar el alias y la tarifa de cualquier tarjeta en cualquier momento desde la interfaz.
- **Eliminación Total e Inmediata:** Al pulsar el botón de eliminar tarjeta, el registro y todo su historial de saldos asociado se purgan permanentemente de la base de datos local SQLite mediante eliminación en cascada (`CASCADE DELETE`).
- **Desconexión de la cuenta RedBus:** La opción **"Desconectar"** del diálogo de cuenta elimina localmente las cookies de sesión del portal del dispositivo. Tus tarjetas, alias e historial locales permanecen intactos.
- **Desinstalación:** Si desinstalas la aplicación desde los ajustes de Android, el sistema operativo elimina la totalidad de los datos y bases de datos locales asociadas sin que quede ningún remanente.

---

## 7. Deslinde de Responsabilidad (Disclaimer)

**SAETA Saldo** es un desarrollo independiente y comunitario de código abierto.
- **No es una aplicación oficial** de la Sociedad Anónima del Estado del Transporte Automotor (SAETA S.A.), ni de MiRedBus (Worldline / Atos), ni del Gobierno de la Provincia de Salta.
- Los nombres comerciales, logotipos y marcas registradas "SAETA" y "RedBus" pertenecen a sus respectivos titulares y se utilizan en esta documentación únicamente con fines de identificación y referencia técnica legítima.
- El servicio de consulta de saldo depende de la disponibilidad y correcto funcionamiento de los servidores de MiRedBus Salta.

---

## 8. Licencia y Código Fuente

El código fuente completo de la aplicación es público y auditable por cualquier persona, garantizando la total transparencia de lo aquí declarado:
- **Repositorio:** [https://github.com/JuannIM/saetaapp](https://github.com/JuannIM/saetaapp)
- **Reporte de incidencias o consultas:** Puedes abrir un issue directamente en el repositorio de GitHub.

---

# English Summary

- **Local-First:** All card data, aliases, and balance logs are stored 100% on-device in a local SQLite database. The developer runs no external servers or cloud services of its own.
- **Zero Trackers:** No analytics, advertising, or telemetry SDKs are included.
- **Direct Queries:** HTTPS queries are made directly from your phone to `salta.miredbus.com.ar`. No device identifiers or personal info are transmitted. Anonymous balance queries may run a Cloudflare Turnstile challenge inside an offscreen in-app WebView on the official domain; Cloudflare processes technical connection data (such as the IP address) to tell humans from bots. No credentials are involved.
- **Optional RedBus Account:** You may optionally log in on the official RedBus site inside a hardened in-app WebView. Credentials go straight to the official page — the app never reads or stores your password. Portal session cookies stay in the app's private CookieManager, are sent only to `salta.miredbus.com.ar`, and are deleted when you disconnect. Without an account, anonymous captcha mode works exactly the same. Account sync stores the linked cards' wallet balances, internal number and description on-device only; pending loads are fetched on demand.
- **Wear OS (optional):** The favorite card's alias, balance, trip estimate and last-update time are sent to your own paired watch through the Google Play services Wearable Data Layer. The watch never contacts the portal.
- **Live bus map:** The map reads publicly available routes, stops and bus positions from the official portal anonymously. Approximate (coarse) location is requested in context only when you tap "Mi ubicación", is used once on-device to center the map, and is never stored, shared, or transmitted.
- **On-Device OCR:** Captchas are processed locally on your phone using Google ML Kit. No images are sent to the cloud.
- **Data Deletion:** Deleting a card wipes all its associated history immediately. Disconnecting the account removes the portal session cookies; your local cards and history remain. Uninstalling the app permanently purges all local data.
- **Open Source:** Full source code is available for audit at [github.com/JuannIM/saetaapp](https://github.com/JuannIM/saetaapp).
