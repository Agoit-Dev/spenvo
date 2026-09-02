# Spenvo Technical Backlog 🗂️

List of atomic tasks for daily coding sessions. AI agents must move tasks between status sections
and check them off `[x]` while respecting the repo's i18n and quality rules (`AGENTS.md`). If a
task's implementation departed from what its plan/design doc specified, note it with a one-line
`Deviation:` under the checked-off item — see `.agents/rules/deviation-logging.md`.

---

## 🛑 Blocked
*No externally blocked tasks right now.*

## 🏃 In Progress
*No tasks currently in progress.*

## 📋 To Do

### 🔒 Architecture & Robustness (Medium Priority)
- [ ] **UI-INS-001:** Audit and verify `WindowInsets` consumption across `PlanScaffold`'s tab
  transitions, to make sure system bars don't cause visual jumps or double padding on foldable
  devices.

### 🎨 Minor UX (Low Priority)
*Findings from front 3's (profile accessible) final review — not blocking, not yet addressed.*
- [ ] **UX-H901:** `HomeScreen` shows the plan name twice (the new `TopAppBar` title + `HomeContenido`'s
  own heading). The design allowed this on purpose, but it's worth evaluating whether to drop the
  duplicate heading.

### 🎨 Product Features / UX (Low Priority)
- [ ] **FEAT-M402:** Extend `Categoria`'s domain model and Room entity to support a hex color field
  (`colorHex`).
- [ ] **FEAT-M403:** Add a basic color picker to the category creation/edit `ModalBottomSheet`.

## 🧪 In Review / QA
*No tasks pending review this session.*

## ✅ Done
- [x] **UI-THEME-001:** Established Delivery 1 of Spenvo's Material 3 design system: explicit Brand
  Light/Dark schemes, independent luminosity and color-source configuration, stable financial
  semantic colors, complete typography and shapes, a deterministic four-variant preview catalog,
  theme contract tests, and migration/audit of current consumers. Focused consumer mapping and
  color-pair resolver tests use the existing `:feature:movimientos` unit-test infrastructure. See
  the approved [design](doc/designs/2026-09-02-spenvo-theming-design.md) and
  [implementation plan](doc/plans/2026-09-02-spenvo-theming-implementation.md). User-facing theme
  preferences and persistence remain deferred to Delivery 2.
- [x] **BUILD-001:** Narrowed `:app`'s kotlinx-serialization dependency from `json` to `core`, which
  is all its `@Serializable` Navigation 3 routes require; kept JSON scoped to `:core:data`,
  regenerated `app/gradle.lockfile`, and verified the project successfully.
- [x] **BUG-H602:** Fixed the "no plans yet" flicker on `PlanesScreen`'s cold start using the
  internal `cargandoLista` state.
- [x] **BUG-M601:** Implemented per-tab local state isolation via `rememberSaveableStateHolder()` in
  `PlanScaffold`, preventing scroll/search state from being destroyed on tab switch.
- [x] **FEAT-U700:** Implemented the full 10-slice flow for transactional `nombreUsuario`
  handling/reservation and secure email account linking without data enumeration.
- [x] **TEST-C401:** Set up UI test infrastructure (Robolectric + `ui-test-junit4`) in the
  `:feature:categorias` module (`CategoriasScreenTest.kt`, commit `2183bde`).
  **Deviation:** landed as a byproduct of `FEAT-U704` (front 3 needed a test for the new avatar
  action on `CategoriasScreen`, and the module had no screen test infra yet), not as its own
  planned task.
- [x] **TEST-C402:** First screen-level integration test for `:feature:categorias`
  (`CategoriasScreenTest.kt`, commit `2183bde`).
  **Deviation:** only covers the `TopAppBar`'s new avatar action, not the category grid/creation
  dialog the original task description implied — consider a `TEST-C403` if that coverage is needed.
- [x] **FEAT-U703:** Real login + logout without automatic anonymous re-creation (front 2/3). Real
  email/password, password recovery, `SesionGateViewModel` + `SesionPreferences` (DataStore) to
  persist explicit logout. Merged to `main` (`53f493e..0f4cccb`).
- [x] **FEAT-U704:** Profile accessible from every screen (front 3/3). `AvatarTopBarAction` on all 4
  tabs of an open plan. Merged to `main` (`0f4cccb..da423ab`).
- [x] **FEAT-U701:** UI gating on invitations. `MiembrosViewModel.puedeInvitar(planId)` derives the
  current session's own role from the same `observarAccesosDelPlan` list already used for the
  member list (no extra query), gated on a new `Rol.esAlMenos(minimo)` domain extension mirroring
  `firestore.rules`' `roleLevel`/`tieneRolMinimo`.
  **Deviation:** the item said "only enabled when..."; implemented as fully hidden instead of
  shown-but-disabled, matching this app's existing convention of not rendering conditional actions
  at all rather than greying them out (e.g. `CuentaMenu`, `EntradaCuenta`/`GateInvitado`).
- [x] **BUG-H603:** `PlanesViewModelTest`'s `resumenesPorPlan combina...`/`resumenesPorPlan se
  recombina...` fixed the 8 `LocalDate.of(2026, 8, ...)` fixtures to `YearMonth.now().atDay(...)`.
  **Root cause, correcting this item's own original guess:** not a coroutine/`combine` timing issue
  — `ObservarResumenMensualPlanUseCase` filters by `YearMonth.now()` by default (correct product
  behavior), and the test's movimientos were hardcoded to August 2026; once the wall clock rolled
  into September the filter excluded everything, summing to 0. Would have broken again every month
  regardless of any code change — a test-fixture staleness bug, not a product bug.
- [x] **UX-H902:** New `MutableList<NavKey>.pushUnlessTop(destino)` extension in `MainActivity.kt`
  — no-ops instead of pushing when `destino` already sits on top of the backstack.
  **Deviation:** the item only named `onAbrirCuenta`, but `onCrearCuenta` and `onAbrirPlan` in the
  same file have the exact same latent double-tap bug — fixed all three with one shared helper
  rather than leaving 2 of 3 identical call sites unprotected.
- [x] **UX-H903:** `PlanesScreen`'s `CuentaMenu` now reuses `AvatarTopBarAction` (the same component
  the 4 tab screens already use) instead of a generic `Icons.Filled.AccountCircle`, sourced from the
  already-collected `sesion.photoUrl` — no new data plumbing.
- [x] **ARCH-U801:** `CuentaViewModel.registrar()` now splits Auth linking from the Firestore
  `Usuario` sync. A sync failure (Auth already linked) sets a distinct `RegistroEstado.syncPendiente`
  instead of a generic error, and `reintentarSyncUsuario()` retries only the sync step — never
  `vincularCredencial` again, which would otherwise fail with "credential already linked". New
  `SincronizacionUsuarioVinculado` private helper shares the sync logic between the initial attempt
  and the retry. `CuentaScreen` surfaces it as an indefinite snackbar with a "Reintentar" action.
- [x] **ARCH-U802:** `AsegurarUsuarioUseCase.paraVincularEmail`'s pending-invite resolution now
  grants each invite independently instead of a plain `forEach` that aborted the whole batch at the
  first failure — one bad invite no longer blocks the rest from being attempted. The method still
  reports overall failure (so `ARCH-U801`'s `reintentarSyncUsuario()` retry gets another chance at
  whichever invites failed); `invitarMiembro`/`eliminar` are both keyed by deterministic Firestore
  document ids, so retrying an already-granted invite is a safe no-op.
- [x] **ARCH-M501:** `EdicionesPendientes`/`ConflictosPendientes` moved off in-memory-only storage
  onto Room (`ediciones_pendientes`/`conflictos_pendientes`, `SpenvoDatabase` v3→v4), closing the
  process-death data-loss gap `doc/architecture.md` had carried as accepted debt. `VersionPendiente`
  is retired; `RegistroEdicionesPendientes`/`RegistroConflictosPendientes` (`:core:domain`) and their
  Room implementations (`:core:data`) replace the old concrete classes, with 4
  `SpenvoDatabase.withTransaction { }` boundaries (write, rollback, snapshot received, conflict
  resolution) closing the race that used to exist between the optimistic Room write and the
  Firestore echo. See `doc/designs/2026-09-01-conflictos-pendientes-room-design.md` for the full
  design and `CHANGELOG.md`'s `[Unreleased]` entry for the complete list of call sites touched.
  **Deviation:** the plan's own file lists undercounted the blast radius of extending
  `MovimientoRepository` by 4 abstract methods — fixing every existing test fake/consumer for that
  interface change (13 hand-written fakes across 4 modules, including 2 in `:feature:planes` missed
  until this closing gate run) and wiring the 4 new use cases into `MovimientoModule.kt` (Hilt) both
  turned out to be real, necessary scope beyond what was originally enumerated.
