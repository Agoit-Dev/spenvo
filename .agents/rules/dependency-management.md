# Rule: Dependency Management

## Purpose
Strict dependency control: approved baseline, versioned locks, gated changes.

## Rules
1. All dependencies are declared in `gradle/libs.versions.toml` (catalog). Never
   inline versions in build files.
2. **Approved baseline** (see AGENTS.md → Stack). Any dependency OUTSIDE the
   baseline requires explicit user OK (different from just changing a version).
3. Changing the version of a baseline dependency:
   - Regenerate locks: `./gradlew dependencies --write-locks`. Do NOT use
     `:app:assembleDebug --write-locks` — it only resolves what `:app` needs to
     assemble, so it skips test/androidTest/lint/release configurations of
     modules not on that path, leaving their lockfiles stale.
   - Run full gates (build + tests + lint + detekt).
   - Note the change in `CHANGELOG.md` and in the milestone summary.
4. Version compatibility (Kotlin 2.2.10):
   - Libraries compiled with Kotlin > 2.3 do NOT compile (metadata). Verify before bumping.
     Real example: Coil 3.5.0 requires Kotlin 2.4 → pinned to 3.4.0.
   - KSP must match Kotlin: `2.2.10-2.0.2`.
5. Do not add dependencies "just in case". Each one is justified by its use case.
6. Lockfiles (`gradle.lockfile` per project) are versioned.

## Compatibility check
Before updating a dependency:
- Check its compiled Kotlin version (klibs.io / changelog / POM).
- Check whether it requires a specific AGP/SDK.
- Run the smoke build + gates.