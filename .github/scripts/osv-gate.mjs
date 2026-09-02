// .github/scripts/osv-gate.mjs

import { readFileSync, appendFileSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import { pathToFileURL } from 'node:url';
import { classifyFinding } from './osv-classify.mjs';

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

/** @param {{vulnId: string, ecosystem: string, packageName: string}} f */
export function issueDedupeKey(f) {
  return `${f.vulnId}::${f.ecosystem}::${f.packageName}`;
}

/**
 * A single logical vulnerability commonly surfaces as multiple raw finding rows: the same
 * package can resolve to several versions across different configurations within one lockfile,
 * and/or the same package+vuln repeats across every module's lockfile in a multi-module repo.
 * Collapses every row sharing an issueDedupeKey into one entry, preserving each row's
 * version/source as an `occurrences` list so no evidence is lost — the consolidated entry is
 * what a single GitHub issue create/update/close action should be planned against.
 * @param {{vulnId: string, ecosystem: string, packageName: string, severity: string, version?: string, source?: string}[]} findings
 */
export function consolidateBlockingFindings(findings) {
  const byKey = new Map();
  for (const f of findings) {
    const key = issueDedupeKey(f);
    if (!byKey.has(key)) {
      byKey.set(key, { vulnId: f.vulnId, ecosystem: f.ecosystem, packageName: f.packageName, severity: f.severity, occurrences: [] });
    }
    if (f.version !== undefined || f.source !== undefined) {
      byKey.get(key).occurrences.push({ version: f.version, source: f.source });
    }
  }
  return [...byKey.values()];
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
  const consolidated = consolidateBlockingFindings(blocking);
  const blockingKeys = new Set(consolidated.map(issueDedupeKey));
  const openByKey = new Map(
    openIssues
      .map((issue) => [keyFromIssueBody(issue.body), issue])
      .filter(([key]) => key !== null),
  );

  for (const finding of consolidated) {
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
        findings.push({
          vulnId: vuln.id,
          ecosystem,
          packageName,
          severity: classifyFinding(vuln),
          version: pkg.package.version,
          source: result?.source?.path,
        });
      }
    }
  }
  return { ok: true, findings };
}

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

/** @param {{version?: string, source?: string}[]} occurrences */
function occurrencesList(occurrences) {
  if (!occurrences?.length) return '';
  const lines = occurrences.map((o) => `- ${o.version ?? '?'} — ${o.source ?? '?'}`).join('\n');
  return `\n\nFound in:\n${lines}`;
}

function applyIssueActions(actions) {
  for (const { finding, key } of actions.creates) {
    execFileSync('gh', ['issue', 'create', '--title', `[security] ${finding.vulnId} in ${finding.packageName}`,
      '--label', 'security', '--label', 'dependencies',
      '--body', `${finding.severity} vulnerability ${finding.vulnId} in ${finding.ecosystem} package ${finding.packageName}.${occurrencesList(finding.occurrences)}\n\n<!-- osv-gate:${key} -->`]);
  }
  for (const { number, finding } of actions.updates) {
    execFileSync('gh', ['issue', 'comment', String(number), '--body', `Still present: ${finding.severity} ${finding.vulnId} in ${finding.packageName}.${occurrencesList(finding.occurrences)}`]);
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
