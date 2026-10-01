# Política de Privacidad / Privacy Policy — SAETA Saldo Android

**Última actualización / Last updated:** 1 de Octubre de 2026  
**Aplicación / Application:** SAETA Saldo (com.saetasaldo.app)  
**Desarrollador / Developer:** Juan Ignacio Mercado (Proyecto de Código Abierto / Open Source Project)  
**Repositorio / Repository:** [https://github.com/JuannIM/saetaapp](https://github.com/JuannIM/saetaapp)

---

## 1. Declaración de Principios y Arquitectura Local-First

**SAETA Saldo** es una aplicación móvil de código abierto desarrollada con el único propósito de permitir a los usuarios del transporte público metropolitano de Salta (SAETA) consultar el saldo y viajes disponibles de sus tarjetas de transporte de forma rápida, accesible y transparente.

La aplicación opera bajo el principio estricto de **Privacidad por Diseño (Privacy by Design)** y **Almacenamiento Local Exclusivo (Local-First)**:
- **Cero Telemetría / Zero Trackers:** No contiene SDKs de análisis, publicidad, rastreo de comportamiento ni analítica (Google Analytics, Firebase Analytics, Crashlytics, Mixpanel, etc.).
- **Sin Cuentas ni Registros:** No requiere correo electrónico, nombre, número de teléfono, contraseña ni vinculación con redes sociales.
- **Sin Servidores Propios:** No disponemos de servidores intermedios, bases de datos en la nube ni servicios de retransmisión (relays). La aplicación no recopila ni centraliza información de sus usuarios.

---

## 2. Datos Tratados y Finalidad

| Dato | Dónde se origina | Dónde se almacena | Se transmite a terceros | Finalidad |
| :--- | :--- | :--- | :--- | :--- |
| **Número de Tarjeta SAETA** | Ingresado manualmente o leído por NFC | 100% localmente en el dispositivo (SQLite Room) | Exclusivamente al portal oficial de MiRedBus Salta vía HTTPS | Consultar el saldo oficial |
| **Alias de la Tarjeta** | Ingresado por el usuario (ej. "Mi Tarjeta") | 100% localmente en el dispositivo | **Nunca** | Identificación visual en la UI |
| **Historial de Saldos** | Calculado tras cada consulta | 100% localmente en el dispositivo | **Nunca** | Mostrar la evolución del saldo al usuario |
| **Tarifa de Referencia** | Configurada por el usuario (por defecto $1.450) | 100% localmente (DataStore / SharedPreferences) | **Nunca** | Calcular viajes restantes disponibles |
| **Imágenes de Captcha** | Descargadas temporalmente del portal de RedBus | En memoria RAM volátil | **Nunca** | Resolución automática del captcha en el equipo |

---

## 3. Procesamiento de Captcha en el Dispositivo (On-Device OCR)

Para automatizar la consulta de saldo sin requerir intervención manual constante, la aplicación utiliza la biblioteca **Google ML Kit Text Recognition** (`com.google.android.gms:play-services-mlkit-text-recognition`).

- El procesamiento óptico de caracteres (OCR) se ejecuta **completamente de forma local en el procesador del dispositivo (on-device)**.
- El mapa de bits (bitmap) del captcha se procesa en memoria volátil y se libera inmediatamente una vez obtenido el texto.
- **Ninguna imagen ni resultado OCR se envía a servidores de Google ni a ningún otro servicio en la nube**.

---

## 4. Comunicaciones de Red y Conectividad

La aplicación únicamente realiza conexiones de red salientes hacia los servidores oficiales de consulta de transporte:
- **Destino:** Servidor oficial de RedBus Salta (`https://salta.miredbus.com.ar`).
- **Seguridad:** Todas las comunicaciones se realizan de forma obligatoria mediante **cifrado TLS 1.2 / TLS 1.3 (HTTPS)**. El tráfico en texto plano (`cleartext HTTP`) está expresamente deshabilitado a nivel del sistema operativo mediante [`network_security_config.xml`](android/app/src/main/res/xml/network_security_config.xml).
- **Carga Útil:** La solicitud únicamente envía los parámetros técnicos requeridos por el servicio de consulta: número de tarjeta y texto del captcha resuelto. **No se envían identificadores del dispositivo (Android ID, IMEI, IMSI, dirección MAC ni ID de Publicidad de Google)**.

---

## 5. Permisos de la Aplicación y Justificación

La aplicación solicita exclusivamente los permisos técnicos estrictamente indispensables para su funcionamiento:

1. **`android.permission.NFC`**:
   - **Justificación:** Se utiliza de manera interactiva en primer plano (foreground dispatch) para leer el UID del chip contactless de la tarjeta SAETA física cuando el usuario la apoya en el sensor NFC del teléfono.
   - **Uso:** El escaneo solo ocurre si el usuario abre la aplicación y acerca la tarjeta; no se realiza escaneo pasivo ni en segundo plano.
2. **`android.permission.INTERNET`**:
   - **Justificación:** Necesario para emitir la petición HTTP segura al servidor de MiRedBus y obtener el saldo.
3. **`android.permission.ACCESS_NETWORK_STATE`**:
   - **Justificación:** Verifica si el dispositivo cuenta con conexión activa a Internet antes de disparar solicitudes innecesarias, ahorrando batería y datos móviles.

---

## 6. Control del Usuario, Modificación y Eliminación de Datos

De acuerdo con la **Ley Argentina N° 25.326 de Protección de los Datos Personales** y las directrices globales de privacidad:

- **Modificación y Edición:** Puedes modificar el alias y la tarifa de cualquier tarjeta en cualquier momento desde la interfaz.
- **Eliminación Total e Inmediata:** Al pulsar el botón de eliminar tarjeta, el registro y todo su historial de saldos asociado se purgan permanentemente de la base de datos local SQLite mediante eliminación en cascada (`CASCADE DELETE`).
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

- **Local-First:** All card data, aliases, and balance logs are stored 100% on-device in a local SQLite database. No external servers or cloud accounts exist.
- **Zero Trackers:** No analytics, advertising, or telemetry SDKs are included.
- **Direct Queries:** HTTPS queries are made directly from your phone to `salta.miredbus.com.ar`. No device identifiers or personal info are transmitted.
- **On-Device OCR:** Captchas are processed locally on your phone using Google ML Kit. No images are sent to the cloud.
- **Data Deletion:** Deleting a card wipes all its associated history immediately. Uninstalling the app permanently purges all local data.
- **Open Source:** Full source code is available for audit at [github.com/JuannIM/saetaapp](https://github.com/JuannIM/saetaapp).
