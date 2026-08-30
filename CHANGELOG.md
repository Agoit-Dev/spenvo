# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Fixed

- `PlanScaffold`'s bottom-nav tab switching disposed the non-selected tab's whole composable subtree
  on every switch, which unregistered its `rememberSaveable` state — e.g. Movimientos' search text,
  selected type filter, and list scroll position were all lost when navigating away and back. Fixed
  by wrapping each tab's content in a `rememberSaveableStateHolder()` keyed per tab (the same
  primitive Navigation 3's own `rememberSaveableStateHolderNavEntryDecorator` uses), which snapshots
  and restores `rememberSaveable` state across the tab's dispose/recompose cycle. Also removed the
  now-unnecessary `@Suppress("LongParameterList")` on `PlanScaffold` (5 params, under detekt's
  default threshold of 6).
- Home screen: a failed save (e.g. `PERMISSION_DENIED`) had no error feedback at all and left the
  shared `MovimientosViewModel`'s error flag unconsumed, so it would leak into the Movimientos tab
  as a stale snackbar later. Home now wraps its content in a `Scaffold`+`SnackbarHost` and mirrors
  `MovimientosScreen`'s `EfectosMovimientos` shape: it attaches `sincronizar(planId)` on open and
  shows/consumes the error the same way. Also replaced the hand-rolled `formatearMontoPlano` (raw
  string concatenation, appended the ISO currency code) with a `NumberFormat.getCurrencyInstance`
  based formatter per AGENTS.md's i18n rule, added `testTag`s so the income/expense/balance figures
  are individually addressable, and gave the quick-action circular buttons an explicit
  `secondaryContainer` color — they previously had no container color and were invisible against
  the screen background.
- Center the adaptive launcher icon artwork in both the standard and round
  launcher masks.
- Movimientos edit modal: the type (Gasto/Ingreso) could be changed on an existing movimiento via
  the type chips, even though `Gasto`/`Ingreso` are separate domain types with their own storage —
  the chips are now locked (shown, disabled, still showing the real type as selected) whenever
  editing an existing movimiento. The category selector also no longer resets to the plan's first
  category on open: the reset guard was firing while the categories `StateFlow` was still at its
  transient empty initial value, before the real list loaded, silently losing the movimiento's
  actual category if the user saved without touching the selector. Switching type while creating a
  new movimiento now correctly clears the previous type's category selection too, closing a related
  path where a stale category could otherwise be saved under the wrong type.
- Movimientos edit modal in the expanded (tablet) list-detail layout: switching from editing one
  movimiento to viewing another used to carry over the previous movimiento's edit mode and
  unsaved field values, since the form's local state wasn't tied to which movimiento it was
  showing. The form is now keyed per movimiento id, so switching always starts fresh. Also fixed
  Cancelar silently restoring a movimiento's stored category even when that category had since
  been deleted — it was blindly reverting to the original id instead of re-checking whether that
  id still exists in the loaded list.
- Planes screen briefly showed "no plans yet" on cold start before the real plan list (or a
  pending invitation) loaded in, because `planes`/`invitacionesPendientes` started at a synthetic
  empty-list `StateFlow` value indistinguishable from "genuinely has none". Both flows now
  distinguish "not loaded yet" from "loaded and empty" internally (`PlanesViewModel.cargandoLista`),
  and the list area shows a centered, accessibly-labeled spinner while loading — covering both the
  anonymous-session-establishment window and the initial Room query — instead of flashing the
  empty state. The top bar and "create plan" FAB are unaffected.

### Changed

- Movimientos edit modal now opens read-only for an existing movimiento, with an explicit "Editar"
  action to enable fields; Cancelar reverts unsaved changes and returns to the read-only view
  instead of dismissing the sheet; Eliminar is now only reachable after Editar. The category
  selector now visibly dims while read-only, matching the amount/description fields and type
  chips.

### Added

- Usuario entity + nombreUsuario, slice 1/10 (foundation only, no user-visible behavior yet): the
  `Usuario` domain model and Room entity gain a `nombreUsuario` field (the unique public handle
  that will replace raw UID display in Miembros), and `nombre`/`email` become nullable to
  correctly represent an anonymous session, which has neither. Room migration 2→3 backfills
  existing rows. Nothing writes or reads `nombreUsuario` yet — that lands in the following slices.
- Usuario entity + nombreUsuario, slice 2/10 (foundation only, still nothing wired to real
  Firestore): adds `GenerarNombreUsuarioUnicoUseCase`, which produces a random
  adjective+noun+number candidate (e.g. `RapidoZorro42`) and retries against a bounded budget of
  8 attempts (widening the numeric range after the first 5 collisions) until the repository
  confirms a transactional reservation, failing loudly if none succeeds. Also adds the
  `UsuarioRepository` domain interface (`obtener`, `obtenerVarios`, `intentarReservarNombreUsuario`,
  `crear`, `actualizar`, `renombrar`, `registrarIndiceEmail`, `resolverPorNombreUsuario`,
  `resolverPorEmail`) with no implementation yet, and `normalizarNombreUsuario`/`normalizarEmail`
  helpers for case/whitespace-insensitive lookups.
- Usuario entity + nombreUsuario, slice 3/10 (still no caller wired in): implements
  `UsuarioRepository` against real Firestore (`FirebaseUsuarioRepository`, `usuarios`/
  `nombres_usuario`/`emails_usuario` collections) plus the `UsuarioDto` mapper (`uid` field name,
  matching the existing `usuarios/{usuarioId}` security rule's `request.resource.data.uid ==
  usuarioId` check). `intentarReservarNombreUsuario` and `renombrar` use a Firestore transaction to
  keep the `nombres_usuario` reservation index and the user document consistent. Wired via
  `UsuarioModule`/`UsuarioUseCaseModule` (Hilt). No unit test for the repository itself — same
  pattern as `FirebasePlanFinancieroRepository`/`FirebaseAccesoPlanRepository`, which talk to real
  Firestore and are only covered by DTO tests plus the emulator-based `rules-tests`.
- Usuario entity + nombreUsuario, slice 4/10 (still no UI showing it, but the first slice where
  `nombreUsuario` actually gets created/persisted through real app flows): adds
  `AsegurarUsuarioUseCase`, wired into both `PlanesViewModel`'s anonymous-session bootstrap and
  `CuentaViewModel.registrar()`. `paraSesionAnonima` is a best-effort call made once the anonymous
  session resolves a real uid — creates the `Usuario` doc with a freshly generated `nombreUsuario`
  if one doesn't exist yet, no-ops otherwise. `paraVincularEmail` runs right after linking an
  email/password credential — updates `nombre`/`email` on the existing doc (preserving its
  `nombreUsuario`) and registers the `emails_usuario` index entry; it also has a defensive fallback
  that creates the doc if the anonymous bootstrap was somehow skipped.
- Usuario entity + nombreUsuario, slice 5/10 (the first slice with real, user-visible UI): a
  registered user can now see and edit their `nombreUsuario` from the account profile screen.
  Adds `RenombrarUsuarioUseCase`, which passes the raw display-form handles straight through to
  `UsuarioRepository.renombrar`'s transactional release-and-reserve; `FirebaseUsuarioRepository`
  normalizes locally only for the `nombres_usuario` index doc IDs, keeping the
  `usuarios/{uid}.nombreUsuario` display field in its original casing (e.g. `GatoAzul42`, never
  `gatoazul42`), per the design doc. Wired via `UsuarioUseCaseModule`. `CuentaViewModel` now
  injects `UsuarioRepository` directly (consistent with `AuthRepository` already being a direct
  constructor param there) and loads the signed-in user's `nombreUsuario` once the session
  resolves to a real, non-anonymous uid, resetting it when the session goes back to anonymous
  (e.g. after logout); a failed load no longer kills the collector, so a later session emission
  gets another chance. `PerfilEstado` gains `nombreUsuario`/`nombreUsuarioError`, and
  `editarNombreUsuario(nuevo)` rejects blank/whitespace-only input without calling the use case,
  catches any use-case failure instead of crashing, and otherwise calls the use case and surfaces
  an "already taken" error without touching the previous value on failure. `CuentaScreen`'s
  profile view gets a new username field + Guardar button (same `OutlinedTextField` +
  inline-error pattern as `RegistroForm`, Guardar also disabled while the trimmed input is blank),
  extracted into its own `CampoNombreUsuario` composable to keep `PerfilContenido` under detekt's
  `LongMethod` threshold. The profile `Column` is now vertically scrollable to accommodate the
  extra field on small screens.
- Home screen: opening a plan now lands on a per-plan dashboard (`HomeScreen`/`HomeViewModel`,
  `:feature:movimientos`) instead of going straight to the Movimientos list — cumulative balance
  across all of the plan's movimientos (new `ObservarBalanceAcumuladoPlanUseCase`), this month's
  income/expense split, and quick "Nuevo Gasto"/"Nuevo Ingreso" actions opening the existing
  `MovimientoFormSheet`. `ResumenMensualPlan` (via `ObservarResumenMensualPlanUseCase`) now exposes
  the month's income and expense totals separately — `netoDelMes` is derived from them rather than
  stored redundantly.
- `:app` gains a bottom-navigation shell (`PlanScaffold`) hosting Home/Movimientos/Categorías/
  Miembros as tabs of a single plan, replacing the previous separate `MovimientosRoute`/
  `MiembrosRoute`/`CategoriasRoute` nav-3 entries with one `PlanRoute(planId)` entry. Home and
  Movimientos share the existing `MovimientosViewModel` instance (hoisted once per `PlanRoute`
  entry via `hiltViewModel()`), matching the sync/error-consumption coupling already documented
  between those two screens. Each tab's own inner `Scaffold` now has its window insets consumed
  via `Modifier.consumeWindowInsets(innerPadding)` on `PlanScaffold`'s content `Box`, avoiding
  double-counted status-bar padding from the outer and inner `Scaffold`s both applying
  `WindowInsets.systemBars`. `:app` gained its first Compose UI test infrastructure (Robolectric +
  `ui-test-junit4`, `libs.androidx.compose.material.icons.{core,extended}` for the tab icons) and
  its first test, `PlanScaffoldTest`. New `nav_home`/`nav_movements`/`nav_categories`/
  `nav_members` strings; `:app` also gains its first `values-en/strings.xml` (only `app_name`
  stays untranslated, marked `tools:ignore="MissingTranslation"` as a proper noun).
- `:core:designsystem` gains a shared `ConfirmarEliminarDialog`, de-duplicating two nearly
  identical private delete-confirmation `AlertDialog`s that `:feature:categorias` and
  `:feature:movimientos` each had implemented on their own (behavior unchanged — same dialog, same
  strings per feature, just one shared component instead of two copies). `:feature:categorias` also
  gained Compose UI test infrastructure (Robolectric + `ui-test-junit4`, mirroring the other
  feature modules) and its first screen-level Compose test.
- M7 Slice A2 (Profile screen — first user-visible piece of the avatar
  feature): `:feature:cuenta`'s `CuentaScreen` now branches on
  `Sesion.esAnonima` — an anonymous session still sees the unchanged
  registration form, while a linked account sees a new profile view with a
  circular avatar (new `AvatarConBadge` in `:core:designsystem`, the first
  real Coil usage in this codebase, with a small edit-icon badge overlay),
  display name, email, an "Account info" card, and a Logout button. Tapping
  the avatar badge opens `ActivityResultContracts.PickVisualMedia`; the
  screen reads the picked image's bytes/content-type via `ContentResolver`
  and calls the new `CuentaViewModel.subirAvatar(bytes, contentType)`, which
  uploads through the existing `SubirAvatarUseCase` (M7 Slice A1) and persists
  the resulting URL via `AuthRepository.actualizarPerfil(photoUrl = ...)`.
  Logout calls `AuthRepository.cerrarSesion()`, which already re-establishes
  an anonymous session (guest-first re-entry, decided in Slice A1).
  `:feature:cuenta` gained its first unit/Compose test setup (JUnit,
  coroutines-test, Robolectric, `ui-test-junit4`).
- Sample plan seeding: the first time a user has zero plans, `SembrarPlanEjemploUseCase`
  creates one demo plan ("Gastos del hogar", EUR) with realistic Spain-locale
  sample expenses/income (Mercadona, gasolina, factura de la luz, nómina,
  etc.) so a fresh install shows a populated demo instead of an empty state.
  Idempotent — does nothing once the user has any plan, mirroring
  `SembrarCategoriasPorDefectoUseCase`'s existing guard.
- M7 Slice A1 (Storage foundation, backend-only — foundation for the upcoming
  Profile avatar feature, no user-visible UI yet): Firebase Storage is wired
  into the app for the first time, with a new `storage.rules` restricting
  `avatars/{uid}/avatar.jpg` to its owner (authenticated, path-scoped,
  `image/*` content-type allowlist, 5MB size limit), covered by a new
  `rules-tests/storage-rules.test.mjs` emulator suite (owner read/write, cross-uid
  read/write denied, oversized/non-image denied, unauthenticated denied) alongside
  the existing `firestore.rules` matrix. New `StorageRepository`/`SubirAvatarUseCase`
  (`:core:domain`) and `FirebaseStorageRepository` (`:core:data`, bridging the
  Task-based Storage SDK via `suspendCancellableCoroutine`, mirroring
  `FirebaseAuthRepository`'s existing pattern) upload to a fixed
  `avatars/{uid}/avatar.jpg` path (overwrite on re-upload) and return the
  download URL. `AuthRepository` gains `actualizarPerfil(nombre, photoUrl)`
  (persisted via `UserProfileChangeRequest`, mirroring `vincularEmail()`) and
  `cerrarSesion()` (signs out, then immediately re-establishes an anonymous
  session — guest-first re-entry). `Sesion` gains `photoUrl`, mapped from
  `FirebaseUser.photoUrl`. Nothing in the UI calls these yet — that lands in a
  later Profile screen slice.

## [0.7.0] - 2026-08-23

### Added (M6 — Home dashboard + Adaptive List-Detail)

- Added branded adaptive and legacy launcher icons and an AndroidX SplashScreen-compatible launch screen.
- M6 Slice A (Home dashboard summary): each plan card on `PlanesScreen` now shows
  a reactive current-month net balance (income minus expenses), computed by the
  new `ObservarResumenMensualPlanUseCase` (`:core:domain`) and combined per-plan
  in `PlanesViewModel.resumenesPorPlan`. Replaces the dead, unwired
  `ListarMovimientosPorMesUseCase` (deleted along with its test). `:feature:planes`
  gained its first unit/Compose test setup (JUnit, coroutines-test, Robolectric,
  `ui-test-junit4`). Extracted a `PlanSincronizacion` interface for
  `PlanSincronizador` (mirroring the existing `MovimientoSincronizacion`/
  `CategoriaSincronizacion` pattern) so `PlanesViewModel` is unit-testable
  without a real `FirebaseFirestore` instance — a small, behavior-preserving
  addition beyond the original slice design, needed to write the required
  `PlanesViewModelTest`.
- M6 Slice B (Adaptive List-Detail in Movimientos): `MovimientosScreen` adopts
  Adaptive List-Detail — the first real usage in this codebase. On expanded/wide
  layouts (Material's Expanded width breakpoint, `maxHorizontalPartitions > 1`),
  the transaction list stays the list pane and `MovimientoFormSheet` (edit/delete)
  is promoted into an inline detail pane via `ListDetailPaneScaffold`
  (`androidx.compose.material3.adaptive.layout`) instead of a full-screen bottom
  sheet. Compact/medium layouts are byte-identical to before M6: the same
  `MovimientosScaffold` + `MovimientoFormularioSheet` as a `ModalBottomSheet`.
  `ConflictoMovimientoDialogHost` (M5 Slice 5b) stays a single top-level call,
  layout-independent, per design Decision 4. `rememberListDetailSceneStrategy<NavKey>()`
  is wired into `MainActivity`'s `NavDisplay` as forward-compatible, currently
  inert plumbing for future cross-route panes (design Decision 3 — see
  `doc/architecture.md`). `MovimientoFormSheet.kt` now exposes
  `MovimientoFormEstadoYContenido` (the pure form state + content, no
  `ModalBottomSheet` chrome) so both the compact sheet and the expanded pane
  reuse the identical fields/logic. `:feature:movimientos` gained its first
  Compose UI test setup (Robolectric + `ui-test-junit4`), asserting the compact
  vs. expanded rendering split and that the conflict dialog fires identically in
  both. This closes the M6 milestone (Slices A + B).

## [0.6.0] - 2026-08-23

### Added (M5 — Movimientos completo: edit/delete, attribution, conflict resolution)

- Gasto/Ingreso update and soft-delete: `Actualizar/Eliminar{Gasto,Ingreso}UseCase`,
  new `MovimientoRepository` update/delete methods, `FirebaseMovimientoRepository`
  get-before-upsert rollback on permanent Firestore failure (mirrors categories).
  Bugfix: `GastoDao`/`IngresoDao`'s `observeByPlan`/`observeByPlanAndRange` now
  exclude soft-deleted rows (`deletedAt IS NULL`), previously unfiltered.
- `editedBy`/`editedAt` attribution stamped client-side (`Instant.now()`) on
  every Gasto/Ingreso/Categoria/PlanFinanciero mutation, enforced server-side
  by updated `firestore.rules` across all four entity families: a spoofed
  `editedBy`, a mutated ownership/creation field, or any direct Firestore
  `delete` are all denied. `rules-tests/rules.test.mjs` grew from 14 to 32
  emulator tests covering the full allow/deny matrix.
- Conflict detection: in-memory `EdicionesPendientes`/`ConflictosPendientes`
  registries (`:core:domain/sync`) track unconfirmed local edits and flag a
  genuine concurrent-edit conflict — an incoming snapshot with a newer
  `editedAt` from a different editor while a local edit is still pending,
  including delete-vs-edit — without special-casing delete. Wired into all
  three sincronizadores (Movimiento/Categoria/Plan). Accepted debt (documented
  in `doc/architecture.md`): the registries are in-memory/process-lifetime
  only, not a persisted outbox; a process death between an optimistic write
  and its Firestore echo loses the pending marker and the remote version
  silently wins on the next sync.
- Conflict resolution UI: a row-level badge on conflicted Gasto/Ingreso
  entries; tapping one opens a blocking `ConflictoDialog` (`:core:designsystem`,
  new `conflict` package, entity-agnostic with no `:core:domain`/`res/`
  dependency) showing both versions side by side, differing fields
  emphasised. Resolution: "usar la mía"/"restaurar mi edición" re-issues the
  local edit; "usar la suya"/"mantener borrado" persists the remote document
  straight into Room via the new `Aplicar{Gasto,Ingreso}RemotoUseCase`,
  bypassing the edit-attribution use case since that write is already
  correctly attributed. `:core:designsystem` gained its first Compose UI test
  setup (Robolectric + `ui-test-junit4`).
- Edit/delete entry points in `:feature:movimientos` (tap a row to edit, a
  confirm-delete dialog), mirroring `:feature:categorias`' existing pattern.

## [0.5.0] - 2026-08-22

### Added (M4 — Categories)

- Categories domain and Firestore data layer in `:core:domain`/`:core:data`:
  `CategoriaRepository`, use cases (`CrearCategoria`, `ActualizarCategoria`,
  `EliminarCategoria`, `ObservarCategorias`, `ObservarCategoriasPorTipo`,
  `SembrarCategoriasPorDefecto`), `FirebaseCategoriaRepository` and a
  `CategoriaSincronizador` (snapshot listener on the plan's categories → Room,
  active-scope only, AGENTS rule 3).
- Optimistic Room-first writes with rollback on permanent Firestore failure for
  both categories and plans (`FirebaseCategoriaRepository`,
  `FirebasePlanFinancieroRepository`): Room updates immediately, and any remote
  failure restores the prior local snapshot before rethrowing. `PlanSincronizador`
  now also attaches a live per-plan snapshot listener instead of a one-shot read.
- Default category seeding is idempotent: deterministic `planId:clave` ids and a
  single batched `crearCategorias` write (`SembrarCategoriasPorDefectoUseCase`).
- `:feature:categorias` (new): category list (grid, filtered by Ingreso/Gasto),
  create/edit in a bottom sheet (name, type, icon picker), delete with a
  confirmation dialog. Reachable from `MovimientosScreen`'s top bar
  (`CategoriasRoute(planId)`). UX referenced from the legacy
  `act02-app_gastos` category screens.
- New dependency added to the approved baseline:
  `androidx.compose.material:material-icons-extended` (Compose BOM-managed),
  needed for a per-category icon set richer than `material-icons-core`'s ~50
  icons. Approved by the user and added to `AGENTS.md`'s "Stack (approved
  baseline)" table.

## [0.4.2] - 2026-08-21

### Changed (M3 close)

- Verified all M3 gates: `:app:assembleDebug`, `testDebugUnitTest`, `lintDebug`
  and `detekt` pass; Firestore rules validated against the Emulator (`spenvo-dev`,
  14/14 tests).
- Deployed the finalized `firestore.rules` and `firestore.indexes.json` to the
  production project (`spenvo-6d10a`), making the deny-by-default role-based rules
  live. `README.md` and `doc/security/owasp.md` updated.
- Remaining M3 manual step: confirm the Anonymous + Email/Password sign-in
  providers and App Check enforcement in the Firebase console.

## [0.4.1] - 2026-08-20

### Fixed (i18n)

- Spanish is now the default UI language (`values/`), matching the project
  convention (`ui-strings-i18n`); the previous build had Spanish in `values-es/`
  and English as the default, so devices on an English locale showed English.
- String keys renamed to English (snake_case): `plans_*`, `account_*`,
  `members_*`, `movements_*` (was `planes_*`, `cuenta_*`, `miembros_*`,
  `movimientos_*`). All `R.string.*` references updated in the four composables.
- English moved to `values-en/` (additive translation); `values-es/` removed.
- Note: `0.3.0`'s "Spanish strings (`values-es`)" entry documented the earlier,
  incorrect location.

## [0.4.0] - 2026-08-20

### Added (M3 — Plans, access, final rules + usable MVP)

- Account creation via email/password linking the anonymous UID
  (`linkWithCredential`), preserving local data without a merge:
  `AuthRepository.vincularEmail` + `VincularCredencialUseCase` in `:core:domain`
  (TDD), `FirebaseAuthRepository` update in `:core:data`, and the new
  `:feature:cuenta` screen (name, email, password). The account menu now shows the
  linked email instead of "Guest".
- Plans and shared access domain (TDD) in `:core:domain`: `PlanFinancieroRepository`,
  `AccesoPlanRepository` and use cases (`CrearPlan`, `ObservarPlanesDelUsuario`,
  `ObservarPlan`, `ActualizarPlan`, `InvitarMiembro`, `AceptarInvitacion`).
- Firestore remote layer in `:core:data`: `FirebasePlanFinancieroRepository`,
  `FirebaseAccesoPlanRepository`, DTO mappers, a `Task.await()` coroutine helper,
  and a `PlanSincronizador` (snapshot listener on the user's accesses → Room) that
  lives only while its Flow is collected (AGENTS rule 3). New DI wiring for the
  Room database (previously not in Hilt), DAOs, `PassphraseProvider`,
  `FirebaseFirestore`, and the plan use cases.
- `:feature:planes` (new): plan list (from Room), create-plan dialog (name +
  ISO 4217 currency), pending invitations with "Accept", and the account menu.
  The navigation root is now the plan list; `MovimientosRoute(planId)` is
  plan-scoped with a members action.
- `:feature:planes`/`:feature:cuenta` member screens: list members, invite by UID
  with a role selector, accept pending invitations.
- Final `firestore.rules` (M3): deny-by-default with server-side roles
  (owner/admin/editor/viewer); `editedBy`/`editedAt` deferred to M5. An owner
  creates their plan + OWNER access; admins invite; a user accepts their own
  pending invitation. `firestore.indexes.json` finalized (categories by
  planId+tipo; expenses/income by planId+fecha; access is single-field).
- `rules-tests/` (Node subproject): `firebase-tools` + `@firebase/rules-unit-testing`
  validate the rules matrix (14 tests) against the Firestore Emulator; `firebase.json`
  added at the repo root (`projectId: spenvo-dev`, emulator on port 8081).
  `node_modules/` and `firestore-debug.log` are gitignored.

### Changed

- Navigation root moved from `MovimientosRoute` to `PlanesRoute`; `MovimientosRoute`
  now carries a `planId` and its account menu moved to the plan list.
- Anonymous sign-in and sync now start from `PlanesViewModel` (was `MovimientosViewModel`).
- `:core:data` loads the SQLCipher native library in `SpenvoDatabase.build`
  (runtime, in addition to the migration-test fix), fixing a startup crash
  (`UnsatisfiedLinkError` on `SQLiteConnection.nativeOpen`).
- Added `libs.firebase.firestore` and regenerated dependency locks for
  `:core:data`, `:app`, `:feature:movimientos`, `:feature:cuenta`, `:feature:planes`.

### Docs

- `doc/architecture.md`: plans/access data flow, new modules, decisions.
- `doc/security/owasp.md`: Firestore rules + App Check status.

## [0.3.0] - 2026-08-20

### Added (M2 — Guest-first identity: anonymous auth + App Check)

- Firebase wired into `:app` (google-services plugin) and `:core:data`
  (`firebase-bom`, `firebase-auth`); App Check provider
  (`firebase-appcheck` + play-integrity in release, debug provider in debug).
- Guest-first anonymous session: `Sesion` model + `AuthRepository` contract +
  `IniciarSesionAnonimaUseCase` in `:core:domain` (TDD); `FirebaseAuthRepository`
  in `:core:data` signs in anonymously in the background and exposes the session
  as a `Flow`. Offline-first: the app opens directly to Movimientos; a failed
  sign-in is silent and retried every 30s.
- Account entry point in the `Movimientos` top app bar: shows the session state
  ("Guest"/"Invitado") and a "Create account" item that links to the M3 screen
  (placeholder).
- `android.permission.INTERNET` declared (required for any Firebase traffic).
- `:feature:movimientos` now depends on `:core:data` (Hilt binding for the
  auth repository); dependency locks regenerated.
- `:core:domain` keeps zero DI dependencies: the anonymous sign-in use case is
  provided via `@Provides` in `:core:data`.

### Changed

- `doc/architecture.md`: `:feature:movimientos → :core:data` edge added.
- Spanish strings (`values-es`) added for the account menu.

## [0.2.0] - 2026-08-20

### Added (M1 — Local data layer)

- Room schema v2 (`SpenvoDatabase`): six local cache entities (`usuarios`,
  `planes_financieros`, `acceso_plan_financiero`, `categorias`, `gastos`, `ingresos`)
  plus `sync_state` (v1, unchanged), all encrypted with SQLCipher.
- `MIGRATION_1_2` in SQL with the same DDL as the exported schema (`2.json`),
  validated by an instrumented migration test (`MigrationTestHelper` +
  SQLCipher `SupportOpenHelperFactory`).
- Mappers between domain models and Room entities, covered by unit tests.
- Schema wiring for the androidTest source set (`assets.directories`).
- New module `:core:security`: `PassphraseProvider` + `AndroidKeystorePassphraseProvider`
  (AES-256/GCM key in the Android Keystore; 256-bit passphrase generated on first
  use, stored encrypted). Instrumented lifecycle test
  (create/insert/close/reopen/read with SQLCipher).
- `SpenvoDatabase.build` now takes a `PassphraseProvider` (wired to
  `:core:security`) instead of a raw passphrase.
- Living docs updated: `doc/database/schema.mdd` v1.1 (Room v2),
  `doc/security/owasp.md` (Keystore control now active).

### Changed

- SQLCipher native library now loaded explicitly with `System.loadLibrary("sqlcipher")`
  (the 4.x `SQLiteDatabase.loadLibs` API was removed).
- `core:data` androidTest dependency lock updated (Room testing + androidx.test).
- New module `:core:security` added to the build (`settings.gradle.kts`) with its
  dependency lock; `androidx.sqlite:sqlite` catalog accessor added.

### Fixed

- Root cause of the earlier `MissingType` KSP error: the Room database file was
  corrupted; rewritten clean with all entities and converters.
- Instrumented migration test failed with `UnsatisfiedLinkError` on
  `SQLiteConnection.nativeOpen`; fixed by loading the native library in `@Before`.

## [0.1.0] - 2026-08-19

### Added (M0 — Bootstrap)

- Multi-module project: `:app`, `:core:domain`, `:core:data`, `:core:designsystem`,
  `:feature:movimientos`.
- Approved baseline stack: AGP 9.3.1 (built-in Kotlin 2.2.10), Compose BOM 2026.02.01,
  Material 3, Navigation 3 (1.1.4), Hilt 2.60.1, Room 2.8.4, SQLCipher 4.18.0,
  DataStore 1.2.1, Coil 3.4.0, kotlinx-serialization 1.11.0, coroutines 1.11.0.
- Green smoke build: compiles, unit tests, lint and detekt with the base architecture.
- Encrypted Room database with SQLCipher (`SpenvoDatabase` + `sync_state`), exported
  versioned schema.
- Security baseline: `allowBackup=false`, R8 enabled, `networkSecurityConfig`
  deny-by-default, versioned dependency locking, secrets out of the repo.
- Agent configuration: `AGENTS.md` + `.agents/` (12 rules, 7 skills, 3 commands).
- Blocking lint: `HardcodedText` and `MissingTranslation` as errors.
- Living docs: `doc/architecture.md`, `doc/database/schema.mdd` v1.0,
  `doc/security/owasp.md`.
- Drafts of `firestore.rules` and `firestore.indexes.json` (deny-by-default, roles).

### Changed

- Kotlin: pinned `kotlin-stdlib` to 2.2.10 for metadata compatibility (compiler 2.2.10).
- Coil pinned to 3.4.0 (3.5.0 requires Kotlin 2.4, incompatible with the 2.2.10 baseline).
- Project daemon JVM pinned to toolchain 21 (local JDK; avoids downloading JDK 25).

### Fixed

- Catalog version accessor `hilt-navigation-compose` (conflict with the Hilt extension).
- SQLCipher 4.18 API: `SQLiteDatabase.getBytes` is private; manual UTF-8 conversion.
- `dependencyLocking` moved to root `build.gradle.kts` + `subprojects` (Gradle 9).
- `android.disallowKotlinSourceSets=false` so KSP can register sources with
  built-in Kotlin.

### Technical notes (M0)

- `gradle-daemon-jvm.properties` points to toolchain 21 (JDK 21.0.3 installed locally).
- The app compiles and packages; on-device validation is pending
  (wireless ADB hung the install) — validated in M3 with the e2e smoke.
- Firebase (google-services plugin) is activated in M2, once the project exists.
