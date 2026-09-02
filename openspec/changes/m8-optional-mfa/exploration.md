# Exploration: OSV-M802 — Optional MFA (M8, MFA half)

Backlog item: `OSV-M802` — "Product/architecture discovery for optional MFA (M8, MFA half) — no
design yet." Second half of milestone M8 (`ROADMAP.md:56-62`); the osv-scanner-in-CI half is a
separate, already in-progress change and is out of scope here.

## Current State

Firebase Auth email/password is the only real auth mechanism today
(`core/domain/.../repository/AuthRepository.kt`, implemented by
`core/data/.../auth/FirebaseAuthRepository.kt`; UI in `:feature:cuenta` —
`CuentaViewModel`/`CuentaScreen`). Session model `Sesion`
(`core/domain/.../model/Sesion.kt`) carries only `uid/esAnonima/email/nombre/photoUrl` — no
MFA-related fields. `SesionGateViewModel` (`:app`) + `SesionPreferences` (DataStore) gate the app
root between anonymous/authenticated/logged-out states (2026-08-30 login/logout design). No MFA
code exists anywhere in the repo. No email-verification flow exists either
(`sendEmailVerification`/`isEmailVerified` — zero matches repo-wide) — this matters because
Firebase requires verified email before MFA enrollment.

Firestore roles (`firestore.rules`) are per-plan documents (`owner(3) > admin(2) > editor(1) >
viewer(0)`) keyed by Firebase Auth `uid`. MFA in Firebase Auth is a property of the `FirebaseUser`
(account-level), fully decoupled from plan membership — confirmed via rules structure.

Firebase Auth Android MFA support (verified against official docs, Sept 2026): both TOTP and SMS
second factors require upgrading the Firebase project to **"Firebase Authentication with Identity
Platform"** (a Blaze/pay-as-you-go plan feature — free up to 50k MAU, SMS billed per message after
10/day free, TOTP has no per-use cost). MFA also **requires email verification** to be enforced
first. Android SDK surface: `user.multiFactor.session`, `PhoneAuthProvider` /
`TotpMultiFactorGenerator`, `user.multiFactor.enroll()`, `FirebaseAuthMultiFactorException` +
`MultiFactorResolver` for the sign-in-time challenge.

## Affected Areas

- `core/domain/.../repository/AuthRepository.kt` — needs new MFA enroll/challenge/unenroll methods.
- `core/domain/.../model/Sesion.kt` — needs fields exposing MFA enrollment/verification state.
- `core/data/.../auth/FirebaseAuthRepository.kt` — implementation against `FirebaseUser.multiFactor`.
- `:feature:cuenta` (`CuentaViewModel`, `CuentaScreen`) — enrollment UI (TOTP secret/QR), sign-in-time
  second-factor challenge UI, new `RegistroEstado`-like states.
- `app/SesionGateViewModel` + `MainActivity` backstack — current auth state machine models simple
  success/failure; a second-factor challenge (`FirebaseAuthMultiFactorException` mid sign-in) is a
  genuinely new intermediate state not currently modeled.
- No Room/DataStore MFA secret storage should be added — Firebase Auth SDK holds enrollment state
  server-side, matching the existing "never derive/store secrets locally" posture
  (`AGENTS.md` security section, SQLCipher/Keystore precedent). `SesionPreferences` (DataStore) is
  only a plausible pattern for non-secret local flags (e.g. "MFA nudge dismissed").
- `doc/security/owasp.md` M3/M8 rows need updating once an approach is chosen.
- No Cloud Functions exist in the repo today (client + Firestore rules only) — any admin-assisted
  MFA-recovery flow would be a new backend surface.

## Approaches

1. **TOTP-only (authenticator app)** — Pros: no per-use SMS cost, no phone-number PII collection,
   works offline for code generation, stronger OWASP posture than SMS. Cons: enrollment UX needs
   QR/secret display + external authenticator app (extra friction for non-technical family
   members); still needs the Identity Platform upgrade and the missing email-verification
   prerequisite. Effort: Medium.
2. **SMS-based MFA** — Pros: familiar UX, no separate app needed. Cons: recurring per-message cost
   beyond 10/day free, requires collecting a phone number (new field, not in `Sesion`/Firestore
   user doc today), weaker against SIM-swap per OWASP, same Identity Platform prerequisite plus new
   PII handling. Effort: Medium-High.
3. **Defer (keep as open backlog item)** — Pros: zero cost/risk now, avoids committing to a
   billing-plan change before validating real need. Cons: leaves owner/admin-role accounts in
   shared plans with no step-up protection beyond a single password. Effort: None.

## Recommendation

TOTP-only, explicitly excluding SMS this milestone (cost, PII, weaker OWASP profile). Treat "add
email verification to registration/linking" as a distinct, possibly-separable prerequisite — it is
independently useful (closes an existing gap where nothing confirms email ownership) and MFA is
blocked without it either way. Do not proceed to `sdd-propose` until the Blaze/Identity-Platform
upgrade is explicitly confirmed acceptable — it is a billing/infra decision outside code that
explore/propose cannot make unilaterally.

## Risks

- **Billing/infra**: MFA (any factor, including TOTP) requires upgrading the Firebase project to
  Identity Platform (Blaze plan) — needs explicit user/product sign-off before design work.
- **Missing prerequisite**: no email-verification flow exists; Firebase requires it for MFA
  enrollment — this is either in-scope for this change or a hard blocking dependency on a
  preceding change.
- **Sign-in state machine**: `CuentaViewModel`/`SesionGateViewModel` model auth as simple
  success/failure today; a second-factor challenge is a new state requiring real design, not a
  small tweak.
- **No recovery story**: Firebase's Android MFA API has no built-in backup-codes concept; a
  lost-device recovery flow likely needs manual admin/support unenrollment via the Firebase Admin
  SDK — a new backend surface (no Cloud Functions exist in this repo today).
- **Open product question**: MFA is confirmed account-scoped, decoupled from plan roles — but
  should the app require/nudge MFA specifically for owner/admin roles (elevated permissions in
  shared plans)? Unresolved, needs a product decision in propose.
- Opt-in entry point (where in `PerfilContenido`/profile screen) is undecided — UX decision for
  propose.

## Ready for Proposal

**No.** Two blockers must be resolved with the user first:

1. Explicit confirmation that the Identity Platform/Blaze upgrade is acceptable.
2. Decision on whether email verification ships as part of this change or as a separate preceding
   change.

Once resolved, `sdd-propose` can proceed with TOTP-only as the recommended default scope.

---

Engram topic: `sdd/m8-optional-mfa/explore` (observation #136).
