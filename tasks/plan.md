# Implementation Plan: Optional RedBus Account

## Canonical Documents

- Design specification:
  `docs/superpowers/specs/2026-10-05-redbus-optional-account-design.md`
- Step-by-step implementation plan:
  `docs/superpowers/plans/2026-10-05-redbus-optional-account.md`
- Architecture decision:
  `docs/decisions/0001-redbus-webview-session.md`
- Execution checklist: `tasks/todo.md`

The canonical implementation plan contains exact interfaces, tests, commands,
and commit boundaries. This file is the compact project-level index.

## Overview

Build an optional RedBus connection for Android. Users authenticate on the
official site in a hardened `WebView`; a dedicated client uses that authorized
session to retrieve linked cards and principal monetary balances. The existing
anonymous captcha/OCR flow remains the default fallback and the widget remains
anonymous in this version.

## Architecture Decisions

- Use the official web login rather than receiving credentials natively.
- Keep anonymous and authenticated cookies in separate HTTP clients.
- Confirm authentication server-side through `usuarioLogeado`, never from a
  URL or page event alone.
- Select only the `Principal (Dinero)` wallet.
- Reuse one `CardRepository.applyBalanceUpdate` path for Room and history.
- Do not add dependencies, a Room migration, native account registration,
  transaction history, or authenticated widget refresh.
- Clear session cookies on disconnect even if remote logout fails.

## Task List

### Phase 0: Contract Gate

1. Capture authorized responses and create sanitized fixtures.

### Phase 1: Foundation

2. Define account domain models and parse the RedBus contract.
3. Bridge WebView cookies into an isolated authenticated client.
4. Implement the account session state machine.
5. Unify local balance persistence.

### Phase 2: Domain Behavior

6. Prefer account balances for individual refreshes.
7. Synchronize linked cards and refresh all efficiently.
8. Model account UI state.

### Phase 3: User Experience

9. Build a hardened official login WebView.
10. Route bulk refresh through the coordinator.
11. Add optional account controls.
12. Wire the account flow into the application.

### Phase 4: Release Gate

13. Update privacy and Data Safety disclosures.
14. Complete README, ADR status, verification, and release evidence.

## Dependency Order

```text
sanitized-contract
        |
        +--> DTO/domain contracts --> account repository --> account ViewModel
        |
        +--> cookie bridge/client ---+
        |
        +--> shared persistence --> individual refresh
                               \--> linked-card sync --> bulk refresh

account ViewModel + secure WebView + bulk refresh
                         |
                         v
                   application wiring
                         |
                         v
               privacy and release gates
```

## Checkpoints

- **Contract:** fixtures are real-shape, anonymized, and human-approved.
- **Foundation:** cookie boundaries, session state, and persistence tests pass.
- **Domain:** authenticated preference and anonymous fallback tests pass.
- **UI:** Turnstile works on a real device and the widget remains functional.
- **Release:** full tests/build, privacy updates, secret scan, terms decision,
  and human diff approval are complete.

## Risks And Mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| Undocumented API changes | High | Fixture tests and anonymous fallback |
| Credential or cookie exposure | High | Official WebView, isolated client, no logs |
| Wrong balance selection | High | Exact principal-wallet mapping |
| Session expiration | Medium | Controlled disconnect state and fallback |
| RedBus distribution terms | High | Explicit authorization/release gate |
| Compromised GitHub credential | High | Revoke exposed token and rotate securely |

## Open Gates

- The exposed GitHub token must be revoked; it will not be used.
- A successful account/card response must be captured and anonymized by an
  authorized account holder before DTO implementation.
- Logout method and behavior must be confirmed.
- Provider authorization/terms must be reviewed before public distribution.
- The human must approve the complete implementation diff before push.
