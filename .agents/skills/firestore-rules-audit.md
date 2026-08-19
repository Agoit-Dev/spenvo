# Skill: firestore-rules-audit

**Activate:** M3 (final rules), and on any change to `firestore.rules` or roles.

## Steps
1. Open `firestore.rules` and `firestore.indexes.json` (drafts created in M0).
2. Verify deny-by-default: every collection with `match /{doc}` and per-role rules.
3. Verify server-side roles:
   - `usuarios/{id}`: owner = `request.auth.uid == id`.
   - `planes_financieros/{id}`: member = exists `acceso_plan_financiero/{uid}_{planId}`.
   - `acceso_plan_financiero/{uid}_{planId}`: owner/editor/admin of that plan.
   - `categorias`, `gastos`, `ingresos`: scoped to the plan and the role.
4. `editedBy`/`editedAt`: set server-side with `request.auth.uid` and `request.time`
   (never trust client fields).
5. Test with Emulator (`firebase emulators:exec`) + `@firebase/rules-unit-testing`
   (M3): positive/negative cases per role.
6. Indexes: every real query has an index; no unnecessary wildcard indexes.
7. Output: collection×role×operation matrix + green rules tests.

## Definitions
- `updatedAt`: local write timestamp.
- `editedAt`: timestamp of the actual last editing author (server-set).
- Visible conflict: badge/notice in the row with a "keep mine / use theirs" option.