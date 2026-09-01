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
| Icons | Compose Material Icons Extended | via Compose BOM |
| Startup | AndroidX Core SplashScreen | 1.2.0 |
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

All versions live in `gradle/libs.versions.toml` — never inline a version in a module
`build.gradle.kts`. Gradle dependency locking is enforced repo-wide (`dependencyLocking` in root +
`subprojects{}`); any `libs.versions.toml` change requires regenerating every module's lockfile
(see "Useful commands" below — the bare root-level invocation doesn't cover the modules) before the
change is done. Kotlin/KSP compatibility is tight — KSP is pinned to match the Kotlin
version (`2.2.10-2.0.2`), and libraries requiring a newer Kotlin (e.g. Coil ≥3.5.0 needs Kotlin 2.4)
must be held back to a compatible version instead (Coil is pinned at 3.4.0 for this reason).

## Module structure

```
:app                    — entry point, root NavDisplay
:core:domain            — models, use cases, repositories (no Android)
:core:data              — Room+SQLCipher, DataStore, repo implementations, Firestore sync
:core:security          — Keystore-backed SQLCipher passphrase
:core:designsystem      — theme, shared UI components
:feature:cuenta         — account creation / email+password linking
:feature:planes         — plans, shared access, invitations
:feature:movimientos    — expenses + income (plan-scoped)
:feature:categorias     — expense/income categories (CRUD, default seeding)
```

Each `:feature:*` module depends only on `:core:domain`, `:core:designsystem`, `:core:data` — never
on another feature module. `:app` is the only module allowed to depend on everything. `:core:domain`
must never import `androidx.room` or `com.google.firebase` (that includes ViewModels living in
domain-adjacent code). `:core:security` depends on nothing but the Android Keystore.

DI follows a consistent per-feature-area Hilt module split: a `@Binds`-only abstract module for
repository/sync interfaces, plus a separate `@Provides` object module for use cases (see
`core/data/.../di/PlanModule.kt` for the pattern used across plans/categories).

## Navigation 3

Uses `androidx.navigation3` (NOT Navigation 2 — don't mix them). Key API shape (1.1.4):
`NavDisplay(backStack, onBack, entryDecorators, entryProvider)`, with
`rememberSaveableStateHolderNavEntryDecorator()` and `rememberViewModelStoreNavEntryDecorator()`
always included so ViewModels are scoped to the NavEntry, not the Activity. There is no `scene<T>`
API — adaptive list-detail layouts use a `SceneStrategy`
(`rememberListDetailSceneStrategy<NavKey>()` from `adaptive-navigation3`).

## Data architecture (non-negotiable rules)

1. The UI ALWAYS reads from Room via Flow. Firestore never feeds the UI directly.
2. Writes go to Firestore (native offline cache). **NO homegrown outbox/WorkManager.** This was
   explicitly evaluated and rejected — see `.agents/rules/devils-advocate.md` before reopening
   that discussion.
3. Snapshot listeners ONLY in active shared scopes (attach when opening a screen,
   detach when leaving). Never global listeners.
4. On-demand sync + pull-to-refresh. No complex TTL.
5. Conflict: LWW with `editedBy/editedAt` + conflict visible in UI.

Writes are **optimistic Room-first**: Room updates immediately, then the write goes to Firestore
(`set()` + `await()`). On a permanent failure (e.g. `PERMISSION_DENIED`) roll back Room — delete
on create-fail, restore the prior snapshot on update/delete-fail. Deferred offline-buffered writes
reconcile later via the snapshot listener, not via `await()`.

Default-data seeding (e.g. category seeding) uses deterministic ids (`planId:clave`) and a single
batched write to stay idempotent under concurrent triggers.

## Security (OWASP-driven, not a patch)

- `android:allowBackup="false"` in the manifest; R8 `optimization.enable = true` in release builds;
  cleartext traffic disabled.
- SQLCipher passphrase comes from Android Keystore (`:core:security`), generated on first use — never
  hardcoded or derived from a static secret.
- App Check (Play Integrity) gates all backend calls. Firestore rules are deny-by-default with
  server-side role checks — never `allow ... if true`.
- Never log amounts, emails, or credentials.

## i18n

`values/` (Spanish) is the default locale; `values-en/` holds English. Keys are English snake_case,
values are the translated strings. Both `HardcodedText` and `MissingTranslation` are **blocking lint
errors** — a new UI string must land in both `values/strings.xml` and `values-en/strings.xml` in the
same change, and money/date formatting must go through `NumberFormat`/`DateUtils`/the project's
`Money` type, never hand-concatenation.

## Testing

Strict TDD: a failing test precedes the implementation that makes it pass. A change without covering
tests is expected to be rejected (documented exceptions only, noted in the milestone summary). Unit
tests use JUnit4 + `kotlinx-coroutines-test`; ViewModel tests use plain JUnit with hand-written fakes
(no MockK) — see `CategoriasViewModelTest` as the reference pattern; DAOs are tested against an
in-memory Room database. `:feature:planes` and `:feature:cuenta` have Compose UI test setups
(Robolectric + `ui-test-junit4`) as of M6/M7; other feature modules don't yet — don't assume
coverage there.

## Gates (definition of done)

A change is ready only if **all** apply:

- `./gradlew :app:assembleDebug` builds.
- `./gradlew testDebugUnitTest` green.
- `./gradlew lintDebug` green (HardcodedText and MissingTranslation = error).
- detekt without findings (`./gradlew detekt`).
- `CHANGELOG.md` updated.
- `backlog.md` updated (task moved to the right status section, checked off if done) and
  `ROADMAP.md` updated if the change closes or opens a phase-level item.
- `doc/database/schema.mdd` versioned if the schema changed.
- `doc/` living docs updated if applicable.
- Dependency locks updated (`--write-locks`) if `libs.versions.toml` changed.
- Push/PR/commit only with **explicit user OK**.
- Phases (M0→M8): close each with a summary and wait for user OK.

## Working with agents

- **`ROADMAP.md`** and **`backlog.md`** (repo root): mandatory reading before proposing an
  architectural change or starting a new feature. `ROADMAP.md` is the strategic phase map;
  `backlog.md` is the atomic task list, moved between its status sections (and checked off) as work
  lands — keep both current as part of finishing a change, the same way `CHANGELOG.md` already is.
- **`.agents/`** is the project's source of truth (rules/skills/commands). Read before acting.
- **Engram**: persistent session memory (`mem_save`/`mem_search`/`mem_session_summary`).
- **MobiAI**: mobile toolbox skills, only invoked **explicitly** when they apply
  (testing, debugging, planning, TDD, etc.). None fires automatically.
- `.engram/` and `.mobiai/` are gitignored (local).

## Useful commands

See `.agents/commands/` for custom commands. The most used:

- `./gradlew :app:assembleDebug` — builds.
- `./gradlew testDebugUnitTest lintDebug detekt` — checks.
- `./gradlew :app:dependencies :core:domain:dependencies :core:data:dependencies :core:security:dependencies :core:designsystem:dependencies :feature:cuenta:dependencies :feature:planes:dependencies :feature:movimientos:dependencies :feature:categorias:dependencies --write-locks`
  — regenerates every module's `gradle.lockfile`. The bare root-level `./gradlew dependencies
  --write-locks` only touches the root project's own (dependency-free) configurations and silently
  leaves every module's lockfile stale — always target modules explicitly.
- `mobiai status` — toolbox status.

A single test class/method: use the standard Gradle `--tests` filter, e.g.
`./gradlew :core:domain:testDebugUnitTest --tests "*.CategoriaUseCaseTest"`.

`rules-tests/` is a separate Node subproject that validates Firestore security rules against the
Firebase Emulator — not part of the Gradle build. Run it independently (`npm test` inside
`rules-tests/`) when touching `firestore.rules` or `core/data/src/androidTest/firestore.emulator.rules`.
