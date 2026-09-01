# Skill: lww-conflict-review

**Activate:** on any change to data writes/sync.

## Steps
1. Confirm the LWW model: `editedBy` + `editedAt` on every synced entity.
2. Verify optimistic write: Room updates immediately; Firestore confirms later.
3. Verify that a REAL conflict (same field edited by another user) is shown
   in the UI and the user decides — not silently overwritten.
4. Verify `editedBy` is enforced server-side against `request.auth.uid` in the
   rules (never trust the client field). `editedAt` is only validated as
   `is timestamp` — the client clock value is trusted there, deliberately: server
   timestamps resolve to `null` in the offline cache and would break optimistic
   writes plus the conflict predicate.
5. Verify listeners: only in the active scope; detach when leaving.
6. Test cases: concurrent editing, offline + remote editing, soft-delete vs edit.

## Output
- Checklist marked + findings + required actions.