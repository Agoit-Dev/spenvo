# Rule: Security Baseline (OWASP)

## Purpose
Meet the OWASP Mobile Top 10 2025 security baseline. See the full matrix in
`doc/security/owasp.md` (generated in M0) and the `security-review` skill.

## Non-negotiable rules (all active)
1. **allowBackup=false** in the manifest (financial data backups forbidden).
2. **R8 enabled** in release (`optimization.enable = true`).
3. **Encrypted traffic**: `cleartextTrafficPermitted="false"` in network_security_config.
4. **Secrets**: NEVER in the repo or in strings. Only `local.properties` (gitignored)
   or environment variables; loaded at build time.
5. **Encrypted DB**: SQLCipher (passphrase from Android Keystore, generated on first use;
   the Keystore module is implemented in M1).
6. **Dependency locking**: versioned `gradle.lockfile`; a dependency change = regenerate.
7. **App Check** (M2): playIntegrity against backend access from outside the app.
8. **Firestore rules**: deny-by-default with server-side roles (M3). Never allow
   `read/write: if true`.
9. **Do not log** sensitive data or credentials. Logs contain no amounts or emails.
10. **Deep links / intents**: validate data URI; do not trust unverified extras.
11. **Storage rules** (M7): deny-by-default, authenticated-only, per-user path scoping
    (`avatars/{uid}/...` writable/readable only by that `uid`), `image/*` content-type
    allowlist, 5MB file-size limit. Never allow `read/write: if true`.

## Continuous monitoring
- New manifest permission → justify in changelog and review with `security-review`.
- New native dependency → review the vector (source, license, maintenance).
- `editedBy/editedAt` prevent spoofing of who edits: set server-side (rules) when
  possible, never trust the client field.

## Reference
- OWASP Mobile Top 10 2025 (official docs).
- `.agents/skills/security-review.md` — review checklist.