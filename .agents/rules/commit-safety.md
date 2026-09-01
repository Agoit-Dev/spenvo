# Rule: Commit Safety

## Purpose
Nothing is integrated without review and without explicit user OK.

## Rules
1. **Do not commit, push, or open a PR without explicit OK.** "Commit", "push",
   "merge", "PR" always require confirming with the user first — commit and
   push/PR are separate confirmations, not one.
2. Before a local commit: `git status`, `git diff`, `git log --oneline -10`.
3. Stage ONLY intentional files. Never upload secrets, `local.properties`,
   `google-services.json`, `.env`, build files or `.idea/`.
4. Brief, descriptive commit message in repo style.
5. If a commit fails or hooks reject it: fix and create a NEW commit
   (do not amend the failed one).
6. Quality gates (build + tests + lint + detekt + changelog) apply BEFORE
   any commit, local or remote.

## Pre-commit checklist
- [ ] `./gradlew :app:assembleDebug`
- [ ] `./gradlew testDebugUnitTest`
- [ ] `./gradlew lintDebug`
- [ ] `./gradlew detekt`
- [ ] `CHANGELOG.md` updated
- [ ] `doc/database/schema.mdd` up to date
- [ ] lockfiles up to date (`--write-locks`)
- [ ] `git status` clean of junk/secrets