# Rule: Data Consistency & Sync

## Purpose
Guarantee that offline read/write neither corrupts data nor misleads the user.

## Approved model (do not change without gating)
1. **Reads**: the UI ALWAYS observes Room (Flow). Firestore does not feed the UI directly.
2. **Writes**: to Firestore with native offline cache. **No homegrown outbox/WorkManager.**
3. **Listeners**: attach a snapshot listener when opening an active shared scope
   (screen/plan), detach when leaving. NEVER app-global listeners.
4. **Refresh**: on-demand + pull-to-refresh. No complex automatic TTL.
5. **Conflict**: LWW with `editedBy` + `editedAt`. If there is a real conflict (same
   field, recent edits by someone else), show the conflict in the UI and let the user decide.

## Invariants
- Every synced entity carries `editedBy`, `editedAt`, `deletedAt` (soft-delete).
- `updatedAt` is the local write stamp; `editedAt` is the stamp of the editing author.
- A local change is reflected in Room immediately (optimistic) and reconciled when
  confirmed against Firestore.
- Firestore's cache is the "single source of truth" of remote state; Room is the
  presentation cache. In M1+ the exact propagation between both is defined.

## Review rules
- Is the listener detached when leaving the screen? (scope + coroutine).
- Is the LWW honest? Do not hide conflicts: surface them.
- Is there a double write (Room + manual Firestore) without reconciliation? → reject.

## Write contract (optimistic Room-first, M4)
Applied by every synced-entity repository (`FirebaseCategoriaRepository`,
`FirebasePlanFinancieroRepository`, `FirebaseMovimientoRepository`):
1. **Room updates optimistically first** (`upsert`, or soft-delete via `deletedAt`),
   so the UI reflects the change immediately.
2. **Firestore `set()` + `await()`**. With the native offline cache the write is
   buffered locally when offline (the task completes against the cache, it does
   NOT block the UI flow indefinitely; no homegrown outbox).
3. **Permanent error thrown by `await()`** (e.g. `PERMISSION_DENIED`) →
   **rollback Room to the previous snapshot** and rethrow:
   - create → `dao.delete(id)`.
   - update/delete → restore the entity read via `dao.get(id)` before the write.
   This guarantees no phantom local rows.
4. **Deferred permanent rejection** (a write buffered offline and later refused by
   the server) is NOT caught by `await()`. It is reconciled by the snapshot
   listener (which reflects server truth) and, for real conflicts, by the
   honest LWW in M5.
5. Firestore snapshot listeners are the ONLY reconciliation path
   (`CategoriaSincronizador`, `PlanSincronizador`): attach when an active shared
   scope (screen) opens, detach when it leaves.