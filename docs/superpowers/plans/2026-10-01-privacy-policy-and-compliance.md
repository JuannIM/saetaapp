# Privacy Policy & Compliance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Provide full legal and store compliance for SAETASaldoApp by establishing a clear, enforceable Privacy Policy document, a Google Play Data Safety specification, and an offline-accessible In-App Privacy & Security dialog.

**Architecture:** Create authoritative bilingual documentation (`PRIVACY_POLICY.md` and `DATA_SAFETY.md`), register a Privacy Policy domain model / provider, and integrate an accessible Material 3 Privacy dialog accessible from the main cards screen and settings. Ensure zero-telemetry and local-first architecture are explicitly proven and documented.

**Tech Stack:** Markdown, Kotlin, Jetpack Compose Material 3, Android Room, GitHub Pages / Raw URL.

**Spec:** `docs/superpowers/specs/2026-09-30-saeta-saldo-android-design.md`

## Global Constraints

- Android API level: minSdk 26 (Android 8.0), targetSdk 35 (Android 15).
- Zero telemetry: no analytics SDKs, no ad trackers, no third-party telemetry, no cloud relays.
- Local-first persistence: all card identifiers, aliases, and balance logs reside exclusively in the on-device Room SQLite database.
- Explicit permissions justification: `INTERNET` solely for querying RedBus API over HTTPS, `NFC` solely for reading Mifare transit tags on foreground.
- Argentine Law 25.326 (Protección de los Datos Personales) and Google Play Data Safety compliance.

## Review Focus

1. **Information Disclosure in Network Payloads:** Verify no device identifiers (IMEI, Android ID, Advertising ID, MAC) are appended to OkHttp requests.
2. **On-Device Data Deletion:** Verify deleting a card from the app cascades and purges all balance history entities completely from local storage.
3. **Offline In-App Access:** Verify the in-app privacy policy dialog renders without requiring an active internet connection.
4. **Third-Party Disclosures:** Clearly state the role of MiRedBus / SAETA as the independent target transit service and declare that SAETASaldoApp is an independent, non-official open-source utility.
5. **On-Device OCR Privacy:** Confirm that ML Kit text recognition executes entirely on-device and never uploads captcha bitmaps to cloud endpoints.

---

### Task 1: Comprehensive Privacy Policy & Data Safety Documents

**Files:**
- Create: `PRIVACY_POLICY.md`
- Create: `docs/compliance/DATA_SAFETY.md`

**Interfaces:**
- Produces: Public Markdown documentation compliant with Google Play Console Developer Policies and Argentine Law 25.326.

- [ ] **Step 1: Write PRIVACY_POLICY.md**
Write `PRIVACY_POLICY.md` in the repository root detailing:
- Data collected (card numbers, aliases, balance history).
- Where data is stored (100% on-device SQLite).
- Network transmissions (HTTPS directly to `salta.miredbus.com.ar`).
- Absence of trackers, telemetry, or user accounts.
- ML Kit on-device processing.
- User rights (deletion, modification, full local control).
- Legal disclaimers (independent open-source utility, not affiliated with SAETA S.A. or MiRedBus).

- [ ] **Step 2: Write docs/compliance/DATA_SAFETY.md**
Write `docs/compliance/DATA_SAFETY.md` mapping line-by-line to the Google Play Console Data Safety questionnaire:
- Data collected: None collected/shared off-device for tracking.
- Ephemeral transit query: Card number transmitted over encrypted HTTPS to provider, not linked to identity, not stored on external servers.
- Security practices: Data encrypted in transit (TLS 1.2+ HTTPS), user can delete all local data anytime.

- [ ] **Step 3: Commit documentation**

```bash
git add PRIVACY_POLICY.md docs/compliance/DATA_SAFETY.md
git commit -m "docs: add comprehensive Privacy Policy and Google Play Data Safety specification"
```

---

### Task 2: In-App Privacy Policy Content Provider & Unit Test

**Files:**
- Create: `android/app/src/main/java/com/saetasaldo/app/domain/model/PrivacyPolicyContent.kt`
- Create: `android/app/src/test/java/com/saetasaldo/app/domain/model/PrivacyPolicyContentTest.kt`

**Interfaces:**
- Produces: `PrivacyPolicyContent` providing structured offline privacy clauses, permission explanations, and transparency statements.

- [ ] **Step 1: Write failing unit test**

```kotlin
package com.saetasaldo.app.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacyPolicyContentTest {

    @Test
    fun `privacy policy contains all essential compliance sections`() {
        val sections = PrivacyPolicyContent.sections
        assertTrue(sections.isNotEmpty())
        
        val titles = sections.map { it.title }
        assertTrue(titles.any { it.contains("Almacenamiento Local", ignoreCase = true) })
        assertTrue(titles.any { it.contains("Permisos", ignoreCase = true) })
        assertTrue(titles.any { it.contains("RedBus", ignoreCase = true) })
        assertTrue(titles.any { it.contains("Privacidad", ignoreCase = true) || it.contains("Datos", ignoreCase = true) })
    }

    @Test
    fun `permission explanations cover NFC and INTERNET`() {
        val permissions = PrivacyPolicyContent.permissionsJustification
        assertTrue(permissions.containsKey("NFC"))
        assertTrue(permissions.containsKey("INTERNET"))
        assertFalse(permissions.getValue("NFC").isBlank())
        assertFalse(permissions.getValue("INTERNET").isBlank())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

- [ ] **Step 3: Implement PrivacyPolicyContent.kt**

```kotlin
package com.saetasaldo.app.domain.model

data class PolicySection(
    val title: String,
    val content: String
)

object PrivacyPolicyContent {
    val sections: List<PolicySection> = listOf(
        PolicySection(
            title = "1. Privacidad y Almacenamiento Local",
            content = "SAETA Saldo es una aplicación de código abierto diseñada bajo la filosofía 'Local-First'. Todas tus tarjetas, alias y registros de saldo se almacenan exclusivamente en la memoria local de tu dispositivo mediante una base de datos SQLite segura. No existen servidores intermedios, cuentas de usuario ni recolección de datos en la nube."
        ),
        PolicySection(
            title = "2. Consultas a MiRedBus",
            content = "Para consultar el saldo disponible, la aplicación se comunica directamente mediante conexión cifrada HTTPS con el portal oficial de MiRedBus Salta. Únicamente se envía el número de tarjeta y el código de seguridad (captcha) para obtener la respuesta. No se recopilan identificadores de dispositivo, ubicación ni datos personales."
        ),
        PolicySection(
            title = "3. Reconocimiento de Captcha en el Dispositivo",
            content = "La resolución automática de códigos captcha se procesa íntegramente de forma local mediante Google ML Kit Text Recognition en tu propio teléfono. Las imágenes nunca se suben ni se comparten con servicios externos."
        ),
        PolicySection(
            title = "4. Permisos Requeridos",
            content = "• NFC: Se utiliza únicamente para detectar y leer el identificador de tu tarjeta SAETA cuando la acercas al teléfono.\n• INTERNET: Se utiliza exclusivamente para consultar el saldo en el portal de RedBus.\n• ACCESS_NETWORK_STATE: Para verificar si el dispositivo cuenta con conexión antes de consultar."
        ),
        PolicySection(
            title = "5. Control y Eliminación de Datos",
            content = "Tú tienes el control total sobre tus datos. Puedes editar o eliminar cualquier tarjeta en cualquier momento; al eliminarla, se borra instantáneamente todo su historial asociado. Al desinstalar la app, todos los datos se destruyen permanentemente del dispositivo."
        ),
        PolicySection(
            title = "6. Descargo de Responsabilidad",
            content = "Esta aplicación es una herramienta independiente y comunitaria de código abierto. No está afiliada, respaldada ni asociada oficialmente con SAETA S.A., MiRedBus ni con el Gobierno de la Provincia de Salta."
        )
    )

    val permissionsJustification: Map<String, String> = mapOf(
        "NFC" to "Lectura del chip contactless de la tarjeta física SAETA para vincularla rápidamente.",
        "INTERNET" to "Comunicación cifrada HTTPS con el portal oficial para consultar el saldo.",
        "ACCESS_NETWORK_STATE" to "Detección del estado de conectividad para evitar errores de red innecesarios."
    )
}
```

- [ ] **Step 4: Run test to verify it passes**

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/com/saetasaldo/app/domain/model/PrivacyPolicyContent.kt android/app/src/test/java/com/saetasaldo/app/domain/model/PrivacyPolicyContentTest.kt
git commit -m "feat: implement in-app privacy policy content provider and tests"
```

---

### Task 3: In-App Privacy & Security Dialog in Jetpack Compose

**Files:**
- Create: `android/app/src/main/java/com/saetasaldo/app/ui/dialogs/PrivacyPolicyDialog.kt`
- Modify: `android/app/src/main/java/com/saetasaldo/app/ui/cards/CardsScreen.kt`

**Interfaces:**
- Consumes: `PrivacyPolicyContent`
- Produces: `PrivacyPolicyDialog` composable displayed via TopAppBar action icon or menu.

- [ ] **Step 1: Implement PrivacyPolicyDialog.kt**
Compose an `AlertDialog` with:
- Scrollable content displaying each `PolicySection`.
- A highlighted badge "100% Local • Sin Rastreo • Código Abierto".
- An "Entendido" dismiss button.
- Optional clickable link to GitHub repository.

- [ ] **Step 2: Wire PrivacyPolicyDialog into CardsScreen.kt**
Add an info / privacy icon button in `CardsTopAppBar` (e.g. `Icons.Default.Info` or `Icons.Default.Shield`) that toggles `showPrivacyDialog`.

- [ ] **Step 3: Verify compilation and tests**

- [ ] **Step 4: Commit**

```bash
git add android/app/src/main/java/com/saetasaldo/app/ui/dialogs/PrivacyPolicyDialog.kt android/app/src/main/java/com/saetasaldo/app/ui/cards/CardsScreen.kt
git commit -m "feat: add in-app privacy policy dialog and top bar action"
```
