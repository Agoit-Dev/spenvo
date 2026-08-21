# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.4.2] - 2026-08-21

### Changed (M3 close)

- Verified all M3 gates: `:app:assembleDebug`, `testDebugUnitTest`, `lintDebug`
  and `detekt` pass; Firestore rules validated against the Emulator (`spenvo-dev`,
  14/14 tests).
- Deployed the finalized `firestore.rules` and `firestore.indexes.json` to the
  production project (`spenvo-6d10a`), making the deny-by-default role-based rules
  live. `README.md` and `doc/security/owasp.md` updated.
- Remaining M3 manual step: confirm the Anonymous + Email/Password sign-in
  providers and App Check enforcement in the Firebase console.

## [0.4.1] - 2026-08-20

### Fixed (i18n)

- Spanish is now the default UI language (`values/`), matching the project
  convention (`ui-strings-i18n`); the previous build had Spanish in `values-es/`
  and English as the default, so devices on an English locale showed English.
- String keys renamed to English (snake_case): `plans_*`, `account_*`,
  `members_*`, `movements_*` (was `planes_*`, `cuenta_*`, `miembros_*`,
  `movimientos_*`). All `R.string.*` references updated in the four composables.
- English moved to `values-en/` (additive translation); `values-es/` removed.
- Note: `0.3.0`'s "Spanish strings (`values-es`)" entry documented the earlier,
  incorrect location.

## [0.4.0] - 2026-08-20

### Added (M3 — Plans, access, final rules + usable MVP)

- Account creation via email/password linking the anonymous UID
  (`linkWithCredential`), preserving local data without a merge:
  `AuthRepository.vincularEmail` + `VincularCredencialUseCase` in `:core:domain`
  (TDD), `FirebaseAuthRepository` update in `:core:data`, and the new
  `:feature:cuenta` screen (name, email, password). The account menu now shows the
  linked email instead of "Guest".
- Plans and shared access domain (TDD) in `:core:domain`: `PlanFinancieroRepository`,
  `AccesoPlanRepository` and use cases (`CrearPlan`, `ObservarPlanesDelUsuario`,
  `ObservarPlan`, `ActualizarPlan`, `InvitarMiembro`, `AceptarInvitacion`).
- Firestore remote layer in `:core:data`: `FirebasePlanFinancieroRepository`,
  `FirebaseAccesoPlanRepository`, DTO mappers, a `Task.await()` coroutine helper,
  and a `PlanSincronizador` (snapshot listener on the user's accesses → Room) that
  lives only while its Flow is collected (AGENTS rule 3). New DI wiring for the
  Room database (previously not in Hilt), DAOs, `PassphraseProvider`,
  `FirebaseFirestore`, and the plan use cases.
- `:feature:planes` (new): plan list (from Room), create-plan dialog (name +
  ISO 4217 currency), pending invitations with "Accept", and the account menu.
  The navigation root is now the plan list; `MovimientosRoute(planId)` is
  plan-scoped with a members action.
- `:feature:planes`/`:feature:cuenta` member screens: list members, invite by UID
  with a role selector, accept pending invitations.
- Final `firestore.rules` (M3): deny-by-default with server-side roles
  (owner/admin/editor/viewer); `editedBy`/`editedAt` deferred to M5. An owner
  creates their plan + OWNER access; admins invite; a user accepts their own
  pending invitation. `firestore.indexes.json` finalized (categories by
  planId+tipo; expenses/income by planId+fecha; access is single-field).
- `rules-tests/` (Node subproject): `firebase-tools` + `@firebase/rules-unit-testing`
  validate the rules matrix (14 tests) against the Firestore Emulator; `firebase.json`
  added at the repo root (`projectId: spenvo-dev`, emulator on port 8081).
  `node_modules/` and `firestore-debug.log` are gitignored.

### Changed

- Navigation root moved from `MovimientosRoute` to `PlanesRoute`; `MovimientosRoute`
  now carries a `planId` and its account menu moved to the plan list.
- Anonymous sign-in and sync now start from `PlanesViewModel` (was `MovimientosViewModel`).
- `:core:data` loads the SQLCipher native library in `SpenvoDatabase.build`
  (runtime, in addition to the migration-test fix), fixing a startup crash
  (`UnsatisfiedLinkError` on `SQLiteConnection.nativeOpen`).
- Added `libs.firebase.firestore` and regenerated dependency locks for
  `:core:data`, `:app`, `:feature:movimientos`, `:feature:cuenta`, `:feature:planes`.

### Docs

- `doc/architecture.md`: plans/access data flow, new modules, decisions.
- `doc/security/owasp.md`: Firestore rules + App Check status.

## [0.3.0] - 2026-08-20

### Added (M2 — Guest-first identity: anonymous auth + App Check)

- Firebase wired into `:app` (google-services plugin) and `:core:data`
  (`firebase-bom`, `firebase-auth`); App Check provider
  (`firebase-appcheck` + play-integrity in release, debug provider in debug).
- Guest-first anonymous session: `Sesion` model + `AuthRepository` contract +
  `IniciarSesionAnonimaUseCase` in `:core:domain` (TDD); `FirebaseAuthRepository`
  in `:core:data` signs in anonymously in the background and exposes the session
  as a `Flow`. Offline-first: the app opens directly to Movimientos; a failed
  sign-in is silent and retried every 30s.
- Account entry point in the `Movimientos` top app bar: shows the session state
  ("Guest"/"Invitado") and a "Create account" item that links to the M3 screen
  (placeholder).
- `android.permission.INTERNET` declared (required for any Firebase traffic).
- `:feature:movimientos` now depends on `:core:data` (Hilt binding for the
  auth repository); dependency locks regenerated.
- `:core:domain` keeps zero DI dependencies: the anonymous sign-in use case is
  provided via `@Provides` in `:core:data`.

### Changed

- `doc/architecture.md`: `:feature:movimientos → :core:data` edge added.
- Spanish strings (`values-es`) added for the account menu.

## [0.2.0] - 2026-08-20

### Added (M1 — Local data layer)

- Room schema v2 (`SpenvoDatabase`): six local cache entities (`usuarios`,
  `planes_financieros`, `acceso_plan_financiero`, `categorias`, `gastos`, `ingresos`)
  plus `sync_state` (v1, unchanged), all encrypted with SQLCipher.
- `MIGRATION_1_2` in SQL with the same DDL as the exported schema (`2.json`),
  validated by an instrumented migration test (`MigrationTestHelper` +
  SQLCipher `SupportOpenHelperFactory`).
- Mappers between domain models and Room entities, covered by unit tests.
- Schema wiring for the androidTest source set (`assets.directories`).
- New module `:core:security`: `PassphraseProvider` + `AndroidKeystorePassphraseProvider`
  (AES-256/GCM key in the Android Keystore; 256-bit passphrase generated on first
  use, stored encrypted). Instrumented lifecycle test
  (create/insert/close/reopen/read with SQLCipher).
- `SpenvoDatabase.build` now takes a `PassphraseProvider` (wired to
  `:core:security`) instead of a raw passphrase.
- Living docs updated: `doc/database/schema.mdd` v1.1 (Room v2),
  `doc/security/owasp.md` (Keystore control now active).

### Changed

- SQLCipher native library now loaded explicitly with `System.loadLibrary("sqlcipher")`
  (the 4.x `SQLiteDatabase.loadLibs` API was removed).
- `core:data` androidTest dependency lock updated (Room testing + androidx.test).
- New module `:core:security` added to the build (`settings.gradle.kts`) with its
  dependency lock; `androidx.sqlite:sqlite` catalog accessor added.

### Fixed

- Root cause of the earlier `MissingType` KSP error: the Room database file was
  corrupted; rewritten clean with all entities and converters.
- Instrumented migration test failed with `UnsatisfiedLinkError` on
  `SQLiteConnection.nativeOpen`; fixed by loading the native library in `@Before`.

## [0.1.0] - 2026-08-19

### Added (M0 — Bootstrap)

- Multi-module project: `:app`, `:core:domain`, `:core:data`, `:core:designsystem`,
  `:feature:movimientos`.
- Approved baseline stack: AGP 9.3.1 (built-in Kotlin 2.2.10), Compose BOM 2026.02.01,
  Material 3, Navigation 3 (1.1.4), Hilt 2.60.1, Room 2.8.4, SQLCipher 4.18.0,
  DataStore 1.2.1, Coil 3.4.0, kotlinx-serialization 1.11.0, coroutines 1.11.0.
- Green smoke build: compiles, unit tests, lint and detekt with the base architecture.
- Encrypted Room database with SQLCipher (`SpenvoDatabase` + `sync_state`), exported
  versioned schema.
- Security baseline: `allowBackup=false`, R8 enabled, `networkSecurityConfig`
  deny-by-default, versioned dependency locking, secrets out of the repo.
- Agent configuration: `AGENTS.md` + `.agents/` (12 rules, 7 skills, 3 commands).
- Blocking lint: `HardcodedText` and `MissingTranslation` as errors.
- Living docs: `doc/architecture.md`, `doc/database/schema.mdd` v1.0,
  `doc/security/owasp.md`.
- Drafts of `firestore.rules` and `firestore.indexes.json` (deny-by-default, roles).

### Changed

- Kotlin: pinned `kotlin-stdlib` to 2.2.10 for metadata compatibility (compiler 2.2.10).
- Coil pinned to 3.4.0 (3.5.0 requires Kotlin 2.4, incompatible with the 2.2.10 baseline).
- Project daemon JVM pinned to toolchain 21 (local JDK; avoids downloading JDK 25).

### Fixed

- Catalog version accessor `hilt-navigation-compose` (conflict with the Hilt extension).
- SQLCipher 4.18 API: `SQLiteDatabase.getBytes` is private; manual UTF-8 conversion.
- `dependencyLocking` moved to root `build.gradle.kts` + `subprojects` (Gradle 9).
- `android.disallowKotlinSourceSets=false` so KSP can register sources with
  built-in Kotlin.

### Technical notes (M0)

- `gradle-daemon-jvm.properties` points to toolchain 21 (JDK 21.0.3 installed locally).
- The app compiles and packages; on-device validation is pending
  (wireless ADB hung the install) — validated in M3 with the e2e smoke.
- Firebase (google-services plugin) is activated in M2, once the project exists.