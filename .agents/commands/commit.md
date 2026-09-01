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
   - `README.md` checked: status, modules, stack and commands reflect the change.
   - `doc/database/schema.mdd` up to date (if schema changed) + `core/data/schemas/` versioned.
   - `AGENTS.md` / `.agents/` reflect new conventions.
   - lockfiles up to date (if `libs.versions.toml` changed).
3. Review `git status` + `git diff`; make sure there are NO secrets or build files.
4. Stage only intentional files.
5. Show the user the staged diff and the proposed commit message; ask for explicit
   OK before committing (`AGENTS.md`: "Push/PR/commit only with explicit user OK").
6. Once approved, commit locally with a brief message (repo style). Do NOT push or
   PR — that needs its own, separate explicit OK.

## Rules
- Any red gate aborts the commit and reports the failure.
- A dependency change without regenerated locks = abort.
- If changelog/docs are missing → complete them before the commit.