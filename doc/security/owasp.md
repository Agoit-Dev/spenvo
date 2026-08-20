# Security matrix — OWASP Mobile Top 10 2025

Version: **1.0** (M0). Reference: OWASP Mobile Application Security (2025).
Control status: ✅ active · 🔜 planned · ⚠️ pending verification.

## Summary

| Control | Where | Status | Milestone |
|---|---|---|---|
| SQLCipher (encryption at rest) | `:core:data` | ✅ passphrase from Keystore (`:core:security`) | M1 |
| Anonymous auth (guest-first) | `:core:data` + `:feature:movimientos` | ✅ background, offline-tolerant | M2 |
| App Check (Play Integrity / debug) | `:app` | ✅ wired; enforcement on Firestore in M3 | M2/M3 |
| Deny-by-default rules + roles | `firestore.rules` | ⚠️ M0 draft | M3 |
| Server-side EditedBy/EditedAt | rules | 🔜 | M5 |
| Rules tests (Emulator) | `@firebase/rules-unit-testing` | 🔜 | M3 |

## Risk → control map

### M1 - Improper Credential Usage (credential and data encryption)
- **Data**: SQLCipher active; passphrase generated on first use and stored in
  Android Keystore (AES-256/GCM). Never in cleartext or hardcoded. ✅ M1.
- **Keys**: Firebase keys in `google-services.json` (gitignored) or build config.

### M2 - Inadequate Supply Chain Security
- **Dependency locking** versioned (`gradle.lockfile` per project). ✅ M0.
- **Version verification**: Kotlin/AGP compatibility before bumping. ✅ M0.
- **App Check** (M2) and **osv-scanner CI** (M8). 🔜.

### M3 - Insecure Authentication/Authorization
- Firebase Auth **anonymous** (guest-first) wired in M2; email/password + Google
  linking in M3; **optional MFA in M8**. 🔜 M3.
- **Authorization**: server-side roles (`owner/admin/editor/viewer`) validated in rules.
  🔜 M3.
- Session/persistence: `FirebaseAuth` handles automatic token refresh. ✅ M2.

### M4 - Insufficient Input/Output Validation
- Validation in the domain (use cases) + amount bounds.
- Amounts in minor units (cents) — no float in persistence. 🔜 M1/M5.
- Deep links/URI validated (M3+). 🔜.

### M5 - Insecure Communication
- `cleartextTrafficPermitted="false"` globally. ✅ M0.
- TLS on Firebase by default (HTTPS). ✅.
- `network_security_config` deny-by-default. ✅ M0.

### M6 - Inadequate Privacy Controls
- `allowBackup=false`; `dataExtractionRules` without data. ✅ M0.
- No logging of amounts/emails/credentials. ✅ M0 rule.
- Minimal permissions; review via `security-review`. ✅ M0.

### M7 - Insufficient Binary Protections
- **R8 enabled** in release (`optimization.enable=true`). ✅ M0.
- App debuggable=false in release. ✅ default.
- Release signing (not automated until there is a user keystore). ⚠️.

### M8 - Security Misconfiguration
- Per-build-type config; secrets in `local.properties` (gitignored). ✅ M0.
- No API keys in strings. ✅ M0.

### M9 - Insecure Data Storage & Exports
- Encrypted Room (SQLCipher). ✅ M1.
- No FileProvider/data export outside the app. ✅.
- Backups disabled. ✅ M0.

### M10 - Insufficient Cryptography
- Keystore AES-256 passphrase; SQLCipher (XChaCha20 / AES-256-CBC depending on version). ✅ M1.
- No weak algorithms; crypto review in `security-review`. ✅.

## Mandatory practices (M0)
1. `allowBackup=false`, `cleartextTrafficPermitted=false`, R8 on. ✅
2. Secrets never in the repo. ✅ (`.gitignore`: `local.properties`, `google-services.json`).
3. Versioned dependency locks. ✅
4. `security-review` before security milestones. ✅ rule.
5. Logs without sensitive data. ✅ rule.

## Pending implementation (M0 debt)
- Account linking (email/Google) + registration screen → **M3**.
- Final rules + tests + Emulator → **M3**.
- Server-side `editedBy/editedAt` → **M5**.
- osv-scanner in CI + MFA → **M8**.