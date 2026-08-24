# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Spenvo — family/team expense tracking app. Native Android, Kotlin, Jetpack Compose (Material 3),
Navigation 3 (state-driven), multi-module Clean Architecture. Reimplements the legacy app
`act02-app_gastos` with security and tests from the first commit. Milestone **M6** (v0.7.0 — Home
dashboard + Adaptive List-Detail) is closed; **M7** (Profile) is in progress — see `CHANGELOG.md`
for the full milestone history and current state.

`AGENTS.md` and `.agents/` (rules, skills, commands) are the project's operational source of truth
and take precedence over generic tooling (e.g. MobiAI skills, which never fire automatically here).
Read `.agents/rules/*.md` before making non-trivial changes — this file summarizes the concrete,
non-obvious parts only.

## Commands

```
./gradlew :app:assembleDebug              # build
./gradlew testDebugUnitTest                # unit tests (all modules)
./gradlew :core:domain:testDebugUnitTest   # unit tests, single module
./gradlew lintDebug                        # lint (HardcodedText, MissingTranslation = error)
./gradlew detekt                           # static analysis (config/detekt/detekt.yml)
./gradlew dependencies --write-locks       # regenerate lockfiles after touching libs.versions.toml
```

A single test class/method: use the standard Gradle `--tests` filter, e.g.
`./gradlew :core:domain:testDebugUnitTest --tests "*.CategoriaUseCaseTest"`.

`rules-tests/` is a separate Node subproject that validates Firestore security rules against the
Firebase Emulator — not part of the Gradle build. Run it independently (`npm test` inside
`rules-tests/`) when touching `firestore.rules` or `core/data/src/androidTest/firestore.emulator.rules`.

### Definition of done (all required)

`assembleDebug` + `testDebugUnitTest` + `lintDebug` + `detekt` all green, `CHANGELOG.md` updated in
the same change, `doc/database/schema.mdd` versioned if the Room schema changed, lockfiles
regenerated if `libs.versions.toml` changed. Commit/push/PR only with explicit user OK — never assume
it from a prior approval. Milestones (M0→M8) close with a summary and wait for user OK before the
next phase.

## Module graph

```
:app                    entry point, root NavDisplay, root DI — the only module allowed to depend on everything
:core:domain            models, use cases, repository interfaces — pure Kotlin, no Android/Firebase imports
:core:security           Keystore-backed SQLCipher passphrase — depends on nothing but Android Keystore
:core:data              Room+SQLCipher, DataStore, Firestore repo impls + sync — depends on :core:domain, :core:security
:core:designsystem      theme, shared UI components — no feature/data deps
:feature:cuenta         account creation / email+password linking
:feature:planes         plans, shared access, invitations
:feature:movimientos    expenses + income, plan-scoped
:feature:categorias     categories: list/create/edit/delete, plan-scoped
```

Each `:feature:*` module depends only on `:core:domain`, `:core:designsystem`, `:core:data` — never
on another feature module. `:core:domain` must never import `androidx.room` or `com.google.firebase`
(that includes ViewModels living in domain-adjacent code).

DI follows a consistent per-feature-area Hilt module split: a `@Binds`-only abstract module for
repository/sync interfaces, plus a separate `@Provides` object module for use cases (see
`core/data/.../di/PlanModule.kt` for the pattern used across plans/categories).

## Data architecture (non-negotiable)

1. UI always reads from Room via `Flow`. Firestore never feeds the UI directly.
2. Writes are **optimistic Room-first**: Room updates immediately, then the write goes to Firestore
   (`set()` + `await()`). On a permanent failure (e.g. `PERMISSION_DENIED`) roll back Room — delete
   on create-fail, restore the prior snapshot on update/delete-fail. Deferred offline-buffered writes
   reconcile later via the snapshot listener, not via `await()`.
3. No homegrown outbox/WorkManager — Firestore's native offline cache is the only offline mechanism.
   (This was explicitly evaluated and rejected — see `.agents/rules/devils-advocate.md`.)
4. Firestore snapshot listeners attach only in an active shared scope (e.g. opening a plan/category
   screen) and detach on leaving. Never a global/app-lifetime listener. Reference implementations:
   `PlanSincronizador`, `CategoriaSincronizador`.
5. Conflicts are honest LWW: every synced entity carries `editedBy`/`editedAt` (server-set via
   Firestore rules, never trusted from the client) + `deletedAt`. Real concurrent-edit conflicts are
   surfaced in the UI, not silently resolved.
6. Default-data seeding (e.g. category seeding) uses deterministic ids (`planId:clave`) and a single
   batched write to stay idempotent under concurrent triggers.

## Navigation 3

Uses `androidx.navigation3` (NOT Navigation 2 — don't mix them). Key API shape (1.1.4):
`NavDisplay(backStack, onBack, entryDecorators, entryProvider)`, with
`rememberSaveableStateHolderNavEntryDecorator()` and `rememberViewModelStoreNavEntryDecorator()`
always included so ViewModels are scoped to the NavEntry, not the Activity. There is no `scene<T>`
API — adaptive list-detail layouts use a `SceneStrategy`
(`rememberListDetailSceneStrategy<NavKey>()` from `adaptive-navigation3`).

## Dependencies

All versions live in `gradle/libs.versions.toml` — never inline a version in a module
`build.gradle.kts`. Anything outside the approved baseline table in `AGENTS.md` needs explicit user
OK before adding. Gradle dependency locking is enforced repo-wide (`dependencyLocking` in root +
`subprojects{}`); any `libs.versions.toml` change requires `./gradlew dependencies --write-locks`
before the change is done. Kotlin/KSP compatibility is tight — KSP is pinned to match the Kotlin
version (`2.2.10-2.0.2`), and libraries requiring a newer Kotlin (e.g. Coil ≥3.5.0 needs Kotlin 2.4)
must be held back to a compatible version instead (Coil is pinned at 3.4.0 for this reason).

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
