# Skill: security-review

**Activate:** on any change to permissions, network, credentials, storage, backend, or before a security milestone.

## Steps
1. Load `doc/security/owasp.md` and `.agents/rules/security-owasp.md`.
2. Review the manifest: new permissions, `allowBackup`, `networkSecurityConfig`.
3. Review secrets handling: no values in repo/strings; `local.properties` gitignored.
4. Review storage: SQLCipher active; passphrase in Keystore; no cleartext data.
5. Review backend: deny-by-default rules, server-side roles, App Check.
6. Review logs: no amounts, emails or credentials.
7. Verify dependency locking and native dependencies.
8. Output: marked checklist + findings + required actions.

## Do not
- Do not assume "it's a demo app" justifies lowering security.
- Do not accept "we'll do it later" for anything in the active baseline.