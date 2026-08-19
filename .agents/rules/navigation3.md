# Rule: Navigation 3

## Purpose
Use Navigation 3 (state-driven) consistently; no string-based navigation or double navigator.

## Patterns
1. Back stack = list of `NavKey` (`@Serializable data object/class : NavKey`).
2. `NavDisplay` in `:app` with `entryProvider` DSL. `entry<T> { ... }`.
3. State-driven: navigate = `backStack.add(...)`; go back = `backStack.removeLastOrNull()`.
4. `onBack = { backStack.removeLastOrNull() }` for the system back gesture.
5. Mandatory decorators:
   - `rememberSaveableStateHolderNavEntryDecorator()` — saves per-entry state.
   - `rememberViewModelStoreNavEntryDecorator()` — NavEntry-scoped ViewModel.
6. List-Detail with Adaptive: `ListDetailSceneStrategy` (`adaptive-navigation3`),
   `listPane()/detailPane()` metadata; `rememberListDetailSceneStrategy<NavKey>()`.

## Rules
- ViewModels are scoped to the NavEntry (not the Activity) with `viewModel()` inside the entry.
- Routes carry serializable data; do not pass "fat" objects.
- Do not use Navigation 2 (androidx.navigation:navigation-compose) in parallel.
- Deep links: define only if the plan asks for it (M3+), with `entryProvider` and SavedStateConfiguration.

## Reference
- Correct API (1.1.4): `NavDisplay(backStack, onBack, entryDecorators, entryProvider)`.
- `scene<T>` does NOT exist: scenarios are resolved with SceneStrategy (List-Detail).