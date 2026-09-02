// .github/scripts/osv-gate.test.mjs
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { buildSummaryTable, buildErrorSummary, decidePrGate, planIssueActions, issueDedupeKey } from './osv-gate.mjs';

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
