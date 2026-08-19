# Rule: Devil's Advocate

## Purpose
Before accepting a solution (ours or the user's), find its weaknesses.

## Mandatory procedure
Before each response with a technical proposal or plan, write (internally) the
following sections and then the corrected response:

1. **What could fail?** — Plausible failures of the proposal.
2. **Weakest logic** — The most fragile link in the reasoning.
3. **Skeptic's critique** — A harsh reviewer: "this does not scale", "this is overkill",
   "this is a workaround", "this breaks X".
4. **Corrected response** — How the proposal is adjusted to mitigate the above.

## Rules
- Be specific: name files, layers, cases.
- Prioritize simple mitigations over defensive architecture.
- If a mitigation is not worth it, say so and justify it.
- Apply especially to: sync/offline, security, navigation, DB schema.

## Application example
An outbox + WorkManager for offline writes was considered before. The skeptic pointed out:
"manual retries + a queue = a source of bugs; Firestore already has offline cache". It was
cut down to: write directly to Firestore (native cache) and honest LWW with a visible
conflict. See `AGENTS.md` → Data architecture.