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
