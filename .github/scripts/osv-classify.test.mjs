// .github/scripts/osv-classify.test.mjs
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { severityBandForScore, parseCvss31BaseScore, classifyFinding } from './osv-classify.mjs';

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
