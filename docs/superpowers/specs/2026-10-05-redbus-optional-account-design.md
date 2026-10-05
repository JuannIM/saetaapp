# Especificacion de Diseno: Cuenta RedBus Opcional

**Fecha:** 2026-10-05
**Proyecto:** SAETA Saldo Android (`com.saetasaldo.app`)
**Estado:** Propuesto para revision
**Alcance:** Android

---

## 1. Objetivo

Agregar una conexion opcional con la cuenta oficial de RedBus para que una
persona autenticada pueda actualizar las tarjetas vinculadas a su cuenta sin
resolver el captcha publico en cada consulta.

La consulta anonima actual mediante captcha y OCR sigue siendo funcional y es
el fallback para:

- personas que no desean conectar una cuenta;
- sesiones vencidas o temporalmente inaccesibles;
- tarjetas locales que no estan vinculadas a la cuenta RedBus;
- respuestas autenticadas sin un saldo monetario principal reconocible.

La funcionalidad no se presenta como un bypass de captcha. Usa la ruta
autenticada que el propio portal ofrece a sus usuarios.

## 2. Evidencia y supuestos

### Contratos verificados pasivamente

El JavaScript publico del portal define:

- `POST /rest/loginInternal/login`
- `GET /rest/loginInternal/usuarioLogeado`
- `GET /rest/tarjetaInternal/listaTarjetas`
- `/rest/loginInternal/logOut` (metodo HTTP pendiente de confirmar)

Sin una sesion valida:

- `usuarioLogeado` devuelve `error = 1`;
- `listaTarjetas` devuelve `error = 99` y `tarjetas = null`.

El login oficial usa Cloudflare Turnstile y dispone de captcha de imagen como
fallback. Cloudflare indica que Turnstile necesita un entorno de navegador y
que una aplicacion Android nativa debe alojarlo en un `WebView`.

Fuentes:

- https://salta.miredbus.com.ar/
- https://developers.cloudflare.com/turnstile/get-started/mobile-implementation/
- https://developer.android.com/reference/android/webkit/CookieManager
- https://developer.android.com/privacy-and-security/risks/insecure-webview-native-bridges

### Supuestos que deben validarse antes de implementar los DTO

- Una respuesta autenticada real y anonimizada confirmara la forma completa de
  `listaTarjetas`.
- El monedero monetario se identifica con la descripcion
  `Principal (Dinero)`.
- `JSESSIONID` y `SERVER_USED`, administradas por `CookieManager`, son
  suficientes para consumir las rutas autenticadas desde OkHttp.
- El cierre remoto usa `GET`; si no se confirma, la primera version cerrara la
  sesion local eliminando cookies y no inventara un metodo.

La validacion se realiza con una cuenta autorizada por su titular. Nunca se
solicitan credenciales para pruebas ni se incorporan datos reales a fixtures,
logs, capturas o commits.

## 3. Experiencia de usuario

### Sin cuenta conectada

- La aplicacion funciona igual que hoy.
- El encabezado de "Mis Tarjetas SAETA" incluye una accion accesible
  "Cuenta RedBus".
- Al abrirla se explica que la conexion es opcional, que se abre el sitio
  oficial y que SAETA Saldo no lee ni almacena la contrasena.
- "Conectar con RedBus" abre el login oficial dentro de una pantalla dedicada.

### Durante el login

- El `WebView` carga exclusivamente contenido HTTPS del portal oficial.
- La persona escribe usuario, contrasena y resuelve Turnstile directamente en
  el sitio de RedBus.
- La aplicacion no inyecta JavaScript, no inspecciona el DOM y no recibe los
  valores de los campos.
- Tras cada navegacion principal completada, la aplicacion verifica la sesion
  mediante `usuarioLogeado`; solo `error = 0` confirma la conexion.
- El boton Atrás navega dentro del historial del `WebView` y, al agotarlo,
  cancela el flujo.

### Con cuenta conectada

- La pantalla de cuenta muestra "Cuenta RedBus conectada".
- El usuario puede sincronizar manualmente las tarjetas vinculadas.
- Al completar el login se ejecuta una primera sincronizacion.
- Las actualizaciones individuales priorizan la ruta autenticada cuando la
  tarjeta esta vinculada; si no, usan la consulta anonima actual.
- "Desconectar" elimina la sesion local. Las tarjetas y su historial local no
  se borran.

### Sesion vencida

- `error = 1` en `usuarioLogeado` o `error = 99` en `listaTarjetas` cambia el
  estado a desconectado.
- La consulta solicitada continua por el flujo anonimo cuando sea posible.
- La UI informa que la sesion vencio sin bloquear el resto de la aplicacion.

## 4. Arquitectura

```text
RedBusLoginScreen (WebView oficial)
            |
            v
Android CookieManager
            |
            v
WebViewCookieJar --------> RedBusAccountApiService
                                  |
                                  v
                         RedBusAccountRepository
                                  |
                    +-------------+-------------+
                    |                           |
                    v                           v
         GetCardBalanceUseCase        SyncRedBusCardsUseCase
                    |                           |
                    +-------------+-------------+
                                  |
                                  v
                           CardRepository
                                  |
                                  v
                         Room + BalanceHistory
```

### Separacion de clientes HTTP

Se mantienen dos clientes:

1. `NetworkClient`: flujo publico existente, con `SessionCookieJar` en memoria
   para enlazar captcha y consulta.
2. `RedBusAccountNetworkClient`: flujo autenticado, con un `CookieJar`
   respaldado por `android.webkit.CookieManager`.

La separacion evita mezclar la sesion autenticada con el captcha anonimo y
permite que el fallback siga funcionando aunque la cuenta se desconecte.

El cliente autenticado:

- usa la misma base `https://salta.miredbus.com.ar/`;
- nunca registra headers, cookies ni bodies;
- no envia cookies a otro host;
- expone solo consulta de sesion, listado de tarjetas y, una vez confirmado,
  cierre de sesion.

### Contratos internos

```kotlin
sealed interface RedBusSessionState {
    data object Unknown : RedBusSessionState
    data object Checking : RedBusSessionState
    data object Disconnected : RedBusSessionState
    data object Connected : RedBusSessionState
}

data class RedBusAccountCard(
    val cardNumber: String,
    val balance: Double,
    val cardType: CardType?,
    val cardState: String?,
    val suggestedName: String?
)

interface RedBusAccountRepository {
    val sessionState: StateFlow<RedBusSessionState>
    suspend fun checkSession(): Result<RedBusSessionState>
    suspend fun getLinkedCards(): Result<List<RedBusAccountCard>>
    suspend fun disconnect()
}

data class CardBalanceUpdate(
    val cardNumber: String,
    val balance: Double,
    val cardType: CardType?,
    val cardState: String?,
    val suggestedName: String?
)
```

`CardRepository.applyBalanceUpdate(update)` es el unico punto que actualiza
saldo e historial, tanto para respuestas anonimas como autenticadas. Asi se
preservan las reglas actuales:

- conservar alias, NFC y favorito de una tarjeta existente;
- crear una tarjeta nueva solo cuando la cuenta devuelve un numero valido;
- registrar historial solo si el saldo cambia;
- nunca eliminar tarjetas porque ya no aparezcan en la cuenta remota.

No se requiere migracion de Room.

## 5. Seleccion de saldo

Una tarjeta beneficiaria puede exponer mas de un monedero. Para no volver a
introducir beneficios que la aplicacion no sabe interpretar:

- se usa exclusivamente el saldo con descripcion normalizada
  `Principal (Dinero)`;
- no se suman boletos, abonos, gratuidades ni otros monederos;
- un monto debe ser finito y parseable;
- si falta ese saldo, la tarjeta se omite de la sincronizacion y la consulta
  local conserva el fallback anonimo.

La conversion de importes reutiliza las reglas probadas de
`SaldoResponseDto.parseAmount`.

## 6. Politica de sincronizacion

### Sincronizacion explicita

`SyncRedBusCardsUseCase` recibe una lista ya validada de
`RedBusAccountCard` y aplica una actualizacion por numero externo:

- tarjeta existente: conserva metadatos locales y actualiza saldo, estado y
  tipo interno;
- tarjeta nueva: usa la descripcion remota no vacia o `Tarjeta SAETA`;
- duplicados remotos: se deduplican por numero externo;
- respuestas parciales: actualizan solo tarjetas validas.

### Actualizacion individual

`GetCardBalanceUseCase` sigue este orden:

1. Si existe un captcha manual, usa directamente el flujo anonimo.
2. Si el estado no es `Connected`, usa el flujo anonimo.
3. Si esta conectado, solicita las tarjetas vinculadas.
4. Si encuentra el numero y saldo principal, aplica `CardBalanceUpdate`.
5. Si no encuentra la tarjeta o falla la ruta autenticada, usa el flujo
   anonimo.

### Actualizacion masiva

`RefreshAllBalancesUseCase` solicita `listaTarjetas` una sola vez:

- sincroniza todas las vinculadas validas;
- actualiza mediante captcha solo las tarjetas locales no cubiertas;
- conserva el espaciado actual de 800 ms entre consultas anonimas;
- reporta errores parciales sin revertir actualizaciones exitosas.

## 7. Seguridad y privacidad

### Credenciales

- No existe formulario nativo de usuario/contrasena.
- No se usa `evaluateJavascript`, `addJavascriptInterface`,
  `postWebMessage` ni lectura del DOM.
- Las credenciales solo se envian desde el formulario oficial.
- Nunca se almacenan contrasenas, tokens Turnstile o payloads de login.

### WebView

- JavaScript y DOM storage se habilitan porque Turnstile los requiere.
- Se deshabilitan acceso a archivos, acceso a contenido local y mixed content.
- Safe Browsing permanece habilitado.
- Los errores TLS se cancelan; nunca se llama `proceed()`.
- Las navegaciones principales se permiten solo para
  `https://salta.miredbus.com.ar`.
- Los subframes HTTPS necesarios para Turnstile pueden cargar, pero no tienen
  puente nativo.
- Enlaces externos de nivel superior se abren fuera de la aplicacion o se
  bloquean; nunca se cargan silenciosamente en el contexto autenticado.
- El `WebView` se destruye al salir de la pantalla.

### Cookies

- Las cookies viven en el almacenamiento privado de `CookieManager`.
- El cliente autenticado las lee solo para el host RedBus.
- No se copian a Room, preferencias, logs, analytics ni backups.
- `android:allowBackup="false"` permanece activo.
- Desconectar elimina las cookies aunque el cierre remoto falle.

### Minimizacion

- `usuarioLogeado` se parsea solo para conocer `error`; nombre, documento,
  roles, telefono y otros datos se ignoran.
- De `listaTarjetas` se conservan solo numero externo, saldo principal, tipo,
  estado y descripcion.
- Numero interno, relacion con el titular y movimientos quedan fuera de
  alcance.

## 8. Fuera de alcance

- Crear cuentas RedBus desde una API nativa.
- Recibir o guardar credenciales RedBus.
- Asociar o desasociar tarjetas remotamente.
- Mostrar movimientos o cargas pendientes.
- Interpretar boletos gratis, abonos o beneficios.
- Actualizacion autenticada desde el widget en procesos en frio; el widget
  conserva el flujo anonimo actual.
- Persistir manualmente `JSESSIONID` fuera de `CookieManager`.
- Garantizar estabilidad de endpoints internos no documentados.

## 9. Documentacion y cumplimiento

Antes de publicar:

- actualizar `PRIVACY_POLICY.md`;
- actualizar `docs/compliance/DATA_SAFETY.md`;
- reemplazar afirmaciones como "no existen cuentas" por una descripcion exacta
  de la conexion opcional;
- cambiar el distintivo "100% Local" por
  "Sin servidores propios - Sin rastreo - Codigo abierto";
- documentar que RedBus procesa credenciales y datos de cuenta;
- mantener el descargo de que SAETA Saldo no es una aplicacion oficial;
- realizar una revision de terminos y obtener autorizacion del proveedor si
  fuera necesaria para distribucion publica.

## 10. Estrategia de pruebas

### Unitarias

- Cookies: separacion por host, valores con `=`, expiracion y limpieza.
- DTO/mapeo: fixture autenticada anonimizada, errores `1` y `99`, esquema
  incompleto, multiples monederos y ausencia de saldo principal.
- Repositorio de cuenta: transiciones de sesion y limpieza incondicional.
- Persistencia: conserva alias/NFC/favorito y evita historial duplicado.
- Actualizacion individual: autenticada, no vinculada, expirada, error de red y
  captcha manual.
- Actualizacion masiva: una sola carga autenticada, fallback de no vinculadas y
  errores parciales.
- ViewModel: conectar, verificar, sincronizar, vencer sesion y desconectar.

### Robolectric

- Configuracion segura del `WebView`.
- Allowlist exacta de esquema y host.
- Boton Atrás y destruccion del `WebView`.

### Manuales en dispositivo

- Login con Turnstile.
- Alta y consulta anonima sin cuenta.
- Sincronizacion de una cuenta real autorizada.
- Reinicio de app y verificacion de sesion.
- Sesion vencida.
- Tarjeta local no vinculada.
- Desconexion y comprobacion de que las tarjetas locales permanecen.
- Widget despues de desconectar.

## 11. Comandos de verificacion

Desde `android/`, en un entorno con Android SDK y Gradle 8.11.1:

```bash
gradle test --stacktrace
gradle assembleDebug --stacktrace
```

La verificacion autoritativa tambien se ejecuta en
`.github/workflows/build-android.yml`.

## 12. Criterios de aceptacion

- La aplicacion sigue siendo totalmente util sin una cuenta RedBus.
- La persona inicia sesion solo en el sitio oficial dentro del `WebView`.
- La aplicacion no recibe, registra ni persiste credenciales.
- Una sesion valida actualiza tarjetas vinculadas sin captcha por consulta.
- Las tarjetas no vinculadas siguen usando el flujo anonimo.
- Solo se muestra y persiste el saldo `Principal (Dinero)`.
- Una sesion vencida no bloquea ni rompe el modo anonimo.
- Desconectar elimina la sesion y conserva datos locales.
- No se agregan dependencias ni migraciones Room.
- Pruebas unitarias y ensamblado debug pasan en CI.
- Politica de privacidad y declaracion Data Safety reflejan el flujo real.

## 13. Puertas de implementacion

1. Revocar cualquier token de GitHub expuesto y provisionar uno nuevo fuera
   del chat si fuera necesario.
2. Aprobar esta especificacion y el plan asociado.
3. Capturar y anonimizar fixtures reales con una cuenta autorizada.
4. Confirmar el contrato de cierre de sesion.
5. Implementar en una rama `feature/redbus-account-session`.
6. Mostrar el diff completo para revision humana antes de subir o abrir PR.
