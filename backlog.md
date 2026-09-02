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

### 🔐 Security & Supply Chain (High Priority)
*M8 from `ROADMAP.md`. `OSV-M801`'s implementation itself is done and validated — see
🧪 In Review / QA below. `OSV-M803` is done (see ✅ Done); these two are what's left before
`OSV-M801` can close.*
- [ ] **OSV-M804:** Configure `scan` (from `osv-scanner-pr.yml`) as a required status check in
  `main`'s branch protection / ruleset. Until this lands, the gate failing red does not actually
  block a merge — it's informative only.
- [ ] **OSV-M805:** Run `osv-scanner-scheduled.yml` for real post-merge (`gh workflow run
  osv-scanner-scheduled.yml --ref main`) and validate its GitHub Issue create/update/close lifecycle
  end-to-end. `OSV-M803`'s baseline is now clean so this is safe to run without an issue flood.
- [ ] **OSV-M802:** Product/architecture discovery for optional MFA (M8, MFA half) — no design yet;
  needs its own brainstorm session before any implementation task exists. Deliberately out of
  `OSV-M801`'s scope.

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
- [ ] **OSV-M801:** OSV-Scanner CI gate (M8, CI half) — implementation done and validated, both
  locally (dry-run against this repo's real 10 lockfiles) and in real GitHub Actions (draft PR #33,
  `chore/m8-osv-scanner-ci` → `main`, triggered `osv-scanner-pr.yml` for real; identical result to
  the local run: 463 blocking rows, 51 unique vulnerabilities, exit code 1). Not moved to Done yet —
  `OSV-M803`'s baseline triage is done; two things remain: `OSV-M804` (make the check required on
  `main`), `OSV-M805` (validate the scheduled workflow's Issue lifecycle post-merge). See
  `doc/designs/2026-09-02-osv-scanner-ci-design.md`, `doc/plans/2026-09-02-osv-scanner-ci-implementation.md`.

## ✅ Done
- [x] **OSV-M803:** Triaged the 463 blocking rows / 51 unique vulnerabilities from the PR #33
  baseline. 26 justified, individually-reasoned `osv-scanner.toml` exceptions (23 dormant
  `io.netty:*` findings limited to AGP's unused `unified-test-platform-*` tooling,
  `ignoreUntil = 2026-12-01`; 3 `org.bouncycastle:*` findings live on `lintDebug`/
  `testDebugUnitTest` classpaths, `ignoreUntil = 2026-10-01`, each citing the exact vulnerable API).
  2 remediated directly instead of exempted: `rules-tests/package.json` `overrides` pin `uuid` to
  `>=11.1.1` and `qs` to `>=6.16.0`; verified via `npm ls` (no `invalid` entries), a full
  `rules-tests` `npm test` run (76/76 passing against the Firestore/Storage emulators), and a
  re-scan showing 0 blocking rows (`osv-gate.mjs --mode=pr` exits 0).
  **Deviation:** the live re-scan (osv.dev is queried live, not embedded in the pinned scanner
  image) surfaced 2 more blocking findings not present in the original PR #33 snapshot — `qs`
  GHSA-4mjr-xmp4-gh2g/GHSA-x5fp-wj9c-mxmx, published in the gap between the two scans. Handled the
  same way (remediated via override, not exempted) rather than deferring to a follow-up task.
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
