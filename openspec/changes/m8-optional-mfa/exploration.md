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

Firebase Auth Android MFA support (verified against Google Cloud/Firebase docs, Sept 2026): both
TOTP and SMS second factors require upgrading the Firebase project to **"Firebase Authentication
with Identity Platform."** Correction from an earlier draft of this document: that upgrade is a
**free switch**, not a Blaze/billing requirement — Identity Platform has no price tag of its own
and is available on the Spark (free) plan. What actually changes is the operational envelope: a
Spark project that upgrades to Identity Platform drops from the standard 50,000-MAU free-auth
allowance to a **3,000-daily-active-users (DAU) cap**; staying under the standard 50,000-MAU
no-cost tier (and beyond, at $0.0055/MAU) requires Blaze instead. **TOTP** has no per-use billing
on either plan. **SMS** is the one piece that does have a hard Blaze requirement: since September
2024, SMS verification needs the Blaze plan with a billing account attached, billed per SMS sent
(first 10/day free). MFA also **requires email verification** to be enforced first. Android SDK
surface: `user.multiFactor.session`, `PhoneAuthProvider` / `TotpMultiFactorGenerator`,
`user.multiFactor.enroll()`, `FirebaseAuthMultiFactorException` + `MultiFactorResolver` for the
sign-in-time challenge.

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

1. **TOTP-only (authenticator app)** — Pros: no per-use cost on either plan, no phone-number PII
   collection, works offline for code generation, stronger OWASP posture than SMS, does not force
   Blaze. Cons: enrollment UX needs QR/secret display + external authenticator app (extra friction
   for non-technical family members); still needs the Identity Platform upgrade (accepting its
   3,000-DAU cap if staying on Spark) and the missing email-verification prerequisite.
   Effort: Medium.
2. **SMS-based MFA** — Pros: familiar UX, no separate app needed. Cons: mandatory Blaze plan +
   billing account (since Sept 2024) plus recurring per-message cost beyond 10/day free, requires
   collecting a phone number (new field, not in `Sesion`/Firestore user doc today), weaker against
   SIM-swap per OWASP, same Identity Platform prerequisite plus new PII handling. Effort:
   Medium-High.
3. **Defer (keep as open backlog item)** — Pros: zero operational/UX footprint now, avoids
   committing to the Identity Platform switch and its DAU-cap tradeoff before validating real
   need. Cons: leaves owner/admin-role accounts in shared plans with no step-up protection beyond
   a single password. Effort: None.

## Recommendation

TOTP-only, explicitly excluding SMS this milestone. SMS is the option with a real mandatory Blaze
+ per-message cost; TOTP does not force any billing plan. Treat "add email verification to
registration/linking" as a distinct, possibly-separable prerequisite — it is independently useful
(closes an existing gap where nothing confirms email ownership) and MFA is blocked without it
either way. Do not proceed to `sdd-propose` until the Identity Platform switch is explicitly
confirmed acceptable — even without a mandatory Blaze cost for TOTP, it is a project-wide
operational change (Spark's DAU cap drops to 3,000, plus audit-logging/multi-tenancy surface
changes) outside code that explore/propose cannot decide unilaterally.

## Risks

- **Operational/infra**: enabling Identity Platform (required for any MFA factor, TOTP included)
  changes the project's operating limits — Spark's free-auth allowance drops from 50,000 MAU to a
  3,000-DAU cap; keeping the 50,000-MAU allowance requires Blaze. This is a platform-wide toggle,
  not scoped to MFA users only — needs explicit user/product sign-off before design work.
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

1. Explicit confirmation that the Identity Platform operational tradeoff (3,000-DAU cap if
   staying on Spark, or Blaze to keep 50,000 MAU) is acceptable — not a mandatory billing cost for
   TOTP, but still a project-wide platform change.
2. Decision on whether email verification ships as part of this change or as a separate preceding
   change.

Once resolved, `sdd-propose` can proceed with TOTP-only as the recommended default scope.

## Resolution

**Deferred**, 2026-09-02. The user decided not to proceed to `sdd-propose` for the current MVP
stage. The deferral rests on this corrected basis (not on a mandatory Blaze/billing cost, which an
earlier draft of this document incorrectly claimed):

- Enabling Identity Platform changes the project's operational limits and conditions platform-wide
  (the DAU/MAU tradeoff above), even though it carries no mandatory fee for TOTP.
- Email verification, a hard MFA prerequisite, is not implemented yet.
- MFA adds real state-machine, recovery, and UX surface (see Risks) — meaningful implementation
  cost regardless of billing.
- It does not deliver enough value for the app's current prototype stage.
- There is potential future cost (Blaze once past the free MAU/DAU tier, or if SMS is ever added)
  but no cost that is mandatory today for a small MVP choosing TOTP-only.

See `backlog.md` (`OSV-M802`), `ROADMAP.md` (M8 closed), `CHANGELOG.md`, and
`openspec/changes/m8-optional-mfa/state.yaml` for the recorded outcome.

---

Engram topic: `sdd/m8-optional-mfa/explore` (observation #136).
