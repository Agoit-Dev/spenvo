# .agents/

Operational source of truth for the project. Any change in behavior, architecture
or conventions must be reflected here before closing a milestone.

## Structure

- `rules/` — mandatory rules (12). Read them before touching code.
- `skills/` — specialized procedures (7). Invoked explicitly when they apply.
- `commands/` — agent commands (3). Actionable slash-commands.

## Relationship with other tools

- Engram (MCP): persistent session memory. `mem_save` after every decision/finding.
- MobiAI: external mobile toolbox. Used ONLY if a task matches one of its skills and
  the user or the plan asks for it explicitly. The rules in `.agents/rules/` take
  precedence over any automation.
- `.engram/` and `.mobiai/` are local and NOT versioned.

## How it is updated

A change to `.agents/` is itself a reviewable change: it goes through the gates
(commit with summary, changelog if applicable) and is mentioned in the milestone summary.