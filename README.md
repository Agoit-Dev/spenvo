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
- **Backend** (M2+): Firebase Auth · Firestore · Storage · App Check (BOM 34.17.0)
- **Images**: Coil 3.4.0
- **Quality**: detekt 1.23.8 · blocking lint (HardcodedText, MissingTranslation)

## Modules

```
:app                    — entry point, root NavDisplay, DI
:core:domain            — pure domain (no Android)
:core:data              — Room+SQLCipher, DataStore, repos
:core:designsystem      — theme and UI components
:feature:movimientos    — expenses + income
```

## How to run

```bash
./gradlew :app:assembleDebug      # build
./gradlew testDebugUnitTest       # unit tests
./gradlew lintDebug detekt        # quality
./gradlew dependencies --write-locks   # regenerate lockfiles
```

Requirements: JDK 21, Android SDK (compileSdk 37), a `local.properties` with `sdk.dir`.

## Documentation

- `AGENTS.md` — operational guide (stack, gates, conventions).
- `doc/architecture.md` — architecture decisions.
- `doc/database/schema.mdd` — versioned data schema.
- `doc/security/owasp.md` — OWASP 2025 security matrix.
- `CHANGELOG.md` — changes per milestone.
- `.agents/` — agent rules, skills and commands.

## Status

Milestone **M0 (Bootstrap)** in progress. See `CHANGELOG.md`.