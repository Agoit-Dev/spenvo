# Rule: Documentation

## Purpose
Documentation lives in the repo and stays up to date in the same commit as the code.

## What is versioned
1. **`CHANGELOG.md`** (English, Keep a Changelog): every user-visible or relevant change.
   Updated in the same commit as the code.
2. **`doc/`** — living project documentation:
   - `doc/database/schema.mdd` — versioned data schema (Mermaid + Room JSON).
   - `doc/security/owasp.md` — OWASP matrix → controls.
   - `doc/architecture.md` — architecture decisions.
3. **`doc/database/schemas/`** — Room JSON exports (room.schemaLocation) versioned,
   one per DB version. Never regenerated without review.

## Rules
- Every commit with data logic touches `doc/database/schema.mdd` if the schema changed.
- Every commit with an important decision touches `doc/architecture.md`.
- `README.md` is the front door: stack, how to run, commands.
- `AGENTS.md` is the operational guide; `.agents/` is the source of truth for rules/skills.
- **All technical documentation (code, `doc/`) is written in English.** This includes
  `CHANGELOG.md`, `README.md`, `AGENTS.md` and everything under `.agents/`.
  Only UI strings stay in Spanish (keys in English, values in `values/`).

## Auto-check
- The `/commit` command validates that changelog + docs are up to date before committing
  (see `.agents/commands/commit.md`).