# RedBus Optional Account Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an optional, secure RedBus account connection that uses the official web login and authenticated card list while preserving the anonymous captcha/OCR flow as fallback.

**Architecture:** The official login runs in a hardened `WebView` and shares only its RedBus cookies through `CookieManager` with a dedicated authenticated Retrofit client. Domain use cases prefer authenticated balances for linked cards, reuse a single local balance-update path, and fall back to the current anonymous repository for every unsupported or failed case.

**Tech Stack:** Kotlin 2.0.20, Jetpack Compose, Android WebView/CookieManager, Retrofit 2.11.0, OkHttp 4.12.0, Room 2.6.1, coroutines, JUnit 4, MockK, Robolectric.

**Spec:** `docs/superpowers/specs/2026-10-05-redbus-optional-account-design.md`

## Global Constraints

- Android API levels remain `minSdk = 26`, `targetSdk = 35`, and `compileSdk = 35`.
- Do not add a native username/password form, inspect the login DOM, or persist credentials.
- Do not use `addJavascriptInterface`, `evaluateJavascript`, WebMessage APIs, or a custom login request.
- Do not add third-party dependencies or a Room migration.
- Keep the anonymous captcha/OCR path operational and isolated from authenticated cookies.
- Treat only `Principal (Dinero)` as spendable money; never aggregate benefits or free-ticket wallets.
- Store session material only in app-private `CookieManager`; never Room, preferences, logs, fixtures, or source.
- Keep the widget on the anonymous path for this version.
- Never commit real names, email addresses, documents, card numbers, cookies, tokens, or complete production responses.
- Before any push, revoke the exposed GitHub token and use a separately provisioned least-privilege credential.

## Review Focus

1. **Cookie exfiltration:** `WebViewCookieJarTest` must prove that RedBus cookies are never returned for another scheme or host.
2. **False-positive login:** `RedBusAccountRepositoryTest` must prove that navigation alone cannot mark the account connected; only `usuarioLogeado.error == 0` can.
3. **Beneficiary wallets:** `RedBusCardListDtoTest` must prove that only `Principal (Dinero)` is mapped and that other balances are neither selected nor summed.
4. **Expired or broken authenticated flow:** `GetCardBalanceUseCaseTest` and `RefreshAllBalancesUseCaseTest` must prove fallback to the anonymous path for error `99`, unlinked cards, network failure, and schema drift.
5. **Local data integrity:** `CardRepositoryTest` must prove authenticated updates preserve alias, NFC UID, favorite status, and avoid duplicate history entries.

## Execution Prerequisites

- Preserve the current dirty worktree. The already-approved card-type, README, and captcha cleanup must remain a separate commit from this feature.
- Create `feature/redbus-account-session` only after reviewing that complete baseline diff.
- Use a device/account controlled by the repository owner for the contract fixture gate.
- Run Android commands from `android/` with JDK 17, Android SDK 35, and Gradle 8.11.1. The current machine lacks that toolchain, so GitHub Actions is the authoritative fallback.
- Do not push or open a pull request until the human has reviewed the complete diff.

---

### Task 0: Verify The Authenticated Contract And Create Sanitized Fixtures

**Files:**
- Create: `android/app/src/test/resources/redbus/session-connected.json`
- Create: `android/app/src/test/resources/redbus/session-disconnected.json`
- Create: `android/app/src/test/resources/redbus/cards-success.json`
- Create: `android/app/src/test/resources/redbus/cards-session-expired.json`
- Modify: `docs/superpowers/specs/2026-10-05-redbus-optional-account-design.md`

**Interfaces:**
- Produces: sanitized, stable fixtures that define the DTO contract used by Task 1.
- Produces: a confirmed HTTP method for `/rest/loginInternal/logOut`, or an explicit decision to perform local-only disconnect.

- [ ] **Step 1: Capture responses through a user-authorized session**

Use browser DevTools on the official RedBus site. Record only the response shapes for:

```text
GET /rest/loginInternal/usuarioLogeado
GET /rest/tarjetaInternal/listaTarjetas
<confirmed method> /rest/loginInternal/logOut
```

Do not export request headers or cookies.

- [ ] **Step 2: Sanitize before writing fixtures**

Replace every real identifier with deterministic values such as:

```text
Usuario: usuario@example.invalid
Tarjeta externa: 12345678
Tarjeta interna: 90000001
Nombre: Persona Ejemplo
Documento: 00000000
```

Preserve JSON types, nesting, nullable fields, field names, wallet descriptions, and numeric/string formatting.

- [ ] **Step 3: Review fixtures for secrets and personal data**

Run:

```bash
git diff -- android/app/src/test/resources/redbus
git grep -nE '(JSESSIONID|SERVER_USED|cf_clearance|ghp_|github_pat_)' -- android/app/src/test/resources/redbus
```

Expected: the first command shows only anonymized data; the second returns no matches.

- [ ] **Step 4: Update the spec with observed facts**

Record the confirmed response nesting, logout method, and any deviation from the assumptions section. Do not generalize from field names that were absent in the captured response.

- [ ] **Step 5: Human contract checkpoint**

The repository owner confirms that fixtures contain no real personal or session data before implementation continues.

- [ ] **Step 6: Commit**

```bash
git add android/app/src/test/resources/redbus docs/superpowers/specs/2026-10-05-redbus-optional-account-design.md
git commit -m "test: document sanitized RedBus account contracts"
```

---

### Task 1: Define Account Domain Models And Parse The RedBus Contract

**Files:**
- Create: `android/app/src/main/java/com/saetasaldo/app/domain/model/RedBusAccountCard.kt`
- Create: `android/app/src/main/java/com/saetasaldo/app/domain/model/RedBusSessionState.kt`
- Create: `android/app/src/main/java/com/saetasaldo/app/domain/repository/RedBusAccountRepository.kt`
- Create: `android/app/src/main/java/com/saetasaldo/app/data/remote/dto/RedBusAccountDtos.kt`
- Create: `android/app/src/test/java/com/saetasaldo/app/data/remote/dto/RedBusAccountDtosTest.kt`

**Interfaces:**
- Produces: `RedBusAccountCard(cardNumber, balance, cardType, cardState, suggestedName)`.
- Produces: `RedBusSessionState.Unknown`, `.Checking`, `.Disconnected`, and `.Connected`.
- Produces: `RedBusAccountRepository.sessionState`, `checkSession()`, `getLinkedCards()`, and `disconnect()`.
- Produces: `RedBusCardListDto.toDomainCards(): List<RedBusAccountCard>`.

- [x] **Step 1: Write fixture-backed failing DTO tests**

Add tests with these exact behaviors:

```kotlin
@Test fun `connected fixture recognizes error zero`()
@Test fun `disconnected fixture recognizes error one`()
@Test fun `card fixture maps external number state type and description`()
@Test fun `card fixture maps only Principal Dinero balance`()
@Test fun `card without Principal Dinero is omitted`()
@Test fun `malformed card entries are omitted without rejecting valid siblings`()
@Test fun `session expired fixture recognizes error ninety nine`()
```

Assert the sanitized card maps to number `12345678`, its exact fixture amount,
the expected `CardType`, and no benefit-wallet amount.

- [ ] **Step 2: Run the focused test and verify failure**

Run:

```bash
gradle testDebugUnitTest --tests "*RedBusAccountDtosTest" --stacktrace
```

Expected: FAIL because the account DTOs and mapper do not exist.

- [x] **Step 3: Add the domain contracts**

Implement exactly:

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
```

- [x] **Step 4: Implement DTOs from the approved fixtures**

Model only fields needed to identify `error`, the external card number,
description, type, state, and wallets. Gson must ignore all account PII and
unused fields. Implement:

```kotlin
fun RedBusCardListDto.toDomainCards(): List<RedBusAccountCard>
```

Normalize whitespace/case only for matching `Principal (Dinero)`. Reuse
`SaldoResponseDto.parseAmount`; reject blank card numbers and non-finite
amounts. Preserve absent card type and state as `null`; do not invent
`ACTIVA` or a beneficiary category.

- [ ] **Step 5: Run focused tests**

Run:

```bash
gradle testDebugUnitTest --tests "*RedBusAccountDtosTest" --stacktrace
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add android/app/src/main/java/com/saetasaldo/app/domain/model/RedBusAccountCard.kt android/app/src/main/java/com/saetasaldo/app/domain/model/RedBusSessionState.kt android/app/src/main/java/com/saetasaldo/app/domain/repository/RedBusAccountRepository.kt android/app/src/main/java/com/saetasaldo/app/data/remote/dto/RedBusAccountDtos.kt android/app/src/test/java/com/saetasaldo/app/data/remote/dto/RedBusAccountDtosTest.kt
git commit -m "feat: define RedBus account response contracts"
```

---

### Task 2: Bridge WebView Cookies Into An Isolated Authenticated Client

**Files:**
- Create: `android/app/src/main/java/com/saetasaldo/app/data/remote/cookie/WebCookieStore.kt`
- Create: `android/app/src/main/java/com/saetasaldo/app/data/remote/cookie/WebViewCookieJar.kt`
- Create: `android/app/src/main/java/com/saetasaldo/app/data/remote/api/RedBusAccountApiService.kt`
- Create: `android/app/src/main/java/com/saetasaldo/app/data/remote/RedBusAccountNetworkClient.kt`
- Create: `android/app/src/test/java/com/saetasaldo/app/data/remote/cookie/WebViewCookieJarTest.kt`

**Interfaces:**
- Produces: `WebCookieStore` as a testable wrapper over `CookieManager`.
- Produces: `WebViewCookieJar(allowedHost, store): CookieJar` plus
  `suspend fun clear()`.
- Produces: `RedBusAccountApiService` for session, linked-card, and confirmed
  logout requests.
- Produces: `RedBusAccountNetworkClient.create(cookieJar)`.

- [x] **Step 1: Write failing cookie-boundary tests**

Add tests:

```kotlin
@Test fun `returns RedBus cookies to the exact https host`()
@Test fun `does not return cookies to a different host`()
@Test fun `does not return cookies over http`()
@Test fun `preserves cookie values containing equals signs`()
@Test fun `writes response cookies back to the web store`()
@Test fun `ignores response cookies from a different host`()
@Test fun `clear removes the web session`()
```

Use an in-memory fake `WebCookieStore`; assertions must never print cookie
values.

- [ ] **Step 2: Run the focused test and verify failure**

Run:

```bash
gradle testDebugUnitTest --tests "*WebViewCookieJarTest" --stacktrace
```

Expected: FAIL because the adapter does not exist.

- [x] **Step 3: Implement the cookie boundary**

Define:

```kotlin
interface WebCookieStore {
    fun getCookieHeader(url: String): String?
    fun setCookie(url: String, setCookieHeader: String)
    fun flush()
    suspend fun clear()
}
```

`AndroidWebCookieStore` wraps `CookieManager`. `WebViewCookieJar` must reject
non-HTTPS and non-`salta.miredbus.com.ar` requests before reading the store.
Parse request-cookie pairs with a split limit of two so values containing `=`
remain intact.

- [x] **Step 4: Add the account API and client**

Define Retrofit methods from Task 0:

```kotlin
suspend fun getLoggedUser(): Response<RedBusSessionDto>
suspend fun getLinkedCards(): Response<RedBusCardListDto>
```

Add `logout()` only with the HTTP method confirmed in Task 0. The authenticated
OkHttp client uses `WebViewCookieJar`, the existing browser-like User-Agent,
15-second timeouts, and no logging interceptor.

- [ ] **Step 5: Run cookie and existing session-cookie tests**

Run:

```bash
gradle testDebugUnitTest --tests "*WebViewCookieJarTest" --tests "*SessionCookieJarTest" --stacktrace
```

Expected: PASS, proving the new adapter did not alter the anonymous jar.

- [ ] **Step 6: Commit**

```bash
git add android/app/src/main/java/com/saetasaldo/app/data/remote/cookie/WebCookieStore.kt android/app/src/main/java/com/saetasaldo/app/data/remote/cookie/WebViewCookieJar.kt android/app/src/main/java/com/saetasaldo/app/data/remote/api/RedBusAccountApiService.kt android/app/src/main/java/com/saetasaldo/app/data/remote/RedBusAccountNetworkClient.kt android/app/src/test/java/com/saetasaldo/app/data/remote/cookie/WebViewCookieJarTest.kt
git commit -m "feat: isolate authenticated RedBus cookies"
```

---

### Task 3: Implement The Account Session State Machine

**Files:**
- Create: `android/app/src/main/java/com/saetasaldo/app/data/repository/RedBusAccountRepositoryImpl.kt`
- Create: `android/app/src/main/java/com/saetasaldo/app/data/remote/RedBusAccountErrors.kt`
- Create: `android/app/src/test/java/com/saetasaldo/app/data/repository/RedBusAccountRepositoryTest.kt`

**Interfaces:**
- Consumes: `RedBusAccountApiService`, `WebViewCookieJar`, DTO mappers.
- Produces: the `RedBusAccountRepository` contract from Task 1.
- Produces: typed internal failures for expired sessions, HTTP/network errors,
  and incompatible response contracts.

- [x] **Step 1: Write failing repository tests**

Add tests:

```kotlin
@Test fun `only error zero marks session connected`()
@Test fun `error one marks session disconnected`()
@Test fun `http failure does not manufacture a connected session`()
@Test fun `cards error ninety nine marks session disconnected`()
@Test fun `cards success returns mapped cards`()
@Test fun `cards network failure preserves connected state for retry`()
@Test fun `disconnect clears cookies even when remote logout fails`()
```

- [ ] **Step 2: Run and verify failure**

Run:

```bash
gradle testDebugUnitTest --tests "*RedBusAccountRepositoryTest" --stacktrace
```

Expected: FAIL because the implementation is absent.

- [x] **Step 3: Implement session semantics**

`checkSession()` sets `Checking` only while the request is active. A recognized
`error == 0` becomes `Connected`; a recognized unauthenticated response becomes
`Disconnected`. HTTP, parse, and unknown-contract failures return
`Result.failure` and restore the prior stable state.

`getLinkedCards()` may call the API only while `sessionState.value` is
`Connected`. Error `99` changes it to `Disconnected`; transient failures do
not.

`disconnect()` attempts the confirmed remote logout when available and always
clears `WebViewCookieJar` in `finally`, then sets `Disconnected`.

- [ ] **Step 4: Run focused tests**

Run:

```bash
gradle testDebugUnitTest --tests "*RedBusAccountRepositoryTest" --stacktrace
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/com/saetasaldo/app/data/repository/RedBusAccountRepositoryImpl.kt android/app/src/main/java/com/saetasaldo/app/data/remote/RedBusAccountErrors.kt android/app/src/test/java/com/saetasaldo/app/data/repository/RedBusAccountRepositoryTest.kt
git commit -m "feat: manage optional RedBus account sessions"
```

---

### Task 4: Unify Local Balance Persistence

**Files:**
- Create: `android/app/src/main/java/com/saetasaldo/app/domain/model/CardBalanceUpdate.kt`
- Modify: `android/app/src/main/java/com/saetasaldo/app/domain/repository/CardRepository.kt`
- Modify: `android/app/src/main/java/com/saetasaldo/app/data/repository/CardRepositoryImpl.kt`
- Modify: `android/app/src/test/java/com/saetasaldo/app/data/repository/CardRepositoryTest.kt`

**Interfaces:**
- Produces: `CardRepository.applyBalanceUpdate(update): SaetaCard`.
- Preserves: `refreshCardBalance(cardNumber, manualCaptcha)` as the anonymous
  captcha/OCR operation.

- [x] **Step 1: Add failing persistence tests**

Add tests:

```kotlin
@Test fun `account update preserves local alias nfc and favorite`()
@Test fun `account update preserves existing type and state when remote values are absent`()
@Test fun `account update creates missing card with suggested name`()
@Test fun `account update uses default name when suggestion is blank`()
@Test fun `account update records history only when balance changes`()
@Test fun `account update rejects blank card number and non finite balance`()
```

- [ ] **Step 2: Run and verify failure**

Run:

```bash
gradle testDebugUnitTest --tests "*CardRepositoryTest" --stacktrace
```

Expected: FAIL because `applyBalanceUpdate` is absent.

- [x] **Step 3: Add the update contract**

Define:

```kotlin
data class CardBalanceUpdate(
    val cardNumber: String,
    val balance: Double,
    val cardType: CardType?,
    val cardState: String?,
    val suggestedName: String?
)
```

Add:

```kotlin
suspend fun applyBalanceUpdate(update: CardBalanceUpdate): SaetaCard
```

- [x] **Step 4: Refactor the existing anonymous success path**

Move the existing upsert/history behavior into `applyBalanceUpdate`. Make
`refreshCardBalance` construct an update from `SaldoResponseDto` and delegate
to it. A null remote type/state retains the existing value; a new card uses
the model's default type and nullable state. Preserve existing error messages
and cancellation propagation.

- [ ] **Step 5: Run repository regression tests**

Run:

```bash
gradle testDebugUnitTest --tests "*CardRepositoryTest" --stacktrace
```

Expected: PASS for existing anonymous behavior and new authenticated updates.

- [ ] **Step 6: Commit**

```bash
git add android/app/src/main/java/com/saetasaldo/app/domain/model/CardBalanceUpdate.kt android/app/src/main/java/com/saetasaldo/app/domain/repository/CardRepository.kt android/app/src/main/java/com/saetasaldo/app/data/repository/CardRepositoryImpl.kt android/app/src/test/java/com/saetasaldo/app/data/repository/CardRepositoryTest.kt
git commit -m "refactor: centralize card balance persistence"
```

---

### Task 5: Prefer Account Balances For Individual Refreshes

**Files:**
- Modify: `android/app/src/main/java/com/saetasaldo/app/domain/usecase/GetCardBalanceUseCase.kt`
- Modify: `android/app/src/test/java/com/saetasaldo/app/domain/usecase/GetCardBalanceUseCaseTest.kt`

**Interfaces:**
- Consumes: `CardRepository` and `RedBusAccountRepository`.
- Preserves: `invoke(cardNumber, manualCaptcha): Result<SaetaCard>`.
- Produces: authenticated-first lookup with anonymous fallback.

- [x] **Step 1: Replace delegation-only tests with decision tests**

Add tests:

```kotlin
@Test fun `connected linked card uses account balance without captcha query`()
@Test fun `manual captcha always bypasses account lookup`()
@Test fun `disconnected state uses anonymous query`()
@Test fun `unlinked card falls back to anonymous query`()
@Test fun `expired session falls back to anonymous query`()
@Test fun `account network failure falls back to anonymous query`()
@Test fun `account contract failure falls back to anonymous query`()
@Test fun `cancellation is rethrown instead of falling back`()
```

- [ ] **Step 2: Run and verify failure**

Run:

```bash
gradle testDebugUnitTest --tests "*GetCardBalanceUseCaseTest" --stacktrace
```

Expected: FAIL because the use case has no account repository.

- [x] **Step 3: Implement the fixed decision order**

Keep the public operator signature unchanged. Inject
`RedBusAccountRepository`. Match normalized external card numbers exactly.
Apply a matched account card through `CardRepository.applyBalanceUpdate`.
Fallback through `CardRepository.refreshCardBalance` for every non-cancellation
failure, unlinked card, or non-connected state.

- [ ] **Step 4: Run focused tests**

Run:

```bash
gradle testDebugUnitTest --tests "*GetCardBalanceUseCaseTest" --stacktrace
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/com/saetasaldo/app/domain/usecase/GetCardBalanceUseCase.kt android/app/src/test/java/com/saetasaldo/app/domain/usecase/GetCardBalanceUseCaseTest.kt
git commit -m "feat: prefer authenticated card balances"
```

---

### Task 6: Synchronize Linked Cards And Refresh All Efficiently

**Files:**
- Create: `android/app/src/main/java/com/saetasaldo/app/domain/usecase/SyncRedBusCardsUseCase.kt`
- Create: `android/app/src/main/java/com/saetasaldo/app/domain/usecase/RefreshAllBalancesUseCase.kt`
- Create: `android/app/src/test/java/com/saetasaldo/app/domain/usecase/SyncRedBusCardsUseCaseTest.kt`
- Create: `android/app/src/test/java/com/saetasaldo/app/domain/usecase/RefreshAllBalancesUseCaseTest.kt`

**Interfaces:**
- Produces: `RedBusSyncResult(syncedCardNumbers, importedCount)`.
- Produces: `RefreshAllBalancesResult(updatedCount, failures)`.
- Produces: one authenticated list request per bulk refresh, followed only by
  anonymous requests for uncovered local cards.

- [x] **Step 1: Write failing synchronization tests**

Add tests:

```kotlin
@Test fun `sync applies each unique account card once`()
@Test fun `sync reports imported count from cards absent locally`()
@Test fun `bulk refresh fetches account list exactly once`()
@Test fun `bulk refresh skips anonymous query for synced cards`()
@Test fun `bulk refresh anonymously updates unlinked local cards`()
@Test fun `bulk refresh falls back for all cards when account fetch fails`()
@Test fun `bulk refresh reports partial failures without discarding successes`()
@Test fun `bulk refresh spaces only anonymous requests by eight hundred milliseconds`()
```

- [ ] **Step 2: Run and verify failure**

Run:

```bash
gradle testDebugUnitTest --tests "*SyncRedBusCardsUseCaseTest" --tests "*RefreshAllBalancesUseCaseTest" --stacktrace
```

Expected: FAIL because both use cases are absent.

- [x] **Step 3: Implement linked-card synchronization**

Define:

```kotlin
data class RedBusSyncResult(
    val syncedCardNumbers: Set<String>,
    val importedCount: Int
)

class SyncRedBusCardsUseCase(private val cardRepository: CardRepository) {
    suspend operator fun invoke(cards: List<RedBusAccountCard>): RedBusSyncResult
}
```

Deduplicate by normalized card number and call `applyBalanceUpdate` once per
valid card. Compute `importedCount` from a pre-update
`cardRepository.getAllCards().first()` snapshot; do not add another repository
lookup method only for this count.

- [x] **Step 4: Implement bulk orchestration**

Define:

```kotlin
data class RefreshAllBalancesResult(
    val updatedCount: Int,
    val failures: Map<String, Throwable>
)

class RefreshAllBalancesUseCase(
    private val cardRepository: CardRepository,
    private val redBusAccountRepository: RedBusAccountRepository,
    private val syncRedBusCardsUseCase: SyncRedBusCardsUseCase,
    private val delayBetweenAnonymousRequestsMillis: Long = 800L,
    private val delay: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) }
) {
    suspend operator fun invoke(localCards: List<SaetaCard>): RefreshAllBalancesResult
}
```

Do not delay authenticated updates. Rethrow `CancellationException`.

- [ ] **Step 5: Run focused tests**

Run:

```bash
gradle testDebugUnitTest --tests "*SyncRedBusCardsUseCaseTest" --tests "*RefreshAllBalancesUseCaseTest" --stacktrace
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add android/app/src/main/java/com/saetasaldo/app/domain/usecase/SyncRedBusCardsUseCase.kt android/app/src/main/java/com/saetasaldo/app/domain/usecase/RefreshAllBalancesUseCase.kt android/app/src/test/java/com/saetasaldo/app/domain/usecase/SyncRedBusCardsUseCaseTest.kt android/app/src/test/java/com/saetasaldo/app/domain/usecase/RefreshAllBalancesUseCaseTest.kt
git commit -m "feat: sync linked RedBus cards efficiently"
```

---

### Task 7: Model Account UI State

**Files:**
- Create: `android/app/src/main/java/com/saetasaldo/app/ui/account/RedBusAccountViewModel.kt`
- Create: `android/app/src/test/java/com/saetasaldo/app/ui/account/RedBusAccountViewModelTest.kt`

**Interfaces:**
- Consumes: `RedBusAccountRepository` and `SyncRedBusCardsUseCase`.
- Produces: `RedBusAccountUiState(sessionState, isSyncing, message)`.
- Produces: `checkExistingSession()`, `verifyLoginAndSync()`, `sync()`,
  `disconnect()`, and `consumeMessage()`.

- [x] **Step 1: Write failing state-machine tests**

Add tests:

```kotlin
@Test fun `existing valid session becomes connected without syncing automatically`()
@Test fun `successful login verification connects and performs first sync`()
@Test fun `failed login verification remains disconnected and does not sync`()
@Test fun `manual sync reports imported card count`()
@Test fun `expired session displays reconnect message`()
@Test fun `disconnect clears state and keeps local cards untouched`()
@Test fun `concurrent verification requests are coalesced`()
```

- [ ] **Step 2: Run and verify failure**

Run:

```bash
gradle testDebugUnitTest --tests "*RedBusAccountViewModelTest" --stacktrace
```

Expected: FAIL because the ViewModel is absent.

- [x] **Step 3: Implement the UI state**

Define:

```kotlin
data class RedBusAccountUiState(
    val sessionState: RedBusSessionState = RedBusSessionState.Unknown,
    val isSyncing: Boolean = false,
    val message: String? = null
)
```

The ViewModel must not retain a username, email, document, cookie, or raw
response. `verifyLoginAndSync()` synchronizes only after a confirmed
`Connected` result. Guard verification with a single in-flight job so
`onPageFinished` cannot issue overlapping calls.

- [ ] **Step 4: Run focused tests**

Run:

```bash
gradle testDebugUnitTest --tests "*RedBusAccountViewModelTest" --stacktrace
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/com/saetasaldo/app/ui/account/RedBusAccountViewModel.kt android/app/src/test/java/com/saetasaldo/app/ui/account/RedBusAccountViewModelTest.kt
git commit -m "feat: model RedBus account connection state"
```

---

### Task 8: Build A Hardened Official Login WebView

**Files:**
- Create: `android/app/src/main/java/com/saetasaldo/app/ui/account/RedBusWebViewSecurity.kt`
- Create: `android/app/src/main/java/com/saetasaldo/app/ui/account/RedBusLoginScreen.kt`
- Create: `android/app/src/test/java/com/saetasaldo/app/ui/account/RedBusWebViewSecurityTest.kt`

**Interfaces:**
- Produces: `configureRedBusLoginWebView(webView)`.
- Produces: `isAllowedRedBusMainFrame(uri): Boolean`.
- Produces: `RedBusLoginScreen(isVerifying, onVerifySession, onBack)`.

- [x] **Step 1: Write failing security-policy tests**

Add tests:

```kotlin
@Test fun `allows exact RedBus https origin`()
@Test fun `rejects http RedBus URL`()
@Test fun `rejects deceptive RedBus suffix and prefix hosts`()
@Test fun `rejects file content javascript and intent schemes`()
@Test fun `configuration enables javascript and dom storage for Turnstile`()
@Test fun `configuration disables file content and mixed content access`()
@Test fun `configuration keeps safe browsing enabled`()
```

The deceptive-host cases include
`salta.miredbus.com.ar.attacker.invalid` and
`attacker-salta.miredbus.com.ar`.

- [ ] **Step 2: Run and verify failure**

Run:

```bash
gradle testDebugUnitTest --tests "*RedBusWebViewSecurityTest" --stacktrace
```

Expected: FAIL because the security helper is absent.

- [x] **Step 3: Implement the WebView security policy**

`configureRedBusLoginWebView` must:

```text
Enable: JavaScript, DOM storage, first-party cookies, third-party cookies.
Disable: file access, content access, file-URL cross-origin access, mixed content.
Keep: Safe Browsing enabled.
Never add: JavaScript interfaces or WebMessage bridges.
```

`isAllowedRedBusMainFrame` accepts only scheme `https` and exact host
`salta.miredbus.com.ar`, case-insensitively after URI parsing.

- [x] **Step 4: Implement the Compose login screen**

Load:

```text
https://salta.miredbus.com.ar/login
```

Use a `WebViewClient` that:

- blocks disallowed main-frame navigations;
- leaves HTTPS subframes available for Turnstile;
- calls `handler.cancel()` on every SSL error;
- invokes `onVerifySession` after trusted main-frame page completion;
- never examines form fields or DOM content.

Show a loading/verifying state, support in-WebView Back navigation, and call
`stopLoading()`, `clearHistory()`, `removeAllViews()`, and `destroy()` from
`DisposableEffect.onDispose`.

- [ ] **Step 5: Run focused tests and assemble**

Run:

```bash
gradle testDebugUnitTest --tests "*RedBusWebViewSecurityTest" --stacktrace
gradle assembleDebug --stacktrace
```

Expected: PASS and `app-debug.apk` produced.

- [ ] **Step 6: Commit**

```bash
git add android/app/src/main/java/com/saetasaldo/app/ui/account/RedBusWebViewSecurity.kt android/app/src/main/java/com/saetasaldo/app/ui/account/RedBusLoginScreen.kt android/app/src/test/java/com/saetasaldo/app/ui/account/RedBusWebViewSecurityTest.kt
git commit -m "feat: add secure official RedBus login screen"
```

---

### Task 9: Route Bulk Refresh Through The Coordinator

**Files:**
- Modify: `android/app/src/main/java/com/saetasaldo/app/ui/cards/CardsViewModel.kt`
- Modify: `android/app/src/test/java/com/saetasaldo/app/ui/cards/CardsViewModelTest.kt`

**Interfaces:**
- Consumes: `RefreshAllBalancesUseCase`.
- Preserves: `refreshCard`, `addNewCard`, deletion, favorites, and existing
  `StateFlow` properties.
- Changes: `refreshAllBalances()` delegates one operation instead of looping
  and delaying in the ViewModel.

- [x] **Step 1: Write the failing delegation tests**

Replace the loop-specific test with:

```kotlin
@Test fun `refresh all delegates the current card snapshot once`()
@Test fun `refresh all displays the first partial failure`()
@Test fun `refresh all with no failures clears stale errors`()
@Test fun `refresh all resets loading after cancellation or failure`()
```

- [ ] **Step 2: Run and verify failure**

Run:

```bash
gradle testDebugUnitTest --tests "*CardsViewModelTest" --stacktrace
```

Expected: FAIL because the ViewModel does not receive the coordinator.

- [x] **Step 3: Delegate the bulk operation**

Inject:

```kotlin
private val refreshAllBalancesUseCase: RefreshAllBalancesUseCase
```

Call it once with `cards.value`, remove the ViewModel-owned 800 ms loop, and
map the first failure to the existing snackbar message. Preserve
`CancellationException` semantics while resetting `_isRefreshing` in
`finally`.

- [ ] **Step 4: Run ViewModel tests**

Run:

```bash
gradle testDebugUnitTest --tests "*CardsViewModelTest" --stacktrace
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/com/saetasaldo/app/ui/cards/CardsViewModel.kt android/app/src/test/java/com/saetasaldo/app/ui/cards/CardsViewModelTest.kt
git commit -m "refactor: coordinate bulk balance refreshes"
```

---

### Task 10: Add The Optional Account Controls

**Files:**
- Create: `android/app/src/main/java/com/saetasaldo/app/ui/account/RedBusAccountDialog.kt`
- Modify: `android/app/src/main/java/com/saetasaldo/app/ui/cards/CardsScreen.kt`

**Interfaces:**
- Consumes: `RedBusAccountUiState`.
- Produces: account action in the cards top bar.
- Produces: accessible connect, sync, and disconnect controls.

- [ ] **Step 1: Implement the account dialog**

Define:

```kotlin
@Composable
fun RedBusAccountDialog(
    state: RedBusAccountUiState,
    onConnect: () -> Unit,
    onSync: () -> Unit,
    onDisconnect: () -> Unit,
    onDismiss: () -> Unit
)
```

Disconnected copy must state that login opens the official RedBus site and
that SAETA Saldo does not read or save the password. Connected copy must state
that only linked cards and their principal monetary balance are synchronized.
Do not claim endorsement or official affiliation.

- [ ] **Step 2: Expose the account action from `CardsScreen`**

Add parameters:

```kotlin
redBusAccountState: RedBusAccountUiState
onConnectRedBus: () -> Unit
onSyncRedBus: () -> Unit
onDisconnectRedBus: () -> Unit
```

Add an account icon with content description:

```text
Cuenta RedBus conectada
```

or:

```text
Conectar cuenta RedBus
```

depending on state. Keep the existing privacy, add-card, and refresh actions.

- [ ] **Step 3: Preview/manual accessibility check**

Verify disconnected, checking, connected, syncing, and error states at font
scale 1.0 and 1.5. Every icon-only action must have a unique Spanish content
description, and dialog actions must remain visible without horizontal
scrolling.

- [ ] **Step 4: Assemble**

Run:

```bash
gradle assembleDebug --stacktrace
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/com/saetasaldo/app/ui/account/RedBusAccountDialog.kt android/app/src/main/java/com/saetasaldo/app/ui/cards/CardsScreen.kt
git commit -m "feat: expose optional RedBus account controls"
```

---

### Task 11: Wire The Account Flow Into The Application

**Files:**
- Modify: `android/app/src/main/java/com/saetasaldo/app/MainActivity.kt`
- Modify: `android/app/src/main/java/com/saetasaldo/app/widget/RefreshBalanceAction.kt`

**Interfaces:**
- Consumes: every contract from Tasks 1-10.
- Produces: `Screen.RedBusLogin` in the existing manual navigation model.
- Preserves: anonymous widget refresh.

- [ ] **Step 1: Wire dependencies once in `MainActivity`**

Create one instance each of:

```text
AndroidWebCookieStore
WebViewCookieJar
RedBusAccountApiService
RedBusAccountRepository
SyncRedBusCardsUseCase
RefreshAllBalancesUseCase
RedBusAccountViewModel
```

Construct `GetCardBalanceUseCase` with both repositories and construct
`CardsViewModel` with the bulk coordinator. Do not introduce a DI framework.

- [ ] **Step 2: Add the login screen state**

Extend the existing sealed `Screen` with `RedBusLogin`. From the cards screen:

- Connect opens `RedBusLogin`.
- Trusted page completion calls `verifyLoginAndSync()`.
- Confirmed connection returns to `CardsList`.
- Back cancels login without deleting an already-valid session.
- App startup calls `checkExistingSession()` once.

- [ ] **Step 3: Keep the widget anonymous**

Change `RefreshBalanceAction` to call the existing
`CardRepository.refreshCardBalance` directly rather than constructing the now
account-aware `GetCardBalanceUseCase`. This preserves current widget behavior
and avoids initializing a WebView session in a cold widget process.

- [ ] **Step 4: Run the complete unit suite and assemble**

Run:

```bash
gradle test --stacktrace
gradle assembleDebug --stacktrace
```

Expected: every test passes and the debug APK is produced.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/com/saetasaldo/app/MainActivity.kt android/app/src/main/java/com/saetasaldo/app/widget/RefreshBalanceAction.kt
git commit -m "feat: wire optional RedBus sessions into Android"
```

---

### Task 12: Update Privacy And Data Safety Disclosures

**Files:**
- Modify: `PRIVACY_POLICY.md`
- Modify: `docs/compliance/DATA_SAFETY.md`
- Modify: `android/app/src/main/java/com/saetasaldo/app/domain/model/PrivacyPolicyContent.kt`
- Modify: `android/app/src/test/java/com/saetasaldo/app/domain/model/PrivacyPolicyContentTest.kt`

**Interfaces:**
- Produces: accurate public disclosure of the optional external account flow.

- [ ] **Step 1: Write failing privacy-content assertions**

Add assertions that the in-app policy:

```text
Explains optional RedBus login.
States that credentials are entered only on the official site.
States that session cookies are used and removable.
States that principal balances and card numbers are synchronized.
Does not claim that no accounts exist.
Does not claim that all processing is 100% local.
```

- [ ] **Step 2: Run and verify failure**

Run:

```bash
gradle testDebugUnitTest --tests "*PrivacyPolicyContentTest" --stacktrace
```

Expected: FAIL against the current policy text.

- [ ] **Step 3: Update user-facing and store privacy documentation**

Replace the badge with:

```text
Sin servidores propios - Sin rastreo - Codigo abierto
```

Document:

- anonymous and optional account modes;
- credentials processed by the official RedBus page, not SAETA Saldo;
- session cookies and explicit disconnect;
- card number and principal balance retrieval;
- no analytics, ads, custom backend, or password storage;
- independent/non-official status;
- the provider authorization/terms caveat for public distribution.

- [ ] **Step 4: Run privacy tests**

Run:

```bash
gradle testDebugUnitTest --tests "*PrivacyPolicyContentTest" --stacktrace
```

Expected: PASS.

- [ ] **Step 5: Commit privacy documentation**

```bash
git add PRIVACY_POLICY.md docs/compliance/DATA_SAFETY.md android/app/src/main/java/com/saetasaldo/app/domain/model/PrivacyPolicyContent.kt android/app/src/test/java/com/saetasaldo/app/domain/model/PrivacyPolicyContentTest.kt
git commit -m "docs: disclose optional RedBus account privacy"
```

---

### Task 13: Complete Documentation And Release Gates

**Files:**
- Modify: `README.md`
- Modify: `docs/decisions/0001-redbus-webview-session.md`

**Interfaces:**
- Produces: final user/developer documentation and recorded release evidence.
- Changes ADR status from `Propuesto` to `Aceptado` only after implementation
  and review.

- [ ] **Step 1: Document both balance modes**

Update the README with the optional account workflow, anonymous fallback,
principal-wallet limitation, and the independent/non-official disclaimer.

- [ ] **Step 2: Mark the ADR accepted**

Change the ADR only after the implementation and tests match the decision.

- [ ] **Step 3: Run final automated gates**

Run:

```bash
gradle test --stacktrace
gradle assembleDebug --stacktrace
git diff --check
git grep -nE '(ghp_|github_pat_|JSESSIONID=|SERVER_USED=|cf_clearance=)' -- . ':!docs/superpowers/plans/2026-10-05-redbus-optional-account.md'
```

Expected: tests/build pass, diff check is clean, and secret scan has no
matches. The plan is excluded because it names cookie keys as documentation,
not values.

- [ ] **Step 4: Complete the real-device matrix**

Record pass/fail for:

```text
Anonymous add and refresh.
Login with Turnstile.
Login image-captcha fallback, if offered.
First account sync.
Existing local alias/NFC/favorite preservation.
Unlinked-card anonymous fallback.
App restart and session check.
Expired-session fallback.
Disconnect and local-card retention.
Widget refresh before and after disconnect.
Blocked deceptive/external top-level URL.
TLS error never proceeds.
```

- [ ] **Step 5: Review the complete diff**

Run:

```bash
git status --short
git diff --stat
git diff
```

The human must approve the complete diff before any push.

- [ ] **Step 6: Commit final documentation**

```bash
git add README.md docs/decisions/0001-redbus-webview-session.md
git commit -m "docs: finalize optional RedBus account integration"
```

- [ ] **Step 7: Push only with a rotated credential**

After the exposed token is revoked and a new least-privilege credential is
provisioned outside chat:

```bash
git push -u origin feature/redbus-account-session
```

Do not push directly to `main`. Do not open a pull request without reading the
repository template, searching open and closed PRs, showing the complete diff,
and receiving explicit human approval.

## Checkpoints

### Contract Checkpoint: After Task 0

- [ ] Real response shapes are represented by sanitized fixtures.
- [ ] No credentials, cookies, tokens, or personal data are present.
- [ ] Logout behavior is confirmed or explicitly limited to local cleanup.
- [ ] Human approval is recorded before DTO implementation.

### Foundation Checkpoint: After Tasks 1-4

- [ ] Anonymous `SessionCookieJar` tests still pass.
- [ ] Account cookies are host/scheme isolated.
- [ ] Session state cannot become connected from navigation alone.
- [ ] Both remote sources share one local persistence path.

### Domain Checkpoint: After Tasks 5-7

- [ ] Linked cards use account balances.
- [ ] Unlinked, expired, malformed, and unavailable cases use anonymous
  fallback.
- [ ] Bulk refresh calls the account card list once.
- [ ] No account PII enters UI state or persistence.

### UI Checkpoint: After Tasks 8-11

- [ ] Turnstile works in the hardened WebView on a real device.
- [ ] Login, sync, session expiry, and disconnect are understandable and
  accessible.
- [ ] The widget remains operational without a WebView session.
- [ ] Full unit suite and debug assembly pass.

### Release Checkpoint: After Task 13

- [ ] Privacy Policy, Data Safety, README, and ADR match actual behavior.
- [ ] Real-device matrix is complete.
- [ ] Secret scan is clean.
- [ ] Provider terms/authorization decision is documented.
- [ ] Human approved the complete diff.
- [ ] Feature branch is ready to push with a rotated credential.

## Risks And Mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| Internal RedBus schema changes | High | Fixture-backed tolerant mapper, explicit contract errors, anonymous fallback |
| Session cookie leakage | High | Dedicated client, exact host/scheme allowlist, no HTTP logging, app-private CookieManager |
| WebView phishing or hostile redirect | High | Exact main-frame origin policy, no native bridge, no DOM inspection, TLS cancellation |
| Wrong beneficiary balance displayed | High | Select only normalized `Principal (Dinero)`; never aggregate wallets |
| Session expires during refresh | Medium | Transition to disconnected and continue with anonymous flow |
| Duplicate or destructive sync | Medium | Upsert by external number, preserve local metadata, never delete absent cards |
| Turnstile incompatibility on some devices | Medium | Official WebView requirements, third-party cookies, real-device matrix, anonymous mode |
| Terms prohibit third-party use | High | Distribution gate and provider authorization review before release |
| Widget cannot access authenticated session | Low | Explicitly retain its existing anonymous refresh path |
| Exposed GitHub token is reused | High | Revoke it; refuse usage; provision replacement outside chat with minimum scope |

## Definition Of Done

- All acceptance criteria in the design spec are met.
- Every focused test listed above passes.
- `gradle test --stacktrace` and `gradle assembleDebug --stacktrace` pass.
- Real-device login and fallback matrix is recorded.
- No secret or real personal data exists in source, fixtures, logs, or git
  history.
- Documentation no longer makes the obsolete "no accounts" or "100% local"
  claims.
- The complete diff receives explicit human approval before push.
