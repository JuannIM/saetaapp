# Optional RedBus Account Tasks

Detailed steps and exact interfaces live in
`docs/superpowers/plans/2026-10-05-redbus-optional-account.md`.

## Task 0: Verify And Sanitize The Authenticated Contract

**Description:** Capture authorized response shapes without credentials or
cookies and establish fixtures before DTO code is written.

**Acceptance criteria:**
- [x] Session and card fixtures preserve the real JSON shape with synthetic data.
- [x] Logout method is confirmed or local-only disconnect is documented.
- [x] A human confirms that no personal or session data remains.

**Verification:**
- [x] `git diff -- android/app/src/test/resources/redbus`
- [x] Secret-pattern scan returns no matches.

**Dependencies:** None
**Estimated scope:** Medium

## Task 1: Define Domain Models And DTO Mapping

**Description:** Add the stable internal account contract and map the sanitized
external response into principal-balance cards.

**Acceptance criteria:**
- [x] Session states and repository interface match the design spec.
- [x] Only `Principal (Dinero)` maps to `RedBusAccountCard.balance`.
- [x] Malformed cards are omitted without hiding valid siblings.

**Verification:**
- [x] `gradle testDebugUnitTest --tests "*RedBusAccountDtosTest" --stacktrace`

**Dependencies:** Task 0
**Estimated scope:** Medium

## Task 2: Isolate Authenticated Cookies And Networking

**Description:** Adapt `CookieManager` to a dedicated, non-logging account
client without changing the anonymous cookie jar.

**Acceptance criteria:**
- [x] Cookies are available only to exact HTTPS RedBus requests.
- [x] Response cookies are returned to `CookieManager` and can be cleared.
- [x] Account endpoints are absent from the anonymous client.

**Verification:**
- [x] `gradle testDebugUnitTest --tests "*WebViewCookieJarTest" --tests "*SessionCookieJarTest" --stacktrace`

**Dependencies:** Task 1
**Estimated scope:** Medium

## Task 3: Implement Account Session State

**Description:** Verify, use, expire, and disconnect account sessions with
explicit error semantics.

**Acceptance criteria:**
- [x] Only `usuarioLogeado.error == 0` produces `Connected`.
- [x] Error `99` disconnects while transient network errors remain retryable.
- [x] Disconnect always clears local cookies.

**Verification:**
- [x] `gradle testDebugUnitTest --tests "*RedBusAccountRepositoryTest" --stacktrace`

**Dependencies:** Tasks 1-2
**Estimated scope:** Small

## Task 4: Unify Balance Persistence

**Description:** Route anonymous and account balances through one Room/history
update function.

**Acceptance criteria:**
- [x] Existing alias, NFC UID, and favorite status are preserved.
- [ ] New cards use a safe suggested/default name.
- [ ] History is written only when the monetary balance changes.

**Verification:**
- [x] `gradle testDebugUnitTest --tests "*CardRepositoryTest" --stacktrace`

**Dependencies:** Task 1
**Estimated scope:** Medium

## Checkpoint: Foundation

- [ ] Tasks 0-4 are reviewed.
- [ ] Cookie-boundary and repository regression tests pass.
- [x] No Room migration, dependency, secret, or personal data was added.

## Task 5: Prefer Account Balance For One Card

**Description:** Make the existing balance use case select a linked account
card before falling back to anonymous captcha/OCR.

**Acceptance criteria:**
- [x] Linked card uses account data without an anonymous request.
- [ ] Manual captcha, unlinked, expired, network, and contract-error cases use fallback.
- [ ] Cancellation is rethrown.

**Verification:**
- [x] `gradle testDebugUnitTest --tests "*GetCardBalanceUseCaseTest" --stacktrace`

**Dependencies:** Tasks 3-4
**Estimated scope:** Small

## Task 6: Synchronize And Bulk Refresh

**Description:** Import/update linked cards once and anonymously refresh only
the uncovered local cards.

**Acceptance criteria:**
- [ ] Bulk refresh issues one account-list request.
- [ ] Duplicate account card numbers are applied once.
- [ ] Partial failures do not discard successful updates.

**Verification:**
- [x] `gradle testDebugUnitTest --tests "*SyncRedBusCardsUseCaseTest" --tests "*RefreshAllBalancesUseCaseTest" --stacktrace`

**Dependencies:** Tasks 3-5
**Estimated scope:** Medium

## Task 7: Model Account UI State

**Description:** Expose connection, verification, synchronization, and messages
without retaining account PII.

**Acceptance criteria:**
- [ ] Login verification synchronizes only after confirmed connection.
- [ ] Concurrent page-finished checks are coalesced.
- [ ] UI state contains no username, document, raw response, or cookie.

**Verification:**
- [x] `gradle testDebugUnitTest --tests "*RedBusAccountViewModelTest" --stacktrace`

**Dependencies:** Tasks 3 and 6
**Estimated scope:** Small

## Checkpoint: Domain

- [ ] Tasks 5-7 are reviewed.
- [ ] Authenticated and anonymous selection tests pass.
- [ ] Session expiry and partial failure behavior is controlled.

## Task 8: Build The Secure Login WebView

**Description:** Host the official Turnstile login with an exact origin policy
and no native JavaScript bridge.

**Acceptance criteria:**
- [ ] Required browser features work while files and mixed content are blocked.
- [ ] Deceptive, non-HTTPS, and non-web main-frame URLs are rejected.
- [ ] TLS errors cancel and the WebView is destroyed on exit.

**Verification:**
- [x] `gradle testDebugUnitTest --tests "*RedBusWebViewSecurityTest" --stacktrace`
- [x] `gradle assembleDebug --stacktrace`
- [ ] Manual Turnstile login on a real device.

**Dependencies:** Tasks 2, 3, and 7
**Estimated scope:** Medium

## Task 9: Delegate Bulk Refresh From CardsViewModel

**Description:** Replace ViewModel-owned looping with the tested bulk use case.

**Acceptance criteria:**
- [ ] One refresh call receives the current card snapshot.
- [ ] First partial failure appears through the existing snackbar.
- [ ] Loading resets after success, failure, or cancellation.

**Verification:**
- [x] `gradle testDebugUnitTest --tests "*CardsViewModelTest" --stacktrace`

**Dependencies:** Task 6
**Estimated scope:** Small

## Task 10: Add Optional Account Controls

**Description:** Add the account action and connect/sync/disconnect dialog to
the cards screen.

**Acceptance criteria:**
- [ ] Copy clearly says login is optional and happens on the official site.
- [ ] Connected copy limits synchronization to linked cards/principal balance.
- [ ] Every icon action has a state-appropriate Spanish content description.

**Verification:**
- [x] `gradle assembleDebug --stacktrace`
- [ ] Manual font-scale and accessibility check.

**Dependencies:** Tasks 7 and 9
**Estimated scope:** Small

## Task 11: Wire The End-To-End Flow

**Description:** Connect the account client, repositories, use cases, ViewModels,
manual navigation, and unchanged anonymous widget path.

**Acceptance criteria:**
- [ ] Login success returns to cards and performs first sync.
- [ ] App startup checks an existing session once.
- [ ] Widget refresh never initializes or depends on the account WebView.

**Verification:**
- [x] `gradle test --stacktrace`
- [x] `gradle assembleDebug --stacktrace`

**Dependencies:** Tasks 5-10
**Estimated scope:** Medium

## Checkpoint: User Experience

- [ ] Tasks 8-11 are reviewed.
- [ ] Login, sync, expiry, fallback, and disconnect work on a real device.
- [ ] Anonymous card registration and widget refresh still work.

## Task 12: Update Privacy And Data Safety Disclosures

**Description:** Make in-app, public-policy, and Google Play disclosures match
the optional account behavior.

**Acceptance criteria:**
- [ ] Privacy Policy and Data Safety match actual account behavior.
- [ ] No obsolete "no accounts" or "100% local" claim remains.
- [ ] Credentials, session cookies, and principal card sync are described accurately.

**Verification:**
- [x] `gradle testDebugUnitTest --tests "*PrivacyPolicyContentTest" --stacktrace`

**Dependencies:** Task 11
**Estimated scope:** Medium

## Task 13: Complete Documentation And Release Gates

**Description:** Finalize README and ADR, execute all automated/manual checks,
and prepare a human-reviewed feature branch.

**Acceptance criteria:**
- [ ] README and accepted ADR match implemented behavior.
- [ ] Complete test/build, real-device matrix, terms decision, and secret scan pass.
- [ ] The human reviews and approves the complete diff before push.

**Verification:**
- [x] `gradle test --stacktrace`
- [x] `gradle assembleDebug --stacktrace`
- [ ] `git diff --check`
- [ ] Human reviews the complete `git diff`.

**Dependencies:** Task 12
**Estimated scope:** Small

## Checkpoint: Ready To Push

- [ ] The exposed GitHub token is revoked.
- [ ] A replacement credential is provisioned outside chat with minimum scope.
- [ ] All automated and manual checks pass.
- [ ] The human explicitly approves the complete diff.
- [ ] Push only `feature/redbus-account-session`; do not push directly to `main`.

## Task 14: Turnstile Token Resolver (Captcha-Free Anonymous Queries)

**Goal:** resolve the captcha silently via a Cloudflare Turnstile token rendered
in an offscreen WebView — works for every card, no account required.

**Acceptance criteria:**
- [x] `GET /rest/getTurnstileKeySite` returns the plaintext sitekey.
- [x] `resultadoSaldo` is called with `X-Use-New-Captcha: true` + token.
- [x] Token path success never requests `captcha.png` nor runs OCR.
- [x] Token failure/error 1/network failure falls back to OCR, then manual.
- [x] Manual captcha bypasses the provider entirely.
- [x] Widget path never creates the WebView provider.
- [x] WebView destroyed on completion/cancellation; TLS errors cancel.

**Verification:**
- [ ] `gradle testDebugUnitTest --tests "*CardRepositoryTest" --stacktrace`
- [ ] `gradle assembleDebug --stacktrace`
- [ ] Real device: turnstile token query works end-to-end.

