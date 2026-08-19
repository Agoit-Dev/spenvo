# /commit

Safe local commit with automatic gate and docs verification.

## Flow
1. Run gates:
   - `./gradlew :app:assembleDebug`
   - `./gradlew testDebugUnitTest`
   - `./gradlew lintDebug`
   - `./gradlew detekt`
2. Validate docs/changelog:
   - `CHANGELOG.md` updated (if there is a visible change).
   - `doc/database/schema.mdd` up to date (if schema changed) + `doc/database/schemas/` versioned.
   - `AGENTS.md` / `.agents/` reflect new conventions.
   - lockfiles up to date (if `libs.versions.toml` changed).
3. Review `git status` + `git diff`; make sure there are NO secrets or build files.
4. Stage only intentional files.
5. Local commit with a brief message (repo style). Do NOT push or PR.
6. Tell the user the local commit is ready and ask for OK to push/PR.

## Rules
- Any red gate aborts the commit and reports the failure.
- A dependency change without regenerated locks = abort.
- If changelog/docs are missing → complete them before the commit.