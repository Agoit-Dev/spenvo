# Rule: Architecture

## Purpose
Keep Clean Architecture and one-directional dependencies.

## Layers and dependencies
```
UI (feature) → domain → data → (Room/Firebase/DataStore)
              ↘ designsystem
```
- `:core:domain` does NOT depend on Android or `:core:data`. Pure Kotlin only.
- `:core:data` depends on `:core:domain` (implements its repositories).
- `:core:designsystem` does NOT depend on features or data.
- `:feature:movimientos` depends on `:core:domain` and `:core:designsystem`.
- `:app` is the only module allowed to depend on everything (root composition).

## Rules
1. UI models (UI state) are distinct from domain models and entities.
2. Repositories in `:core:domain` define the contract; `:core:data` implements it.
3. ViewModels must not import `androidx.room` or `com.google.firebase` classes.
4. The UI knows nothing about Room or Firestore.
5. State hoisting: state lives in the ViewModel; composables receive state and callbacks.

## Common mistakes to avoid
- Putting business logic in composables or ViewModels.
- `:core:data` exposing Room entities to `:core:domain`.
- Circular dependencies between features.
- Using `Context` outside data/designsystem.