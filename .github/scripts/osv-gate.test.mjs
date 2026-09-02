// .github/scripts/osv-gate.test.mjs
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { buildSummaryTable, buildErrorSummary, decidePrGate, planIssueActions, issueDedupeKey, interpretScannerExitCode, normalizeOsvScanOutput } from './osv-gate.mjs';

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

// A single logical vulnerability commonly surfaces as multiple raw finding rows: the same
// package resolves to several versions across configurations within one lockfile (e.g. AGP's
// unified-test-platform-* tooling resolving io.netty:netty-handler to both 4.1.110.Final and
// 4.1.93.Final), and/or the same package+vuln repeats across this repo's 9 Android module
// lockfiles. Without dedup, planIssueActions used to plan one create/update per raw row —
// discovered live via OSV-M805 validation: a single blocking finding produced 18 duplicate
// GitHub issues in one run (2 resolved versions x 9 lockfiles).

test('planIssueActions consolidates N raw rows sharing the same dedupe key into exactly 1 create', () => {
  const rowFor = (version, source) => ({
    vulnId: 'GHSA-9', ecosystem: 'Maven', packageName: 'io.netty:netty-handler', severity: 'HIGH', version, source,
  });
  const scanResult = {
    ok: true,
    findings: [
      rowFor('4.1.110.Final', 'app/gradle.lockfile'),
      rowFor('4.1.93.Final', 'app/gradle.lockfile'),
      rowFor('4.1.110.Final', 'core/data/gradle.lockfile'),
    ],
  };
  const actions = planIssueActions(scanResult, { openIssues: [] });
  assert.equal(actions.creates.length, 1);
  assert.equal(actions.updates.length, 0);
});

test('planIssueActions consolidates N raw rows sharing the same dedupe key into exactly 1 update, never a create', () => {
  const rowFor = (source) => ({ vulnId: 'GHSA-9', ecosystem: 'npm', packageName: 'left-pad', severity: 'HIGH', source });
  const openIssues = [OPEN_ISSUE_FOR('GHSA-9', 'npm', 'left-pad', 42)];
  const scanResult = { ok: true, findings: [rowFor('a/package-lock.json'), rowFor('b/package-lock.json'), rowFor('c/package-lock.json')] };
  const actions = planIssueActions(scanResult, { openIssues });
  assert.equal(actions.creates.length, 0);
  assert.equal(actions.updates.length, 1);
  assert.equal(actions.updates[0].number, 42);
});

test('planIssueActions never produces both a create and an update for the same key, across distinct keys in one run', () => {
  const netty = (version) => ({ vulnId: 'GHSA-NEW', ecosystem: 'Maven', packageName: 'io.netty:netty-handler', severity: 'HIGH', version });
  const bc = (version) => ({ vulnId: 'GHSA-OLD', ecosystem: 'Maven', packageName: 'org.bouncycastle:bcprov-jdk18on', severity: 'CRITICAL', version });
  const openIssues = [OPEN_ISSUE_FOR('GHSA-OLD', 'Maven', 'org.bouncycastle:bcprov-jdk18on', 7)];
  const scanResult = {
    ok: true,
    findings: [netty('4.1.110.Final'), netty('4.1.93.Final'), bc('1.79'), bc('1.81')],
  };
  const actions = planIssueActions(scanResult, { openIssues });
  assert.equal(actions.creates.length, 1, 'exactly one create, for GHSA-NEW');
  assert.equal(actions.updates.length, 1, 'exactly one update, for GHSA-OLD');
  assert.equal(actions.creates[0].key, issueDedupeKey({ vulnId: 'GHSA-NEW', ecosystem: 'Maven', packageName: 'io.netty:netty-handler' }));
  assert.equal(actions.updates[0].key, issueDedupeKey({ vulnId: 'GHSA-OLD', ecosystem: 'Maven', packageName: 'org.bouncycastle:bcprov-jdk18on' }));
});

test('planIssueActions preserves every occurrence (version/source) when consolidating duplicate rows — no evidence lost', () => {
  const rowFor = (version, source) => ({
    vulnId: 'GHSA-9', ecosystem: 'Maven', packageName: 'io.netty:netty-handler', severity: 'HIGH', version, source,
  });
  const scanResult = {
    ok: true,
    findings: [
      rowFor('4.1.110.Final', 'app/gradle.lockfile'),
      rowFor('4.1.93.Final', 'core/data/gradle.lockfile'),
      rowFor('4.1.110.Final', 'feature/planes/gradle.lockfile'),
    ],
  };
  const actions = planIssueActions(scanResult, { openIssues: [] });
  const { occurrences } = actions.creates[0].finding;
  assert.equal(occurrences.length, 3);
  assert.deepEqual(occurrences, [
    { version: '4.1.110.Final', source: 'app/gradle.lockfile' },
    { version: '4.1.93.Final', source: 'core/data/gradle.lockfile' },
    { version: '4.1.110.Final', source: 'feature/planes/gradle.lockfile' },
  ]);
});

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

test('normalizeOsvScanOutput carries version and source lockfile path per finding — needed to consolidate duplicate rows without losing evidence', () => {
  const raw = {
    results: [
      {
        source: { path: 'app/gradle.lockfile' },
        packages: [
          {
            package: { name: 'com.example:lib', ecosystem: 'Maven', version: '1.2.3' },
            vulnerabilities: [{ id: 'GHSA-1', database_specific: { severity: 'HIGH' } }],
          },
        ],
      },
    ],
  };
  const result = normalizeOsvScanOutput(raw);
  assert.equal(result.findings[0].version, '1.2.3');
  assert.equal(result.findings[0].source, 'app/gradle.lockfile');
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
