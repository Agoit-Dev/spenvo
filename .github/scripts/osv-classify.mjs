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
 * 1. Evaluates EVERY interpretable severity source (database_specific.severity and every CVSS_V3
 *    entry in severity[]) and returns the highest one, never just the first source present.
 * 2. If any source was PRESENT but could not be interpreted (an unrecognized database_specific
 *    label, a severity[] entry whose type isn't CVSS_V3, or a CVSS_V3 entry with a malformed
 *    vector), that represents real uncertainty this function can't resolve — the result escalates
 *    to UNKNOWN (blocking) UNLESS a recognized source already reached CRITICAL/HIGH on its own,
 *    since that's already at least as conservative as UNKNOWN.
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
