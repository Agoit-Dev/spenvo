# Home screen + bottom navigation — design

Status: approved, pending implementation.
Sub-project 3 of 3 in the UI/UX review started 2026-08-25. Sub-project 1 (movimientos edit modal)
and sub-project 2 (planes loading feedback) are already merged to `main`. This is the largest and
most architectural of the three — it touches app-level navigation, not just one feature module.

## Problem

Two complaints from the original review:

1. There is no Home screen. Tapping a plan in the Planes list lands directly on Movimientos (the
   transaction list) — the user's own framing: "no debería ser movimientos" the entry point to a
   plan.
2. There is no bottom navigation bar to reach a plan's main screens. Today, `MovimientosScreen`'s
   TopBar has two icon buttons (`MovimientosTopBar` in
   `feature/movimientos/src/main/java/com/agoitdev/spenvo/movimientos/MovimientosScaffoldPartes.kt:25`)
   that push `MiembrosRoute`/`CategoriasRoute` onto the outer Navigation 3 backstack — an ad hoc,
   low-discoverability substitute for real navigation between sibling screens.

## Scope decision: Home is per-plan, not a new top-level tab

Verified against the reference implementation the user pointed to
(`act02-app_gastos`'s `ui/home/HomeScreen.kt`): its `HomeScreen` takes a `planId` and is a
**per-plan dashboard** (balance card, budget bar, a grid of shortcuts to that plan's
Ingresos/Gastos/Usuarios/Categorías), not an app-level screen. This matches complaint #1 exactly —
the issue is what you see *after opening a plan*, not a missing app-level landing page. Spenvo's
Planes list already functions as the closest thing to an app-level home (each `PlanCard` already
shows a monthly net balance via `resumenesPorPlan`); a second, separate cross-plan Home tab would
duplicate that. Decision: **Home is the landing screen for a plan**, not a sibling of Planes.

## Navigation architecture

**Chosen approach (confirmed with the user over two alternatives):** a single new route,
`PlanRoute(planId)`, replaces the current direct push to `MovimientosRoute(planId)` when opening a
plan. `PlanRoute`'s composable hosts one `Scaffold` with a Material 3 `NavigationBar` (4
destinations: Home, Movimientos, Categorías, Miembros); switching tabs is **local Compose state**,
not a Navigation 3 backstack operation. Pressing back from any tab exits the whole plan shell
straight to Planes, regardless of which tab was active — matching standard Android bottom-nav
behavior (tabs are peers, not a stack) and avoiding the complexity of preventing back-stack growth
from tab-switching.

**Rejected alternative:** each tab as its own `NavKey`, switched by replacing the top of the outer
backstack. Would give "real" per-tab navigation (deep-linkable, works with
`rememberSaveableStateHolderNavEntryDecorator`/`rememberViewModelStoreNavEntryDecorator` for free)
but back-button behavior becomes less predictable (can bounce between recently-visited tabs before
exiting the plan), and nothing in this app currently needs per-tab deep linking. Rejected for
unneeded complexity.

**Consequence — `MovimientosTopBar`'s icon buttons are removed.** `onGestionarCategorias`/
`onVerMiembros` (`MovimientosScaffoldPartes.kt:25`) exist only to reach Categorías/Miembros from
Movimientos; once both are peer bottom-nav tabs, those icons are a redundant second path to the
same place. `MovimientosScreen`'s public signature loses both callback parameters. **Flagging this
explicitly for spec review** — it's a deletion of existing UI, not just an addition.

**MainActivity.kt changes:** `MovimientosRoute`, `MiembrosRoute`, `CategoriasRoute` are removed as
independently-pushed top-level `NavKey`s (they become tab content inside `PlanRoute`, not routes).
New `PlanRoute(val planId: String) : NavKey`. `PlanesScreen`'s `onAbrirPlan` now pushes `PlanRoute`
instead of `MovimientosRoute`. `CuentaRoute` is untouched (reached from Planes' account menu, not
from inside a plan).

**Shared ViewModel across Home and the Movimientos tab:** `PlanRoute`'s composable creates one
`MovimientosViewModel` (via `hiltViewModel()`, scoped to that single NavEntry like everything else
in this app) and passes it to both the Home tab (for the quick-add actions) and the Movimientos
tab. A movimiento added from Home's quick action is immediately visible in the Movimientos tab
without a reload, since both read the same `StateFlow`s.

## Home screen content

Order confirmed with the user, informed by both the legacy `HomeScreen.kt` (balance card, quick
shortcuts) and three WealthFlow mockup screenshots the user shared (balance-first layout, circular
quick-action icons — neither copied literally, both adapted to what Spenvo's domain actually
supports):

1. Greeting + plan name.
2. **Cumulative balance card** — net of every Gasto/Ingreso ever recorded for this plan (income
   minus expenses, no date filter), *not* a real account/bank balance — Spenvo has no concept of a
   starting balance or connected accounts, so this must be labeled clearly (e.g. "Balance
   acumulado") to avoid implying it's money actually held anywhere.
3. **This month's income/expense breakdown** — two figures, current calendar month only.
4. **Quick actions** — two circular icon buttons, "Nuevo Gasto" / "Nuevo Ingreso", each opening the
   existing `MovimientoFormSheet` (`feature/movimientos/.../MovimientoFormSheet.kt`) as a modal
   over Home with the matching `tipoInicial`, using the shared `MovimientosViewModel` from above.
   No new form component.

### Domain changes for the cumulative balance + monthly breakdown

`ResumenMensualPlan` (`core/domain/src/main/java/com/agoitdev/spenvo/domain/model/ResumenMensualPlan.kt`)
currently only exposes `netoDelMes: Monto`. `ObservarResumenMensualPlanUseCase`
(`core/domain/.../usecase/ObservarResumenMensualPlanUseCase.kt`) already computes `gastosMes` and
`ingresosMes` separately internally before collapsing them into the net — extend
`ResumenMensualPlan` to also carry `ingresosMes: Monto` and `gastosMes: Monto` (additive, backward
compatible: `PlanCard` in `PlanesScreen.kt` only reads `netoDelMes` today and keeps working
unchanged).

The cumulative (all-time) balance needs a new use case — same shape as
`ObservarResumenMensualPlanUseCase` but without the `YearMonth` filter (sum every non-deleted
Gasto/Ingreso for the plan, no date bound). Exact naming and whether it's a genuinely separate use
case or a parameterized variant (`mes: YearMonth? = null` meaning "no filter") is an implementation
detail for the planning phase, not fixed here.

## Modules touched

- `:app` — `MainActivity.kt` (new `PlanRoute`, removal of `MovimientosRoute`/`MiembrosRoute`/
  `CategoriasRoute` as top-level entries, new `PlanScaffold`-style composable hosting the
  `NavigationBar`).
- `:feature:movimientos` — new `HomeScreen`/`HomeViewModel` (new package or alongside existing
  files — planning phase decides), `MovimientosScaffoldPartes.kt`'s `MovimientosTopBar` loses its
  two icon buttons and their callback params, `MovimientosScreen`'s public signature changes.
- `:core:domain` — `ResumenMensualPlan` gains two fields, new cumulative-balance use case.
- `:feature:planes` — `PlanesScreen.kt`'s `onAbrirPlan` call site (no signature change, just what
  it navigates to — this stays in `:app`/`MainActivity.kt`, not `:feature:planes` itself, since
  `PlanesScreen` just exposes the callback, doesn't own routing).
- Possibly `:feature:categorias`/`:feature:planes` (`MiembrosScreen`) if their screens need
  signature adjustments to fit as tab content instead of full-screen routes with their own
  implicit back behavior — planning phase confirms exact diff.

## Testing

Per `AGENTS.md`'s strict TDD:
- `ObservarResumenMensualPlanUseCaseTest`: extend for the new `ingresosMes`/`gastosMes` fields.
- New use case test for the cumulative balance (no date filter, sums everything).
- New `HomeViewModelTest` covering both figures + the quick-action wiring.
- Compose test for the bottom nav: tapping each of the 4 destinations shows the right tab content
  without losing the other tabs' state (e.g. Movimientos' scroll position or filter survives a
  round-trip through another tab) — exact preservation mechanism (`rememberSaveable` scoping per
  tab) is a planning-phase implementation detail.
- Compose test proving back-button-from-any-tab exits to Planes.
- Compose test proving a movimiento added via Home's quick action appears in the Movimientos tab
  without navigating away.
- Regression test confirming `MovimientosTopBar` no longer renders the removed icon buttons.

## Out of scope (explicitly)

- Budget and Analytics — not built, no reserved bottom-nav slot (verified against Material 3's own
  guideline of 3-5 `NavigationBar` destinations; adding two dead icons now would both exceed that
  and create dead-end taps). Adding them later is a cheap, independent change once those features
  exist.
- Deep-linking directly to a specific tab — not needed today, was the deciding factor against the
  rejected per-tab-NavKey navigation approach.
- Sub-projects 1 and 2 — already merged, unrelated.
- M8 items (osv-scanner, MFA) — unrelated.
- The pending "no re-authentication after logout" gap (see project memory
  `project_pending_no_reauth_after_logout`) — separate, deferred issue.
