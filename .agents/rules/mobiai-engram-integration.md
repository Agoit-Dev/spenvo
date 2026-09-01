# Rule: MobiAI & Engram Integration

## Purpose
Integrate Engram (persistent memory) and MobiAI (mobile toolbox) without conflicts or noise.

## Engram (persistent memory)
1. ALWAYS save after:
   - Completed bug fix (`type=bugfix`).
   - Architecture/design decision (`type=decision|architecture`).
   - Non-obvious finding (`type=discovery`).
   - Established pattern/convention (`type=pattern`).
2. Before starting something "already done": `mem_search` by topic.
3. `mem_session_summary` at milestone/session close.
4. Content format: **What / Why / Where / Learned**.

## MobiAI (toolbox)
1. No MobiAI skill fires automatically. It is invoked explicitly by the user or
   by the approved plan.
2. When invoked, use the correct skill: `mobiai-mobile-tdd` before implementing,
   `mobiai-mobile-debugging` before proposing a fix, `mobiai-android-testing` /
   `mobiai-write-tests` for tests, `mobiai-mobile-verification` before declaring
   done, etc.
3. If there is a conflict between a `.agents/` rule and a MobiAI suggestion, `.agents/`
   wins (and note the divergence for review).
4. **Mechanical/tooling skills carry no architecture risk and may be reached for
   proactively** when they clearly apply, without waiting for an explicit ask:
   `mobiai-android-device` (adb/logcat/screenshots), `mobiai-analyze-crash`,
   `mobiai-reproduce-bug`, `mobiai-crashlytics`, `mobiai-android-build`
   (build/Gradle troubleshooting). They execute mechanics, not architecture.
5. Everything else (planning, TDD workflow, architecture navigation, review,
   verification) stays explicit-only per rule 1 — it can shape decisions, so it
   needs a deliberate ask.

## Coexistence
- `.engram/` and `.mobiai/` are gitignored; not versioned.
- The operational source of truth is `.agents/`; Engram is memory; MobiAI is a toolbox.