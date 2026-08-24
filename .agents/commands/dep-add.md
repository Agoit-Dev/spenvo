# /dep-add

Add or update a dependency with strict control.

## Flow
1. Identify the dependency and version. Verify:
   - Kotlin compatibility (≤2.3 metadata for our 2.2.10 compiler).
   - AGP/SDK compatibility.
   - License and maintenance.
2. Classify:
   - **Inside the baseline** → update the version in `libs.versions.toml` (justify).
   - **Outside the baseline** → propose to the user, apply devils-advocate,
     and wait for **explicit OK**.
3. Edit `gradle/libs.versions.toml` (never inline versions in build files).
4. Regenerate locks: `./gradlew dependencies --write-locks`.
5. Run gates: `./gradlew testDebugUnitTest lintDebug detekt`.
6. Update `CHANGELOG.md` (dependencies) and `AGENTS.md` if the baseline changes.
7. Report the result.

## Rules
- An unused dependency is not added.
- If the new version breaks compilation/tests, revert and report the cause.