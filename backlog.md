# Spenvo Technical Backlog 🗂️

List of atomic tasks for daily coding sessions. AI agents must move tasks between status sections
and check them off `[x]` while respecting the repo's i18n and quality rules (`AGENTS.md`). If a
task's implementation departed from what its plan/design doc specified, note it with a one-line
`Deviation:` under the checked-off item — see `.agents/rules/deviation-logging.md`.

---

## 🛑 Blocked
*No externally blocked tasks right now.*

## 🏃 In Progress
*No active tasks right now.*

## 📋 To Do

### 🔒 Architecture & Robustness (Medium Priority)
- [ ] **ARCH-M501:** Design a prototype to persist `EdicionesPendientes` and `ConflictosPendientes`
  in the Room database instead of keeping them in memory only (`process-lifetime`), mitigating the
  risk of losing sync state on process death.
- [ ] **UI-INS-001:** Audit and verify `WindowInsets` consumption across `PlanScaffold`'s tab
  transitions, to make sure system bars don't cause visual jumps or double padding on foldable
  devices.
- [ ] **ARCH-U801:** `CuentaViewModel.registrar()` wraps credential linking and
  `AsegurarUsuarioUseCase.paraVincularEmail` in the same `runCatching`: if Auth linking succeeds but
  the Firestore sync fails, the UI shows an error even though the account was already created, and a
  retry then hits "email already linked". Documented as a deliberately deferred gap in
  `doc/designs/2026-08-30-usuario-nombreusuario-design.md`.
- [ ] **ARCH-U802:** `AsegurarUsuarioUseCase.paraVincularEmail` resolves pending email invitations in
  a sequential `forEach` with no partial-failure handling: if one fails mid-loop, the remaining
  invitations are orphaned with no retry path. Documented as a deliberately deferred gap in the same
  design doc.

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
