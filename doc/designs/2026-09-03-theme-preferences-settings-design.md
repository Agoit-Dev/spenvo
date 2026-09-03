# Theme Preferences and Settings Design

**Date:** 2026-09-03

**Status:** Approved

**Platform:** Android / Jetpack Compose Material 3

**Depends on:** UI-THEME-001 (`SpenvoTheme`, `ThemeMode`, `ColorMode`), merged into `main` at
`5b24836`.

## Context

Delivery 1 (UI-THEME-001) gave `SpenvoTheme` two independent, injectable axes — `ThemeMode`
(`SYSTEM`/`LIGHT`/`DARK`) and `ColorMode` (`BRAND`/`DYNAMIC`) — but `MainActivity` calls it with no
persisted values, so the app always starts at `SYSTEM` + `BRAND`. There is no Settings route, no
preferences ViewModel, and no backlog entry for exposing this to the user. Delivery 2 closes that
gap: a user-facing Settings screen backed by local, reactive, persisted preferences.

`:core:data` already depends on DataStore Preferences (`SesionPreferences`, keyed by device, with
`IOException` recovery to `emptyPreferences()`) — that is the reference pattern this design reuses
rather than reinvents.

## Decisions

### Domain contract and persistence

`:core:domain` declares neutral models, independent of the design system:

```kotlin
enum class ThemePreference { SYSTEM, LIGHT, DARK }
enum class ColorPreference { BRAND, DYNAMIC }

data class AppearancePreferences(
    val theme: ThemePreference,
    val color: ColorPreference,
)

interface AppearancePreferencesRepository {
    val preferences: Flow<AppearancePreferences>
    suspend fun actualizarTema(theme: ThemePreference): Result<Unit>
    suspend fun actualizarColor(color: ColorPreference): Result<Unit>
}
```

`:core:data` implements it with `ThemePreferences`, a `DataStore<Preferences>` of its own
(`preferencesDataStore(name = "appearance")`), separate from `sesion` — same granularity as the
existing store, one responsibility each. Enum values are persisted via `.name` / `valueOf()`,
wrapped in `runCatching { ... }.getOrDefault(...)`, matching the only existing precedent for a
persisted enum in this codebase (`Converters.tipoRegistroToString/stringToTipoRegistro`). No
separate string-vocabulary codec is introduced.

Use cases wrap the repository (`:feature:ajustes` and `:app` never inject it directly):
`ObservarAppearanceUseCase`, `ActualizarTemaUseCase`, `ActualizarColorUseCase`.

Mapping between `ThemePreference`/`ColorPreference` (domain) and `ThemeMode`/`ColorMode` (design
system) happens only in `:app`. The nominal duplication is deliberate: it keeps `:core:data` free
of `:core:designsystem`, and `SpenvoTheme` free of persistence concepts.

### Error handling

- Read failure or unknown persisted value → `SYSTEM` + `BRAND`.
- A `DYNAMIC` value found on API < 31 (restored backup, OS downgrade) is normalized to `BRAND` in
  the DataStore itself when read, not just in the emitted value — the corrected state is what gets
  persisted back, so the fix is permanent rather than reapplied on every collection.
- Write failure: the UI rolls back to the last confirmed value and shows a localized Snackbar. The
  affected preference axis is independent of the other; a failure on one never blocks the other.
  `DataStore.edit {}` already serializes transactions per file, so no additional per-axis lock is
  needed for correctness.

### Root coordination and startup

`AppearanceViewModel` (`:app`) exposes:

```kotlin
sealed interface AppearanceUiState {
    data object Loading : AppearanceUiState
    data class Ready(val themeMode: ThemeMode, val colorMode: ColorMode) : AppearanceUiState
}
```

`MainActivity` already installs the native splash (`installSplashScreen()`) and has a precedent for
gating on an async root state — `SesionGateViewModel`'s `EstadoGate.Cargando`, rendered as `Unit`
until resolved. `AppearanceViewModel.estado` is combined with that gate: the splash is retained
until both resolve, then `SpenvoTheme` composes with confirmed values. Any read error still resolves
to `Ready(SYSTEM, BRAND)` — the splash is never held indefinitely.

### Settings screen

New module `:feature:ajustes` (depends only on `:core:domain`, `:core:designsystem`, `:core:data`,
per the existing per-feature module rule), with its own Hilt split (`@Binds` module +
`@Provides` use-case module, the `PlanModule.kt` pattern).

```kotlin
@Serializable
data object AjustesRoute : NavKey
```

One screen, one section (Appearance) — no placeholder sections for future preferences (language,
notifications):

```
Ajustes
└── Apariencia
    ├── Tema      — Sistema / Claro / Oscuro   (RadioButton rows)
    └── Colores   — Spenvo / Colores dinámicos  (RadioButton rows)
```

Each option is a full Material 3 row: `ListItem` + `RadioButton`, whole row selectable, correct
`TalkBack` selection semantics, supporting text where it adds information, no clipping under large
font scale. Radio-button rows were chosen over segmented buttons (three luminosity options plus the
required Dynamic-disabled explanatory text don't fit a segmented control) and over
dialog-per-option pickers (which hide current state and add a step).

On API 30 and below, "Colores dinámicos" stays visible, disabled, with supporting text stating it
requires Android 12. On API 31+, both options are selectable. No Save button, no separate preview —
the applied change is the preview.

### Immediate apply

Each selection calls its use case directly; there is no pending/unsaved state. This matches
`SesionPreferences` (`marcarLogout`/`limpiarLogout` write immediately) and avoids the only
temporary, not-yet-persisted UI state the screen would otherwise have.

### Scope of the preference

Local to the device, one DataStore instance, shared by guest and any signed-in account — no `uid`
scoping, no Firestore sync. Consistent with `SesionPreferences`, with `:core:domain` never importing
Firebase, and with Spenvo being a shared/family expense app rather than a multi-profile device.

### Navigation and shared menu

The avatar today is not one shared component: `AvatarTopBarAction` (`:core:designsystem`) is reused,
but each of `HomeTopBar`, `MovimientosTopBar`, `CategoriasTopBar`, and `MiembrosTopBar` wraps it in
its own private `IconButton(onClick = onAbrirCuenta)`, while `PlanesScreen` already wraps it in a
local `CuentaMenu` (`DropdownMenu` with an account item) — added under `FEAT-U704`.

This design extracts a shared `AvatarMenu` into `:core:designsystem`, wrapping the existing
`AvatarTopBarAction` directly (no generic `@Composable` avatar slot):

```kotlin
@Composable
fun AvatarMenu(
    photoUrl: String?,
    onOpenAccount: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
)
```

It owns its own `DropdownMenu` open/close state, closes before invoking a callback, and presents
"Cuenta" and "Ajustes". It carries no navigation knowledge — callers wire the two callbacks.

Used in `PlanesScreen` and the four plan-tab top bars. Callbacks route through the existing
`backStack` in `MainActivity`:

```kotlin
onOpenAccount  -> backStack.pushUnlessTop(CuentaRoute)
onOpenSettings -> backStack.pushUnlessTop(AjustesRoute)
```

The login gate applies the persisted theme (it already renders through `SpenvoTheme`) but does not
show the Settings menu — it stays focused on authentication.

Explicitly out of scope: refactoring the five top bars beyond replacing the duplicated avatar
button with `AvatarMenu`.

## Delivery boundaries

### Delivery 2: theme preferences and settings (this design)

`AppearancePreferencesRepository` + use cases, `ThemePreferences` DataStore, `AppearanceViewModel`
root coordination, `:feature:ajustes` with `AjustesRoute`/`AjustesScreen`/`AjustesViewModel`, shared
`AvatarMenu`, es/en strings, and the full test plan below.

### Explicitly out of scope

Firebase-synced preferences, per-user (per-`uid`) preferences, custom color picker, medium/high
contrast, financial-color harmonization, per-category colors, M3 Expressive, custom
theme-transition animations, a global spacing system, and any Settings section beyond Appearance
(language, notifications, etc.).

## Testing and acceptance

- `:core:domain` — model defaults; use cases delegate to the repository and propagate its `Result`.
- `:core:data` — first read with no keys returns `SYSTEM`/`BRAND`; each axis persists without
  overwriting the other; persistence survives a fresh DataStore instance (instrumented test);
  unknown persisted value decodes to the default; `IOException`/corruption recovers to defaults
  without blocking the flow; `DYNAMIC` normalizes to `BRAND` in the store itself when the running
  API is below 31.
- `:app` (`AppearanceViewModel`) — initial state is `Loading`; first emission produces `Ready`;
  correct mapping for all six domain→design-system value pairs; a read error still resolves to
  `Ready(SYSTEM, BRAND)`; later flow emissions update the theme without recreating the Activity.
- `:feature:ajustes` — each `RadioButton` reflects the current preference; a selection invokes
  exactly the matching use case; Dynamic is enabled on API 31+ and visible-but-disabled below it; a
  write failure restores the last confirmed value and shows the Snackbar; large font scale does not
  clip any row.
- `AvatarMenu` and its five consumers — opens/closes correctly; "Cuenta" invokes only
  `onOpenAccount`; "Ajustes" invokes only `onOpenSettings`; wired correctly from `PlanesScreen` and
  the four plan-tab screens.
- Gates: `:app:assembleDebug`, `testDebugUnitTest`, `lintDebug` (`HardcodedText`/`MissingTranslation`
  as errors), `detekt`, plus the focused instrumented DataStore test on a compatible emulator/device.
