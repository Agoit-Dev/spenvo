# /milestone

Close a milestone (M0…M8) with a summary and gating of the next phase.

## Flow
1. Verify all milestone deliverables are complete (review `doc/`
   and the milestone plan).
2. Run full gates (build + tests + lint + detekt).
3. Confirm `CHANGELOG.md` has the milestone section with its items.
4. Confirm `README.md` and the affected `doc/` files (architecture, schema, security)
   are up to date with the milestone work.
5. Apply the `security-review` skill if the milestone touches security (M0, M2, M3, M7, M8).
6. Write the summary:
   - What was built.
   - Gates verified.
   - Changes vs the plan (with devils-advocate applied).
   - Known risks / debt.
   - What the next phase requires.
7. Save in Engram: `mem_session_summary` (or `mem_save`) with the exact state.
8. **Pause and ask for explicit user OK** to start the next phase.
   Do not continue without that OK.

## Rules
- Do not mark a milestone closed if there are red gates.
- If the milestone changed scope vs the plan, expose it to the user before closing.