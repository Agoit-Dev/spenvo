# Rule: Deviation Logging

## Purpose
A plan/design doc is a contract with the reader, not just with the implementer. When the actual
implementation departs from what an approved `doc/plans/*.md` or `doc/designs/*.md` explicitly
prescribed, that gap must be recorded somewhere durable — never left to only live in a subagent's
final chat report, which nobody reads again once the task is marked done.

## What counts as a deviation
Any of the following, once implementation reveals it wasn't anticipated by the approved plan/design:
- A different file, function signature, or structure than the plan's literal code.
- An extra fix needed to satisfy a gate (detekt, lint, an existing test) that the plan didn't call
  for.
- A test technique or assertion that had to change from what the plan specified (e.g. the plan's
  code didn't compile/pass as written).
- A scope cut, addition, or reordering relative to what was approved.

Not every rewording or trivial rename needs this — use judgment for what a future reader would
actually want to know "why does the code not match the doc here."

## Rules
1. **Every deviation is stated in the commit message** that introduces it: what changed vs. the
   plan/design, and why. This is the minimum bar — always required, no exceptions.
2. **If the deviation corresponds to a `backlog.md` task**, add a one-line `Deviation:` note under
   that task when it's checked off into the ✅ Done section, pointing at the commit SHA. Keep it to
   1-2 sentences — the commit message carries the full explanation.
3. **If the deviation reveals a genuine gap or follow-up worth doing later** (not just an
   implementation detail resolved in the same task), it becomes a **new** `backlog.md` entry under
   the right priority section — do not let it disappear once the original task is checked off.
4. **If the deviation changes something the approved design doc asserted as fact** (e.g. "screen X
   has no top bar" when it turns out it does, or a claimed test would pass unmodified when it
   wouldn't), correct the design doc itself in the same change, not just the plan/backlog — a design
   doc that's known-wrong and left uncorrected misleads the next reader.
5. When executing a plan via subagents (`mobiai-mobile-executing-plans-with-subagents`), require each
   implementer's final report to include a "Deviations" section (even if empty) — this is already
   this project's established practice; this rule makes it explicit instead of ad hoc.

## Non-goals
This is not a request to over-document trivial choices ("used a `for` loop instead of `.forEach`").
It's specifically about the plan/design *contract* — the parts a future reader would reasonably
expect to still be true and would be surprised to find aren't.
