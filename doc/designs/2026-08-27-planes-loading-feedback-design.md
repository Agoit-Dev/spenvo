# Startup loading feedback (Planes) — design

Status: approved, pending implementation.
Sub-project 2 of 3 in the UI/UX review started 2026-08-25 (sub-project 1,
`doc/designs/2026-08-26-movimientos-edit-modal-design.md`, is already merged to `main`). Sub-project
3 (Home screen + bottom navigation) is separate, not addressed here.

## Problem

Two complaints were raised about the app's cold-start experience:

1. The splash screen shows only the logo, with no progress feedback, for however long it takes.
2. After the splash screen hands off, the Planes screen briefly shows "no plans" before the real
   plan list loads in.

**Correction to the original framing** (verified against the code before designing around it): the
splash screen is AndroidX Core `installSplashScreen()`, a native OS-controlled surface (icon +
background only) — it cannot host a `CircularProgressIndicator` or any custom view, and per an
existing architecture decision (`doc/architecture.md`) it has no artificial delay, so it dismisses
almost immediately once the first Compose frame draws. There is no meaningful "waiting on the
splash" window to add a spinner to. The actual problem is entirely on the Planes-screen side of the
handoff, in `PlanesViewModel`.

### Root cause

`PlanesViewModel.planes` (`feature/planes/src/main/java/com/agoitdev/spenvo/planes/PlanesViewModel.kt:54-57`):
```kotlin
val planes: StateFlow<List<PlanFinanciero>> = sesion.flatMapLatest { s ->
    val uid = s.uid
    if (uid == null) flowOf(emptyList()) else observarPlanes(uid)
}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
```
`stateIn`'s `emptyList()` initial value is a synthetic placeholder, indistinguishable from "the user
genuinely has zero plans" — the same bug class fixed in sub-project 1 for movimiento categories, but
here the loading window is longer and has two sources, not one:
- **Session establishment**: `sesion` starts at `Sesion.Anonima` and only gets a real `uid` once
  `iniciarSesionAnonima()` succeeds (`PlanesViewModel.kt:83-90`, a retry loop that can take a real
  amount of time offline or on a slow connection). While `uid == null`, `planes` deliberately emits
  `flowOf(emptyList())` — correct behavior for "no session yet," but visually identical to "no
  plans."
- **Room query latency**: once `uid` is known, `observarPlanes(uid)`'s first real emission still
  takes a moment (Room runs on a background dispatcher), during which the `stateIn` placeholder is
  what's visible.

`invitacionesPendientes` (`PlanesViewModel.kt:69-78`) has the identical shape and the identical
placeholder problem. This matters because `PlanesLista`
(`feature/planes/src/main/java/com/agoitdev/spenvo/planes/PlanesScreen.kt:168`) decides the empty
state by checking **both** lists together (`planes.isEmpty() && invitaciones.isEmpty()`) — fixing
only `planes` would still flash "no plans" for a user with a pending invitation but no owned plans
yet.

## Design

### ViewModel: nullable-sentinel StateFlow

Represent "not yet resolved" explicitly instead of conflating it with "resolved to empty":

```kotlin
private val planesRaw: StateFlow<List<PlanFinanciero>?> = sesion.flatMapLatest { s ->
    val uid = s.uid
    if (uid == null) flowOf(null) else observarPlanes(uid).map<List<PlanFinanciero>, List<PlanFinanciero>?> { it }
}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initialValue = null)

val planes: StateFlow<List<PlanFinanciero>> = planesRaw
    .map { it.orEmpty() }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
```
Same shape for `invitacionesPendientes` (`invitacionesRaw: StateFlow<List<AccesoPlan>?>`, derived
`invitacionesPendientes: StateFlow<List<AccesoPlan>>`).

A new combined flag:
```kotlin
val cargandoLista: StateFlow<Boolean> = combine(planesRaw, invitacionesRaw) { planes, invitaciones ->
    planes == null || invitaciones == null
}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initialValue = true)
```
`true` until both `planesRaw` and `invitacionesRaw` have received their first real emission
(covering both the session-establishment window and the Room-query window).

**Correction (found in code-quality review, 2026-08-27):** this is *not* permanently `false` once
resolved. `AuthRepository.cerrarSesion()` signs out and immediately re-establishes a new anonymous
session (`doc/architecture.md`'s guest-first re-entry decision), which produces a transient
`Sesion(uid = null)` — `sesion.flatMapLatest` switches back to the `null` branch, and
`cargandoLista` correctly returns to `true` for that window too. This is the right behavior (show
the spinner while the new session establishes, rather than briefly showing the previous account's
stale plans) — the original text describing it as one-way was simply wrong, not a bug to fix.

Naming note (also from code-quality review): this is deliberately `cargandoLista`, not `cargando` —
the codebase already uses bare `cargando` for "an action is in flight" (`CrearPlanEstado.cargando`,
similar fields elsewhere). Reusing that name for "the screen is loading" would collide in meaning
one call site away, in the same `PlanesScreen` composable.

`resumenesPorPlan` is explicitly **not** given this treatment — `PlanCard` already renders correctly
with `resumen == null` (just omits the balance line), so gating the whole screen on it would trade a
real problem (wrong content shown) for an invented one (screen blocked on a non-blocking field).

### UI: `PlanesLista` shows a centered spinner in the list area only

Confirmed with the user: the loading state replaces only the `LazyColumn` content area.
`Scaffold`'s `topBar` (title, account menu) and `floatingActionButton` (create plan) stay exactly as
they are — the chrome the user's session identity lives in shouldn't disappear during a load.

```kotlin
if (cargandoLista) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
} else {
    PlanesLista(planes = planes, resumenesPorPlan = resumenesPorPlan, invitaciones = invitaciones, ...)
}
```
`CircularProgressIndicator` matches the existing pattern already used elsewhere in the app for short
waits (the Guardar button in the movimiento form, `CrearPlanDialog`'s confirm button) — no new
component invented.

No artificial minimum display time, no timeout: `cargandoLista` flips to `false` the instant both flows
resolve, matching the project's already-documented "no artificial delay" philosophy (the same
principle the splash screen itself was built on, per `doc/architecture.md`).

## Out of scope (explicitly)

- The native splash screen (`installSplashScreen()`, `Theme.Spenvo.Starting`) — unchanged. No
  `setKeepOnScreenCondition` extension; the loading window is on the Planes-screen side, not during
  the splash itself.
- `resumenesPorPlan` — already tolerates a not-yet-loaded state per plan, not gated.
- Sub-project 3 (Home screen, bottom navigation) — separate design cycle.
- M8 items (osv-scanner, MFA) — unrelated.

## Testing

Per `AGENTS.md`'s strict TDD:
- `PlanesViewModelTest`: a test proving `cargandoLista` starts `true` and flips to `false` only after
  both a `planes` and an `invitaciones` emission have been observed (using a controllable fake
  repository flow, same technique as the movimiento category-race test in sub-project 1 — a
  `MutableStateFlow` the test drives manually to reproduce the empty-then-real transition
  deterministically).
- A test proving `cargandoLista` stays `true` while `sesion.uid` is still null (session not yet
  established).
- A Compose test on `PlanesScreen`/`PlanesLista` proving the spinner renders in place of the list
  while `cargandoLista`, and that the TopBar/FAB remain visible during that state.
- A regression test proving the existing empty-state text ("no plans") still renders correctly once
  `cargandoLista` is `false` and both lists are genuinely empty — this is the actual "user has zero
  plans" case, not the loading case, and must not regress.
