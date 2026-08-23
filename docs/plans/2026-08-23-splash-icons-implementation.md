# Spenvo Icons and Splash Screen Implementation Plan

> **For agentic workers:** Use `mobiai-mobile-executing-plans-with-subagents` to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Replace the placeholder launcher artwork with the approved Spenvo identity and add the AndroidX SplashScreen startup experience.

**Architecture:** Keep Google Play artwork outside Android resources, generate adaptive and legacy launcher resources from the transparent mark, and use a dedicated starting theme that hands off to `Theme.Spenvo`. Register AndroidX Core SplashScreen in the approved dependency baseline and invoke it before `super.onCreate()`.

**Tech Stack:** Android, Kotlin, XML resources, AndroidX Core SplashScreen 1.2.0, Gradle version catalog

**Platform:** Android

---

### Task 1: Dependency and baseline registration

**Files:**
- Modify: `AGENTS.md`
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

- [x] Add AndroidX Core SplashScreen 1.2.0 to the approved stack table.
- [x] Declare its version and library alias in the version catalog.
- [x] Add the catalog dependency to the app module.
- [x] Regenerate dependency locks after all implementation changes.

### Task 2: Launcher and splash artwork resources

**Files:**
- Create: `app/src/main/res/drawable-xxxhdpi/spenvo_splash_icon.png`
- Create: `app/src/main/res/drawable-xxxhdpi/ic_launcher_foreground_art.png`
- Create: `app/src/main/res/drawable-xxxhdpi/ic_launcher_monochrome.png`
- Replace: `app/src/main/res/mipmap-*/ic_launcher*`
- Modify: `app/src/main/res/drawable/ic_launcher_background.xml`
- Modify: `app/src/main/res/drawable/ic_launcher_foreground.xml`
- Modify: `app/src/main/res/mipmap-anydpi/ic_launcher.xml`
- Modify: `app/src/main/res/mipmap-anydpi/ic_launcher_round.xml`

- [x] Export density-aware splash artwork into `drawable-xxxhdpi`.
- [x] Rebuild adaptive foreground/background and monochrome resources with cream `#FFF8E7` and safe-zone-compliant artwork.
- [x] Generate legacy density variants for the existing launcher resource names.
- [x] Confirm no important artwork is clipped by common launcher masks.

### Task 3: Starting theme, activity integration, documentation, and verification

**Files:**
- Modify: `app/src/main/res/values/colors.xml`
- Modify: `app/src/main/res/values/themes.xml`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/agoitdev/spenvo/MainActivity.kt`
- Modify: `CHANGELOG.md`
- Modify: versioned Gradle lockfiles

- [x] Add the opaque splash background color.
- [x] Add `Theme.Spenvo.Starting` with the splash icon, background, and post-splash theme.
- [x] Apply the starting theme only to `MainActivity`.
- [x] Call `installSplashScreen()` before `super.onCreate()`.
- [x] Add an English Keep a Changelog entry.
- [x] Regenerate dependency locks.
- [x] Run `:app:assembleDebug`, `testDebugUnitTest`, `lintDebug`, and `detekt`.
- [x] Inspect the final launcher and splash resources for dimensions and transparency.

No task may commit, push, merge, or open a PR without separate explicit user approval.
