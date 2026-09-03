# Spenvo

Spenvo is a privacy-focused native Android app for tracking personal, family,
and shared expenses. Users organize expenses and income into financial plans,
collaborate through invitations, continue working offline, and resolve
synchronization conflicts explicitly.

The project is built with Kotlin and Jetpack Compose using a multi-module Clean
Architecture. Financial data is encrypted locally with SQLCipher and synchronized
through Firebase.

> **Active development:** Spenvo is not yet distributed as a production release.

## Screenshots

_Screenshots coming soon — see the [changelog](CHANGELOG.md) for current feature status._

<!--
Add 3–4 representative screenshots when the final assets and repository paths
are decided. Suggested set:

- Plans dashboard
- Movements list and edit flow
- Profile
- Compact and expanded adaptive layouts
-->

## Features

- Personal and shared financial plans.
- Expense and income tracking scoped to each plan.
- Custom expense and income categories, including deterministic default seeding.
- Guest-first access with optional email and password account linking.
- Shared-plan invitations and role-based access.
- Offline-capable local storage and Firebase synchronization.
- Optimistic local updates with rollback on permanent remote failures.
- Visible last-write-wins conflict resolution for concurrent edits.
- Profile management with avatar upload.
- Adaptive layouts for compact and expanded screens.
- Spanish default UI with English translations.

## Project status

Spenvo is under active development.

- **Latest completed milestone:** M8 — OSV-Scanner CI gate (baseline triaged,
  required status check, scheduled-scan issue lifecycle validated) and
  optional MFA discovery (deliberately deferred, not implemented).
- **Latest completed delivery:** `UI-THEME-001` — Material 3 design-system
  foundation (`:core:designsystem`): explicit Brand Light/Dark schemes,
  independent luminosity/color-source configuration, stable financial
  semantic colors, typography, shapes, and a preview catalog. User-facing
  theme preferences and persistence are a separate, deferred delivery.
- **Current milestone:** none in progress; remaining work is unassigned
  backlog debt (see `backlog.md`).

See the [changelog](CHANGELOG.md) for completed work and implementation details.

## Architecture

Spenvo follows unidirectional data flow and Clean Architecture:

```text
Compose UI
    ↓ events       ↑ UiState / Flow
ViewModel
    ↓
Use cases
    ↓
Repository interfaces
    ↓
Repository implementations
    ├── Room + SQLCipher ──→ observable local state ──→ UI
    └── Firebase ──────────→ remote synchronization
```

Key data rules:

- The UI always observes Room through `Flow`; Firestore never feeds the UI
  directly.
- Writes update Room optimistically and are then sent to Firestore.
- Permanent remote failures roll back the corresponding local mutation.
- Firestore's native offline cache handles deferred remote writes; the project
  does not maintain a custom outbox.
- Snapshot listeners are attached only while a shared scope is active.
- Concurrent edits use last-write-wins metadata and expose conflicts in the UI.
- Navigation is state-driven with Navigation 3; the application owns its typed
  back stack.

For the complete architectural decisions, see
[Architecture](doc/architecture.md).

## Technology

- **Build:** AGP 9.3.1, built-in Kotlin 2.2.10, Gradle 9.5, JDK 21 daemon
  toolchain.
- **UI:** Jetpack Compose, Material 3, and Material 3 Adaptive list-detail
  layouts.
- **Navigation:** Navigation 3 with a state-driven typed back stack.
- **Dependency injection:** Hilt with KSP2.
- **Persistence:** Room, SQLCipher, and DataStore.
- **Backend:** Firebase Auth, Firestore, Storage, and App Check.
- **Asynchronous state:** Kotlin Coroutines and Flow.
- **Images:** Coil 3.
- **Quality:** JUnit, Compose UI tests, blocking Android lint, and detekt.

Exact dependency versions are centralized in
[`gradle/libs.versions.toml`](gradle/libs.versions.toml); module build files do
not declare versions inline.

## Modules

```text
:app                    — application entry point, root NavDisplay, DI
:core:domain            — domain models, use cases, repository contracts
:core:data              — Room, DataStore, repositories, Firebase sync
:core:security          — Keystore-backed SQLCipher passphrase
:core:designsystem      — theme and shared UI components
:feature:cuenta         — registration, account linking, and profile
:feature:planes         — plans, shared access, and invitations
:feature:movimientos    — plan-scoped expenses and income
:feature:categorias     — expense and income category management
```

Feature modules depend on core modules, never on other feature modules. The
`:app` module is the only module allowed to integrate all features.

## Quick start

### Prerequisites

- JDK 21.
- Android Studio with Android SDK 37 installed.
- A project-local `local.properties` containing `sdk.dir`.
- A Firebase Android configuration file at `app/google-services.json`.
- Node.js 20 or newer only when running the Firebase rules tests.

### Firebase configuration

Download `google-services.json` for the Android app from the appropriate
Firebase console project and place it at:

```text
app/google-services.json
```

The file is gitignored and must never be committed. The Android build cannot
wire Firebase Auth, Firestore, Storage, and App Check without it.

### Build the debug app

macOS or Linux:

```bash
./gradlew :app:assembleDebug
```

Windows PowerShell:

```powershell
.\gradlew.bat :app:assembleDebug
```

## Verification

Run the main local quality gates:

macOS or Linux:

```bash
./gradlew testDebugUnitTest lintDebug detekt
```

Windows PowerShell:

```powershell
.\gradlew.bat testDebugUnitTest lintDebug detekt
```

Run instrumented tests with a device or emulator available:

```bash
./gradlew connectedDebugAndroidTest
```

Lint treats hardcoded UI text and missing translations as blocking errors.

### Firebase rules tests

The separate Node.js test project validates the Firestore and Storage security
rules against Firebase Emulator Suite:

```bash
cd rules-tests
npm install
npm test
```

The emulator configuration uses project ID `spenvo-dev`, Firestore port `8081`,
and Storage port `9199`. These tests are independent from the Gradle build.

### Dependency locks

Dependency locking is enforced across the project. After an approved change to
`gradle/libs.versions.toml`, regenerate the lockfiles with:

```bash
./gradlew dependencies --write-locks
```

## Documentation

- [Development guide](AGENTS.md) — stack, conventions, architectural boundaries,
  and definition of done.
- [Architecture](doc/architecture.md) — architectural decisions and rationale.
- [Database schema](doc/database/schema.mdd) — versioned data model.
- [Security](doc/security/owasp.md) — OWASP security matrix.
- [Changelog](CHANGELOG.md) — milestone history and notable changes.
- [Agent resources](.agents/) — project-specific rules, skills, and commands.

## Development conventions

- Code and technical documentation are written in English.
- Spanish is the default UI locale; English translations live in `values-en/`.
- New behavior is developed test-first.
- New dependencies require explicit approval.
- Versions belong in the version catalog, never inline in a module build file.
- Changes are ready only after build, tests, lint, and detekt pass.
- Security-rule changes additionally require the Node.js emulator test suite.

See [AGENTS.md](AGENTS.md) for the complete contribution and verification rules.

## Security

Spenvo applies defense-in-depth to financial data:

- Room data is encrypted with SQLCipher.
- The database passphrase is generated and protected by Android Keystore.
- Cleartext traffic and Android backups are disabled.
- Firebase App Check gates backend access.
- Firestore and Storage rules are deny-by-default and role- or owner-scoped.
- Amounts, email addresses, and credentials must never be logged.

See the [OWASP security matrix](doc/security/owasp.md) for the detailed controls.

## License

MIT — see [LICENSE](LICENSE).
