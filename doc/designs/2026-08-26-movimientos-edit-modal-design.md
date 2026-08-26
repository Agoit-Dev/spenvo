# Movimientos edit modal — fix + redesign

Status: approved, pending implementation.
Sub-project 1 of 3 in the UI/UX review requested 2026-08-25 (see conversation log). The other
two — startup loading feedback, and Home screen + bottom navigation — are separate sub-projects,
each with its own design cycle. Not addressed here.

## Problem

`MovimientoFormSheet` (`feature/movimientos/src/main/java/com/agoitdev/spenvo/movimientos/MovimientoFormSheet.kt`)
is shared between "create" and "edit" flows and has three problems:

1. **Type can be changed on an existing movimiento.** `FiltroTipoMovimiento`
   (`MovimientosScaffoldPartes.kt:74`) renders both Gasto/Ingreso `FilterChip`s fully interactive
   regardless of whether a `movimientoExistente` is being edited. A movimiento's type is not
   editable data — `Gasto` and `Ingreso` are separate domain types with separate DAOs, Firestore
   collections, and use cases (`CrearGastoUseCase`/`CrearIngresoUseCase`,
   `EliminarGastoUseCase`/`EliminarIngresoUseCase`, etc.). Changing type in place isn't a valid
   edit operation in this architecture — it would require deleting one entity and creating an
   unrelated one, breaking `editedBy`/`editedAt` continuity and conflict tracking, which are keyed
   by the original id.
2. **Category resets to the first one on open.** In `MovimientoFormEstadoYContenido`
   (`MovimientoFormSheet.kt:120-124`), the `LaunchedEffect(categoriasDisponibles)` guard fires
   while `categoriasDisponibles` is still the initial empty list emitted by the `StateFlow`
   before Room/Firestore data loads. Because the guard doesn't distinguish "still loading" from
   "genuinely has no matching category," it wipes the real `categoriaId` during that transient
   empty state; when the real list arrives a moment later, `categoriaId` is already blank and
   falls back to `firstOrNull()`. If the user saves without touching the category selector, the
   movimiento silently changes category.
3. **No view-before-edit protection.** Today `editando: Boolean` in `MovimientoFormContenido`
   only changes the sheet title and whether the Eliminar button exists — it does not gate field
   editability. The sheet opens fully editable immediately, with no way to back out short of
   dismissing the whole sheet. (Deletion itself already goes through a confirmation dialog —
   `ConfirmarEliminarMovimientoDialog` in `MovimientoFormularioHost.kt:147-165` — this point is
   about field editability, not delete safety.)

## Interaction model

**Crear (no `movimientoExistente`)**: unchanged. Opens directly editable, no Eliminar button.

**Editar (existing movimiento)**: opens in **view mode**:
- Monto, categoría, and descripción are read-only/disabled.
- Type chips are always disabled for an existing movimiento (see below) — this never changes,
  view mode or not.
- Only an "Editar" button is visible. No Guardar, Cancelar, or Eliminar yet.

Pressing **Editar** enables monto/categoría/descripción and reveals Guardar, Cancelar, and
Eliminar. Type stays locked.

Pressing **Cancelar** discards any unsaved field changes and returns to view mode — it does not
dismiss the sheet. Rationale: view mode is a legitimate resting state (the movimiento's detail
view), not just a gate before editing, so cancelling an edit should return to that state rather
than kick the user out of a screen they may have opened just to look something up.

Pressing **Guardar** behaves as it does today: on success the sheet closes automatically
(`EfectosMovimientos` in `MovimientosScreen.kt:69`, unaffected by this change).

Pressing **Eliminar** opens the confirmation dialog (already exists, see below) instead of
deleting immediately — unchanged from today, just now only reachable after Editar.

### Type chip visual treatment

Both chips stay visible and always show which type the movimiento actually is, even though
neither is clickable. `FilterChip` already exposes `selected` and `enabled` as independent
parameters, so this needs no new mechanism:

- The chip matching the movimiento's real type: `selected = true, enabled = false` (shown
  highlighted, not clickable).
- The other chip: `selected = false, enabled = false` (shown dim/inactive, not clickable).

### Category loading fix

Change the `LaunchedEffect(categoriasDisponibles)` guard in `MovimientoFormEstadoYContenido` so
it only corrects `categoriaId` once the list has genuinely loaded, not during the transient empty
state:

```kotlin
LaunchedEffect(categoriasDisponibles) {
    if (categoriasDisponibles.isNotEmpty() && categoriasDisponibles.none { it.id == categoriaId }) {
        categoriaId = categoriasDisponibles.first().id
    }
}
```

This still covers both real cases — a new movimiento with a blank `categoriaId` gets defaulted to
the first category once the list loads, and an existing movimiento whose stored category no
longer exists falls back safely — while never touching `categoriaId` during the empty-list
loading window.

### Delete confirmation — de-duplicate, not add

Both modules already have their own confirmation dialog, not just one:
`ConfirmarEliminarDialog` (private, `CategoriasScreen.kt:346`) and
`ConfirmarEliminarMovimientoDialog` (private, `MovimientoFormularioHost.kt:147-165`) — near-
identical `AlertDialog`s, each hardcoded to its own module's strings. This is duplication to
remove, not a missing feature to add.

`:core:designsystem` already has a precedent for a shared cross-feature `AlertDialog`:
`ConflictoDialog.kt` (`core/designsystem/.../conflict/ConflictoDialog.kt`) — public, and
critically, it takes its strings **pre-resolved by the caller** as a plain data class
(`ConflictoDialogTextos`) rather than calling `stringResource` internally, since
`:core:designsystem` has no `res/values/strings.xml` of its own in this project. The new shared
dialog follows the same shape:

```kotlin
data class ConfirmarEliminarTextos(
    val titulo: String,
    val mensaje: String,
    val confirmar: String,
    val cancelar: String,
)

@Composable
fun ConfirmarEliminarDialog(
    textos: ConfirmarEliminarTextos,
    onConfirmar: () -> Unit,
    onCancelar: () -> Unit,
)
```

Each feature module keeps resolving its own existing `stringResource(...)` calls (categorías'
`categories_delete_confirm_*`/`categories_cancel`, movimientos' `movements_delete_confirm_*`/
`movements_cancel` — both already exist in both languages, no new string keys needed for this
part) and passes them into `ConfirmarEliminarTextos` when calling the shared dialog. No string
keys are centralized or renamed.

`CategoriasScreen.kt`'s `CategoriaFormularioSheet` and `MovimientoFormularioHost.kt`'s
`MovimientoFormularioEstado` both switch to the shared dialog, and their private
`ConfirmarEliminarDialog`/`ConfirmarEliminarMovimientoDialog` composables are deleted.

Eliminar in the movimientos modal keeps opening this dialog after Editar is pressed (behavior
unchanged, just now only reachable from edit mode); confirming still calls the existing
`viewModel.eliminar(movimiento)`; cancelling still just closes the dialog.

## Modules touched

- `:feature:movimientos` — `MovimientoFormSheet.kt` (view/edit state, chip gating, category-load
  fix), wiring for the delete confirmation dialog.
- `:core:designsystem` — new shared `ConfirmarEliminarDialog`.
- `:feature:categorias` — `CategoriasScreen.kt` switches to the shared dialog, removing the
  private one.

## Testing (strict TDD per `AGENTS.md` — failing test precedes implementation)

- Compose UI test: opening the edit modal for an existing movimiento shows disabled fields and
  only the "Editar" button.
- Compose UI test: pressing Editar enables monto/categoría/descripción while type chips stay
  disabled.
- Compose UI test: type chip for the movimiento's real type renders `selected = true` while
  disabled; the other renders `selected = false` while disabled.
- Compose UI test: pressing Cancelar after making edits reverts field values and returns to view
  mode without dismissing the sheet.
- Compose UI test: pressing Eliminar opens the confirmation dialog instead of deleting directly;
  confirming calls through to the existing delete path.
- Compose/ViewModel test reproducing the category-loading race: a `StateFlow` that emits an empty
  list first and the real list (containing the movimiento's stored `categoriaId`) afterward must
  leave `categoriaId` unchanged.
- `:feature:categorias` has no Compose test setup at all today (only `junit` +
  `kotlinx-coroutines-test`, confirmed against `build.gradle.kts`) — this change adds Robolectric
  + `ui-test-junit4` there (mirroring `:feature:movimientos`/`:feature:planes`/`:feature:cuenta`)
  and writes that module's first Compose test, confirming `CategoriaFormularioSheet` still opens
  the shared `ConfirmarEliminarDialog` and deletes correctly after the swap.

## Out of scope (explicitly)

- Splash screen loading feedback and the post-splash "no plans" flash — separate sub-project.
- Home screen and bottom navigation — separate sub-project.
- Any change to `:feature:cuenta`, `:feature:planes` beyond the dialog promotion above.
- Google Sign-In, MFA, osv-scanner (M8 / OWASP debt) — unrelated.

## Reusable pattern — apply to other screens with similar structure

The view-before-edit gating (open read-only → explicit "Editar" to unlock fields → Cancelar
returns to view rather than dismissing → Eliminar behind a shared confirmation dialog) is not
specific to movimientos. `:feature:planes`'s plan edit sheet and `:feature:categorias`'s category
edit sheet have the same shape today: open straight into a fully editable, immediately deletable
form, no confirmation, no cancel-to-view. When either of those screens comes up for its own
redesign, apply the same pattern instead of re-deriving it. Also reuse the `selected`+`enabled`
independent-flags trick for any other case of "show the current value on a chip/toggle without
letting it be changed."

This is not part of this change's scope — noted here, and saved as decisions in MobiAI Brain
(`android`/`ui_patterns`, `android`/`domain_model`, `android`/`designsystem`), so it surfaces
automatically via `mobiai brain review` when those screens are next touched.
