# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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