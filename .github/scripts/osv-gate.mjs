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
