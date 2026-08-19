# Rule: Milestone Gating

## Purpose
Each phase (M0…M8) is a reviewable unit that closes with a summary and user OK.

## Rules
1. Do not start the next phase without **explicit user OK** on the previous one.
2. Phase close = summary with:
   - What was built (features/files).
   - Gates verified (build, tests, lint, detekt, changelog, docs, locks).
   - Plan/assumption changes vs what was planned.
   - Known risks and pending debt.
3. `CHANGELOG.md` reflects each milestone's content under its heading.
4. If a phase discovers the plan needs change (scope, design), propose it to the
   user with the devils-advocate rule applied and wait for a decision.
5. The close is recorded in Engram (`mem_session_summary` or `mem_save`) with the exact
   state so the next session continues without losing context.

## Milestones
- **M0** Bootstrap (this milestone). · **M1** Core domain + Room+SQLCipher TDD.
- **M2** Auth + App Check. · **M3** Plans+access+final rules+listeners+usable MVP.
- **M4** Categories. · **M5** Movements+offline+editedAt/By (v1.1).
- **M6** Home dashboard (List-Detail). · **M7** Profile+Storage. · **M8** Hardening.