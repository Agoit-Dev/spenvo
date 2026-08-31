# Perfil accesible desde todas las pantallas — design

Status: approved, pending implementation.
Front 3 of 3 in the auth/identity feature series. Front 1 ("Usuario entity + nombreUsuario") and
front 2 ("Login real + logout sin recreación anónima") are merged to `main` (`0f4cccb`).

Scope decision, confirmed with the user: **navigation only**. `CuentaScreen`'s existing
`PerfilContenido` (built in front 1) is not redesigned — this front only makes it reachable from
more places. A reference image for the profile screen's visual design was shared earlier in a prior
session and did not survive a context compaction; the user confirmed no redesign is needed, so this
is moot for this front.

## Problem

Verified against code: the account/profile entry point (`CuentaMenu`, an icon button in the top bar)
exists only on `PlanesScreen` (`feature/planes/src/main/java/com/agoitdev/spenvo/planes/PlanesScreen.kt:314`),
via the `onCrearCuenta` callback plumbed from `MainActivity.kt`. Once a user opens a plan
(`PlanRoute` → `PlanScaffold`, `app/src/main/java/com/agoitdev/spenvo/PlanScaffold.kt`, with its four
bottom-nav tabs Home/Movimientos/Categorías/Miembros), there is no way to reach the profile screen
short of backing out of the plan entirely to `PlanesScreen`.

Each of the four tabs maintains its own `TopAppBar` independently, in its own feature module
(`MovimientosScaffoldPartes.kt`, `CategoriasScreen.kt`, `MiembrosScreen.kt`; `HomeScreen` currently
has none). There is no shared top bar across tabs — `PlanScaffold` only owns the bottom
`NavigationBar`.

## Scope decision: per-screen action, not a shared top bar

Two approaches were considered:

1. **A shared top bar owned by `PlanScaffold`** — one implementation, but would require migrating
   each tab's own top-bar actions (e.g. Miembros' invite action) up into `PlanScaffold`, touching
   the internal structure of four feature modules for a purely additive feature. Rejected: too
   invasive for what this front needs.
2. **A small reusable action added to each screen's existing `TopAppBar`** (chosen) — matches this
   codebase's established pattern of passing navigation callbacks down from `:app` into feature
   screens as composable parameters (`onCrearCuenta`, `onAbrirPlan`, `onRegistroCompletado` all
   already work this way). No feature module's internal structure changes; each screen adds one
   parameter and one action icon to a bar it already owns.

## Component: `AvatarTopBarAction`

New composable in `:core:designsystem` (`core/designsystem/src/main/java/com/agoitdev/spenvo/designsystem/components/Avatar.kt`,
alongside the existing `AvatarConBadge`): a compact, badge-less avatar meant for a `TopAppBar`'s
`actions` slot.

```kotlin
@Composable
fun AvatarTopBarAction(
    photoUrl: String?,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
)
```

Reuses `AvatarConBadge`'s existing `AsyncImage`/Coil3 pattern (`ContentScale.Crop`, `CircleShape`)
for `photoUrl != null`; falls back to the same generic person icon `PlanesScreen`'s `CuentaMenu`
already uses today when `photoUrl == null` (covers both an anonymous session and a registered user
who never uploaded a photo).

## Data flow: no new plumbing needed

`Sesion` (`core/domain/.../model/Sesion.kt`) already carries `photoUrl: String? = null`, sourced
from Firebase Auth's profile and kept in sync by `CuentaViewModel.subirAvatar` (uploads to Storage,
then calls `authRepository.actualizarPerfil(photoUrl = url)`). Every screen this front touches
already has a path to `AuthRepository.observeSesion()`:

- `PlanesViewModel`, `MovimientosViewModel`, `CategoriasViewModel`, `MiembrosViewModel` already
  inject `AuthRepository` (verified by reading each constructor).
- `HomeViewModel` does not yet — it gains the dependency as part of this front, exposing a
  `avatarUrl: StateFlow<String?>` (or reusing an existing `sesion` exposure if the ViewModel already
  has one) the same way the other three will.

No Firestore/Room changes, no new use case — this is presentation-layer wiring over data that
already exists and is already live-updated.

## Navigation

Each screen gains an `avatarUrl: String?` + `onAbrirCuenta: () -> Unit` parameter pair, wired from
`:app`'s `entryProvider` blocks in `MainActivity.kt`'s `SpenvoApp`, all pointing at the same
`backStack.add(CuentaRoute)` call `PlanesScreen`'s `onCrearCuenta` already uses. Navigation 3's
existing backstack model means tapping the avatar from inside a plan pushes `CuentaRoute` on top of
the current `PlanRoute`/tab state; backing out returns exactly to where the user was (tab selection
included, since `PlanScaffold`'s `rememberSaveableStateHolder()` already preserves per-tab state
across recomposition).

`CuentaScreen` needs no changes: it already renders `PerfilContenido` whenever
`sesion.estaAutenticada`, regardless of `tabInicial` (which only affects the signed-out `AuthForm`
path). An anonymous session tapping the avatar mid-plan sees the same "Crear cuenta" entry point
`PlanesScreen`'s `CuentaMenu` already provides today — no new state to design for.

## Testing

Per `AGENTS.md`'s strict TDD:

- `AvatarTopBarActionTest` (`:core:designsystem`, mirrors the existing `AvatarTest.kt` pattern) —
  renders with/without `photoUrl`, confirms the click callback fires, confirms the fallback icon
  shows when `photoUrl == null`.
- For each of the four tab screens: a test confirming the new avatar action is present in the top
  bar and that tapping it invokes the passed `onAbrirCuenta` callback (existing Robolectric/Compose
  test infra already covers `PlanesScreenTest`, `CuentaScreenTest`; `HomeScreenTest`,
  `MovimientosScreenListDetailTest`, `CategoriaFormularioSheetTest`/equivalent, and a Miembros
  screen test already exist per the front 1/2 diffs — extend them rather than adding new files
  where a suitable test class already exists).
- `HomeViewModelTest` gains coverage for the new `AuthRepository` dependency and the exposed
  `avatarUrl`/`sesion` state, following the same pattern already used by
  `MovimientosViewModelTest`/`CategoriasViewModelTest`/`MiembrosViewModelTest` for their existing
  `AuthRepository` usage.
- No new instrumented (`androidTest`) coverage is needed — nothing here touches Firestore rules,
  Room, or DataStore.

## Modules touched

- `:core:designsystem` — new `AvatarTopBarAction` composable + test.
- `:feature:movimientos` — `HomeScreen`/`HomeViewModel` (new `AuthRepository` dependency),
  `MovimientosScreen`'s existing top bar gains the action.
- `:feature:categorias` — `CategoriasScreen`'s existing top bar gains the action.
- `:feature:planes` — `MiembrosScreen`'s existing top bar gains the action. `PlanesScreen` is
  unchanged (already has this entry point).
- `:app` — `MainActivity.kt`'s `SpenvoApp` wires `avatarUrl`/`onAbrirCuenta` into each of the four
  tab `entry<PlanRoute>` composables, sourced from a shared session read (likely
  `PlanesViewModel.sesion` or a lightweight equivalent already scoped at the `PlanRoute` level —
  exact source decided during implementation, since multiple tabs' ViewModels all expose the same
  `Sesion` independently and duplicating the read four times is wasteful but harmless; picking one
  canonical source is a plan-level detail, not a design-level one).

## Out of scope (explicitly)

- Any visual redesign of `PerfilContenido`/`CuentaScreen` — confirmed with the user, this front is
  navigation-only.
- Showing the avatar anywhere it doesn't already make sense (e.g. inside `CuentaScreen` itself,
  which already has its own avatar via `AvatarConBadge`).
- Front 1's two "known gap, deliberately deferred" items and front 2's Google Sign-In deferral —
  pre-existing, unrelated to this front.
