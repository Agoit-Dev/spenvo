# AGENTS.md

Working guide for AI agents and humans in the **Spenvo** repository.

## What is Spenvo

Family/team expense tracking app. Native Android, Kotlin, Jetpack Compose
(Material 3), Navigation 3 (state-driven), multi-module. Requirements and the
functional guide come from the legacy app in `act02-app_gastos`; Spenvo
reimplements it with Clean Architecture, security and tests from the first commit.

## Language conventions

- **Code and technical docs**: English.
- **UI (strings)**: Spanish by default (`values/`), keys in English.
- **CHANGELOG.md**: English (Keep a Changelog format).
- **Commit messages**: brief, in repo style.

## Stack (approved baseline)

| Layer | Choice | Version |
|---|---|---|
| Build | AGP + built-in Kotlin | 9.3.1 / Kotlin 2.2.10 |
| Compose | BOM + Material 3 + Adaptive (List-Detail) | 2026.02.01 / 1.3.0 |
| Navigation | Navigation 3 (`androidx.navigation3`) | 1.1.4 |
| DI | Hilt + KSP2 | 2.60.1 / 2.2.10-2.0.2 |
| Persistence | Room + SQLCipher | 2.8.4 / 4.18.0 |
| Async | Coroutines / Flow | 1.11.0 |
| Serialization | kotlinx-serialization | 1.11.0 |
| Images | Coil 3 | 3.4.0 |
| Prefs | DataStore Preferences | 1.2.1 |
| Backend | Firebase (Auth, Firestore, Storage, App Check) | BOM 34.17.0 |
| Quality | detekt + blocking lint | 1.23.8 |

> Outside the baseline: **any** new dependency requires explicit user OK.

## Module structure

```
:app                    — entry point, root NavDisplay
:core:domain            — models, use cases, repositories (no Android)
:core:data              — Room+SQLCipher, DataStore, repo implementations
:core:designsystem      — theme, shared UI components
:feature:movimientos    — combined expenses + income
```

## Data architecture (non-negotiable rules)

1. The UI ALWAYS reads from Room via Flow. Firestore never feeds the UI directly.
2. Writes go to Firestore (native offline cache). **NO homegrown outbox/WorkManager.**
3. Snapshot listeners ONLY in active shared scopes (attach when opening a screen,
   detach when leaving). Never global listeners.
4. On-demand sync + pull-to-refresh. No complex TTL.
5. Conflict: LWW with `editedBy/editedAt` + conflict visible in UI.

## Gates (definition of done)

A change is ready only if **all** apply:

- `./gradlew :app:assembleDebug` builds.
- `./gradlew testDebugUnitTest` green.
- `./gradlew lintDebug` green (HardcodedText and MissingTranslation = error).
- detekt without findings (`./gradlew detekt`).
- `CHANGELOG.md` updated.
- `doc/database/schema.mdd` versioned if the schema changed.
- `doc/` living docs updated if applicable.
- Dependency locks updated (`--write-locks`) if `libs.versions.toml` changed.
- Push/PR/commit only with **explicit user OK**.
- Phases (M0→M8): close each with a summary and wait for user OK.

## Working with agents

- **`.agents/`** is the project's source of truth (rules/skills/commands). Read before acting.
- **Engram**: persistent session memory (`mem_save`/`mem_search`/`mem_session_summary`).
- **MobiAI**: mobile toolbox skills, only invoked **explicitly** when they apply
  (testing, debugging, planning, TDD, etc.). None fires automatically.
- `.engram/` and `.mobiai/` are gitignored (local).

## Useful commands

See `.agents/commands/` for custom commands. The most used:

- `./gradlew :app:assembleDebug` — builds.
- `./gradlew testDebugUnitTest lintDebug detekt` — checks.
- `./gradlew dependencies --write-locks` — regenerates lockfiles.
- `mobiai status` — toolbox status.