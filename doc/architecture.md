# Architecture

## Context

Spenvo is a native Android expense-tracking app (family / team) written in
Kotlin with Jetpack Compose. It reimplements the functionality of the legacy app
`act02-app_gastos` with Clean Architecture, security from the first line and
tests before committing.

## Principles

1. **Clean Architecture** with one-directional dependencies (UI → domain → data).
2. **State-driven UI** with Navigation 3; navigation is a serializable list.
3. **Local read, remote write**: the UI reads from Room (Flow); writes go
   to Firestore with native offline cache. No homegrown outbox.
4. **OWASP 2025 security** by design (not as a patch): SQLCipher, App Check,
   deny-by-default rules, secrets out of the repo, dependency locking.
5. **Additive i18n**: Spanish default, English keys, translations via `values-XX`.

## Layers and modules

```
:app (root NavDisplay, root DI, Application)
 :core:designsystem (theme, UI components)
 :feature:movimientos (expenses + income)
 :core:domain (models, use cases, contracts — pure Kotlin)
 :core:security (Keystore-backed SQLCipher passphrase)
 :core:data (Room + SQLCipher, DataStore, remote repos, mappers)
```

Dependencies:
- `:app` → `:core:domain`, `:core:data`, `:core:designsystem`, `:feature:movimientos`.
- `:feature:movimientos` → `:core:domain`, `:core:designsystem`.
- `:core:data` → `:core:domain`, `:core:security`.
- `:core:security` → Android Keystore only.
- `:core:domain` has no Android dependencies.
- `:core:designsystem` has no feature or data dependencies.

## Data architecture (approved in plan v3)

### Read flow
UI ← Flow ← Room ← (reconciliation) ← Firestore.

### Write flow
1. The user edits → Room updates optimistically (immediate).
2. The write goes to Firestore (native offline cache; persists offline).
3. When confirmed against the backend, it is reconciled in Room.

### Change listening
- Firestore snapshot listeners **only** in active shared scopes
  (attach when opening the plan screen, detach when leaving). Never global.
- On-demand refresh + pull-to-refresh. No complex TTL.

### Conflicts (honest LWW)
- Every synced entity carries `editedBy` + `editedAt` (server-set) + `deletedAt`.
- Real conflict (same field, concurrent edits) → visible in UI, user's decision.

## Key recorded decisions

| Decision | Why | Where |
|---|---|---|
| Room + SQLCipher from M1 | financial data encrypted at rest | M1 |
| No outbox/WorkManager | Firestore already provides offline cache; outbox = bug source | plan v3 |
| LWW with visible conflict | honest and auditable sync | plan v3 |
| Navigation 3 (not Nav2) | state-driven, native List-Detail with Adaptive | plan v3 |
| App Check in M2 | only legitimate clients against the backend | plan v3 |
| Min SDK 26 | coverage/API balance | plan v3 |
| es-default strings + blocking lint | additive i18n from day 1 | plan v3 |

## Known risks and debt

- **Wireless ADB**: the on-device install hung during M0. The M3 e2e smoke will
  resume it (local emulator preferred).
- **Firebase**: `google-services.json` and the plugin are added in M2 once the
  Firebase project exists.
- **Rules/indexes**: drafts in M0; finalization + rules tests in M3.