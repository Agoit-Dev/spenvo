# Spenvo Roadmap & Strategic Plan 🗺️

This file defines the strategic direction and development phases of **Spenvo**. AI agents must
consult it before proposing architectural changes or starting new features.

## 🎯 Engineering Goals (Success Metrics)
- **Real Local-First:** UI fed 100% by `Flow`s from Room encrypted with SQLCipher.
- **Rigorous Security:** OWASP compliance via App Check (Play Integrity), Keystore for SQLCipher,
  and restrictive Firestore/Storage rules.
- **Solid Code:** Strict TDD coverage using JUnit4 in ViewModels/use cases and Robolectric in the
  UI layer.

---

## ⏳ Milestone Map Status

### 🟢 Phase 1 to 6: MVP Core and Stability (Completed)
*Project foundations, anonymous authentication, local database, and adaptive dashboard.*
- [x] **M1 & M2:** Encrypted local data layer and guest-first authentication with App Check.
- [x] **M3 & M4:** Shared plans flow, default category seeding, and Firebase rules test
  infrastructure.
- [x] **M5:** Transactional logic and UI for manual conflict resolution (LWW).
- [x] **M6:** Plans dashboard (*Home*) with net balances and adaptive List-Detail layout for large
  screens (`ListDetailPaneScaffold`).

### 🟢 Phase 7: Identity, Real Sessions, and Profile (Completed)
*Series of 3 fronts: unique handles without enumeration, real login with definitive logout, and a
profile reachable from the whole app.*
- [x] **Front 1 — `nombreUsuario` (Slices 1-10):** Transactional Firestore infrastructure for
  unique-name reservation without user enumeration. Includes closing a privilege-escalation hole
  found in the final review before merging.
- [x] **Avatars (Slices A1-A2):** Firebase Storage integration, storage rules, and profile screen
  with Coil 3.
- [x] **Front 2 — Real login + logout without anonymous re-creation:** Real email/password sign-in,
  password recovery, and a `SesionGateViewModel` in `:app` so logging out no longer silently
  re-creates an anonymous session. Includes closing a post-logout dead end and an uncaught crash in
  the anonymous bootstrap, both found in the final review.
- [x] **Front 3 — Profile accessible from every screen:** `AvatarTopBarAction`
  (`:core:designsystem`) on all 4 tabs of a plan (Home/Movimientos/Categorías/Miembros), not just
  the Plans list.
- [x] **Invite UI gating:** the "Invite" button in `MiembrosScreen` is now hidden unless the current
  user's role in the plan is admin+ (`FEAT-U701`), matching what Firestore rules already enforced
  server-side.

### 🔵 Phase 8: Next Milestones and Technical Debt (Upcoming)
*Critical improvements found in code reviews across milestones M4 to M7.*
- [ ] **Conflict State Persistence:** Evaluate migrating the in-memory `EdicionesPendientes` /
  `ConflictosPendientes` records to Room, to avoid losing state on process death.
- [ ] **Product Evolution:** Evaluate adding customizable per-category color palettes (post-M4
  request).
- [ ] **Phase 7 robustness (minor debt):** `ARCH-U801`, `ARCH-U802`, `UX-H902`, and `UX-H903` —
  gaps documented and deliberately deferred during fronts 1-3 — are all resolved. Only `UX-H901`
  (duplicate plan-name heading on Home, a product-design judgment call) remains open in the
  backlog, none blocking.
- [ ] **M8:** osv-scanner in CI + optional MFA. Not started.

### ⚪ Phase 9: Future Ideas Backlog (Under Review)
- [ ] Alternative third-party auth provider integration (OAuth / Google Sign-In) — deliberately left
  out of front 2's scope, no milestone assigned.
