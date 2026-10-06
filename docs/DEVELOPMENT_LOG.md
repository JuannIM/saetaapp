# Registro de desarrollo — Integración RedBus y captcha sin fricción

Fecha: octubre 2026 · Rama: `feature/redbus-account-session` → mergeada en `main` (PR #1) · Release: `v1.1.0`

## Objetivo

Eliminar la fricción del captcha en las consultas de saldo y permitir que la app
refleje fielmente el servicio real de SAETA/RedBus, sin comprometer privacidad
ni almacenar credenciales.

## Investigación del portal (evidencia en producción)

Se analizó el portal `salta.miredbus.com.ar` y su JavaScript (ofuscado) en vivo:

| Hallazgo | Evidencia |
|---|---|
| `resultadoSaldo` siempre exige `verificacionCaptcha` válido | Probe autenticado con captcha vacío/omitido → `error: 1` |
| El portal ya usa Cloudflare Turnstile | `api.js` de Turnstile se carga en la home; `captchaHelper.min.js` renderiza el widget |
| Contrato del modo nuevo | `getCaptchaHeaders()` → `X-Use-New-Captcha: "true"` literal; `getCaptchaToken()` → token en `verificacionCaptcha` |
| Fallback oficial | `error-callback`/`expired-callback` de Turnstile activan el captcha de imagen |
| Sitekey pública | `GET /rest/getTurnstileKeySite` → texto plano, sin autenticación |
| Tarjetas por sesión | `GET /rest/tarjetaInternal/listaTarjetas` devuelve monederos; solo `Principal (Dinero)` es dinero real |
| Auto-vinculación limitada | `POST /rest/tarjetaInternal/registrarTarjetaSimple` → `error: 2` (requiere titularidad/cuestionario) |
| Turnstile rechaza automatización | Playwright/Chrome recibe `errorCode 600010`; un WebView real sí resuelve |

Conclusión: la app oficial no usa un endpoint secreto — genera un token Turnstile
en un WebView. El login solo elimina captcha para tarjetas ya vinculadas a la
cuenta.

## Arquitectura implementada

### Tres caminos de consulta de saldo (en orden)

1. **Captcha manual** — el usuario lo tipea → path anónimo legacy directo.
2. **Cuenta vinculada** — sesión conectada + tarjeta en `listaTarjetas` → saldo
   del monedero `Principal (Dinero)` sin captcha ni request extra.
3. **Turnstile anónimo** — `WebViewTurnstileTokenProvider`: WebView offscreen
   que carga la home, inyecta `turnstile.render` con la sitekey y obtiene el
   token por un puente JS unidireccional; se envía con `X-Use-New-Captcha`.
4. **Fallback** — si el token falla (`error 1`), hay error de red o timeout:
   captcha de imagen + OCR on-device (ML Kit); si el OCR falla 3 veces, diálogo
   manual. Degrada, nunca rompe.

### Cuenta RedBus opcional

- Login en WebView endurecido: allowlist de main-frame al host exacto, TLS
  cancela, sin bridge JS ni inspección de DOM (ver `RedBusWebViewSecurity`).
- Cookies aisladas en `WebViewCookieJar` (solo HTTPS + host exacto, sin
  subdominios); `disconnect()` las borra preservando tarjetas e historial.
- Sync eficiente: `listaTarjetas` una vez por refresh masivo; upsert por número
  externo preservando alias, NFC UID, favorito e historial locales.
- La contraseña nunca toca la app (login 100% en la página oficial).

### Resolver Turnstile

- `TurnstileTokenProvider` (dominio) + `WebViewTurnstileTokenProvider` (Android).
- Sitekey validada con whitelist `^[0-9A-Za-z_-]{1,128}$` antes de inyectarla.
- `Mutex` serializa tokens (single-use); timeout 45s; WebView destruido en
  `finally`; `CancellationException` se relanza.
- Bridge `@JavascriptInterface` solo con `onToken`/`onError` — aceptable porque
  la página es pública y anónima; el WebView de login lo sigue prohibiendo
  (ADR 0001, addendum).
- El widget construye su propio repositorio sin provider — jamás crea WebView.

## CI/CD

- `.github/workflows/build-android.yml`: JDK 17, Gradle 8.11.1,
  `testDebugUnitTest` + reportes como artifact + `assembleDebug` + APK.
- Robolectric fijado a SDK 34 (`robolectric.properties`); manifest
  `EnableSafeBrowsing` en vez del setter removido del SDK 35.
- 171 tests verdes; fixes de CI: llave extra en test, validación RFC de nombres
  de cookie, `includeAndroidResources` para tests de manifest.

## Verificación

- [x] Suite completa verde en CI (runs 37393301109, 37413499959)
- [x] Consulta por Turnstile end-to-end en dispositivo real
- [x] Merge a `main` + release `v1.1.0` con APK publicado

## Pendientes

- Matriz de dispositivo ampliada (sesión expirada, NFC re-scan, accesibilidad).
- Revisión de términos/autorización de RedBus para distribución pública.
- Propuesta de colaboración formal enviada a SAETA (documento externo al repo).
- Posible mejora: mostrar WebView de Turnstile brevemente si el desafío exige
  interacción; hoy degrada a OCR tras el timeout.
- Build de release firmado con keystore propio para Play Store.
