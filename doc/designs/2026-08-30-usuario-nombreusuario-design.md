# Usuario entity + nombreUsuario — Design

> First of three fronts closing the "no re-authentication after logout" gap: (1) this doc — a
> live `Usuario` entity with a public `nombreUsuario`; (2) real login screen + logout without
> auto-anonymous re-entry; (3) avatar/profile entry point reachable from every screen. Fronts 2
> and 3 get their own design docs once this one ships.

**Goal:** Turn the currently-orphaned `Usuario`/`UsuarioEntity`/`UsuarioDao` skeleton into a live,
synced entity with a unique, human-friendly `nombreUsuario`, and stop showing raw Firebase UIDs
anywhere in the UI (Miembros list, invite-by-identifier flow) — without ever letting the app
confirm or deny whether a given email/nombreUsuario belongs to a real account.

**Architecture:** Client-only, no Cloud Functions (matches the existing "no homegrown
outbox/WorkManager" stance) — uniqueness and identifier→uid lookups are done with single-document
Firestore reads/transactions, never queries/listings, so nothing here can be used to enumerate
accounts. Room stays the read model for the UI; Firestore is synced in on a per-scope basis, same
as every other entity in this app.

---

## Domain model changes

`Usuario` (`core/domain/.../model/Entities.kt`) gains `nombreUsuario: String` and `email` becomes
`String?`:

```kotlin
data class Usuario(
    val id: String,
    val nombreUsuario: String,
    val nombre: String? = null,
    val email: String? = null,
    val avatarUrl: String? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
)
```

`email` staying nullable at the type level is deliberate, not a relaxation: an anonymous session
has no email, so the type has to allow it. The invariant "a registered user always has an email"
is enforced by construction instead of by the type — the only code path that ever creates or
updates a *non-anonymous* `Usuario` is driven by `vincularEmail(email, password, nombre)`, which
requires a non-blank email as a parameter. There is no code path that can produce a non-anonymous
`Usuario` with `email = null`.

`nombre` also becomes nullable (an anonymous `Usuario` has no display name either) — this matches
`Sesion.nombre`'s existing nullability, just propagated to the newly-live entity.

## Persistence

Room migration 2→3 on `UsuarioEntity`: add `nombreUsuario TEXT NOT NULL`, make `nombre`/`email`
nullable. `UsuarioDao` already has everything needed (`upsert`, `upsertAll`, `observe`, `get`) —
no new methods.

## Firestore shape

Three top-level collections, all single-document-keyed lookups — no collection ever needs a
`whereEqualTo` query on a sensitive field, so nothing here supports probing "does X exist":

- **`usuarios/{uid}`** — the user doc itself. `get` on a known uid: any authenticated user (plan
  co-members need to resolve each other's `nombreUsuario`/avatar). `list: if false` — no query
  against this collection is ever allowed, for the same reason as the two index collections below:
  an open `list` would let anyone dump every user's `email`/`nombre` via `orderBy`/`limit`,
  defeating the point of routing email lookups through `emails_usuario` in the first place. Write:
  only the doc's own uid — *and*, for any write that sets or changes `nombreUsuario`, only if the
  matching `nombres_usuario/{normalizado}` reservation already exists and already points at that
  same uid. "It's my own document" is not sufficient on its own here, because `nombreUsuario` is
  rendered publicly (`MiembroCard`): without the reservation check, anyone could bypass
  `RenombrarUsuarioUseCase` and write another member's handle straight through the SDK,
  impersonating them in the Miembros list.
- **`nombres_usuario/{nombreUsuarioNormalizado}`** — `{ usuarioId }`. Doc ID is the normalized
  (lowercased, trimmed) `nombreUsuario`; existence of the doc *is* the uniqueness guarantee.
  Create: only if the request's `usuarioId` matches `request.auth.uid` and the doc doesn't already
  exist (enforced via a client-side transaction: read-then-create, abort if present). Delete: only
  by the uid the doc points to (a rename is delete-old + create-new in one transaction). No update.
- **`emails_usuario/{emailNormalizado}`** — `{ usuarioId }`. Same shape as above, but no
  collision-retry needed: Firebase Auth already guarantees email uniqueness at the identity level,
  so this is a pure lookup index, written once inside the same flow that calls `vincularEmail`.

Firestore rules for both index collections split `get` from `list` explicitly — this is the part
that actually keeps them non-enumerable, not just "the app never queries them":
`allow get: if request.auth != null; allow list: if false;`. `get` only ever succeeds when the
caller already supplies the exact normalized ID (email or nombreUsuario) they're resolving, which
means they already knew it going in — the doc's existence just confirms a match, same as it would
for a nombreUsuario. `list: if false` blocks any query/enumeration of the collection outright, for
every client, including one that bypasses the app entirely and talks to the Firestore SDK
directly — not just "our app's code never issues that query." `rules-tests/` gets a new suite
mirroring the existing `storage-rules.test.mjs` pattern: `get` on a known doc ID succeeds for any
authenticated user, `list`/collection queries are denied outright, owner can create/delete their
own reservation, cannot create one pointing to another uid, cannot overwrite an existing
reservation, non-owner create/delete is denied.

## nombreUsuario generation

Format: `Adjetivo` + `Sustantivo` + number, e.g. `GatoAzul42`. Two short curated Spanish word
lists live as `private val` constants next to the generator (no new dependency) — no accents, no
spaces, capitalized, so the concatenation reads cleanly and normalizes predictably
(`gatoazul42` as the Firestore doc ID).

Generation happens once, at `Usuario` creation time:
- **First-ever anonymous sign-in** (`iniciarSesionAnonima()` succeeding for a brand-new uid): a
  new `AsegurarUsuarioUseCase` generates a candidate, attempts the `nombres_usuario` reservation
  transaction, retries with a new random number on collision (bounded attempts, widening the
  number range on each retry), then writes the `usuarios/{uid}` doc with `nombre = null`,
  `email = null`.
- **`vincularEmail()` succeeding** (anonymous → registered upgrade, same uid): the `Usuario` doc
  already exists from the step above — `AsegurarUsuarioUseCase` (or the equivalent update path)
  only updates `nombre`/`email` and writes the new `emails_usuario/{emailNormalizado}` index doc.
  `nombreUsuario` is carried over unchanged.
- A brand-new sign-in via the future login screen (front 2) reuses the existing `Usuario` doc for
  that uid — out of scope here, just needs to already work once front 2 lands.

## Editing nombreUsuario

Editable, but only from `PerfilContenido` (`CuentaScreen.kt`), which today only renders for a
non-anonymous session — consistent with front 3's decision that an anonymous session has no
profile screen of its own, only the account-creation flow. In practice: auto-generated always,
editable once registered. Surfaced as a text field + save action next to the existing
avatar/name/email card, with an inline error if the chosen name is taken (this one *is* safe to
say plainly — collision on your own edit attempt reveals nothing about anyone else's account, it
is the direct, expected result of your own input).

Editing runs as **two sequential, separately-committed transactions**, not one:

1. **A** — reserve `nombres_usuario/{normalizadoNuevo}`, aborting if it already exists (this is
   the "name is taken" answer).
2. **B**, only if A committed — update `usuarios/{uid}.nombreUsuario` to the new display value
   and delete the old `nombres_usuario/{normalizadoAnterior}` reservation.

The split is forced by the reservation check on `usuarios` above, and was verified empirically
against the Firebase Emulator (see the paired `renombrar en UNA/DOS transaccion(es)` cases in
`rules-tests/rules.test.mjs`): a `get()` evaluated inside a security rule reads the
*pre-transaction* snapshot, so a reservation written earlier in the **same** transaction is not
yet visible when the `usuarios` write is validated — the one-transaction version is denied
outright. By the time B commits, A is durably committed and visible, so the rule passes.

Tradeoff, accepted rather than engineered around: a rename is no longer atomic. If A commits and
B fails, the user holds *both* reservations and keeps their old display name — nothing is lost
and no handle is handed to anyone else; the recovery today is simply to try the rename again. A
retry/repair mechanism for that window is deliberately not built (same "known gap, deliberately
deferred" stance as the sections below).

The display value written to `usuarios/{uid}.nombreUsuario` is always **trimmed** (casing
preserved: `GatoAzul42`, never `gatoazul42`). That is load-bearing, not cosmetic: the rule
re-derives the reservation's doc ID as `request.resource.data.nombreUsuario.lower()`, and the
Firestore rules language has no `.trim()`, so an untrimmed display value would fail to resolve to
its own reservation and a perfectly legitimate rename would be denied.

## Miembros: show nombreUsuario instead of UID

`MiembrosScreen` gains a sync step: on open, for the plan's `AccesoPlan.usuarioId` list, one
`get(usuarios/{uid})` per member, run concurrently (this app's plans are small family/team
groups, so N parallel single-doc gets is cheap — and it's what the `list: if false` rule above
requires, not just a style choice), attached/detached with the screen's active lifecycle same as
categoria/movimiento sync, upserted into Room. `MiembroCard` reads the resolved `Usuario` (join
by `usuarioId` done in the ViewModel) and shows `nombreUsuario` instead of `acceso.usuarioId`. If
resolution hasn't completed yet (cold cache), fall back to a loading placeholder rather than the
raw UID.

## Invite by nombreUsuario or email — never confirms non-existence

`InvitarDialog`'s single text field accepts either: if the trimmed input contains `@`, resolve
via `emails_usuario/{emailNormalizado}`; otherwise via `nombres_usuario/{nombreUsuarioNormalizado}`.
Both are single-document `get`s.

The dialog's outcome is **always** identical, regardless of whether resolution found a real
account: `InvitarDialog` simply closes, with no confirmation message and no error. (That is what
`MiembrosScreen` actually does — an earlier draft of this doc promised a generic "Invitación
enviada" snackbar; the anti-enumeration guarantee is the same either way, since what matters is
that the outcome does not branch on whether the identifier resolved, not that a message is shown.
Adding a confirmation message is a fine follow-up, but it is not what ships today.) What happens
behind that identical outcome differs:

- **Resolved to a real `usuarioId`:** creates the `AccesoPlan` exactly as today.
- **Not resolved, input looked like an email:** writes a pending-invite doc to
  `invitaciones_pendientes_por_email/{emailNormalizado}_{planId}` (`{ email, planId, rol,
  invitadoPor, createdAt }`). When that email later completes `vincularEmail()`
  (`AsegurarUsuarioUseCase`'s update path, see above), it queries this collection for the
  newly-registered email, converts every match into a real `AccesoPlan` with the now-known uid,
  and deletes the pending docs. This is the intended path for "invite a family member who hasn't
  installed the app yet."
- **Not resolved, input looked like a nombreUsuario:** discarded, no pending record. Unlike an
  email, a nombreUsuario isn't something anyone knows before they've opened the app at all, so a
  non-match here is overwhelmingly a typo, not a "future user" — there's no real case to hold a
  pending invite for.

An anonymous Firebase Analytics event (`invitacion_no_resuelta`, no parameters — no email, no
nombreUsuario, nothing identifying) fires whenever resolution fails, giving the developer
aggregate visibility into how often this happens without the app ever logging what was searched
(per `AGENTS.md`'s "never log emails" rule). This needs `firebase-analytics-ktx` added to
`gradle/libs.versions.toml` (new dependency, approved) and wired into `:app`.

Doc ID is `{emailNormalizado}_{planId}` (composite, since one email can be pending-invited to
multiple plans), so resolving "my own pending invites" after registering needs a scoped `list`,
not a `get` — the newly-registered user doesn't know which `planId`s invited them in advance.
Firestore rules restrict that `list` to queries whose equality filter on `email` matches the
caller's own verified `request.auth.token.email` (a standard Firestore rules pattern for
"list only your own records"), so this collection is queryable, but only by the exact person the
invite is for, and only by their own already-verified email — not an open query surface.
`create`: only an **admin(2)+ of the target plan**, with `invitadoPor` pinned to the caller's uid
and the doc ID pinned to its own `email`/`planId` fields. An earlier draft allowed any
authenticated user here, on the reasoning that "inviting someone doesn't require plan access
beyond what `InvitarMiembroUseCase` already enforces app-side" — that was wrong, and it was a
privilege-escalation hole rather than a lax permission: combined with the
`acceso_plan_financiero.create` self-grant disjunct below, anyone could write
`invitaciones_pendientes_por_email/{ownEmail}_{anyPlanId}` with `rol: 'owner'` of their own
choosing and then self-grant owner access to a plan they had nothing to do with (plan IDs are
enumerable, since `acceso_plan_financiero` allows an open `read`). The rule, not the app, is the
only real barrier here — `MiembrosScreen` today offers "Invitar" to *any* plan member without
checking their role. `delete`: only by the matching-email caller (cleanup after resolving), whose
token email is lowercased before comparison so a mixed-case token still resolves the
lowercase-stored doc. No `update`.

## Known gap, deliberately deferred (found during Task 7's review) — resolved, `ARCH-U802`

`AsegurarUsuarioUseCase.paraVincularEmail`'s pending-invite resolution loop
(`pendientesRepository.obtenerPorEmail(...).forEach { invitarMiembro(...); eliminar(...) }`) ran
sequentially and uncaught. If granting invite N of several threw (a genuine Firestore error),
invites before N were already correctly granted-and-removed, but invite N and everything after it
were never attempted. The original note that "`paraVincularEmail` only ever runs once, at the
anonymous-to-registered transition" is now stale — `ARCH-U801` gave `CuentaViewModel` a
`reintentarSyncUsuario()` retry path that re-runs the whole method — but that alone didn't fix
invites N+1 onward never being *attempted* in the first place, only that a retry could eventually
reach them.

Resolved as `ARCH-U802`: each invite is now granted independently (its own `runCatching`), so one
Firestore failure no longer blocks the rest of the batch from being attempted in the same pass.
`paraVincularEmail` still reports overall failure when any invite failed, so `ARCH-U801`'s retry
path is what eventually resolves a stuck one — safe to re-run in full since `invitarMiembro`
(`.set()` on `accesoDocId(usuarioId, planId)`) and `eliminar` (`.delete()` on
`docId(emailNormalizado, planId)`) are both keyed by deterministic Firestore document ids.
A user who never revisits the retry snackbar (dismisses it, or the app is killed before retrying)
still has no automatic resolution and no sender-side visibility — that part of the original gap
(no retry-with-backoff, no stuck-invite UI) remains genuinely out of scope, same reasoning as
before: there's still no sender-side pending-invite UI to show a stuck invite even if detected.

## Known gap, deliberately deferred (found during Task 4's review) — resolved, `ARCH-U801`

`CuentaViewModel.registrar()` ran `vincularCredencial(...)` (which permanently upgrades the
Firebase Auth account from anonymous to email+password) and `asegurarUsuario.paraVincularEmail(...)`
inside the same `runCatching` block. If the Auth linking succeeded but the Firestore `Usuario`
sync then failed, the UI reported registration as failed even though the account was actually
created — and a retry would hit Firebase Auth's "credential already linked" error instead of
succeeding. Resolved as `ARCH-U801`: the two steps are now separate, a sync failure sets a
distinct `RegistroEstado.syncPendiente` (not the generic error path), and `reintentarSyncUsuario()`
retries only the sync — never `vincularCredencial` again. The product decision made was the
distinct-partial-failure-state option, not the `PlanesViewModel`-style best-effort one: unlike that
bootstrap, `registrar()` has no recurring session-re-emission to self-heal on, so a silently
swallowed sync failure here would have left the `Usuario` doc missing indefinitely.

## Out of scope (explicitly)

- Front 2 (real login screen, logout without auto-anonymous re-entry) and front 3 (avatar/profile
  entry point on every screen) — separate design docs, separate implementation cycles.
- Removing/renaming the `account_registration_*` flow's own display-name field (`Sesion.nombre` /
  `RegistroForm`'s `nombre` input) — that's the person's real display name, unrelated to
  `nombreUsuario`.
- Rate-limiting invite attempts — the anti-enumeration design here relies on the response being
  identical regardless of match, not on throttling; if abuse becomes a real concern later
  (someone scripting thousands of invite attempts against arbitrary emails), that's a follow-up,
  not blocking this design.
- Any UI for a plan owner to see/cancel pending email invitations before they resolve — the
  existing "invitaciones pendientes" surface on `PlanesScreen` is for the *invitee's* view of
  their own pending accesses today; a *sender*-side pending-invite management UI is a reasonable
  follow-up but not required for this design to be complete and safe.

## Testing

Per `AGENTS.md`'s strict TDD:
- Generator: format, normalization, and bounded-retry-on-collision behavior against a fake
  reservation repository.
- `UsuarioDao` Room test (migration 2→3 + basic CRUD) alongside the existing DAO test pattern.
- `AsegurarUsuarioUseCase`: creates on first anonymous sign-in, updates (not re-creates) on
  `vincularEmail`, carries `nombreUsuario` across the transition.
- `rules-tests/` suite for `usuarios`, `nombres_usuario`, `emails_usuario`, and
  `invitaciones_pendientes_por_email` against the Firebase Emulator: owner/non-owner
  create/get/delete matrix per collection, plus explicitly asserting `list`/collection-query
  requests are denied on `usuarios`/`nombres_usuario`/`emails_usuario` and that
  `invitaciones_pendientes_por_email` only allows a `list` whose `email` filter matches the
  caller's own verified email.
- `MiembrosScreen`/`MiembrosViewModel`: shows `nombreUsuario` instead of `usuarioId`, loading
  placeholder before resolution completes.
- Invite flow: resolves by email, resolves by nombreUsuario, creates pending doc on email miss,
  discards silently on nombreUsuario miss, always reaches the same dialog-closes outcome
  regardless of resolution (a test that would fail if the UI ever branched on resolution success).
- Pending-invite resolution: registering with an email that has 1+ pending invites converts them
  into real `AccesoPlan`s and removes the pending docs.
