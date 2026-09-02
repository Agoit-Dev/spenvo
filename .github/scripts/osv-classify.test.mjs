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
