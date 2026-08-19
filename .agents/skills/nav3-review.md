# Skill: nav3-review

**Activate:** on any change to navigation, back stack or scoped ViewModels.

## Steps
1. Confirm every route is `@Serializable` and `: NavKey`.
2. Confirm `NavDisplay` is only in `:app` with the `entryProvider` DSL.
3. Confirm complete `entryDecorators` (saveable state + viewmodel store).
4. Confirm ViewModels are scoped to the NavEntry (`viewModel()` inside the entry)
   and NOT to the Activity.
5. Confirm `onBack` delegated to the system with `backStack.removeLastOrNull()`.
6. List-Detail: use `rememberListDetailSceneStrategy` + `listPane()/detailPane()` metadata
   (do not invent a custom strategy unless necessary).
7. Programmatic navigation always through the back stack (never string routes).

## Anti-patterns to flag
- Routes as strings.
- ViewModels in the Activity scope.
- `backStack` stored outside `rememberNavBackStack` (does not survive rotation/process death).