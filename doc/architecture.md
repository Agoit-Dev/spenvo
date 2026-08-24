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
 :feature:cuenta (account creation / email+password linking)
 :feature:planes (plans, shared access, invitations)
 :feature:movimientos (expenses + income, plan-scoped)
 :feature:categorias (categories: list, create/edit, delete, plan-scoped)
 :core:domain (models, use cases, contracts — pure Kotlin)
 :core:security (Keystore-backed SQLCipher passphrase)
 :core:data (Room + SQLCipher, DataStore, Firestore repos + sync, mappers)
```

Dependencies:
- `:app` → `:core:domain`, `:core:data`, `:core:designsystem`,
  `:feature:cuenta`, `:feature:planes`, `:feature:movimientos`, `:feature:categorias`.
- `:feature:cuenta` → `:core:domain`, `:core:designsystem`, `:core:data`.
- `:feature:planes` → `:core:domain`, `:core:designsystem`, `:core:data`
  (auth/session + plan repositories, sync).
- `:feature:movimientos` → `:core:domain`, `:core:designsystem`, `:core:data`.
- `:feature:categorias` → `:core:domain`, `:core:designsystem`, `:core:data`
  (category repository, use cases, sync).
- `:core:data` → `:core:domain`, `:core:security`, Firebase (Auth, Firestore, App Check).
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
- Conflict detection (M5 Slice 4): each repository's optimistic write registers
  an unconfirmed pending edit in `EdicionesPendientes` (`:core:domain`, keyed
  `"$coleccion:$id"`) at the point it already reads `previo`. Each
  sincronizador's snapshot listener consults it per document; a conflict is
  flagged only when the incoming `editedAt` is newer than the pending edit's
  known base AND `editedBy` differs AND a pending edit exists — a plain remote
  update fires nothing. Flagged documents are held back from Room and queued
  in `ConflictosPendientes` (`StateFlow<Map<String, ConflictoEdicion>>`) for
  per-record UI resolution (Slice 5b), never an app-wide interrupt. **Accepted
  debt**: both registries are in-memory only (process lifetime), by design —
  not a homegrown outbox. If the process dies between the optimistic Room
  write and the Firestore echo, the pending marker is lost and the remote
  version silently wins on the next sync.
- Conflict resolution UI (M5 Slice 5b, movimientos only): a row-level badge
  (`MovimientoItem`) marks any id present in `ConflictosPendientes.conflictos`;
  the blocking `ConflictoDialog` (`:core:designsystem`, entity-agnostic —
  no `:core:domain` or `res/` dependency) opens only when the user taps that
  row again, never as an app-wide interrupt. `feature/movimientos` maps the
  domain `ConflictoEdicion`/`SnapshotConflicto` to the dialog's plain-string UI
  model and resolves `R.string.conflict_field_*` labels before calling in
  (`ConflictoMovimientoHost.kt`). Resolution: "usar la mía"/"restaurar mi
  edición" re-issue `actualizar()` with the local version (fresh
  `editedBy`/`editedAt`); "usar la suya"/"mantener borrado" persist the
  Firestore document straight into Room via
  `MovimientoRepository.aplicar{Gasto,Ingreso}Remoto(id)`, bypassing the
  edit-attribution use case since the remote write is already correctly
  attributed and was only held back from the last sync. Every path ends by
  calling `ConflictosPendientes.resolver(key)`.

## Key recorded decisions

| Decision | Why | Where |
|---|---|---|
| Room + SQLCipher from M1 | financial data encrypted at rest | M1 |
| No outbox/WorkManager | Firestore already provides offline cache; outbox = bug source | plan v3 |
| LWW with visible conflict | honest and auditable sync | plan v3 |
| Navigation 3 (not Nav2) | state-driven, native List-Detail with Adaptive | plan v3 |
| App Check in M2 | only legitimate clients against the backend | plan v3 |
| Guest-first anonymous auth | app opens directly; account created on demand (M3 links it) | plan v3 (user decision) |
| AndroidX Core SplashScreen, no artificial delay | native cold-start branding without blocking the first frame; `installSplashScreen()` runs before `super.onCreate()` in `MainActivity`, `Theme.Spenvo.Starting` hands off to `Theme.Spenvo` the moment content draws — no timers, no extra Compose screen | PR #23 |
| Email/password links the anonymous UID | `linkWithCredential` preserves local data, no merge | M3 |
| Account + plans in separate features | `:feature:cuenta` + `:feature:planes`; movimientos stays plan-scoped | M3 (user decision) |
| Auth/session repo in `:core:data` | keeps a single data layer for remote sources | M2 |
| Firestore rules tested in a Node subproject | `rules-tests/` validates the matrix with the Emulator; app stack stays Kotlin | M3 |
| Min SDK 26 | coverage/API balance | plan v3 |
| es-default strings + blocking lint | additive i18n from day 1 | plan v3 |
| Optimistic Room-first writes with rollback | Room updates immediately for a responsive UI; a permanent Firestore failure restores the prior local snapshot instead of leaving Room ahead of the backend | M4 |
| Per-plan live snapshot listeners for plans/categories | `PlanSincronizador`/`CategoriaSincronizador`, active-scope only (open plan/category screen) | M4 |
| Deterministic ids for default category seeding | `planId:clave` makes `SembrarCategoriasPorDefectoUseCase` idempotent and race-safe under a single batched write | M4 |
| Local `ListDetailPaneScaffold` split (not nav-graph `SceneStrategy`) for Movimientos | `MovimientosScreen` gates on `calculatePaneScaffoldDirective(currentWindowAdaptiveInfoV2()).maxHorizontalPartitions`, driven by the existing `formulario` state, instead of pairing two `NavKey`s (list/detail) on the back stack via `rememberListDetailSceneStrategy<NavKey>()`. Verified: on compact, `NavDisplay` only composes what the active `SceneStrategy` returns, so `ListDetailSceneStrategy`'s single-pane fallback would replace the list with a full-screen push instead of preserving it behind a bottom sheet — a real regression against "keep today's exact compact UX." `rememberListDetailSceneStrategy<NavKey>()` is still wired into `MainActivity`'s `NavDisplay` as forward-compatible plumbing for future cross-route list/detail pairs, but is a no-op today (zero routes carry pane metadata). Both patterns now coexist: nav-graph `SceneStrategy` for cross-route pairs (inert), local `ListDetailPaneScaffold` for single-route panes (the real, working implementation) | M6 Slice B |
| Logout re-anonymous re-entry lives in `AuthRepository.cerrarSesion()` | `cerrarSesion()` calls `auth.signOut()` immediately followed by `iniciarSesionAnonima()` at the repository level, so every caller (currently `CuentaViewModel.logout()`) gets guest-first re-entry for free without repeating the two-call sequence itself | M7 Slice A1/A2 |

## Known risks and debt

- **Rules/indexes**: finalized in M3 and validated against the Emulator (14
  tests). **Deploy `firebase deploy --only firestore:rules` to go live**; until
  then the plan/access writes fail with permission-denied against the real project.
- **Firebase console setup**: the Anonymous sign-in provider and the App Check
  API must be enabled (see `CHANGELOG` 0.3.0). The App Check debug token is
  registered for debug builds.
- **Firestore Emulator (v1.22) quirks**: `get()` with a concatenated path must be
  written directly in an `allow` (it fails inside a function), and a `get()` on a
  missing doc returns `null` (check `!= null`, not `.exists`). See
  `rules-tests/` for the working pattern.
- **Account registration is email/password only** in M3; Google Sign-In is
  deferred a second time in M7 (out of scope per the `user-profile` spec; no
  committed milestone yet).
- **No ViewModel/Compose UI tests for `:feature:categorias`, `:feature:planes`,
  `:feature:movimientos`, `:feature:cuenta`** (M4): `CategoriasViewModelTest` is
  the first ViewModel test in the project (plain JUnit + hand-written fakes,
  same convention as `:core:domain`); no Compose screen/instrumented tests yet.
- **No color per category** (M4): matches the legacy `act02-app_gastos`
  reference, which only has name/type/icon; a future milestone could add it.