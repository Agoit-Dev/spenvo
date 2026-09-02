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
