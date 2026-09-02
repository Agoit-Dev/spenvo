# OSV-Scanner CI Gate Implementation Plan

> **For agentic workers:** Use `mobiai-mobile-executing-plans-with-subagents` (recommended) or
> `mobiai-mobile-executing-plans` to implement this plan task-by-task. Steps use checkbox syntax for
> tracking.

**Goal:** Ship the CRITICAL/HIGH/UNKNOWN-blocking OSV-Scanner PR gate and daily scheduled scan
defined in `doc/designs/2026-09-02-osv-scanner-ci-design.md`.

**Architecture:** Two GitHub Actions workflows run a pinned-by-digest OSV-Scanner container via
`docker run` (with `--config` pointed explicitly at the repo-root `osv-scanner.toml`, since
OSV-Scanner does not auto-propagate a root config to lockfiles in subdirectories) on an
`ubuntu-latest` runner, capture both its JSON output and its real exit code (0/1/127/128 are
distinct and meaningful — never flattened by `|| true`), and hand both to a shared Node script
(`.github/scripts/osv-gate.mjs` + `.github/scripts/osv-classify.mjs`) that classifies severity,
renders a step-summary table (including an explicit error state, never silently "no vulnerabilities
found"), decides the PR gate's exit code, and — on the scheduled run only — manages deduplicated
GitHub Issues, closing them only after a fully successful scan+parse. **UNKNOWN severity blocks,
same as CRITICAL/HIGH** — a finding this system cannot classify is treated as a risk, not a pass,
consistent with the design's fail-closed policy.

**Tech Stack:** GitHub Actions, Docker (`ghcr.io/google/osv-scanner`, pinned by digest), Node.js 20
(`node:test`/`node:assert/strict` — no new npm dependency; `.github/scripts/` gets its own minimal
`package.json`, separate from any Gradle module or `rules-tests/`), `gh` CLI (pre-installed on
GitHub-hosted runners).

**Platform:** N/A — CI/tooling infrastructure, not app code. No Android build/lint/detekt gates
apply to this slice's commits; its own gate is `cd .github/scripts && npm test` (see Task 14).

**Branch:** `chore/m8-osv-scanner-ci` (pushed to origin, carrying the approved design doc commit
`121736e`).

**Note on scope hygiene:** this branch's working tree currently also carries unrelated, uncommitted
changes (`CHANGELOG.md`, `app/build.gradle.kts`, `app/gradle.lockfile`, `backlog.md` — a
`BUILD-001` dependency-narrowing fix that predates this branch). Every `git add` below is scoped to
exact file paths for that reason — never `git add -A`/`git add .` on this branch until that stray
work is resolved on its own terms.

---

## Task 1: `osv-scanner.toml` — native exception mechanism

No TDD here — it's a config file, not code.

**Files:**
- Create: `osv-scanner.toml`

- [ ] **Step 1: Write the file**

```toml
# OSV-Scanner ignore list — see doc/designs/2026-09-02-osv-scanner-ci-design.md.
#
# NOT auto-discovered for lockfiles outside this directory: OSV-Scanner only auto-applies a config
# file placed in the SAME directory as the scanned lockfile, and does not propagate it to child
# directories (google.github.io/osv-scanner/configuration/). Every scan invocation in this repo's
# workflows passes --config=/repo/osv-scanner.toml explicitly (see Tasks 10-11) so this one file
# governs every lockfile regardless of which subdirectory it lives in.
#
# Every entry needs: the exact vulnerability ID, a reason with the risk assessment, a relatively
# short ignoreUntil, and a reference to the authorizing issue/decision when one exists. When
# ignoreUntil passes, OSV-Scanner treats the finding as active again automatically.
#
# Example (remove once a real exception is needed — an empty [[IgnoredVulns]] list is valid and
# expected most of the time):
#
# [[IgnoredVulns]]
# id = "GHSA-xxxx-xxxx-xxxx"
# ignoreUntil = 2026-12-01
# reason = "One sentence: risk assessed, why deferred. Ref: <issue/decision link>."
```

- [ ] **Step 2: Commit**

```bash
git add osv-scanner.toml
git commit -m "chore(ci): add osv-scanner.toml ignore-list scaffold"
```

---

## Task 2: `osv-classify.mjs` — `severityBandForScore`

Pure numeric-band mapping, no CVSS parsing involved yet — exact boundary values only.

**Files:**
- Create: `.github/scripts/osv-classify.mjs`
- Create: `.github/scripts/osv-classify.test.mjs`
- Create: `.github/scripts/package.json`

- [ ] **Step 1: Scaffold the scripts' own minimal package.json**

```json
{
  "name": "spenvo-osv-scripts",
  "private": true,
  "type": "module",
  "engines": { "node": ">=20" },
  "scripts": {
    "test": "node --test"
  }
}
```

- [ ] **Step 2: Write the failing test**

```js
// .github/scripts/osv-classify.test.mjs
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { severityBandForScore } from './osv-classify.mjs';

test('severityBandForScore boundaries', () => {
  assert.equal(severityBandForScore(10), 'CRITICAL');
  assert.equal(severityBandForScore(9.0), 'CRITICAL');
  assert.equal(severityBandForScore(8.9), 'HIGH');
  assert.equal(severityBandForScore(7.0), 'HIGH');
  assert.equal(severityBandForScore(6.9), 'MEDIUM');
  assert.equal(severityBandForScore(4.0), 'MEDIUM');
  assert.equal(severityBandForScore(3.9), 'LOW');
  assert.equal(severityBandForScore(0), 'LOW');
});

test('severityBandForScore fail-closed on invalid input', () => {
  assert.equal(severityBandForScore(-1), 'UNKNOWN');
  assert.equal(severityBandForScore(NaN), 'UNKNOWN');
  assert.equal(severityBandForScore(null), 'UNKNOWN');
  assert.equal(severityBandForScore(undefined), 'UNKNOWN');
  assert.equal(severityBandForScore('9.8'), 'UNKNOWN');
});
```

- [ ] **Step 3: Run test to verify it fails**

Run (from repo root): `node --test .github/scripts/osv-classify.test.mjs`
Expected: FAIL — `osv-classify.mjs` doesn't exist yet / has no `severityBandForScore` export.

- [ ] **Step 4: Write minimal implementation**

```js
// .github/scripts/osv-classify.mjs

/**
 * Maps a CVSS base score to this project's 4-band scheme. CVSS 0.0 ("None") folds into LOW —
 * this scheme has no separate NONE band. Fail-closed: anything that isn't a valid score >= 0
 * returns UNKNOWN, which the gate treats as blocking (see osv-gate.mjs's decidePrGate).
 */
export function severityBandForScore(score) {
  if (typeof score !== 'number' || Number.isNaN(score) || score < 0) return 'UNKNOWN';
  if (score >= 9.0) return 'CRITICAL';
  if (score >= 7.0) return 'HIGH';
  if (score >= 4.0) return 'MEDIUM';
  return 'LOW';
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `node --test .github/scripts/osv-classify.test.mjs`
Expected: PASS (2 tests).

- [ ] **Step 6: Commit**

```bash
git add .github/scripts/package.json .github/scripts/osv-classify.mjs .github/scripts/osv-classify.test.mjs
git commit -m "feat(ci): add severityBandForScore CVSS-band mapping"
```

---

## Task 3: `osv-classify.mjs` — `parseCvss31BaseScore`

Implements the official CVSS v3.1 Base Score formula (FIRST.org specification, verified against
`first.org/cvss/v3-1/specification-document` for every metric weight and equation). Every non-error
expected value below was produced by executing this exact formula in Node and cross-checked: the
CRITICAL/Scope-Unchanged anchor (9.8) and the CRITICAL/Scope-Changed vector (9.6) are independently
corroborated externally (multiple published CVSS write-ups agree on both); the remaining vectors
exercise the Scope-Changed branch, non-trivial rounding, and the zero-impact early return, but are
this-implementation-computed rather than independently sourced — their job is regression coverage
of the formula's distinct branches, not fresh independent verification of each number.

**Files:**
- Modify: `.github/scripts/osv-classify.mjs`
- Modify: `.github/scripts/osv-classify.test.mjs`

- [ ] **Step 1: Write the failing test**

```js
// append to .github/scripts/osv-classify.test.mjs — merge `parseCvss31BaseScore` into the existing
// top-of-file import instead of a second import statement (see Task 4's note on this pattern)

test('parseCvss31BaseScore: Scope Unchanged, verified externally (9.8, CRITICAL)', () => {
  assert.equal(parseCvss31BaseScore('CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:H'), 9.8);
});

test('parseCvss31BaseScore: Scope Changed branch, verified externally (9.6, CRITICAL)', () => {
  assert.equal(parseCvss31BaseScore('CVSS:3.1/AV:N/AC:L/PR:N/UI:R/S:C/C:H/I:H/A:H'), 9.6);
});

test('parseCvss31BaseScore: zero-impact vector returns 0, not null', () => {
  assert.equal(parseCvss31BaseScore('CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:N/I:N/A:N'), 0);
});

test('parseCvss31BaseScore: non-trivial rounding cases', () => {
  assert.equal(parseCvss31BaseScore('CVSS:3.1/AV:P/AC:H/PR:H/UI:R/S:U/C:L/I:N/A:N'), 1.6);
  assert.equal(parseCvss31BaseScore('CVSS:3.1/AV:N/AC:H/PR:N/UI:R/S:U/C:L/I:L/A:L'), 5.0);
  assert.equal(parseCvss31BaseScore('CVSS:3.1/AV:A/AC:L/PR:L/UI:N/S:U/C:H/I:N/A:N'), 5.7);
});

test('parseCvss31BaseScore is fail-closed on structurally invalid input', () => {
  assert.equal(parseCvss31BaseScore('CVSS:4.0/AV:N/AC:L/AT:N/PR:N/UI:N/VC:H/VI:H/VA:H/SC:N/SI:N/SA:N'), null);
  assert.equal(parseCvss31BaseScore('not a vector'), null);
  assert.equal(parseCvss31BaseScore('CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:H'), null); // missing A
  assert.equal(parseCvss31BaseScore('CVSS:3.1/AV:Z/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:H'), null); // bad AV value
  assert.equal(parseCvss31BaseScore(null), null);
  assert.equal(parseCvss31BaseScore(42), null);
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `node --test .github/scripts/osv-classify.test.mjs`
Expected: FAIL — `parseCvss31BaseScore` not exported yet.

- [ ] **Step 3: Write minimal implementation**

```js
// append to .github/scripts/osv-classify.mjs

const AV_WEIGHTS = { N: 0.85, A: 0.62, L: 0.55, P: 0.2 };
const AC_WEIGHTS = { L: 0.77, H: 0.44 };
const PR_WEIGHTS_UNCHANGED = { N: 0.85, L: 0.62, H: 0.27 };
const PR_WEIGHTS_CHANGED = { N: 0.85, L: 0.68, H: 0.5 };
const UI_WEIGHTS = { N: 0.85, R: 0.62 };
const CIA_WEIGHTS = { H: 0.56, L: 0.22, N: 0 };

// FIRST.org CVSS v3.1 spec, Appendix A — avoids floating-point rounding drift.
function roundup(input) {
  const intInput = Math.round(input * 100000);
  if (intInput % 10000 === 0) return intInput / 100000;
  return (Math.floor(intInput / 10000) + 1) / 10;
}

/**
 * Parses a CVSS v3.1 vector string into its Base Score, per the FIRST.org v3.1 specification.
 * Returns null for anything structurally invalid (wrong version prefix, missing/unknown metric) —
 * callers must treat null as "could not classify", never as a score of 0.
 */
export function parseCvss31BaseScore(vector) {
  if (typeof vector !== 'string' || !vector.startsWith('CVSS:3.1/')) return null;

  const metrics = {};
  for (const part of vector.slice('CVSS:3.1/'.length).split('/')) {
    const [key, value] = part.split(':');
    if (!key || !value) return null;
    metrics[key] = value;
  }

  const { AV, AC, PR, UI, S, C, I, A } = metrics;
  if (!AV || !AC || !PR || !UI || !S || !C || !I || !A) return null;
  if (S !== 'U' && S !== 'C') return null;
  if (!(AV in AV_WEIGHTS) || !(AC in AC_WEIGHTS) || !(UI in UI_WEIGHTS)) return null;
  if (!(C in CIA_WEIGHTS) || !(I in CIA_WEIGHTS) || !(A in CIA_WEIGHTS)) return null;
  const prWeights = S === 'C' ? PR_WEIGHTS_CHANGED : PR_WEIGHTS_UNCHANGED;
  if (!(PR in prWeights)) return null;

  const iss = 1 - (1 - CIA_WEIGHTS[C]) * (1 - CIA_WEIGHTS[I]) * (1 - CIA_WEIGHTS[A]);
  const impact = S === 'C'
    ? 7.52 * (iss - 0.029) - 3.25 * Math.pow(iss - 0.02, 15)
    : 6.42 * iss;
  if (impact <= 0) return 0;

  const exploitability = 8.22 * AV_WEIGHTS[AV] * AC_WEIGHTS[AC] * prWeights[PR] * UI_WEIGHTS[UI];
  const raw = S === 'C' ? 1.08 * (impact + exploitability) : impact + exploitability;

  return roundup(Math.min(raw, 10));
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `node --test .github/scripts/osv-classify.test.mjs`
Expected: PASS (6 tests).

- [ ] **Step 5: Commit**

```bash
git add .github/scripts/osv-classify.mjs .github/scripts/osv-classify.test.mjs
git commit -m "feat(ci): add CVSS v3.1 base score parser"
```

---

## Task 4: `osv-classify.mjs` — `classifyFinding`

Wires a single OSV vulnerability record (public OSV schema — `id`, `severity[]`,
`database_specific`, per `ossf.github.io/osv-schema`) to a severity band. **Conservative by design,
in two layers:**

1. Evaluates every interpretable source — `database_specific.severity` (when recognized; GHSA's
   `MODERATE` aliases to `MEDIUM`) and every `CVSS_V3` entry in `severity[]` — and returns the
   *highest* one, never just the first source present. A single source's own imprecision (a
   stale/under-reported `database_specific` field sitting next to an accurate CVSS vector, a known
   real-world OSV/GHSA data quirk) must never downgrade a finding.
2. If any source was *present but could not be interpreted* (an unrecognized `database_specific`
   label, a `severity[]` entry whose type isn't `CVSS_V3`, or a `CVSS_V3` entry with a malformed
   vector), that's real unresolved uncertainty — the result escalates to `UNKNOWN` (blocking)
   *unless* a recognized source already reached CRITICAL/HIGH on its own, since that's already at
   least as conservative as `UNKNOWN`.

(Two issues were caught in review before this task's commit, neither ever shipped: an earlier draft
picked whichever source was checked first — `database_specific` unconditionally over CVSS — which
let a LOW `database_specific` value silently mask a CRITICAL CVSS score; a second pass then also
missed that an unreadable source sitting next to an otherwise non-blocking recognized severity
[e.g. LOW `database_specific` next to an unsupported CVSS v4 vector] still needs to escalate to
`UNKNOWN`, not quietly resolve to the recognized-but-non-blocking value.)

**Files:**
- Modify: `.github/scripts/osv-classify.mjs`
- Modify: `.github/scripts/osv-classify.test.mjs`

- [ ] **Step 1: Write the failing test**

```js
// append to .github/scripts/osv-classify.test.mjs
import { classifyFinding } from './osv-classify.mjs'; // merge into the existing top-of-file import instead of a second import statement

test('classifyFinding uses database_specific.severity when it is the only recognized source', () => {
  assert.equal(classifyFinding({ database_specific: { severity: 'CRITICAL' } }), 'CRITICAL');
  assert.equal(classifyFinding({ database_specific: { severity: 'MODERATE' } }), 'MEDIUM');
});

test('classifyFinding uses the CVSS_V3 entry when database_specific is absent/unrecognized', () => {
  const finding = {
    severity: [{ type: 'CVSS_V3', score: 'CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:H' }],
  };
  assert.equal(classifyFinding(finding), 'CRITICAL');
});

test('classifyFinding returns UNKNOWN (not an exception) on unsupported or missing severity data', () => {
  assert.equal(classifyFinding({ severity: [{ type: 'CVSS_V4', score: 'CVSS:4.0/...' }] }), 'UNKNOWN');
  assert.equal(classifyFinding({}), 'UNKNOWN');
  assert.equal(classifyFinding({ severity: [] }), 'UNKNOWN');
  assert.equal(classifyFinding(null), 'UNKNOWN');
});

test('classifyFinding is conservative on contradictory sources: LOW database_specific next to CRITICAL CVSS still returns CRITICAL', () => {
  const finding = {
    database_specific: { severity: 'LOW' },
    severity: [{ type: 'CVSS_V3', score: 'CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:H' }],
  };
  assert.equal(classifyFinding(finding), 'CRITICAL');
});

test('classifyFinding is conservative regardless of which source is higher: CRITICAL database_specific next to LOW CVSS still returns CRITICAL', () => {
  const finding = {
    database_specific: { severity: 'CRITICAL' },
    severity: [{ type: 'CVSS_V3', score: 'CVSS:3.1/AV:P/AC:H/PR:H/UI:R/S:U/C:L/I:N/A:N' }], // 1.6, LOW
  };
  assert.equal(classifyFinding(finding), 'CRITICAL');
});

test('classifyFinding escalates to UNKNOWN when a non-blocking recognized severity sits next to an unreadable source (unsupported CVSS type)', () => {
  const finding = {
    database_specific: { severity: 'LOW' },
    severity: [{ type: 'CVSS_V4', score: 'CVSS:4.0/AV:N/AC:L/AT:N/PR:N/UI:N/VC:H/VI:H/VA:H/SC:N/SI:N/SA:N' }],
  };
  assert.equal(classifyFinding(finding), 'UNKNOWN');
});

test('classifyFinding escalates to UNKNOWN when a non-blocking recognized severity sits next to an unreadable source (malformed CVSS_V3 vector)', () => {
  const finding = {
    database_specific: { severity: 'MEDIUM' },
    severity: [{ type: 'CVSS_V3', score: 'not a valid vector' }],
  };
  assert.equal(classifyFinding(finding), 'UNKNOWN');
});

test('classifyFinding does NOT escalate when the recognized severity is already blocking (CRITICAL/HIGH), even next to an unreadable source', () => {
  const finding = {
    database_specific: { severity: 'HIGH' },
    severity: [{ type: 'CVSS_V4', score: 'CVSS:4.0/...' }],
  };
  assert.equal(classifyFinding(finding), 'HIGH');
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `node --test .github/scripts/osv-classify.test.mjs`
Expected: FAIL — `classifyFinding` not exported yet.

- [ ] **Step 3: Write minimal implementation**

```js
// append to .github/scripts/osv-classify.mjs

const KNOWN_DATABASE_SEVERITIES = new Map([
  ['CRITICAL', 'CRITICAL'],
  ['HIGH', 'HIGH'],
  ['MODERATE', 'MEDIUM'],
  ['MEDIUM', 'MEDIUM'],
  ['LOW', 'LOW'],
]);

const SEVERITY_ORDER = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL']; // ascending — highest wins

function higherSeverity(a, b) {
  return SEVERITY_ORDER.indexOf(b) > SEVERITY_ORDER.indexOf(a) ? b : a;
}

const BLOCKING_BANDS = new Set(['CRITICAL', 'HIGH']);

/**
 * @param {unknown} finding — a single OSV vulnerability record (ossf.github.io/osv-schema).
 *
 * Conservative by design, in two layers:
 * 1. Evaluates EVERY interpretable severity source and returns the highest one, never just the
 *    first source present.
 * 2. If any source was PRESENT but could not be interpreted (unrecognized database_specific label,
 *    a severity[] entry whose type isn't CVSS_V3, or a malformed CVSS_V3 vector), that's real
 *    unresolved uncertainty — escalates to UNKNOWN (blocking) UNLESS a recognized source already
 *    reached CRITICAL/HIGH on its own, since that's already at least as conservative as UNKNOWN.
 */
export function classifyFinding(finding) {
  const candidates = [];
  let hadUnreadableSource = false;

  const dbSeverity = finding?.database_specific?.severity;
  if (typeof dbSeverity === 'string') {
    const mapped = KNOWN_DATABASE_SEVERITIES.get(dbSeverity.toUpperCase());
    if (mapped) candidates.push(mapped);
    else hadUnreadableSource = true;
  }

  const severityEntries = Array.isArray(finding?.severity) ? finding.severity : [];
  for (const entry of severityEntries) {
    if (entry?.type !== 'CVSS_V3') {
      hadUnreadableSource = true;
      continue;
    }
    const score = parseCvss31BaseScore(entry.score);
    if (score !== null) candidates.push(severityBandForScore(score));
    else hadUnreadableSource = true;
  }

  if (candidates.length === 0) return 'UNKNOWN';

  const highest = candidates.reduce(higherSeverity);
  if (hadUnreadableSource && !BLOCKING_BANDS.has(highest)) return 'UNKNOWN';
  return highest;
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `node --test .github/scripts/osv-classify.test.mjs`
Expected: PASS (15 tests total).

- [ ] **Step 5: Commit**

```bash
git add \
  .github/scripts/osv-classify.mjs \
  .github/scripts/osv-classify.test.mjs \
  doc/plans/2026-09-02-osv-scanner-ci-implementation.md
git commit -m "feat(ci): add conservative OSV finding classification"
```

---

## Task 5: `osv-gate.mjs` — `buildSummaryTable` and `buildErrorSummary`

Renders every classified finding (all severities, always) as a GitHub-flavored Markdown table. A
**separate, explicit** renderer covers the "scan/parse failed" case — the CLI (Task 8) picks between
them based on `scanResult.ok`, so a broken run can never render as "No vulnerabilities found."

**Files:**
- Create: `.github/scripts/osv-gate.mjs`
- Create: `.github/scripts/osv-gate.test.mjs`

- [ ] **Step 1: Write the failing test**

```js
// .github/scripts/osv-gate.test.mjs
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { buildSummaryTable, buildErrorSummary } from './osv-gate.mjs';

const SAMPLE = [
  { vulnId: 'GHSA-1111', ecosystem: 'Maven', packageName: 'com.example:lib', severity: 'MEDIUM' },
  { vulnId: 'GHSA-2222', ecosystem: 'npm', packageName: 'left-pad', severity: 'CRITICAL' },
  { vulnId: 'GHSA-3333', ecosystem: 'Maven', packageName: 'com.example:other', severity: 'UNKNOWN' },
  { vulnId: 'GHSA-4444', ecosystem: 'npm', packageName: 'right-pad', severity: 'HIGH' },
  { vulnId: 'GHSA-5555', ecosystem: 'Maven', packageName: 'com.example:third', severity: 'LOW' },
];

test('buildSummaryTable includes every finding regardless of severity', () => {
  const table = buildSummaryTable(SAMPLE);
  assert.match(table, /GHSA-1111/);
  assert.match(table, /GHSA-2222/);
  assert.match(table, /GHSA-3333/);
  assert.match(table, /GHSA-4444/);
  assert.match(table, /GHSA-5555/);
});

test('buildSummaryTable orders all 5 severity bands: CRITICAL, HIGH, UNKNOWN, MEDIUM, LOW', () => {
  const table = buildSummaryTable(SAMPLE);
  const indexOf = (id) => table.indexOf(id);
  assert.ok(indexOf('GHSA-2222') !== -1, 'CRITICAL row present'); // CRITICAL
  assert.ok(indexOf('GHSA-4444') !== -1, 'HIGH row present'); // HIGH
  assert.ok(indexOf('GHSA-3333') !== -1, 'UNKNOWN row present'); // UNKNOWN
  assert.ok(indexOf('GHSA-1111') !== -1, 'MEDIUM row present'); // MEDIUM
  assert.ok(indexOf('GHSA-5555') !== -1, 'LOW row present'); // LOW
  assert.ok(indexOf('GHSA-2222') < indexOf('GHSA-4444'), 'CRITICAL before HIGH');
  assert.ok(indexOf('GHSA-4444') < indexOf('GHSA-3333'), 'HIGH before UNKNOWN');
  assert.ok(indexOf('GHSA-3333') < indexOf('GHSA-1111'), 'UNKNOWN before MEDIUM');
  assert.ok(indexOf('GHSA-1111') < indexOf('GHSA-5555'), 'MEDIUM before LOW');
});

test('buildSummaryTable handles an empty finding list', () => {
  assert.match(buildSummaryTable([]), /No vulnerabilities found/);
});

test('buildErrorSummary renders the failure distinctly, never as "no vulnerabilities"', () => {
  const summary = buildErrorSummary('osv-scanner exited with code 127');
  assert.match(summary, /Scan\/parse failed/);
  assert.match(summary, /code 127/);
  assert.doesNotMatch(summary, /No vulnerabilities found/);
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `node --test .github/scripts/osv-gate.test.mjs`
Expected: FAIL — `osv-gate.mjs` doesn't exist yet.

- [ ] **Step 3: Write minimal implementation**

```js
// .github/scripts/osv-gate.mjs

const SEVERITY_RANK = { CRITICAL: 0, HIGH: 1, UNKNOWN: 2, MEDIUM: 3, LOW: 4 };

/** @param {{vulnId: string, ecosystem: string, packageName: string, severity: string}[]} findings */
export function buildSummaryTable(findings) {
  if (findings.length === 0) {
    return '## OSV-Scanner results\n\nNo vulnerabilities found.\n';
  }

  const sorted = [...findings].sort(
    (a, b) => (SEVERITY_RANK[a.severity] ?? 99) - (SEVERITY_RANK[b.severity] ?? 99),
  );

  const rows = sorted
    .map((f) => `| ${f.severity} | ${f.vulnId} | ${f.ecosystem} | ${f.packageName} |`)
    .join('\n');

  return [
    '## OSV-Scanner results',
    '',
    '| Severity | Vulnerability | Ecosystem | Package |',
    '| --- | --- | --- | --- |',
    rows,
    '',
  ].join('\n');
}

/** @param {string} error */
export function buildErrorSummary(error) {
  return [
    '## OSV-Scanner results',
    '',
    `**Scan/parse failed (fail-closed — treated as blocking):** ${error}`,
    '',
  ].join('\n');
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `node --test .github/scripts/osv-gate.test.mjs`
Expected: PASS (4 tests — one of them, the ordering test, now asserts the complete CRITICAL→HIGH→
UNKNOWN→MEDIUM→LOW sequence across a 5-band fixture rather than just one pairwise comparison, per
review: the original 3-entry fixture only proved CRITICAL sorts before MEDIUM, not that UNKNOWN
actually sorts ahead of MEDIUM/LOW — the specific ordering property this task exists to guarantee).

- [ ] **Step 5: Commit**

```bash
git add .github/scripts/osv-gate.mjs .github/scripts/osv-gate.test.mjs doc/plans/2026-09-02-osv-scanner-ci-implementation.md
git commit -m "feat(ci): add osv-gate step-summary renderers"
```

---

## Task 6: `osv-gate.mjs` — `decidePrGate`

`--mode=pr` decision: block on any unignored **CRITICAL, HIGH, or UNKNOWN** finding, or on a
scan/parse failure. Only MEDIUM/LOW pass. This is the fix for blocker #1 — the earlier draft only
blocked on CRITICAL/HIGH, silently letting an unclassifiable finding through.

**Files:**
- Modify: `.github/scripts/osv-gate.mjs`
- Modify: `.github/scripts/osv-gate.test.mjs`

- [ ] **Step 1: Write the failing test**

```js
// append to .github/scripts/osv-gate.test.mjs — merge `decidePrGate` into the existing top-of-file
// import instead of a second import statement (see Task 4's note on this pattern)

test('decidePrGate blocks on CRITICAL or HIGH findings', () => {
  const result = decidePrGate({ ok: true, findings: [{ severity: 'HIGH' }] });
  assert.equal(result.blocked, true);
});

test('decidePrGate blocks on UNKNOWN findings — fail-closed, not a pass-through', () => {
  const result = decidePrGate({ ok: true, findings: [{ severity: 'UNKNOWN' }] });
  assert.equal(result.blocked, true);
  assert.match(result.reason, /UNKNOWN/);
});

test('decidePrGate passes when only MEDIUM/LOW findings exist', () => {
  const result = decidePrGate({ ok: true, findings: [{ severity: 'MEDIUM' }, { severity: 'LOW' }] });
  assert.equal(result.blocked, false);
});

test('decidePrGate passes with zero findings', () => {
  assert.equal(decidePrGate({ ok: true, findings: [] }).blocked, false);
});

test('decidePrGate is fail-closed on a scan/parse failure, independent of severity', () => {
  const result = decidePrGate({ ok: false, error: 'malformed JSON', findings: [] });
  assert.equal(result.blocked, true);
  assert.match(result.reason, /scan|parse/i);
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `node --test .github/scripts/osv-gate.test.mjs`
Expected: FAIL — `decidePrGate` not exported yet.

- [ ] **Step 3: Write minimal implementation**

```js
// append to .github/scripts/osv-gate.mjs

const BLOCKING_SEVERITIES = new Set(['CRITICAL', 'HIGH', 'UNKNOWN']);

/**
 * @param {{ok: boolean, error?: string, findings: {severity: string}[]}} scanResult
 * @returns {{blocked: boolean, reason: string}}
 */
export function decidePrGate(scanResult) {
  if (!scanResult.ok) {
    return { blocked: true, reason: `Scan/parse failed (fail-closed): ${scanResult.error}` };
  }
  const blocking = scanResult.findings.filter((f) => BLOCKING_SEVERITIES.has(f.severity));
  if (blocking.length > 0) {
    return { blocked: true, reason: `${blocking.length} unignored CRITICAL/HIGH/UNKNOWN finding(s)` };
  }
  return { blocked: false, reason: 'No unignored CRITICAL/HIGH/UNKNOWN findings' };
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `node --test .github/scripts/osv-gate.test.mjs`
Expected: PASS (9 tests total).

- [ ] **Step 5: Commit**

```bash
git add .github/scripts/osv-gate.mjs .github/scripts/osv-gate.test.mjs doc/plans/2026-09-02-osv-scanner-ci-implementation.md
git commit -m "feat(ci): add fail-closed OSV PR gate decision"
```

---

## Task 7: `osv-gate.mjs` — `planIssueActions`

`--mode=scheduled` decision logic, using the **same** `BLOCKING_SEVERITIES` set as `decidePrGate`
(CRITICAL/HIGH/UNKNOWN) so the two modes can never disagree on what counts as a reportable finding.
Dedup key: `(vulnId, ecosystem, packageName)`, encoded as a hidden marker `<!-- osv-gate:KEY -->` in
the issue body. Closing is only ever planned when `scanResult.ok === true`.

**Files:**
- Modify: `.github/scripts/osv-gate.mjs`
- Modify: `.github/scripts/osv-gate.test.mjs`

- [ ] **Step 1: Write the failing test**

```js
// append to .github/scripts/osv-gate.test.mjs — merge `planIssueActions, issueDedupeKey` into the
// existing top-of-file import instead of a second import statement (see Task 4's note)

const OPEN_ISSUE_FOR = (vulnId, ecosystem, packageName, number) => ({
  number,
  body: `Some text\n<!-- osv-gate:${issueDedupeKey({ vulnId, ecosystem, packageName })} -->`,
});

test('issueDedupeKey is stable for the same (vulnId, ecosystem, packageName)', () => {
  const a = issueDedupeKey({ vulnId: 'GHSA-1', ecosystem: 'Maven', packageName: 'x:y' });
  const b = issueDedupeKey({ vulnId: 'GHSA-1', ecosystem: 'Maven', packageName: 'x:y' });
  assert.equal(a, b);
});

test('planIssueActions creates for a new unignored CRITICAL/HIGH finding with no matching open issue', () => {
  const scanResult = { ok: true, findings: [{ vulnId: 'GHSA-9', ecosystem: 'npm', packageName: 'left-pad', severity: 'CRITICAL' }] };
  const actions = planIssueActions(scanResult, { openIssues: [] });
  assert.equal(actions.creates.length, 1);
  assert.equal(actions.closes.length, 0);
});

test('planIssueActions also creates for an UNKNOWN finding — same blocking set as the PR gate', () => {
  const scanResult = { ok: true, findings: [{ vulnId: 'GHSA-U', ecosystem: 'Maven', packageName: 'x:y', severity: 'UNKNOWN' }] };
  const actions = planIssueActions(scanResult, { openIssues: [] });
  assert.equal(actions.creates.length, 1);
});

test('planIssueActions updates instead of creating when a matching open issue exists', () => {
  const finding = { vulnId: 'GHSA-9', ecosystem: 'npm', packageName: 'left-pad', severity: 'HIGH' };
  const openIssues = [OPEN_ISSUE_FOR('GHSA-9', 'npm', 'left-pad', 42)];
  const actions = planIssueActions({ ok: true, findings: [finding] }, { openIssues });
  assert.equal(actions.creates.length, 0);
  assert.equal(actions.updates.length, 1);
  assert.equal(actions.updates[0].number, 42);
});

test('planIssueActions closes an open issue whose finding no longer appears, only on a successful run', () => {
  const openIssues = [OPEN_ISSUE_FOR('GHSA-STALE', 'Maven', 'com.example:lib', 7)];
  const actions = planIssueActions({ ok: true, findings: [] }, { openIssues });
  assert.equal(actions.closes.length, 1);
  assert.equal(actions.closes[0].number, 7);
});

test('planIssueActions never closes, creates, or updates on a failed scan/parse', () => {
  const openIssues = [OPEN_ISSUE_FOR('GHSA-STALE', 'Maven', 'com.example:lib', 7)];
  const actions = planIssueActions({ ok: false, error: 'malformed JSON', findings: [] }, { openIssues });
  assert.equal(actions.closes.length, 0);
  assert.equal(actions.creates.length, 0);
  assert.equal(actions.updates.length, 0);
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `node --test .github/scripts/osv-gate.test.mjs`
Expected: FAIL — `planIssueActions`/`issueDedupeKey` not exported yet.

- [ ] **Step 3: Write minimal implementation**

```js
// append to .github/scripts/osv-gate.mjs

/** @param {{vulnId: string, ecosystem: string, packageName: string}} f */
export function issueDedupeKey(f) {
  return `${f.vulnId}::${f.ecosystem}::${f.packageName}`;
}

function keyFromIssueBody(body) {
  const match = /<!-- osv-gate:(.+?) -->/.exec(body ?? '');
  return match ? match[1] : null;
}

/**
 * @param {{ok: boolean, error?: string, findings: {vulnId: string, ecosystem: string, packageName: string, severity: string}[]}} scanResult
 * @param {{openIssues: {number: number, body: string}[]}} context
 */
export function planIssueActions(scanResult, { openIssues }) {
  const actions = { creates: [], updates: [], closes: [] };
  if (!scanResult.ok) return actions; // fail-closed: no issue mutation at all on a broken run

  const blocking = scanResult.findings.filter((f) => BLOCKING_SEVERITIES.has(f.severity));
  const blockingKeys = new Set(blocking.map(issueDedupeKey));
  const openByKey = new Map(
    openIssues
      .map((issue) => [keyFromIssueBody(issue.body), issue])
      .filter(([key]) => key !== null),
  );

  for (const finding of blocking) {
    const key = issueDedupeKey(finding);
    const existing = openByKey.get(key);
    if (existing) {
      actions.updates.push({ number: existing.number, finding, key });
    } else {
      actions.creates.push({ finding, key });
    }
  }

  for (const [key, issue] of openByKey) {
    if (!blockingKeys.has(key)) {
      actions.closes.push({ number: issue.number, key });
    }
  }

  return actions;
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `node --test .github/scripts/osv-gate.test.mjs`
Expected: PASS (15 tests total).

- [ ] **Step 5: Commit**

```bash
git add .github/scripts/osv-gate.mjs .github/scripts/osv-gate.test.mjs
git commit -m "fix(ci): planIssueActions treats UNKNOWN as blocking, matching decidePrGate"
```

---

## Task 8: `osv-gate.mjs` — `interpretScannerExitCode`, strict `normalizeOsvScanOutput`, CLI wiring

Three fixes land together here because they're all part of "can this run's output be trusted at
all," which is the CLI's job to establish before anything else runs:

1. **`interpretScannerExitCode`**: OSV-Scanner's documented exit codes
   (`google.github.io/osv-scanner/usage/`, corroborated externally) are **0** = clean, **1** =
   vulnerabilities found, **127** = execution failed, **128** = no packages found. Only 0 and 1 are
   "the scan produced a trustworthy result" — 127 and 128 are operational failures and must block
   even if a `osv-scan-output.json` file happens to exist and parse as valid JSON.
2. **Strict `normalizeOsvScanOutput`**: the earlier draft used `result.packages ?? []` and
   `pkg.vulnerabilities ?? []`, which silently turned a malformed/incomplete structure into "zero
   findings" — a false green. This version validates every level explicitly and fails closed
   (`ok: false`) the moment a structural expectation isn't met, rather than defaulting to empty.
3. **CLI wiring** picks `buildErrorSummary` vs. `buildSummaryTable` based on `scanResult.ok`, so an
   operational failure never renders as "No vulnerabilities found" in the step summary.

The exact raw-JSON wrapper shape (`results[].packages[].vulnerabilities[]`) still isn't independently
confirmed against a live scan — Step 1 attempts that; Task 13 is the fallback/final confirmation
this plan already scheduled for that specific reason.

**Files:**
- Modify: `.github/scripts/osv-gate.mjs`
- Modify: `.github/scripts/osv-gate.test.mjs`
- Create: `.github/scripts/fixtures/sample-scan-output.json` (real, captured — not hand-authored, if
  Step 1 succeeds)

- [ ] **Step 1: Capture a real OSV-Scanner JSON sample (best-effort)**

If Docker is available locally, a `left-pad@1.3.0` lockfile (as originally sketched here) is a
reasonable first attempt, but note it produced a real, valid, **empty** result (`{"results": []}`)
when this task actually ran — `left-pad` currently has no OSV advisory, so that capture never
exercises the nested `packages[]/vulnerabilities[]` shape it exists to verify. **What this task
actually used**: `lodash@4.17.4`, a well-documented package with multiple real GHSA advisories, to
get a non-empty capture:

```bash
mkdir -p /tmp/osv-known-vuln && cd /tmp/osv-known-vuln
cat > package-lock.json <<'EOF'
{
  "name": "osv-sample",
  "version": "1.0.0",
  "lockfileVersion": 3,
  "packages": {
    "": { "name": "osv-sample", "version": "1.0.0" },
    "node_modules/lodash": { "version": "4.17.4" }
  }
}
EOF
docker run --rm -v "$PWD:/repo" ghcr.io/google/osv-scanner:latest --format json -L /repo/package-lock.json > sample-scan-output.json; echo "exit=$?"
```

This produced a real 10-finding capture (`exit=1`) and is what's committed as
`.github/scripts/fixtures/sample-scan-output.json`. **The real shape matched this task's own
assumption exactly** — `results[].packages[].package.{name,ecosystem}` and
`results[].packages[].vulnerabilities[]` needed no adjustment; the only extras were a harmless
top-level `experimental_config` key and a per-package `groups` key, neither of which
`normalizeOsvScanOutput` inspects. If Docker isn't available in a given environment, rely on the
documented shape (`google.github.io/osv-scanner/output/`) and treat Task 13 as the empirical
confirmation point instead.

- [ ] **Step 2: Write the failing tests**

```js
// append to .github/scripts/osv-gate.test.mjs — merge `interpretScannerExitCode,
// normalizeOsvScanOutput` into the existing top-of-file import instead of a second import
// statement (see Task 4's note)

test('interpretScannerExitCode: 0 and 1 are trustworthy scan outcomes', () => {
  assert.equal(interpretScannerExitCode(0).ok, true);
  assert.equal(interpretScannerExitCode(1).ok, true);
});

test('interpretScannerExitCode: 127 (execution failed) and 128 (no packages found) block', () => {
  assert.equal(interpretScannerExitCode(127).ok, false);
  assert.match(interpretScannerExitCode(127).error, /127/);
  assert.equal(interpretScannerExitCode(128).ok, false);
  assert.match(interpretScannerExitCode(128).error, /128/);
});

test('interpretScannerExitCode: any other code is treated as an unexpected failure', () => {
  assert.equal(interpretScannerExitCode(2).ok, false);
});

test('normalizeOsvScanOutput flattens results/packages/vulnerabilities into findings', () => {
  const raw = {
    results: [
      {
        source: { path: 'app/gradle.lockfile' },
        packages: [
          {
            package: { name: 'com.example:lib', ecosystem: 'Maven', version: '1.0.0' },
            vulnerabilities: [{ id: 'GHSA-1', database_specific: { severity: 'HIGH' } }],
          },
        ],
      },
    ],
  };
  const result = normalizeOsvScanOutput(raw);
  assert.equal(result.ok, true);
  assert.equal(result.findings.length, 1);
  assert.equal(result.findings[0].vulnId, 'GHSA-1');
  assert.equal(result.findings[0].ecosystem, 'Maven');
  assert.equal(result.findings[0].packageName, 'com.example:lib');
  assert.equal(result.findings[0].severity, 'HIGH');
});

test('normalizeOsvScanOutput handles a clean run with no results', () => {
  const result = normalizeOsvScanOutput({ results: [] });
  assert.equal(result.ok, true);
  assert.deepEqual(result.findings, []);
});

test('normalizeOsvScanOutput fails closed on malformed top-level structure', () => {
  assert.equal(normalizeOsvScanOutput(null).ok, false);
  assert.equal(normalizeOsvScanOutput({ notResults: [] }).ok, false);
  assert.equal(normalizeOsvScanOutput('not an object').ok, false);
});

test('normalizeOsvScanOutput fails closed when a result is missing packages[] entirely (not just empty)', () => {
  const result = normalizeOsvScanOutput({ results: [{ source: { path: 'x' } }] });
  assert.equal(result.ok, false);
  assert.match(result.error, /packages/);
});

test('normalizeOsvScanOutput fails closed when a package is missing vulnerabilities[] entirely', () => {
  const raw = { results: [{ packages: [{ package: { name: 'x', ecosystem: 'npm' } }] }] };
  const result = normalizeOsvScanOutput(raw);
  assert.equal(result.ok, false);
  assert.match(result.error, /vulnerabilities/);
});

test('normalizeOsvScanOutput fails closed when a package is missing name/ecosystem', () => {
  const raw = { results: [{ packages: [{ package: {}, vulnerabilities: [] }] }] };
  const result = normalizeOsvScanOutput(raw);
  assert.equal(result.ok, false);
  assert.match(result.error, /package identity|name|ecosystem/);
});

test('normalizeOsvScanOutput fails closed when a vulnerability is missing its id', () => {
  const raw = { results: [{ packages: [{ package: { name: 'x', ecosystem: 'npm' }, vulnerabilities: [{}] }] }] };
  const result = normalizeOsvScanOutput(raw);
  assert.equal(result.ok, false);
  assert.match(result.error, /id/);
});
```

- [ ] **Step 3: Run test to verify it fails**

Run: `node --test .github/scripts/osv-gate.test.mjs`
Expected: FAIL — new exports missing.

- [ ] **Step 4: Write minimal implementation**

```js
// append to .github/scripts/osv-gate.mjs — add `import { classifyFinding } from
// './osv-classify.mjs';` at the top of the file with the module's own imports, not mid-file

/** @param {number} exitCode — osv-scanner's own process exit code. */
export function interpretScannerExitCode(exitCode) {
  if (exitCode === 0 || exitCode === 1) return { ok: true };
  if (exitCode === 127) {
    return { ok: false, error: 'osv-scanner exited 127 (execution failed) — see google.github.io/osv-scanner/usage/' };
  }
  if (exitCode === 128) {
    return { ok: false, error: 'osv-scanner exited 128 (no packages found — check the scanned paths)' };
  }
  return { ok: false, error: `osv-scanner exited with unexpected code ${exitCode}` };
}

/** @param {unknown} raw — parsed OSV-Scanner --format json output */
export function normalizeOsvScanOutput(raw) {
  if (raw === null || typeof raw !== 'object' || !Array.isArray(raw.results)) {
    return { ok: false, error: 'Unexpected OSV-Scanner JSON shape (missing results[])', findings: [] };
  }

  const findings = [];
  for (const [resultIndex, result] of raw.results.entries()) {
    if (!Array.isArray(result?.packages)) {
      return { ok: false, error: `results[${resultIndex}] is missing packages[]`, findings: [] };
    }
    for (const [pkgIndex, pkg] of result.packages.entries()) {
      const ecosystem = pkg?.package?.ecosystem;
      const packageName = pkg?.package?.name;
      if (typeof ecosystem !== 'string' || typeof packageName !== 'string' || !ecosystem || !packageName) {
        return { ok: false, error: `results[${resultIndex}].packages[${pkgIndex}] is missing package identity (name/ecosystem)`, findings: [] };
      }
      if (!Array.isArray(pkg.vulnerabilities)) {
        return { ok: false, error: `results[${resultIndex}].packages[${pkgIndex}] is missing vulnerabilities[]`, findings: [] };
      }
      for (const [vulnIndex, vuln] of pkg.vulnerabilities.entries()) {
        if (typeof vuln?.id !== 'string' || !vuln.id) {
          return { ok: false, error: `results[${resultIndex}].packages[${pkgIndex}].vulnerabilities[${vulnIndex}] is missing id`, findings: [] };
        }
        findings.push({ vulnId: vuln.id, ecosystem, packageName, severity: classifyFinding(vuln) });
      }
    }
  }
  return { ok: true, findings };
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `node --test .github/scripts/osv-gate.test.mjs`
Expected: PASS (40 tests total — 15 from `osv-classify.test.mjs` + 25 from `osv-gate.test.mjs`).

- [ ] **Step 6: Add the CLI entrypoint**

```js
// append to .github/scripts/osv-gate.mjs — add these three import lines to the top of the file's
// import block (first use of node:fs/node:child_process/node:url in this file, nothing to merge
// into, but still belongs at the top with the rest, not mid-file):
//   import { readFileSync, appendFileSync } from 'node:fs';
//   import { execFileSync } from 'node:child_process';
//   import { pathToFileURL } from 'node:url';

function parseArgs(argv) {
  const get = (flag) => argv.find((a) => a.startsWith(`--${flag}=`))?.split('=')[1];
  const mode = get('mode');
  const input = get('input');
  const scannerExitCode = get('scanner-exit-code');
  if (mode !== 'pr' && mode !== 'scheduled') throw new Error('--mode must be "pr" or "scheduled"');
  if (!input) throw new Error('--input=<path-to-osv-scanner-json> is required');
  if (scannerExitCode === undefined || Number.isNaN(Number(scannerExitCode))) {
    throw new Error('--scanner-exit-code=<n> is required');
  }
  return { mode, input, scannerExitCode: Number(scannerExitCode) };
}

function loadScanResult(inputPath, scannerExitCode) {
  const exitInterpretation = interpretScannerExitCode(scannerExitCode);
  if (!exitInterpretation.ok) return { ok: false, error: exitInterpretation.error, findings: [] };

  try {
    const raw = JSON.parse(readFileSync(inputPath, 'utf8'));
    return normalizeOsvScanOutput(raw);
  } catch (err) {
    return { ok: false, error: `Could not read/parse ${inputPath}: ${err.message}`, findings: [] };
  }
}

function writeStepSummary(markdown) {
  const summaryPath = process.env.GITHUB_STEP_SUMMARY;
  if (summaryPath) appendFileSync(summaryPath, markdown);
  else process.stdout.write(markdown); // local/manual runs without the GitHub env var
}

function listOpenIssues() {
  const raw = execFileSync('gh', ['issue', 'list', '--label', 'security', '--state', 'open', '--json', 'number,body'], { encoding: 'utf8' });
  return JSON.parse(raw);
}

function applyIssueActions(actions) {
  for (const { finding, key } of actions.creates) {
    execFileSync('gh', ['issue', 'create', '--title', `[security] ${finding.vulnId} in ${finding.packageName}`,
      '--label', 'security', '--label', 'dependencies',
      '--body', `${finding.severity} vulnerability ${finding.vulnId} in ${finding.ecosystem} package ${finding.packageName}.\n\n<!-- osv-gate:${key} -->`]);
  }
  for (const { number, finding } of actions.updates) {
    execFileSync('gh', ['issue', 'comment', String(number), '--body', `Still present: ${finding.severity} ${finding.vulnId} in ${finding.packageName}.`]);
  }
  for (const { number } of actions.closes) {
    execFileSync('gh', ['issue', 'close', String(number), '--comment', 'No longer detected by osv-scanner as of this run.']);
  }
}

function main() {
  const { mode, input, scannerExitCode } = parseArgs(process.argv.slice(2));
  const scanResult = loadScanResult(input, scannerExitCode);
  writeStepSummary(scanResult.ok ? buildSummaryTable(scanResult.findings) : buildErrorSummary(scanResult.error));

  if (mode === 'pr') {
    const gate = decidePrGate(scanResult);
    process.stdout.write(`${gate.reason}\n`);
    process.exit(gate.blocked ? 1 : 0);
  }

  const openIssues = listOpenIssues();
  const actions = planIssueActions(scanResult, { openIssues });
  applyIssueActions(actions);
  process.exit(scanResult.ok ? 0 : 1);
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main();
}
```

**Why `pathToFileURL` instead of the naive `` `file://${process.argv[1]}` `` string comparison**:
found during this task's own verification, not by a unit test — the naive form never matches on
Windows (`import.meta.url` is `file:///C:/Users/.../osv-gate.mjs`; `process.argv[1]` is a bare
Windows path with backslashes, e.g. `C:\Users\...\osv-gate.mjs`, so the two strings can never be
equal). `pathToFileURL(...).href` normalizes both sides to the same URL form on every platform.
The `process.argv[1] &&` guard additionally protects against `pathToFileURL(undefined)` throwing in
a context where no main script path exists (e.g. this module `import`ed from a REPL or a context
with no `argv[1]`) — `main()` correctly stays inert in the `node --test` runner on every platform,
confirmed by manually running the CLI end-to-end (not just its unit tests) after this fix, including
its fail-closed paths: real captured findings, exit codes 127/128, malformed JSON, an incomplete
nested structure, and a genuinely clean scan — every case rendered correctly and set the right exit
code.

- [ ] **Step 7: Run full test suite**

Run: `cd .github/scripts && npm test`
Expected: PASS (40 tests total — 15 from `osv-classify.test.mjs` + 25 from `osv-gate.test.mjs`;
`main()` itself stays untouched by the test suite, gated behind the `import.meta.url` CLI check).

- [ ] **Step 8: Commit**

```bash
git add \
  .github/scripts/osv-gate.mjs \
  .github/scripts/osv-gate.test.mjs \
  .github/scripts/fixtures/sample-scan-output.json \
  doc/plans/2026-09-02-osv-scanner-ci-implementation.md
git commit -m "feat(ci): add strict scan normalization and OSV gate CLI"
```

---

## Task 9: Resolve pinned versions — OSV-Scanner digest and Action SHAs

These values are consumed **directly** by Task 10/11's YAML when it's first written — no
placeholder is ever committed (a broken `<DIGEST>`-style commit followed by a "fix" commit later
would violate this repo's commit-safety rule: every commit must be correct as committed, not fixed
by the next one).

- [ ] **Step 1: Resolve the OSV-Scanner image digest**

```bash
docker pull ghcr.io/google/osv-scanner:latest
docker inspect --format='{{index .RepoDigests 0}}' ghcr.io/google/osv-scanner:latest
```

Record the exact `ghcr.io/google/osv-scanner@sha256:<digest>` string.

**Resolved (this task's own run):** `ghcr.io/google/osv-scanner@sha256:8108ae94eadea5a02c9bec6e646909d5b790b44bd62d7f5b7f0b1d6d0ffc7734`
— `docker inspect`'s `org.opencontainers.image.version` label confirms this is `2.5.1`.

- [ ] **Step 2: Resolve `actions/checkout` and `actions/setup-node` commit SHAs for the major
  version pinned**

```bash
git ls-remote --tags https://github.com/actions/checkout 'refs/tags/v4.*' | sort -t/ -k3 -V | tail -1
git ls-remote https://github.com/actions/checkout refs/tags/v4   # confirm bare v4 matches the above
git ls-remote --tags https://github.com/actions/setup-node 'refs/tags/v4.*' | sort -t/ -k3 -V | tail -1
git ls-remote https://github.com/actions/setup-node refs/tags/v4 # confirm bare v4 matches the above
```

(A lightweight tag `vN` like `v4` typically points at the same commit as the newest `v4.x.y` release
tag — confirm both resolve to the same SHA before using the bare `v4` ref's commit; if they differ,
use the newest concrete `v4.x.y` tag's SHA.) Record both resolved SHAs, in the form
`actions/checkout@<sha> # v4.x.y` (comment noting the human-readable version, since the SHA alone
isn't self-documenting).

**Resolved (this task's own run — both bare `v4` tags matched their newest concrete `v4.x.y` release
exactly, no discrepancy):**
- `actions/checkout@11d5960a326750d5838078e36cf38b85af677262 # v4.4.0`
- `actions/setup-node@49933ea5288caeca8642d1e84afbd3f7d6820020 # v4.4.0`

- [ ] **Step 3: No commit** — these three values feed directly into Task 10 and Task 11's YAML.

---

## Task 10: `.github/workflows/osv-scanner-pr.yml`

**Files:**
- Create: `.github/workflows/osv-scanner-pr.yml`

- [ ] **Step 1: Write the workflow, substituting Task 9's resolved values directly**

```yaml
name: OSV-Scanner (PR gate)

on:
  pull_request:
    branches: [main]

permissions:
  contents: read

jobs:
  scan:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@11d5960a326750d5838078e36cf38b85af677262 # v4.4.0
      - uses: actions/setup-node@49933ea5288caeca8642d1e84afbd3f7d6820020 # v4.4.0
        with:
          node-version: '20'

      - name: Run OSV-Scanner
        id: scan
        run: |
          set +e
          docker run --rm -v "$PWD:/repo" \
            ghcr.io/google/osv-scanner@sha256:8108ae94eadea5a02c9bec6e646909d5b790b44bd62d7f5b7f0b1d6d0ffc7734 \
            --config=/repo/osv-scanner.toml \
            --format json \
            -L /repo/app/gradle.lockfile \
            -L /repo/core/data/gradle.lockfile \
            -L /repo/core/designsystem/gradle.lockfile \
            -L /repo/core/domain/gradle.lockfile \
            -L /repo/core/security/gradle.lockfile \
            -L /repo/feature/categorias/gradle.lockfile \
            -L /repo/feature/cuenta/gradle.lockfile \
            -L /repo/feature/movimientos/gradle.lockfile \
            -L /repo/feature/planes/gradle.lockfile \
            -L /repo/rules-tests/package-lock.json \
            > osv-scan-output.json
          echo "exit_code=$?" >> "$GITHUB_OUTPUT"
          set -e
        # osv-scanner's own exit code (0=clean, 1=vulnerabilities, 127=execution failed,
        # 128=no packages found) is captured explicitly via `set +e` around this one command —
        # `|| true` would discard the distinction between "found vulnerabilities" and "the scanner
        # itself broke", which the gate script needs (see osv-gate.mjs's interpretScannerExitCode).

      - name: Evaluate gate
        run: node .github/scripts/osv-gate.mjs --mode=pr --input=osv-scan-output.json --scanner-exit-code=${{ steps.scan.outputs.exit_code }}
```

- [ ] **Step 2: Commit**

```bash
git add .github/workflows/osv-scanner-pr.yml
git commit -m "feat(ci): add OSV-Scanner PR gate workflow"
```

---

## Task 11: `.github/workflows/osv-scanner-scheduled.yml`

**Files:**
- Create: `.github/workflows/osv-scanner-scheduled.yml`

- [ ] **Step 1: Write the workflow, substituting Task 9's resolved values directly**

```yaml
name: OSV-Scanner (daily scheduled scan)

on:
  schedule:
    - cron: '17 3 * * *' # 03:17 UTC daily — low-activity window
  workflow_dispatch:

permissions:
  contents: read
  issues: write

jobs:
  scan:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@11d5960a326750d5838078e36cf38b85af677262 # v4.4.0
      - uses: actions/setup-node@49933ea5288caeca8642d1e84afbd3f7d6820020 # v4.4.0
        with:
          node-version: '20'

      - name: Run OSV-Scanner
        id: scan
        run: |
          set +e
          docker run --rm -v "$PWD:/repo" \
            ghcr.io/google/osv-scanner@sha256:8108ae94eadea5a02c9bec6e646909d5b790b44bd62d7f5b7f0b1d6d0ffc7734 \
            --config=/repo/osv-scanner.toml \
            --format json \
            -L /repo/app/gradle.lockfile \
            -L /repo/core/data/gradle.lockfile \
            -L /repo/core/designsystem/gradle.lockfile \
            -L /repo/core/domain/gradle.lockfile \
            -L /repo/core/security/gradle.lockfile \
            -L /repo/feature/categorias/gradle.lockfile \
            -L /repo/feature/cuenta/gradle.lockfile \
            -L /repo/feature/movimientos/gradle.lockfile \
            -L /repo/feature/planes/gradle.lockfile \
            -L /repo/rules-tests/package-lock.json \
            > osv-scan-output.json
          echo "exit_code=$?" >> "$GITHUB_OUTPUT"
          set -e

      - name: Evaluate and manage issues
        env:
          GITHUB_TOKEN: ${{ github.token }}
        run: node .github/scripts/osv-gate.mjs --mode=scheduled --input=osv-scan-output.json --scanner-exit-code=${{ steps.scan.outputs.exit_code }}
```

- [ ] **Step 2: Commit**

```bash
git add .github/workflows/osv-scanner-scheduled.yml
git commit -m "feat(ci): add OSV-Scanner daily scheduled scan with issue lifecycle"
```

---

## Task 12: Acceptance run

`workflow_dispatch`/`schedule` triggers require the workflow file to already exist on the
repository's **default branch** (`docs.github.com`, confirmed: *"To trigger the workflow_dispatch
event, your workflow must be in the default branch"*) — so `osv-scanner-scheduled.yml` genuinely
cannot be dispatched from this branch before merge. `pull_request` has no such restriction (a
workflow newly added inside a PR's own branch still runs for that same PR), which is what makes the
PR-gate workflow testable now.

- [ ] **Step 1: Local dry-run (if Docker is available)** — run the same `docker run` command from
  Task 10/11 locally against this repo's real lockfiles, then run
  `node .github/scripts/osv-gate.mjs --mode=pr --input=<output> --scanner-exit-code=<real exit
  code>` locally and inspect stdout (no `GITHUB_STEP_SUMMARY` env var locally, so the summary prints
  to stdout — see Task 8's `writeStepSummary`). Also deliberately force an error path (e.g. an
  intentionally wrong image reference) to confirm the fail-closed summary/exit-code behavior in
  practice, not just in unit tests.

- [ ] **Step 2: Push and open a PR** from `chore/m8-osv-scanner-ci` into `main` (draft is fine — this
  is for the workflow to run, not to merge yet):

```bash
git push origin chore/m8-osv-scanner-ci
gh pr create --draft --base main --head chore/m8-osv-scanner-ci \
  --title "chore: OSV-Scanner CI gate (M8)" --body "Implements doc/plans/2026-09-02-osv-scanner-ci-implementation.md"
```

This triggers `osv-scanner-pr.yml` for real, against this repo's actual `gradle.lockfile`s and
`rules-tests/package-lock.json`.

- [ ] **Step 3: Inspect the run's step summary** (`gh pr checks` / the Actions UI) — confirm the
  findings table (or error summary) rendered correctly, and that `normalizeOsvScanOutput` (Task 8)
  didn't silently mis-map the real shape. If it did, fix the normalizer and its tests before
  considering this plan done — do not merge a normalizer that was never actually exercised against
  real output.

- [ ] **Step 4: Scheduled-workflow verification is explicitly deferred to after merge** — record
  this as a follow-up, don't attempt to fake it now: once `chore/m8-osv-scanner-ci` merges to `main`,
  run `gh workflow run osv-scanner-scheduled.yml --ref main` once and confirm the Issue
  create/update/close path against the live `gh` API works end-to-end. This step is **not**
  achievable from this branch and is not part of this branch's own completion criteria.

---

## Task 14: Documentation and gates

**Files:**
- Modify: `CHANGELOG.md`
- Modify: `backlog.md`
- Modify: `ROADMAP.md`

- [ ] **Step 1: Run the gate for this slice**

```bash
cd .github/scripts && npm test
```

Expected: PASS, all tests from Tasks 2-8 green together (no Android gates apply — see this plan's
own header note).

- [ ] **Step 2: Update CHANGELOG.md's `[Unreleased]` section** with an entry describing the shipped
  PR gate and scheduled scan (concrete wording depends on the final commit history — summarize what
  actually shipped, not what was planned).

- [ ] **Step 3: Check off the M8/OSV-scanner atomic backlog.md items** (added separately, after this
  plan's approval, in the plan+backlog+ROADMAP commit) as done, with a `Deviation:` note if Task 8's
  real-shape verification (Step 1/Task 12 Step 3) turned up a difference from what this plan assumed.

- [ ] **Step 4: Update ROADMAP.md's M8 line** from "design/plan in progress" to reflect the
  osv-scanner-CI half as implemented — the MFA half of M8 stays `Not started`, untouched.

- [ ] **Step 5: Commit**

```bash
git add CHANGELOG.md backlog.md ROADMAP.md
git commit -m "docs: close out OSV-Scanner CI gate (M8) documentation"
```

---

## Self-Review

**Spec coverage:** every design-doc requirement has a task, and every blocker from this plan's own
review round is fixed at its source rather than patched afterward: UNKNOWN blocks in both
`decidePrGate` and `planIssueActions` (Task 6-7, same `BLOCKING_SEVERITIES` set), scanner exit codes
127/128 are distinguished from 0/1 and block regardless of JSON content (Task 8's
`interpretScannerExitCode`, wired through both workflows' `set +e` capture), `--config` is passed
explicitly in both `docker run` invocations (Tasks 10-11), `normalizeOsvScanOutput` fails closed on
any structural gap instead of defaulting to empty (Task 8), the step summary has a distinct error
renderer that can never read as "clean" (Task 5/8), and Task 12 no longer assumes `workflow_dispatch`
works pre-merge — it uses the PR trigger for what's actually testable now and explicitly defers the
scheduled-workflow check to post-merge.

**Placeholder scan:** Tasks 10-11's YAML now shows the real resolved digest/SHAs directly (Task 9's
own run resolved `sha256:8108ae94...`, `actions/checkout@11d5960a... # v4.4.0`,
`actions/setup-node@49933ea5... # v4.4.0`) — no `<RESOLVED_SHA>`/`<RESOLVED_DIGEST>` placeholder
was ever committed (unlike the earlier draft's `<DIGEST_FROM_TASK_9>` placeholder + later fix-up
commit, which review correctly flagged as violating this repo's commit-safety rule).

**Type consistency:** `finding` objects carry `{vulnId, ecosystem, packageName, severity}`
consistently from `normalizeOsvScanOutput` (Task 8) through `buildSummaryTable`/`decidePrGate`/
`planIssueActions` (Tasks 5-7); `scanResult` carries `{ok, error?, findings}` consistently
throughout, and `BLOCKING_SEVERITIES` (`CRITICAL`/`HIGH`/`UNKNOWN`) is defined once in `osv-gate.mjs`
and reused by both `decidePrGate` and `planIssueActions` rather than duplicated.

**Test coverage additions from this review round:** exit-code matrix (Task 8), partially-valid JSON
structures at every nesting level (Task 8), UNKNOWN-blocks assertions (Tasks 6-7), contradictory
severity sources (Task 4), and CVSS Scope-Changed/rounding/zero-impact branches (Task 3).

---

## Deferred, not part of this plan's own tasks

Backlog.md atomic-task entries and the ROADMAP.md M8 "in progress" pointer land together in one
commit after this plan is reviewed and approved (separate from Task 14, which closes them out once
implementation is actually done). The stray `BUILD-001` working-tree changes noted in this plan's
header are this session's to resolve with the user before any broad `git add`, not something this
plan's tasks touch.
