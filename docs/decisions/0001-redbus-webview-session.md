# ADR-0001: Sesion RedBus opcional mediante el login web oficial

## Estado

Aceptado

## Fecha

2026-10-05

## Contexto

La consulta publica de saldo de RedBus exige un captcha asociado a una sesion
HTTP. SAETA Saldo actualmente descarga esa imagen, la resuelve localmente con
ML Kit y consulta `resultadoSaldo`.

El portal RedBus ofrece otra ruta a usuarios autenticados:

- verifica la sesion con `usuarioLogeado`;
- obtiene tarjetas asociadas mediante `listaTarjetas`;
- devuelve sus saldos sin solicitar un captcha por cada consulta.

El login oficial usa Cloudflare Turnstile y puede usar un captcha de imagen
como fallback. Turnstile necesita un navegador con JavaScript, DOM storage y
cookies. Implementar un formulario nativo implicaria recibir credenciales,
reproducir un contrato interno de autenticacion y asumir responsabilidades de
almacenamiento que la aplicacion no necesita.

## Decision

La cuenta RedBus sera opcional. El inicio de sesion se realizara en un
`WebView` que cargue el formulario oficial de
`https://salta.miredbus.com.ar`.

La aplicacion:

- no implementara un formulario nativo de credenciales;
- no leera el DOM ni inyectara un puente JavaScript;
- compartira la sesion mediante `android.webkit.CookieManager`;
- usara un cliente OkHttp/Retrofit separado para endpoints autenticados;
- confirmara la conexion exclusivamente con
  `GET /rest/loginInternal/usuarioLogeado`;
- obtendra tarjetas con
  `GET /rest/tarjetaInternal/listaTarjetas`;
- mantendra el flujo anonimo de captcha/OCR como fallback;
- eliminara cookies al desconectar, incluso si el cierre remoto falla.

Solo se importara el monedero `Principal (Dinero)`. La app no interpretara ni
sumara beneficios, abonos o boletos gratuitos.

## Alternativas consideradas

### Formulario de login nativo

**Ventajas**

- Interfaz completamente integrada.
- Control directo del estado de carga y errores.

**Rechazada porque**

- La app recibiria usuario y contrasena.
- El contrato de login es interno y puede cambiar.
- Turnstile no se ejecuta nativamente.
- Aumenta el riesgo de phishing, filtracion por logs y almacenamiento
  accidental.

### Abrir un Custom Tab o navegador externo

**Ventajas**

- Aislamiento y seguridad proporcionados por el navegador.
- Compatibilidad alta con Turnstile y gestores de contrasenas.

**Rechazada para la primera version porque**

- RedBus no ofrece OAuth, App Links ni un callback documentado.
- No existe un intercambio oficial de token que devuelva la sesion a la app.
- Las cookies del navegador no se pueden leer desde la aplicacion.

Se debe reconsiderar si RedBus publica OAuth o un mecanismo de deep link.

### Persistir manualmente `JSESSIONID`

**Ventajas**

- Facilitaria actualizaciones autenticadas desde procesos en segundo plano.

**Rechazada porque**

- La cookie es una credencial portadora.
- Duplica el almacenamiento de sesion de `CookieManager`.
- Exige cifrado, rotacion y migraciones adicionales.
- El widget ya dispone del flujo anonimo y no justifica ese riesgo.

### Eliminar el flujo anonimo

**Rechazada porque**

- Obligaria a crear una cuenta.
- No todas las tarjetas locales estan asociadas a la cuenta.
- Los endpoints autenticados no estan documentados ni garantizados.

## Consecuencias

### Positivas

- El usuario decide si conecta su cuenta.
- La app no recibe ni persiste contrasenas.
- Las tarjetas asociadas pueden actualizarse sin captcha repetitivo.
- Una falla del servicio autenticado no elimina el modo existente.
- No se agregan dependencias ni migraciones de base de datos.

### Negativas

- El login depende del Android System WebView y de cambios del portal.
- Se habilitan JavaScript, DOM storage y cookies de terceros en esa pantalla.
- La API autenticada es interna y puede cambiar sin versionado.
- Las actualizaciones autenticadas del widget quedan fuera de la primera
  version.
- La politica de privacidad y Data Safety deben actualizarse.

## Controles obligatorios

- Solo HTTPS y host principal exacto `salta.miredbus.com.ar`.
- Mixed content, archivos y contenido local deshabilitados.
- Safe Browsing habilitado.
- Errores TLS siempre cancelados.
- Ningun JavaScript bridge o inspeccion del DOM.
- Cliente autenticado sin logging de headers o bodies.
- Cookies nunca persistidas fuera de `CookieManager`.
- Fixtures anonimizadas antes de entrar al repositorio.
- Fallback seguro ante sesion vencida, red o cambio de esquema.
- Revision de los terminos del proveedor antes de distribucion publica.

## Addendum — Turnstile WebView (2026-10-05)

`WebViewTurnstileTokenProvider` introduce un segundo WebView, fuera de
pantalla, que resuelve el captcha anonimo de `resultadoSaldo` ejecutando el
desafio Turnstile del portal y enviando el token con `X-Use-New-Captcha: true`.

A diferencia del WebView de login, este lleva un puente JavaScript unidireccional
(`@JavascriptInterface` `AndroidTurnstile.onToken`/`onError`): es la unica via
para que el desafio devuelva el token a Kotlin. Se acepta aqui porque la pagina
cargada es la publica y anonima `https://salta.miredbus.com.ar/` — no se ingresan
credenciales ni se inspecciona una sesion autenticada — y porque el main frame
sigue limitado al host exacto por HTTPS, los errores TLS cancelan la carga y el
WebView se destruye al finalizar cada intento (exito, error, timeout o
cancelacion). El sitekey se obtiene de `GET /rest/getTurnstileKeySite` y se
valida contra una lista blanca antes de inyectarlo en el script.

El WebView de login sigue prohibiendo cualquier puente nativo o inspeccion del
DOM: ahi si circulan credenciales y cookies de sesion.

El widget nunca construye este provider: mantiene el repositorio sin Turnstile
y el flujo captcha/OCR. Si el token falla, es rechazado (`error: 1`) o la red
falla, la consulta degrada al captcha de imagen con OCR y luego al ingreso
manual.

## Referencias

- `docs/superpowers/specs/2026-10-05-redbus-optional-account-design.md`
- https://developers.cloudflare.com/turnstile/get-started/mobile-implementation/
- https://developer.android.com/reference/android/webkit/CookieManager
- https://developer.android.com/privacy-and-security/risks/insecure-webview-native-bridges
- https://developer.android.com/privacy-and-security/risks/webview-unsafe-file-inclusion
