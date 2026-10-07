# Audit Remediation Implementation Plan (2026-10-07)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the defects found in the 2026-10-07 audit (history data loss, detail crash, lost edits, duplicate cards, blocking navigation, flaky watch refresh, stale widget, outdated privacy disclosures) without changing product scope.

**Architecture:** Targeted fixes inside the existing layers (Room DAOs, ViewModels, Compose screens, Wear service, manifest, docs). No new modules, no new dependencies.

**Tech Stack:** Kotlin 2.0.20, AGP 8.7.0, Compose BOM 2024.09.02, Room 2.6.1 (KSP), Robolectric 4.13 (SDK 34), MockK 1.13.12, kotlinx-coroutines-test 1.9.0.

**Spec:** the "Audit Findings" section below (no separate spec).

## Audit Findings

Baseline before any change: `:app:testDebugUnitTest` 189/189 green, `:app:assembleDebug` and `:wear:assembleDebug` OK, lint 0 errors / 58 warnings, 3 Kotlin warnings. The suite is green because no test exercises the defects below.

| ID | Sev | Finding | Evidence |
|---|---|---|---|
| F1 | Critical | Every balance refresh and every card edit deletes the card's entire balance history. `CardDao.insertCard` is `@Insert(REPLACE)`; SQLite `REPLACE` deletes the existing row, and `balance_history.cardId` is `ON DELETE CASCADE` with `PRAGMA foreign_keys = ON` (Room-generated `onOpen`). History never holds more than one row and its difference is always 0. | `CardDao.kt:29`, `BalanceHistoryEntity.kt:12-17`, `build/generated/ksp/.../SaetaDatabase_Impl.java` `onOpen` |
| F2 | Critical | Opening any card detail crashes with an NPE. The `init` block (line 40) launches `card.collect` before `card` (line 56) is initialized; `viewModelScope` runs on `Dispatchers.Main.immediate`, so the coroutine starts synchronously inside the constructor. Tests hide it by using `StandardTestDispatcher`. | `CardDetailViewModel.kt:40-58` |
| F3 | High | Renaming from "Editar tarjeta" is reverted: `updateCardName` and `updateCardColor` each save a full copy of the same stale snapshot, and the color save runs last with the old name. | `CardDetailScreen.kt:355-356`, `CardDetailViewModel.kt:161-176` |
| F4 | Medium | Adding a number that already exists (manual or after an NFC scan) inserts a duplicate row; later updates hit an arbitrary one (`getCardByNumber ... LIMIT 1`). | `CardsViewModel.kt:84-117`, `CardDao.kt:20` |
| F5 | Medium | An NFC tap on a known card, or "Guardar y Consultar", blocks navigation until the whole balance query finishes (Turnstile timeout is 45 s) and drops its errors. | `MainActivity.kt:236-240`, `CardsViewModel.kt:103-110` |
| F6 | Medium | Detail auto-refresh never fires: it checks `lastUpdated == 0L`, but never-refreshed cards have `lastUpdated == null`. | `CardDetailScreen.kt:117-121` |
| F7 | Medium | Rotation recreates `MainActivity`: navigation resets to the list, the login WebView reloads (typed credentials lost), dialogs close, and a second object graph is built while the previous ViewModels are never cleared. | `MainActivity.kt:101-150`, manifest without `configChanges` |
| F8 | Medium | The detail ViewModel re-requests `cargaspendientes` on every emission of the cards table (any refresh of any card), for every detail ever opened. | `CardDetailViewModel.kt:42-53` |
| F9 | Medium | Watch refresh is unreliable: `PhoneWearListenerService` launches the work in a scope it cancels in `onDestroy`, and the system may destroy the service as soon as `onMessageReceived` returns. The watch shows "..." forever when no phone node is connected. | `PhoneWearListenerService.kt:23-47`, wear `MainActivity.kt:95-110` |
| F10 | Medium | The home-screen widget is not updated when the app or the watch refreshes the favorite card; it stays stale until the 30-minute periodic update. | `MainActivity.kt:224-229`, `PhoneWearListenerService.kt` |
| F11 | Medium | Privacy disclosures are out of date: no mention of the Wear OS sync, wallets, internal number, card description or pending loads; Turnstile is described as involving no personal data (Cloudflare processes IP/browser signals); ACCESS_NETWORK_STATE is claimed to check connectivity but no code uses it (WorkManager requires it, plus WAKE_LOCK, RECEIVE_BOOT_COMPLETED, FOREGROUND_SERVICE); the fare is described as stored in DataStore/SharedPreferences but only lives in memory; VIBRATE is missing; wrong ML Kit artifact. | `PrivacyPolicyContent.kt`, `PRIVACY_POLICY.md`, `docs/compliance/DATA_SAFETY.md`, merged manifest |
| F12 | Low | ADR-0001's addendum justifies the Turnstile JS bridge because the page is "public and anonymous", but that WebView shares the global `CookieManager`: for logged-in users the page loads with the portal session. Exposure is small (the bridge only accepts `onToken`/`onError`). | `WebViewTurnstileTokenProvider.kt:91-92`, `RedBusWebViewSecurity.kt:35-37` |
| F13 | Low | `SaetaDatabase.getInstance` lacks the second null check inside `synchronized`; a concurrent first access (app + widget + Wear service) can build two Room instances whose invalidation trackers miss each other's writes. | `SaetaDatabase.kt:37-47` |
| F14 | Low | History queries order by `timestamp` only; two records in the same millisecond make "latest" ambiguous and the stored difference wrong. | `BalanceHistoryDao.kt:12-16` |
| F15 | Low | The 1→2 migration test hand-writes a v1 schema without the FK/index and never opens the database through Room, so Room's schema validation never runs. DB v2 has not shipped yet (v1.1.0 ships DB v1). | `SaetaDatabaseMigrationTest.kt` |
| F16 | Low | Dead code: `CardsViewModel`'s legacy refresh loop and `refreshCard` (the coordinator is always injected); its `getCardBalanceUseCase` becomes unused once F5 is fixed. | `CardsViewModel.kt:44-56,67-82` |

Items that need a decision or resources are listed in "Phase 2" at the end and are not part of this plan.

## Global Constraints

- No new dependencies, no version bumps, Room schema version stays 2.
- Do not add or remove code comments (removing comments that belong to deleted code is fine).
- Spanish user-facing copy in Task 8 is copied verbatim from this plan.
- Robolectric stays on SDK 34 (`robolectric.properties`).
- Build from `android/` with `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64` and `/tmp/gradle-dist/gradle-8.11.1/bin/gradle`.
- The implementer does not commit; the lead commits after review.

## Review Focus

1. Re-registering an existing card number (manual or NFC) reuses the existing card and attaches the NFC UID; no duplicate row. → Task 4 tests.
2. The account session becomes Connected after the detail screen opened: pending loads load exactly once. → Task 2 test.
3. Two balance updates in the same millisecond keep history order and difference correct. → Task 1 test (same-millisecond is the normal case in a fast test).
4. Rotating on the login or detail screen keeps the screen and the WebView. → Task 6 manifest test.
5. Upgrading from v1.1.0 (DB v1 with history rows) keeps cards and history and passes Room validation. → Task 7 test.

---

### Task 1: Stop the history wipe (F1, F13, F14)

**Files:**
- Modify: `android/app/src/main/java/com/saetasaldo/app/data/local/dao/CardDao.kt`
- Modify: `android/app/src/main/java/com/saetasaldo/app/data/local/dao/BalanceHistoryDao.kt`
- Modify: `android/app/src/main/java/com/saetasaldo/app/data/local/SaetaDatabase.kt`
- Create: `android/app/src/test/java/com/saetasaldo/app/data/repository/CardRepositoryRoomTest.kt`

**Interfaces:** unchanged (`CardDao.insertCard(card: CardEntity)` keeps its name and signature).

- [ ] **Step 1: Write the failing test.** Robolectric runner; `Room.inMemoryDatabaseBuilder(context, SaetaDatabase::class.java).allowMainThreadQueries().build()` in `@Before`, `close()` in `@After`; real `CardRepositoryImpl(db.cardDao(), db.balanceHistoryDao(), mockk(relaxed = true), mockk(relaxed = true))`; `runBlocking`.

```kotlin
@Test
fun `balance updates and card edits keep earlier history`() = runBlocking {
    val first = repository.applyBalanceUpdate(CardBalanceUpdate("12345678", 1000.0, null, null, null))
    repository.applyBalanceUpdate(CardBalanceUpdate("12345678", 800.0, null, null, null))
    repository.saveCard(repository.getCardById(first.id)!!.copy(name = "Renombrada"))

    val history = repository.getHistoryForCard(first.id).first()
    assertEquals(listOf(800.0, 1000.0), history.map { it.balance })
    assertEquals(listOf(-200.0, 0.0), history.map { it.difference })
    val card = repository.getCardById(first.id)!!
    assertEquals("Renombrada", card.name)
    assertEquals(800.0, card.currentBalance!!, 0.001)
}

@Test
fun `deleting a card still removes its history`() = runBlocking {
    val card = repository.applyBalanceUpdate(CardBalanceUpdate("12345678", 1000.0, null, null, null))
    repository.deleteCard(card)
    assertTrue(repository.getHistoryForCard(card.id).first().isEmpty())
}
```

- [ ] **Step 2: Run it.** `gradle :app:testDebugUnitTest --tests "*CardRepositoryRoomTest"` → the first test FAILS (history is empty or has one row). If it passes, stop and report.
- [ ] **Step 3: Fix.** `CardDao.insertCard`: `@Upsert` instead of `@Insert(onConflict = REPLACE)`. `BalanceHistoryDao`: both queries `ORDER BY timestamp DESC, id DESC`. `SaetaDatabase.getInstance`: re-check `INSTANCE` inside `synchronized` before building.
- [ ] **Step 4: Run.** `--tests "*CardRepositoryRoomTest" --tests "*CardRepositoryTest"` → PASS.

### Task 2: Detail crash and pending-loads spam (F2, F8)

**Files:**
- Modify: `android/app/src/main/java/com/saetasaldo/app/ui/detail/CardDetailViewModel.kt`
- Test: `android/app/src/test/java/com/saetasaldo/app/ui/detail/CardDetailViewModelTest.kt`

**Interfaces:** public API unchanged.

- [ ] **Step 1: Write the failing tests.** Each test first calls `Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))` to reproduce `Main.immediate`. Fixtures: `linkedCard = testCard.copy(internalNumber = "90000001")` placed in `cardsFlow`; `sessionFlow = MutableStateFlow<RedBusSessionState>(...)`; `accountRepository = mockk<RedBusAccountRepository>()` with `every { sessionState } returns sessionFlow` and `coEvery { getPendingLoads("90000001") } returns Result.success(listOf(PendingLoad(amount = 500.0)))`.

```kotlin
@Test
fun `constructing on an immediate dispatcher loads pending loads once per internal number`() = runTest {
    // sessionFlow starts Connected
    val viewModel = CardDetailViewModel(
        cardId = "card-1",
        repository = repository,
        getCardBalanceUseCase = getCardBalanceUseCase,
        accountRepository = accountRepository
    )
    assertEquals(listOf(PendingLoad(amount = 500.0)), viewModel.pendingLoads.value)

    cardsFlow.value = listOf(linkedCard.copy(currentBalance = 100.0))
    coVerify(exactly = 1) { accountRepository.getPendingLoads("90000001") }
}

@Test
fun `pending loads load when the session connects after the detail opened`() = runTest {
    // sessionFlow starts Unknown
    val viewModel = CardDetailViewModel(/* same arguments */)
    assertNull(viewModel.pendingLoads.value)

    sessionFlow.value = RedBusSessionState.Connected
    assertEquals(listOf(PendingLoad(amount = 500.0)), viewModel.pendingLoads.value)
    coVerify(exactly = 1) { accountRepository.getPendingLoads("90000001") }
}
```

- [ ] **Step 2: Run.** `--tests "*CardDetailViewModelTest"` → both FAIL (NPE from the init coroutine / null pending loads). If not, stop and report.
- [ ] **Step 3: Fix.** Move the `init` block below the `card` declaration. Body: only when `accountRepository != null`, launch a collector of `combine(card.map { it?.internalNumber }, accountRepository.sessionState) { number, state -> number to state }.distinctUntilChanged()`; `number == null` → `_pendingLoads.value = null`; `state == RedBusSessionState.Connected` → `_pendingLoads.value = accountRepository.getPendingLoads(number).getOrNull()`; otherwise leave the value.
- [ ] **Step 4: Run.** `--tests "*CardDetailViewModelTest"` → PASS.

### Task 3: One write for the edit dialog (F3)

**Files:**
- Modify: `android/app/src/main/java/com/saetasaldo/app/ui/detail/CardDetailViewModel.kt`
- Modify: `android/app/src/main/java/com/saetasaldo/app/ui/detail/CardDetailScreen.kt`
- Test: `android/app/src/test/java/com/saetasaldo/app/ui/detail/CardDetailViewModelTest.kt`

**Interfaces:**
- Produces: `CardDetailViewModel.updateCard(newName: String, colorArgb: Int?)`.
- Removes: `updateCardName`, `updateCardColor`.

- [ ] **Step 1: Replace** the test `updateCardName saves card with updated name` with:

```kotlin
@Test
fun `updateCard saves name and color in a single write`() = runTest {
    val viewModel = CardDetailViewModel(cardId = "card-1", repository = repository, getCardBalanceUseCase = getCardBalanceUseCase, calculateRemainingTripsUseCase = calculateRemainingTripsUseCase)
    backgroundScope.launch { viewModel.card.collect() }
    advanceUntilIdle()

    viewModel.updateCard("Nueva Saeta", 0xFF1B5E20.toInt())
    advanceUntilIdle()

    coVerify(exactly = 1) { repository.saveCard(any()) }
    coVerify { repository.saveCard(match { it.id == "card-1" && it.name == "Nueva Saeta" && it.colorArgb == 0xFF1B5E20.toInt() }) }
}

@Test
fun `updateCard with a blank name keeps the current name`() = runTest {
    // same setup
    viewModel.updateCard("   ", null)
    advanceUntilIdle()

    coVerify(exactly = 1) { repository.saveCard(match { it.name == "Mi Saeta" && it.colorArgb == null }) }
}
```

- [ ] **Step 2: Run** → FAIL (unresolved `updateCard`).
- [ ] **Step 3: Implement** `updateCard` with a single `repository.saveCard(currentCard.copy(name = newName.trim().ifBlank { currentCard.name }, colorArgb = colorArgb))`; delete the two old methods; the dialog's "Guardar" calls `viewModel.updateCard(newName, selectedColor)`.
- [ ] **Step 4: Run** `--tests "*CardDetailViewModelTest"` → PASS.

### Task 4: Registration and NFC open flows (F4, F5, F6, F16)

**Files:**
- Modify: `android/app/src/main/java/com/saetasaldo/app/domain/repository/CardRepository.kt`
- Modify: `android/app/src/main/java/com/saetasaldo/app/data/repository/CardRepositoryImpl.kt`
- Modify: `android/app/src/main/java/com/saetasaldo/app/ui/cards/CardsViewModel.kt`
- Modify: `android/app/src/main/java/com/saetasaldo/app/ui/detail/CardDetailScreen.kt`
- Modify: `android/app/src/main/java/com/saetasaldo/app/MainActivity.kt`
- Test: `android/app/src/test/java/com/saetasaldo/app/ui/cards/CardsViewModelTest.kt`

**Interfaces:**
- `CardRepository`: add `suspend fun getCardByNumber(cardNumber: String): SaetaCard?`; impl `cardDao.getCardByNumber(cardNumber.trim())?.toDomain()`.
- `CardsViewModel(repository: CardRepository, refreshAllBalancesUseCase: RefreshAllBalancesUseCase)`; `getCardBalanceUseCase`, `refreshCard` and the legacy loop are removed.
- `CardsViewModel.addNewCard(name: String, cardNumber: String, nfcUid: String? = null, onComplete: ((Result<SaetaCard>) -> Unit)? = null)`: saves and reports the saved card; never queries the balance.
- `Screen.CardDetail(val cardId: String, val refreshOnOpen: Boolean = false)`.
- `CardDetailScreen(viewModel: CardDetailViewModel, refreshOnOpen: Boolean, onBackClick: () -> Unit, modifier: Modifier = Modifier)`.

- [ ] **Step 1: Update `CardsViewModelTest`.** Construct every VM as `CardsViewModel(repository, refreshAllBalancesUseCase)` and drop the `getCardBalanceUseCase` mock. Delete `refreshCard queries balance for specific card number`. In `refresh all delegates the current card snapshot once`, delete the `getCardBalanceUseCase` verify. In `refresh all with no failures clears stale errors`, produce the stale error with a first `refreshAllBalances()` whose result is `RefreshAllBalancesResult(updatedCount = 0, failures = mapOf("111111" to RuntimeException("stale")))`. Replace `addNewCard saves card and immediately refreshes balance` with:

```kotlin
@Test
fun `addNewCard saves a new card without querying the balance`() = runTest {
    val viewModel = CardsViewModel(repository, refreshAllBalancesUseCase)
    coEvery { repository.getCardByNumber("999999") } returns null
    var saved: SaetaCard? = null

    viewModel.addNewCard(name = "Mi Tarjeta", cardNumber = " 999999 ", nfcUid = "04a1b2c3") { saved = it.getOrNull() }
    advanceUntilIdle()

    coVerify(exactly = 1) { repository.saveCard(match { it.cardNumber == "999999" && it.name == "Mi Tarjeta" && it.nfcUid == "04A1B2C3" }) }
    assertEquals("999999", saved?.cardNumber)
}

@Test
fun `addNewCard reuses an existing card with the same number`() = runTest {
    val viewModel = CardsViewModel(repository, refreshAllBalancesUseCase)
    val existing = SaetaCard(id = "1", name = "Principal", cardNumber = "111111", currentBalance = 1500.0)
    coEvery { repository.getCardByNumber("111111") } returns existing
    var saved: SaetaCard? = null

    viewModel.addNewCard(name = "", cardNumber = "111111", nfcUid = "04ab") { saved = it.getOrNull() }
    advanceUntilIdle()

    coVerify(exactly = 1) { repository.saveCard(match { it.id == "1" && it.name == "Principal" && it.nfcUid == "04AB" && it.currentBalance == 1500.0 }) }
    assertEquals("1", saved?.id)
}
```

- [ ] **Step 2: Run** `--tests "*CardsViewModelTest"` → FAIL (compile).
- [ ] **Step 3: Implement.**
  - `addNewCard`: trim the number, uppercase the UID; if `getCardByNumber` finds a card → `existing.copy(name = name.trim().ifBlank { existing.name }, nfcUid = uid ?: existing.nfcUid)`, else a new `SaetaCard(id = UUID, name = name.trim().ifBlank { "Tarjeta SAETA" }, cardNumber = number, nfcUid = uid, isFavorite = cards.value.isEmpty())`; `saveCard`; `onComplete(Result.success(card))`. Rethrow `CancellationException`; any other exception sets the error message and calls `onComplete(Result.failure(e))`.
  - `MainActivity`: NFC `ExistingCardFound` → `currentScreen = Screen.CardDetail(result.card.id, refreshOnOpen = true)` with no balance call; the new-card dialog navigates to `Screen.CardDetail(saved.id, refreshOnOpen = true)` on success; build `CardsViewModel(repository, refreshAllBalancesUseCase)`; pass `refreshOnOpen = screen.refreshOnOpen` to `CardDetailScreen`; add `ExperimentalCoroutinesApi::class` to the existing `@OptIn` on `SaetaAppContent` (warning from `resetReplayCache`).
  - `CardDetailScreen`: replace the `lastUpdated == 0L` effect with `LaunchedEffect(card?.id) { val current = card ?: return@LaunchedEffect; if (refreshOnOpen || current.lastUpdated == null) viewModel.refreshBalance() }`.
- [ ] **Step 4: Run** `--tests "*CardsViewModelTest"` and `:app:compileDebugKotlin` → PASS, no new warnings.

### Task 5: Watch refresh and widget freshness (F9, F10)

**Files:**
- Modify: `android/app/src/main/java/com/saetasaldo/app/wear/PhoneWearListenerService.kt`
- Modify: `android/wear/src/main/java/com/saetasaldo/wear/MainActivity.kt`
- Modify: `android/app/src/main/java/com/saetasaldo/app/MainActivity.kt`

- [ ] `PhoneWearListenerService.onMessageReceived`: run the existing body inside `runBlocking` (the callback already runs on a background thread), then `SaetaBalanceWidget().updateAll(this@PhoneWearListenerService)`. Delete `scope` and `onDestroy`.
- [ ] Wear `requestRefresh`: when `connectedNodes` is empty, set `refreshing.value = false`.
- [ ] App `SaetaAppContent` favorite observer: `repository.getAllCards().map { cards -> cards.firstOrNull { it.isFavorite } }.distinctUntilChanged().collect { favorite -> sync.pushFavoriteCard(favorite); SaetaBalanceWidget().updateAll(context) }`.
- [ ] **Verify:** `:app:compileDebugKotlin :wear:compileDebugKotlin` → PASS. (No unit test: Wearable and Glance need GMS and a launcher.)

### Task 6: Keep state across rotation (F7)

**Files:**
- Modify: `android/app/src/main/AndroidManifest.xml`
- Create: `android/app/src/test/java/com/saetasaldo/app/MainActivityManifestTest.kt`

- [ ] **Step 1: Write the failing test** (Robolectric):

```kotlin
@Test
fun `main activity handles rotation and dark mode without being recreated`() {
    val app = RuntimeEnvironment.getApplication()
    val info = app.packageManager.getActivityInfo(ComponentName(app, MainActivity::class.java), 0)
    val required = ActivityInfo.CONFIG_ORIENTATION or ActivityInfo.CONFIG_SCREEN_SIZE or
        ActivityInfo.CONFIG_SCREEN_LAYOUT or ActivityInfo.CONFIG_SMALLEST_SCREEN_SIZE or
        ActivityInfo.CONFIG_UI_MODE
    assertEquals(required, info.configChanges and required)
}
```

- [ ] **Step 2: Run** → FAIL.
- [ ] **Step 3:** Add `android:configChanges="orientation|screenSize|screenLayout|smallestScreenSize|keyboardHidden|uiMode"` to `.MainActivity`.
- [ ] **Step 4: Run** `--tests "*MainActivityManifestTest"` → PASS.

### Task 7: Migration test through Room (F15)

**Files:**
- Modify: `android/app/src/test/java/com/saetasaldo/app/data/local/SaetaDatabaseMigrationTest.kt`

- [ ] **Step 1: Rewrite the test.** Create `migration-test.db` at version 1 with the exact Room v1 DDL below; insert card `('c1', 'Mi Tarjeta', '12345678', 'AZUL_COMUN', 1500.0, isFavorite 1)` and history row `(cardId 'c1', balance 1500.0, difference 0.0, timestamp 1)`; close the helper. Then open `Room.databaseBuilder(context, SaetaDatabase::class.java, "migration-test.db").addMigrations(SaetaDatabase.MIGRATION_1_2).allowMainThreadQueries().build()` and, through the DAOs (`runBlocking`), assert: name `Mi Tarjeta`, `currentBalance == 1500.0`, `colorArgb == null`, `internalNumber == null`, latest history balance `1500.0`. Close the database.

```sql
CREATE TABLE IF NOT EXISTS `cards` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `cardNumber` TEXT NOT NULL, `nfcUid` TEXT, `type` TEXT NOT NULL, `currentBalance` REAL, `lastUpdated` INTEGER, `isFavorite` INTEGER NOT NULL, `cardState` TEXT, PRIMARY KEY(`id`))
CREATE TABLE IF NOT EXISTS `balance_history` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `cardId` TEXT NOT NULL, `balance` REAL NOT NULL, `difference` REAL NOT NULL, `timestamp` INTEGER NOT NULL, FOREIGN KEY(`cardId`) REFERENCES `cards`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )
CREATE INDEX IF NOT EXISTS `index_balance_history_cardId` ON `balance_history` (`cardId`)
```

- [ ] **Step 2: Prove it validates.** Temporarily remove one `ALTER TABLE` from `MIGRATION_1_2`, run the test, confirm it fails with Room's "Migration didn't properly handle", then restore the line.
- [ ] **Step 3: Run** `--tests "*SaetaDatabaseMigrationTest"` → PASS.

### Task 8: Accurate disclosures (F11, F12)

**Files:**
- Modify: `android/app/src/main/java/com/saetasaldo/app/domain/model/PrivacyPolicyContent.kt`
- Test: `android/app/src/test/java/com/saetasaldo/app/domain/model/PrivacyPolicyContentTest.kt`
- Modify: `PRIVACY_POLICY.md`, `docs/compliance/DATA_SAFETY.md`, `docs/decisions/0001-redbus-webview-session.md`

- [ ] **Step 1: Add the failing tests** to `PrivacyPolicyContentTest`:

```kotlin
@Test
fun `policy discloses account wallets, internal number and pending loads`() {
    val text = allPolicyText()
    assertTrue(text.contains("monederos", ignoreCase = true))
    assertTrue(text.contains("número interno", ignoreCase = true))
    assertTrue(text.contains("cargas pendientes", ignoreCase = true))
}

@Test
fun `policy discloses the Wear OS sync of the favorite card`() {
    val text = allPolicyText()
    assertTrue(text.contains("Wear OS"))
    assertTrue(text.contains("Google Play Services"))
}

@Test
fun `permission explanations cover VIBRATE`() {
    assertFalse(PrivacyPolicyContent.permissionsJustification.getValue("VIBRATE").isBlank())
}
```

- [ ] **Step 2: Run** → FAIL.
- [ ] **Step 3: Apply the copy below verbatim.**
- [ ] **Step 4: Run** `--tests "*PrivacyPolicyContentTest"` → PASS (all old and new tests).

#### 8a. `PrivacyPolicyContent.kt`

Sections 1 and the old 5 ("Control y Eliminación de Datos") and 6 ("Descargo de Responsabilidad") keep their content; renumber them to 1, 6 and 7. Replace sections 2, 3 and the old 4, and insert a new section 4, so the list reads:

```kotlin
PolicySection(
    title = "2. Consultas a MiRedBus",
    content = "La app ofrece dos formas de consultar tu saldo. En el modo anónimo, siempre disponible, se envía únicamente el número de tarjeta y el código de seguridad (captcha) al portal oficial de MiRedBus Salta mediante conexión cifrada HTTPS. Opcionalmente puedes conectar tu cuenta de RedBus: el inicio de sesión se realiza directamente en la página oficial de RedBus dentro de un WebView protegido, y SAETA Saldo nunca recibe, lee ni almacena tu contraseña. Las cookies de sesión del portal se guardan solo en el almacenamiento privado de la app y se usan exclusivamente para consultar tus tarjetas vinculadas sin captcha: se leen el número de tarjeta vinculada, el saldo principal y el de los demás monederos que informe el portal, el tipo y el estado de la tarjeta, su número interno y la descripción que tiene en tu cuenta, y se consultan sus cargas pendientes de acreditación. Esos datos se guardan solo en tu dispositivo. No se recopilan identificadores de dispositivo ni ubicación, y nada se envía al desarrollador."
),
PolicySection(
    title = "3. Reconocimiento de Captcha en el Dispositivo",
    content = "La resolución automática de códigos captcha se procesa íntegramente de forma local mediante Google ML Kit Text Recognition en tu propio teléfono. Las imágenes nunca se suben ni se comparten con servicios externos. Además, la app puede resolver la verificación ejecutando un desafío de Cloudflare Turnstile en un WebView interno sobre el dominio oficial de MiRedBus, igual que lo haría un navegador. Ese desafío lo provee Cloudflare, que procesa datos técnicos de la conexión y del navegador (como la dirección IP) para distinguir personas de bots. No intervienen credenciales."
),
PolicySection(
    title = "4. Reloj Wear OS (Opcional)",
    content = "Si tienes un reloj Wear OS vinculado con SAETA Saldo instalado, el teléfono le envía el alias, el saldo, la estimación de viajes y la hora de la última actualización de tu tarjeta favorita mediante la API Wearable Data Layer de Google Play Services, que sincroniza esos datos entre tus dispositivos. El reloj no se conecta al portal: solo muestra lo que recibe del teléfono y puede pedirle que actualice el saldo."
),
PolicySection(
    title = "5. Permisos Requeridos",
    content = "• NFC: Se utiliza únicamente para detectar y leer el identificador de tu tarjeta SAETA cuando la acercas al teléfono.\n• INTERNET: Para comunicarse con el portal oficial de RedBus (consulta de saldo, inicio de sesión opcional y desafío Turnstile).\n• VIBRATE: Para una vibración breve al detectar tu tarjeta por NFC.\n• ACCESS_NETWORK_STATE, WAKE_LOCK, RECEIVE_BOOT_COMPLETED y FOREGROUND_SERVICE: Las requiere WorkManager, la biblioteca de Android Jetpack que usa el widget para su trabajo en segundo plano; la app no las usa directamente."
),
```

`permissionsJustification` becomes:

```kotlin
mapOf(
    "NFC" to "Lectura del chip contactless de la tarjeta física SAETA para vincularla rápidamente.",
    "INTERNET" to "Comunicación cifrada HTTPS con el portal oficial para consultar el saldo.",
    "VIBRATE" to "Respuesta háptica breve al detectar la tarjeta por NFC.",
    "ACCESS_NETWORK_STATE" to "Requerido por WorkManager (Android Jetpack), que usa el widget; la app no lo usa directamente."
)
```

#### 8b. `PRIVACY_POLICY.md` (replace exactly)

1. `**Última actualización / Last updated:** 5 de Octubre de 2026\` → `**Última actualización / Last updated:** 7 de Octubre de 2026\`
2. Row `| **Tarifa de Referencia** | Configurada por el usuario (por defecto $1.450) | 100% localmente (DataStore / SharedPreferences) | **Nunca** | Calcular viajes restantes disponibles |` → `| **Tarifa de Referencia** | Configurada por el usuario en el detalle de la tarjeta (por defecto $1.450) | Solo en memoria mientras el detalle está abierto (no se guarda) | **Nunca** | Calcular viajes restantes disponibles |`
3. After the row `| **Sesión RedBus (cookies)** | ... |` add two rows:
   - `| **Monederos, número interno, descripción y cargas pendientes** (cuenta RedBus opcional) | Portal oficial, a través de tu sesión | Localmente en el dispositivo (SQLite Room); las cargas pendientes, solo en memoria | **Nunca** | Mostrar el saldo de cada monedero y avisar cargas pendientes de acreditación |`
   - `| **Resumen de la tarjeta favorita** (alias, saldo, viajes estimados, hora de actualización) | Calculado en el teléfono | Teléfono y reloj Wear OS vinculado | Solo a tu propio reloj, mediante Google Play Services (Wearable Data Layer) | Mostrar el saldo en el reloj |`
4. `` (`com.google.android.gms:play-services-mlkit-text-recognition`) `` → `` (`com.google.mlkit:text-recognition`, con el modelo incluido en la app) ``
5. `La aplicación únicamente realiza conexiones de red salientes hacia los servidores oficiales de consulta de transporte:` → `La aplicación realiza conexiones de red salientes hacia los servidores oficiales de consulta de transporte y hacia el servicio anti-bots que usa el propio portal:`
6. `No intervienen credenciales ni datos personales; si el desafío falla se recurre al captcha de imagen con OCR local.` → ``El desafío lo provee Cloudflare (`challenges.cloudflare.com`), que procesa datos técnicos de la conexión y del navegador (como la dirección IP) para distinguir personas de bots, según su propia política de privacidad. No intervienen credenciales; si el desafío falla se recurre al captcha de imagen con OCR local.``
7. `(número de tarjeta, saldo principal, tipo y estado)` → `(número de tarjeta, saldo principal y de los demás monederos, tipo, estado, número interno, descripción y cargas pendientes de acreditación)`
8. After the bullet `- **La aplicación nunca recibe ni almacena tu contraseña de RedBus.**` insert:

   ```markdown

   ### 4.2 Reloj Wear OS (Opcional)

   Si tienes un reloj Wear OS vinculado con SAETA Saldo instalado, el teléfono le envía el alias, el saldo, la estimación de viajes y la hora de la última actualización de tu tarjeta favorita mediante la API **Wearable Data Layer de Google Play Services**, que sincroniza esos datos entre tus dispositivos. El reloj no se conecta al portal: solo muestra lo que recibe del teléfono y puede pedirle que actualice el saldo.
   ```

9. `La aplicación solicita exclusivamente los permisos técnicos estrictamente indispensables para su funcionamiento:` → `La aplicación solicita los siguientes permisos (algunos los agrega automáticamente una biblioteca de Android Jetpack):`
10. Under item 3 (`ACCESS_NETWORK_STATE`), the justification line → `   - **Justificación:** La requiere WorkManager, la biblioteca de Android Jetpack que usa el widget de escritorio. La app no la usa directamente.` Then add:

    ```markdown
    4. **`android.permission.VIBRATE`**:
       - **Justificación:** Vibración breve (respuesta háptica) al detectar la tarjeta por NFC.
    5. **`android.permission.WAKE_LOCK`, `android.permission.RECEIVE_BOOT_COMPLETED` y `android.permission.FOREGROUND_SERVICE`**:
       - **Justificación:** Las agrega automáticamente WorkManager para el trabajo en segundo plano del widget. La app no las usa directamente.
    ```

11. English summary: `Anonymous balance queries may run a Cloudflare Turnstile challenge inside an offscreen in-app WebView on the official domain — no credentials involved.` → `Anonymous balance queries may run a Cloudflare Turnstile challenge inside an offscreen in-app WebView on the official domain; Cloudflare processes technical connection data (such as the IP address) to tell humans from bots. No credentials are involved.` Append to the end of the "Optional RedBus Account" bullet: ` Account sync stores the linked cards' wallet balances, internal number and description on-device only; pending loads are fetched on demand.` Insert after that bullet: `- **Wear OS (optional):** The favorite card's alias, balance, trip estimate and last-update time are sent to your own paired watch through the Google Play services Wearable Data Layer. The watch never contacts the portal.`

#### 8c. `docs/compliance/DATA_SAFETY.md` (replace exactly)

1. After the "Optional Account Connection" bullet in section 1 insert:
   - `- **Wear OS Companion:** The phone sends the favorite card's alias, formatted balance, trip estimate and last-update time to the user's own paired watch via the Google Play services Wearable Data Layer. This is a user-initiated transfer between the user's own devices; nothing reaches developer servers.`
   - `- **Cloudflare Turnstile:** Anonymous queries may load the portal's Cloudflare Turnstile challenge in an offscreen WebView. As on any website that uses it, Cloudflare processes technical connection data (e.g., IP address, browser signals) for bot detection; the app itself does not collect it.`
2. `The answers below were updated for the optional RedBus account flow on 2026-10-05.` → `The answers below were updated for the optional RedBus account flow on 2026-10-05 and for account wallets, pending loads and the Wear OS companion on 2026-10-07.`
3. ``With the optional account connection, the linked card number, `Principal (Dinero)` balance, card type, and card state are additionally read through the authenticated provider session and stored only in the on-device database.`` → ``With the optional account connection, the linked card number, the `Principal (Dinero)` balance and any other wallet balances, card type, card state, internal card number (`nroInterno`) and the account's card description are additionally read through the authenticated provider session and stored only in the on-device database; pending loads (`cargaspendientes`) are fetched on demand and kept in memory only.``

#### 8d. `docs/decisions/0001-redbus-webview-session.md`

Insert before `## Referencias`:

```markdown
**Correccion (2026-10-07):** el WebView de Turnstile comparte el `CookieManager`
global con el WebView de login. Si el usuario conecto su cuenta, la home se carga
con las cookies de sesion del portal, asi que la pagina no es estrictamente
anonima. El riesgo se acepta porque el puente solo expone `onToken`/`onError`
(no devuelve datos a la pagina ni lee la sesion) y el main frame sigue
restringido al host exacto por HTTPS. Alternativa evaluada: reemplazar
`addJavascriptInterface` por `WebViewCompat.addWebMessageListener` con allowlist
de origen `https://salta.miredbus.com.ar` (requiere `androidx.webkit`).

```

### Final gate

Run once: `gradle :app:testDebugUnitTest :app:assembleDebug :wear:assembleDebug :app:lintDebug`.
Expected: BUILD SUCCESSFUL; 198 tests, 0 failures (189 − 3 removed + 12 added); lint 0 errors; Kotlin warnings limited to the two pre-existing `RedBusWebViewSecurity.kt` deprecations.

---

## Phase 2 — Needs a decision (not executed)

| ID | Sev | Item |
|---|---|---|
| D1 | High | The GitHub Release ships the **debug** APK: it is debuggable (`adb run-as` exposes the Room DB and WebView cookies), `<debug-overrides>` trusts user-installed CAs (a user CA can intercept the RedBus session), and HTTP logging is on. Options: remove `<debug-overrides>` now, and/or set up release signing in CI (needs a keystore secret). |
| D2 | Medium | iOS `Info.plist` turns ATS off globally (`NSAllowsArbitraryLoads`, `NSAllowsArbitraryLoadsInWebContent`, TLS 1.0 exceptions). Fixing it needs testing on an iPhone. |
| D3 | Medium | Proper ViewModel lifecycle: app-scoped object graph plus `ViewModelProvider`/`viewModel(key)`. Task 6 is the minimal mitigation. |
| D4 | Low | Replace the Turnstile `addJavascriptInterface` with `WebViewCompat.addWebMessageListener` limited to the portal origin (adds `androidx.webkit`). |
| D5 | Product | The fare only lives in memory in the detail screen; the list, widget and watch always use $1.450. Persist it app-wide? |
| D6 | Low | CI hardening: `contents: write` only for tag releases, actions pinned to SHAs, `lintDebug` step, a committed Gradle wrapper. |
| D7 | Release | Bump `versionCode`/`versionName` before the next release: DB v2 and Wear are unreleased and `versionCode 2` is already used by v1.1.0. |
| D8 | Low | Turnstile back-off so a bulk refresh does not wait 45 s per card while Turnstile is failing. |
| D9 | Low | Room `exportSchema = true` with a committed schemas directory. |
| D10 | Hygiene | `logs/` and `logs.zip` are tracked despite `.gitignore` (`git rm --cached`). |
| D11 | Low | NFC tech filter opens the app for any NFC-A tag; fixed 200 dp card height clips at large font sizes; color swatches lack labels; no-op clickable card in the detail; 20 outdated dependencies (update one at a time); `dataExtractionRules`; wear app icon; unused strings; `SolveCaptchaUseCase` (domain) depends on Android/data classes; `SaldoResponseDto` falls back to parsing `mensaje` and can store a made-up 0.0 when `saldos` is missing. |
