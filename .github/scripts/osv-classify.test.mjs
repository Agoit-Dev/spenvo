// .github/scripts/osv-classify.test.mjs
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { severityBandForScore, parseCvss31BaseScore } from './osv-classify.mjs';

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
