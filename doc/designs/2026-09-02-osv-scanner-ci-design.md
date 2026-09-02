# OSV-Scanner in CI — Design

**Roadmap item:** M8 (`ROADMAP.md:56` — "osv-scanner in CI + optional MFA. Not started."). This design
covers only the osv-scanner-in-CI half. Optional MFA is explicitly out of scope; it needs its own
separate product/architecture discovery before any implementation work starts.

**Goal:** Give Spenvo a dependency vulnerability gate — CRITICAL/HIGH findings block pull requests;
a daily scheduled scan catches new advisories published against unchanged lockfiles, with
deduplicated GitHub Issues so a finding is never silently missed between PRs.

**Architecture:** Two new GitHub Actions workflows (which also means: this repo currently has no CI
pipeline at all — no `.github/workflows/`, confirmed empty at design time — so this design creates the
first one, deliberately scoped to security scanning only, not to automating the existing local gates
in `AGENTS.md` like `assembleDebug`/`testDebugUnitTest`/`lintDebug`/`detekt`; those remain a separate,
later backlog item). Both workflows run the official OSV-Scanner container image, pinned by digest,
and hand its JSON output to one shared, repo-owned Node script that classifies severity, decides
pass/fail, and (on the scheduled run only) manages GitHub Issues.

**Tech stack:** GitHub Actions, OSV-Scanner container image (`ghcr.io/google/osv-scanner`, pinned by
`sha256` digest — exact digest resolved and recorded at implementation time, then kept current via a
dedicated recurring backlog task, not Dependabot, since Dependabot doesn't track container digests
referenced only via a raw digest string), Node.js (already a project dependency via `rules-tests/`,
no new toolchain needed) for the gate/report script, `gh` CLI (pre-installed on GitHub-hosted runners)
for Issue lifecycle management.

---

## Decision Record: CLI + custom script vs. `google/osv-scanner-action` reusable workflows

**Chosen:** Run the OSV-Scanner CLI via its pinned-by-digest container image, with a repo-owned
Node script post-processing the JSON output for severity gating and Issue management.

**Rejected:** `google/osv-scanner-action`'s reusable workflows
(`osv-scanner-reusable-pr.yml` / `osv-scanner-reusable.yml`, both confirmed at
`google.github.io/osv-scanner/github-action/`, referenced pinned to a tag like `@v2.5.0` in the
official docs — commit-SHA pinning is possible for a reusable-workflow `uses:` ref too, but isn't
the documented default pattern there).

**Reason:** the reusable workflows' `fail-on-vuln` input is a boolean — it fails the job on any
unignored finding, with no confirmed severity threshold. Verified two ways: (1) neither the action's
README nor the OSV-Scanner GitHub Action docs page describe a severity-threshold input; (2)
[google/osv-scanner#1400](https://github.com/google/osv-scanner/issues/1400), a feature request for
exactly this ("configurable CVSS threshold in config.toml"), was closed as **not planned**. A
`--min-severity` flag exists, but it's documented in the context of `osv-scanner fix` (guided
remediation), not confirmed to gate the plain `scan` command's exit code. To get "CRITICAL/HIGH
blocks, MEDIUM/LOW stays visible but non-blocking" out of the reusable workflow, we'd still need to
post-process its JSON/SARIF output ourselves — same custom code, plus an extra layer of indirection
over a third-party action whose full `action.yml` input schema we could not fully verify via
`WebFetch` (it returns a summarized excerpt, not the raw file).

**Consequence:** the parser/gate script is security-critical code that ships with the repo, not a
third-party dependency — it needs its own unit tests with fixture JSON (see Testing), and its
pinned base image digest needs periodic, deliberate review the same way a dependency version does.

---

## Components

### 1. `osv-scanner.toml` (repo root)

Native ignore/exception mechanism — no custom code needed here. Verified syntax
(`google.github.io/osv-scanner/configuration/`):

```toml
[[IgnoredVulns]]
id = "GHSA-xxxx-xxxx-xxxx"
ignoreUntil = 2026-12-01
reason = "One sentence: risk assessed, why deferred. Ref: <issue/decision link if any>."
```

Every exception requires: the exact vulnerability ID, a reason with the risk assessment, a
relatively short `ignoreUntil`, and a reference to the authorizing issue/decision when one exists.
When `ignoreUntil` passes, OSV-Scanner treats the finding as active again automatically — the gate
re-blocks with no extra automation needed. This applies uniformly to any severity; a MEDIUM/LOW
finding an engineer wants formally waived (not just "visible but non-blocking by default") goes
through the same file.

### 2. `.github/workflows/osv-scanner-pr.yml`

- Runner: `ubuntu-latest` — **not** a job-level `container:`. Checkout, Node, and `gh` are the
  runner's own tooling; running the whole job inside the OSV-Scanner image would leave those
  unguaranteed (that image ships the scanner, nothing else). The scanner itself runs as a single
  scoped step: `docker run --rm -v "$PWD:/repo" ghcr.io/google/osv-scanner@sha256:<digest> ...`
  against `/repo`'s 9 `gradle.lockfile` paths and `rules-tests/package-lock.json`, writing
  `--format json` to a file on the mounted workspace.
- Trigger: `pull_request` (all branches targeting `main`).
- Permissions: `contents: read` only.
- The scan step must not abort the job on a non-zero exit — OSV-Scanner itself exits non-zero when
  it finds vulnerabilities, which is expected, not an error. The step runs with
  `continue-on-error: true` (or an explicit `|| true` capturing `$?` into a step output), so both
  the JSON file and the scanner's own exit code reach the next step regardless of outcome. The gate
  script (`--mode=pr`) is what actually decides pass/fail, and it distinguishes two different
  failure shapes: "the scanner ran and found CRITICAL/HIGH vulnerabilities" (expected, blocking)
  vs. "the scanner didn't produce usable output" (`docker run` itself failed, or the JSON file is
  missing/malformed — an operational error, also blocking, but reported differently in the
  step-summary so a maintainer isn't left guessing which one happened).
- Script writes the full findings table (every severity, always) to `$GITHUB_STEP_SUMMARY` and
  exits non-zero if either an unignored CRITICAL/HIGH finding exists or the scan/parse itself failed
  (see Error Handling's fail-closed policy).

### 3. `.github/workflows/osv-scanner-scheduled.yml`

- Runner: `ubuntu-latest`, same `docker run ghcr.io/google/osv-scanner@sha256:<digest>` scan step
  and non-aborting capture as the PR workflow (shared, not reimplemented — see Component 4).
- Trigger: `schedule` (daily cron, low-activity UTC window — exact time picked at implementation
  time) + `workflow_dispatch` for manual reruns.
- Permissions: `contents: read`, `issues: write` — scoped to this workflow only; the PR workflow
  never gets `issues: write`.
- Gate script in `--mode=scheduled`: writes the same step-summary table, and for every unignored
  CRITICAL/HIGH finding, creates or updates a GitHub Issue via `gh issue create`/`gh issue edit`,
  deduplicated by a stable key derived from `(vulnerability ID, package ecosystem, package name)` —
  search by that key encoded in the issue title or a hidden marker in the body, not by fuzzy title
  matching. Labels: `security`, `dependencies`. No unrelated data (raw scan environment details,
  unrelated dependency versions) goes into the issue body — only what identifies and explains the
  specific vulnerability.
- **Closing an issue is conditioned on a fully successful scan and parse for that run.** A
  previously-open issue's finding no longer appearing in the current output is only treated as
  "resolved" when this run's scan+parse both completed cleanly end-to-end; on any operational
  failure (scan didn't run, JSON malformed, a finding's severity couldn't be classified) the script
  takes the fail-closed branch instead — it leaves every existing open issue untouched and does not
  attempt closes at all for that run. "Absent from a broken run" is never read as "fixed".

### 4. `.github/scripts/osv-gate.mjs` (shared)

The one piece of real logic, shared by both workflows via a `--mode` flag so PR and scheduled
behavior can never drift apart. Responsibilities:

- Parse the OSV-Scanner JSON output (the full OSV record per finding — schema at
  `ossf.github.io/osv-schema`, `severity[]` array of `{type, score}` where `score` for
  `CVSS_V3`/`CVSS_V4` entries is a full vector string, not a bare number).
- Classify each finding as CRITICAL/HIGH/MEDIUM/LOW/UNKNOWN. Exact source field(s) for severity
  (CVSS vector parsing vs. any simpler `database_specific.severity` enum some ecosystems may
  populate) gets confirmed empirically against real scan output during implementation, not assumed
  here — the design's contract is the classification's *behavior*, not the parsing internals.
- **Conservative-by-default policy (user-specified precaution)**: a finding with no usable severity
  data, or any JSON the script cannot fully parse, is treated as **blocking** (fail-closed), never
  silently dropped or treated as passing. A parser error must never produce a false green.
- Render the step-summary table (all severities, always — MEDIUM/LOW never disappear from output,
  they're just non-blocking).
- `--mode=pr`: set process exit code from CRITICAL/HIGH presence, or from a scan/parse failure.
- `--mode=scheduled`: same classification, plus Issue create/update calls via `gh` for present
  unignored CRITICAL/HIGH findings — and Issue **close** calls only when this run's scan and parse
  both completed successfully end-to-end; a scan/parse failure skips closing entirely for that run
  (open issues stay open) rather than reading "missing from a broken run" as "resolved".

---

## Error Handling

- Malformed/empty scanner JSON, or a scan step that never produced output (`docker run` itself
  failed) → treated as a blocking failure in both modes (fail-closed), with a clear step-summary
  message distinguishing "scan tool failed" from "vulnerabilities found" so a maintainer isn't left
  guessing which one happened. This is why the scan step must not abort the job on OSV-Scanner's own
  non-zero exit (Component 2/3) — the gate script needs to see the real output (or its absence) to
  tell these two cases apart, rather than the job dying before the script ever runs.
- Scheduled mode specifically: a scan/parse failure blocks Issue **closing**, not just gating — see
  Component 3/4. Creating/updating issues for findings that *did* classify successfully in a
  partially-degraded run is still fine; closing is the one-way, easy-to-get-wrong operation that
  gets the conservative treatment.
- `gh` API failures during Issue management (scheduled mode) fail that job — the scan result itself
  isn't affected, but a failed scheduled run is visible in the Actions tab and is not silently
  swallowed.
- Digest pinning going stale (a real, disclosed OSV-Scanner CVE landing after our pinned image is
  cut) is a known accepted risk for any pinned-by-digest supply-chain setup; mitigated by treating
  the pinned digest like any other dependency version — reviewed and bumped deliberately, not
  auto-tracked.

## Testing

- `osv-gate.mjs` gets unit tests against fixture JSON files (not live scans): a CRITICAL finding, a
  HIGH finding, a MEDIUM/LOW-only finding, a finding with no severity data, malformed JSON, and an
  empty (no findings) result — covering both `--mode=pr` exit-code behavior and `--mode=scheduled`
  Issue-lifecycle decisions (create vs. update vs. close), asserted against a faked `gh` invocation
  rather than hitting the real GitHub API in tests.
- One `workflow_dispatch`-triggered manual run against the real repo lockfiles is the acceptance
  check before relying on the daily schedule.

## Documentation

`backlog.md` gets a short pointer to this design doc under the M8 breakdown — not the reasoning
above. `ROADMAP.md`'s M8 line gets updated only once an implementation item exists to link to; no
design detail duplicated there.

## Out of Scope

- Automating the existing local Android gates (`assembleDebug`, `testDebugUnitTest`, `lintDebug`,
  `detekt`) into CI — separate, later backlog item; noted here only so it isn't assumed bundled into
  "M8 CI" scope.
- Optional MFA (the other half of M8) — needs its own product/architecture discovery.
- Any decision about *which* concrete OSV-Scanner container digest/version to pin, or the exact
  cron time — resolved at implementation time against the then-current release, not fixed in this
  design.
