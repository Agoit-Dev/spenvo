# Spenvo

Family/team expense tracking app. Native Android, Kotlin, Jetpack Compose
(Material 3), Navigation 3, multi-module with Clean Architecture. Financial data
encrypted with SQLCipher; sync with Firebase (Auth, Firestore, Storage, App Check).

## Stack

- **Build**: AGP 9.3.1 (built-in Kotlin 2.2.10) · Gradle 9.5 · JDK 21 (daemon toolchain)
- **UI**: Compose BOM 2026.02.01 · Material 3 · Adaptive (List-Detail)
- **Navigation**: Navigation 3 (1.1.4) state-driven
- **DI**: Hilt 2.60.1 + KSP2 (2.2.10-2.0.2)
- **Data**: Room 2.8.4 + SQLCipher 4.18.0 · DataStore 1.2.1 · Coroutines/Flow 1.11.0
- **Security**: SQLCipher passphrase in the Android Keystore (AES-256/GCM, `:core:security`)
- **Backend** (M2+): Firebase Auth (anonymous guest-first) · Firestore · Storage · App Check (BOM 34.17.0)
- **Images**: Coil 3.4.0
- **Quality**: detekt 1.23.8 · blocking lint (HardcodedText, MissingTranslation)

## Modules

```
:app                    — entry point, root NavDisplay, DI
:core:domain            — pure domain (no Android)
:core:data              — Room+SQLCipher, DataStore, repos, Firestore sync
:core:security          — Keystore-backed SQLCipher passphrase
:core:designsystem      — theme and UI components
:feature:cuenta         — account creation / email+password linking
:feature:planes         — plans, shared access, invitations
:feature:movimientos    — expenses + income (plan-scoped)
```

## How to run

```bash
./gradlew :app:assembleDebug      # build
./gradlew testDebugUnitTest       # unit tests
./gradlew connectedDebugAndroidTest  # instrumented tests (device/emulator)
./gradlew lintDebug detekt        # quality
./gradlew dependencies --write-locks   # regenerate lockfiles
```

### Firestore rules tests (Node)

```bash
cd rules-tests
npm install                       # install firebase-tools + rules-unit-testing
npm test                          # run the rules matrix against the Emulator (14 tests)
```

The emulator uses `projectId: spenvo-dev` and listens on port 8081 (`firebase.json`).

Requirements: JDK 21, Android SDK (compileSdk 37), a `local.properties` with `sdk.dir`;
Node 20+ (only for the `rules-tests/` subproject).

## Documentation

- `AGENTS.md` — operational guide (stack, gates, conventions).
- `doc/architecture.md` — architecture decisions.
- `doc/database/schema.mdd` — versioned data schema.
- `doc/security/owasp.md` — OWASP 2025 security matrix.
- `CHANGELOG.md` — changes per milestone.
- `.agents/` — agent rules, skills and commands.

## Status

Milestone **M3 (Plans, access, final rules, usable MVP)** complete: account
creation (email/password linking the anonymous UID), plans with shared access and
invitations, and the finalized Firestore rules validated against the Emulator
(14 tests) and deployed to production (`spenvo-6d10a`). Pending manual
verification of the Auth sign-in providers and App Check in the Firebase console.
See `CHANGELOG.md`.