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
  only the doc's own uid.
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
editable once registered. Editing goes through the same reservation-transaction path (delete old
`nombres_usuario` doc, create new one, update `usuarios/{uid}`), surfaced as a text field + save
action next to the existing avatar/name/email card, with an inline error if the chosen name is
taken (this one *is* safe to say plainly — collision on your own edit attempt reveals nothing
about anyone else's account, it is the direct, expected result of your own input).

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

The dialog's outcome is **always** the same generic confirmation ("Invitación enviada"),
regardless of whether resolution found a real account. What happens behind that message differs:

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
invite is for, and only by their own already-verified email — not an open query surface. `create`:
any authenticated user (anyone can invite). `delete`: only by the matching-email caller (cleanup
after resolving). No `update`.

## Known gap, deliberately deferred (found during Task 7's review)

`AsegurarUsuarioUseCase.paraVincularEmail`'s pending-invite resolution loop
(`pendientesRepository.obtenerPorEmail(...).forEach { invitarMiembro(...); eliminar(...) }`) runs
sequentially and uncaught. If granting invite N of several throws (a genuine Firestore error),
invites before N are already correctly granted-and-removed, but invite N and everything after it
are never attempted — and since `paraVincularEmail` only ever runs once, at the
anonymous-to-registered transition, those remaining invites are never retried. They stay
permanently pending with no automatic resolution and no signal to either the inviter or invitee.
Not fixed here: correctly recovering (retry-with-backoff, or at minimum surfacing the stuck
invites somewhere either party can see and re-trigger) is more scope than this bug fix warrants on
its own, and there's currently no sender-side pending-invite UI at all (see "Out of scope" above)
to even show a stuck invite if we detected one. Revisit alongside building that UI, if it's ever
built — until then, worth noting that this bug only bites plans that invite 2+ not-yet-registered
emails where the second-or-later grant hits a real Firestore error, which should be rare in
practice (not from this bug when Firestore itself is healthy — see the separate, higher-priority
`acceso_plan_financiero.create` rule gap in the plan's Task 9, without which EVERY pending-invite
resolution fails, not just a rare partial one).

## Known gap, deliberately deferred (found during Task 4's review)

`CuentaViewModel.registrar()` runs `vincularCredencial(...)` (which permanently upgrades the
Firebase Auth account from anonymous to email+password) and `asegurarUsuario.paraVincularEmail(...)`
inside the same `runCatching` block. If the Auth linking succeeds but the Firestore `Usuario`
sync then fails, the UI reports registration as failed even though the account was actually
created — and a retry would hit Firebase Auth's "credential already linked" error instead of
succeeding. Not fixed here: the right fix (treat the Firestore sync as best-effort like
`PlanesViewModel`'s equivalent call, or give `registrar()` a distinct partial-failure state) is a
product decision about error surfacing, not a mechanical one, and out of this slice's scope.
Revisit before front 2 (real login) ships, since a confusing stuck-registration state is exactly
the kind of thing that pushes someone toward re-registering instead of logging in.

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
  discards silently on nombreUsuario miss, always shows the same generic confirmation regardless
  of outcome (a test that would fail if the UI ever branched its message on resolution success).
- Pending-invite resolution: registering with an email that has 1+ pending invites converts them
  into real `AccesoPlan`s and removes the pending docs.
