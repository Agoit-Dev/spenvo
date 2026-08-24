# Skill: firebase-rules-audit

**Activate:** M3 (final Firestore rules), M7 (Storage rules), and on any change to
`firestore.rules`, `storage.rules`, or roles.

## Firestore — steps
1. Open `firestore.rules` and `firestore.indexes.json` (drafts created in M0).
2. Verify deny-by-default: every collection with `match /{doc}` and per-role rules.
3. Verify server-side roles:
   - `usuarios/{id}`: owner = `request.auth.uid == id`.
   - `planes_financieros/{id}`: member = exists `acceso_plan_financiero/{uid}_{planId}`.
   - `acceso_plan_financiero/{uid}_{planId}`: owner/editor/admin of that plan.
   - `categorias`, `gastos`, `ingresos`: scoped to the plan and the role.
4. `editedBy`/`editedAt`: set server-side with `request.auth.uid` and `request.time`
   (never trust client fields).
5. Indexes: every real query has an index; no unnecessary wildcard indexes.

## Storage — steps
6. Open `storage.rules`. Verify deny-by-default and an owner-only path match
   (`avatars/{uid}/{fileName}`: `request.auth.uid == uid`).
7. Verify the content-type allowlist (`image/*`) and size limit (5MB) are enforced
   server-side in the rule, not just client-side.
8. Confirm no broader read than intended (today: owner-only, no cross-user read).

## Shared — steps
9. Test with Emulator (`firebase emulators:exec`) + `@firebase/rules-unit-testing`:
   positive/negative cases per role, for both Firestore and Storage
   (`rules-tests/*.test.mjs`).
10. Output: collection/path × role × operation matrix + green rules tests.

## Definitions
- `updatedAt`: local write timestamp.
- `editedAt`: timestamp of the actual last editing author (server-set).
- Visible conflict: badge/notice in the row with a "keep mine / use theirs" option.

## Scope note (revisit if this grows)
Storage is folded into this skill because it's one bucket, one rule
(`avatars/{uid}/avatar.jpg`), sharing the same audit verb as Firestore: "prove
deny-by-default + role checks against the Emulator." If Storage grows past
that — multiple buckets, non-owner sharing, role hierarchies beyond
owner-only — split it back into a dedicated `storage-rules-audit` skill instead
of overloading this one.
