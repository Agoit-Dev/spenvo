# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Fixed

- `PlanScaffold`'s bottom-nav tab switching disposed the non-selected tab's whole composable subtree
  on every switch, which unregistered its `rememberSaveable` state — e.g. Movimientos' search text,
  selected type filter, and list scroll position were all lost when navigating away and back. Fixed
  by wrapping each tab's content in a `rememberSaveableStateHolder()` keyed per tab (the same
  primitive Navigation 3's own `rememberSaveableStateHolderNavEntryDecorator` uses), which snapshots
  and restores `rememberSaveable` state across the tab's dispose/recompose cycle. Also removed the
  now-unnecessary `@Suppress("LongParameterList")` on `PlanScaffold` (5 params, under detekt's
  default threshold of 6).
- Home screen: a failed save (e.g. `PERMISSION_DENIED`) had no error feedback at all and left the
  shared `MovimientosViewModel`'s error flag unconsumed, so it would leak into the Movimientos tab
  as a stale snackbar later. Home now wraps its content in a `Scaffold`+`SnackbarHost` and mirrors
  `MovimientosScreen`'s `EfectosMovimientos` shape: it attaches `sincronizar(planId)` on open and
  shows/consumes the error the same way. Also replaced the hand-rolled `formatearMontoPlano` (raw
  string concatenation, appended the ISO currency code) with a `NumberFormat.getCurrencyInstance`
  based formatter per AGENTS.md's i18n rule, added `testTag`s so the income/expense/balance figures
  are individually addressable, and gave the quick-action circular buttons an explicit
  `secondaryContainer` color — they previously had no container color and were invisible against
  the screen background.
- Center the adaptive launcher icon artwork in both the standard and round
  launcher masks.
- Movimientos edit modal: the type (Gasto/Ingreso) could be changed on an existing movimiento via
  the type chips, even though `Gasto`/`Ingreso` are separate domain types with their own storage —
  the chips are now locked (shown, disabled, still showing the real type as selected) whenever
  editing an existing movimiento. The category selector also no longer resets to the plan's first
  category on open: the reset guard was firing while the categories `StateFlow` was still at its
  transient empty initial value, before the real list loaded, silently losing the movimiento's
  actual category if the user saved without touching the selector. Switching type while creating a
  new movimiento now correctly clears the previous type's category selection too, closing a related
  path where a stale category could otherwise be saved under the wrong type.
- Movimientos edit modal in the expanded (tablet) list-detail layout: switching from editing one
  movimiento to viewing another used to carry over the previous movimiento's edit mode and
  unsaved field values, since the form's local state wasn't tied to which movimiento it was
  showing. The form is now keyed per movimiento id, so switching always starts fresh. Also fixed
  Cancelar silently restoring a movimiento's stored category even when that category had since
  been deleted — it was blindly reverting to the original id instead of re-checking whether that
  id still exists in the loaded list.
- Planes screen briefly showed "no plans yet" on cold start before the real plan list (or a
  pending invitation) loaded in, because `planes`/`invitacionesPendientes` started at a synthetic
  empty-list `StateFlow` value indistinguishable from "genuinely has none". Both flows now
  distinguish "not loaded yet" from "loaded and empty" internally (`PlanesViewModel.cargandoLista`),
  and the list area shows a centered, accessibly-labeled spinner while loading — covering both the
  anonymous-session-establishment window and the initial Room query — instead of flashing the
  empty state. The top bar and "create plan" FAB are unaffected.
- The `usuarios` collection was never actually appearing in Firestore: the security rules built
  across the Usuario/nombreUsuario slices had only ever been validated against the local emulator,
  never deployed to the live project — deployed now. Separately, `PlanesViewModel`'s anonymous-uid
  bootstrap (`asegurarUsuario.paraSesionAnonima`) swallowed any failure with a bare `runCatching { }`
  and no `.onFailure`, so a denied write would have been invisible either way; it now logs the
  exception (`Log.e`, tag `PlanesViewModel`) without surfacing it in the UI, since the bootstrap
  stays best-effort by design.
- `MiembrosScreen`'s "Invite" button was reachable by any plan member, viewers included, even
  though `firestore.rules` already rejected the write server-side for anyone below admin — the UI
  didn't reflect that. New `Rol.esAlMenos(minimo)` domain extension (`:core:domain`, mirrors
  `firestore.rules`' `roleLevel`/`tieneRolMinimo` ordering) backs `MiembrosViewModel.puedeInvitar`,
  which derives the current session's own role from the same member list already fetched for
  display — the button is now hidden outright for anyone below admin, not just disabled.
- `MainActivity.kt`'s `onCrearCuenta`/`onAbrirCuenta`/`onAbrirPlan` pushed straight onto the
  Navigation 3 backstack with no double-tap guard, so a fast double tap on an avatar or a plan card
  could push the same route twice. New `MutableList<NavKey>.pushUnlessTop(destino)` no-ops instead
  of pushing when the destination is already on top.
- `PlanesScreen`'s account menu showed a generic person icon while the 4 plan tabs already showed
  the real avatar photo since front 3. `CuentaMenu` now reuses the same `AvatarTopBarAction`,
  sourced from `sesion.photoUrl` (already collected for the menu's own state text) — no new data
  plumbing.
- `CuentaViewModel.registrar()` wrapped Auth credential linking and the Firestore `Usuario` sync in
  the same `runCatching`: if linking succeeded but the sync then failed, the UI reported the whole
  registration as failed even though the account already existed, and a retry hit Firebase's
  "credential already linked" instead of succeeding. The two steps are now separate; a sync failure
  sets a distinct `RegistroEstado.syncPendiente`, surfaced by `CuentaScreen` as an indefinite
  snackbar with a "Reintentar" action that calls the new `reintentarSyncUsuario()` — which retries
  only the sync, never the credential link again.
- `AsegurarUsuarioUseCase.paraVincularEmail`'s pending-invite resolution loop granted invites with a
  plain `forEach`: if granting invite N of several threw, invites after N were never attempted at
  all, not just N itself. Each invite is now resolved independently, so one Firestore failure no
  longer blocks the rest of the batch. The method still reports overall failure when any invite
  failed, so the caller's retry (`CuentaViewModel.reintentarSyncUsuario`, see above) gets another
  chance — safe to re-run in full since `invitarMiembro`/`eliminar` are both keyed by deterministic
  document ids.

### Changed

- Dependency bump: `composeBom` 2026.02.01 → 2026.08.00 (Compose 1.12), `nav3`
  (`navigation3-runtime`/`navigation3-ui`) 1.1.4 → 1.1.7, `robolectric` 4.15.1 → 4.16.1.
  `kotlin`/`ksp`/`coil` intentionally held back — they're a tightly-coupled trio (Coil 3.5.0+
  requires Kotlin 2.2+, 3.6.0+ requires Kotlin 2.4.10) and warrant their own dedicated upgrade and
  test pass, not a drive-by bump.
  `./gradlew dependencies --write-locks` re-run per module (the root-level invocation only covers
  the root project's own, dependency-free configurations).
- Dependency bump, second half: `kotlin` 2.2.10 → 2.4.10, `ksp` 2.2.10-2.0.2 → 2.3.10 (matched pair,
  verified against KSP's own release notes rather than assumed from the version string — KSP's
  numbering is no longer reliably tied to the exact Kotlin patch), `coil` 3.4.0 → 3.6.1 (previously
  held back specifically because it needed a newer Kotlin than was pinned; no longer the case).
  Verified with a full Hilt (`:app`) and Room (`:core:data`) KSP codegen smoke pass before running
  the full gate suite, since this trio touches annotation processing in every module.
- Dependency bump: `agp` 9.3.1 → 9.3.2 (a Lint/JDK 17 crash fix, no breaking changes), `firebaseBom`
  34.17.0 → 34.18.0 (larger Firestore document/message-size limits over gRPC; no breaking changes
  for the Auth/Firestore/Storage/App Check products this project uses — Firebase AI Logic's
  breaking change in this release doesn't apply, the project doesn't depend on it). Both were
  previously reported as already-latest; that check was stale.
- Movimientos edit modal now opens read-only for an existing movimiento, with an explicit "Editar"
  action to enable fields; Cancelar reverts unsaved changes and returns to the read-only view
  instead of dismissing the sheet; Eliminar is now only reachable after Editar. The category
  selector now visibly dims while read-only, matching the amount/description fields and type
  chips.
- **ARCH-M501:** `EdicionesPendientes`/`ConflictosPendientes` are no longer in-memory-only — Room is
  now the single source of truth for both the pending-edit and the conflict-detection registries,
  closing the process-death data-loss gap `doc/architecture.md` had documented as accepted debt (a
  process death between the optimistic Room write and the Firestore echo used to silently drop the
  pending marker, letting the remote version win with no conflict ever surfaced). Two new tables
  (`ediciones_pendientes`, `conflictos_pendientes`; `SpenvoDatabase` v3→v4 via a real
  `MIGRATION_3_4`) back new `:core:domain` interfaces `RegistroEdicionesPendientes`/
  `RegistroConflictosPendientes`, implemented by `RegistroEdicionesPendientesRoom`/
  `RegistroConflictosPendientesRoom` in `:core:data`. `VersionPendiente` is retired: its one real
  read site is superseded by reconstructing the local snapshot from the main table at
  conflict-detection time, since the write-path transaction now guarantees the marker and the
  main-table row are always in sync. Four transaction boundaries (write, rollback, snapshot
  received, conflict resolution) wrap `SpenvoDatabase.withTransaction { }` around what used to be
  two or more unrelated calls with no atomicity between them; `CategoriaSincronizador`/
  `MovimientoSincronizador`/`PlanSincronizador` each gain an internal FIFO `Channel` per collection
  so overlapping Firestore snapshot callbacks process strictly in delivery order instead of racing.
  Conflict resolution (movimientos only) gets 4 new dedicated `MovimientoRepository` operations
  (`resolverConflicto{Gasto,Ingreso}Usando{Local,Remoto}`) and matching use cases, replacing the
  previous two-call sequences that could leave a conflict half-resolved on a crash between them;
  `MovimientosViewModel.claveVisible()` replaces the ambiguous by-`registroId`-only conflict lookup
  (a Gasto and an Ingreso could share an id). `MovimientoModule.kt` (Hilt) now wires all 4 new use
  cases — a real gap the previous commit had left unwired, only caught during this slice's
  repository-wide gate pass. Also fixed along the way: `core:data` was missing the
  `kotlinx-serialization` plugin/dependency needed by the new JSON-column `Converters` entry, a
  flaky shared-`planId` race between sibling tests in `CategoriaSyncEmulatorTest`, and 13
  hand-written `MovimientoRepository` test fakes across `:core:domain`, `:core:data` (androidTest),
  `:feature:movimientos`, and `:feature:planes` that hadn't been updated for the interface's 4 new
  abstract methods.

### Added

- Login real + logout sin recreación anónima automática (front 2/3): real email/password
  sign-in and password recovery replace the placeholder registration-only flow. New
  `AuthRepository.iniciarSesionConEmail`/`enviarRecuperacionPassword`, backed by
  `IniciarSesionConEmailUseCase`/`EnviarRecuperacionPasswordUseCase` (`:core:domain`) and
  `FirebaseAuthRepository`; `CuentaViewModel.iniciarSesion`/`recuperarPassword` map every
  failure — wrong password, unknown email, malformed email — to the same generic
  `errorRes` (`mapearErrorAuth`), so the UI never confirms or denies whether a given
  email is registered, the same anti-enumeration property the Usuario/nombreUsuario work
  established for Miembros invites. `recuperarPassword` reports success unconditionally,
  regardless of whether `enviarRecuperacionPassword` actually found the account.
  `recuperarPassword` also keeps reporting success when the send itself fails, and disables
  the dialog's submit button while a send is in flight so repeated taps can't fire several
  `sendPasswordResetEmail` calls. Previously, logging out via `AuthRepository.cerrarSesion()`
  always silently re-created a new anonymous session, so a signed-out user could never see a
  real sign-in screen — the gate closes that: a new `SesionPreferences` (`:core:data`,
  DataStore Preferences, `sesion_cerrada_explicitamente` key) persists an explicit-logout flag
  across process restarts, and root-level `SesionGateViewModel` (new `EstadoGate` — `Cargando`/
  `MostrarApp`/`MostrarGate`) combines that flag with the live session to decide whether
  to show the app or block on `CuentaScreen` until the user explicitly signs in or chooses
  to continue as guest, only then re-establishing anonymous auth. Reaching `CuentaScreen`
  through the gate opens it on "Iniciar sesión" and adds a "Continuar como invitado" action
  (`SesionGateViewModel.continuarComoInvitado`, which clears the logout flag and establishes a
  fresh anonymous session); reaching it from `PlanesScreen`'s account menu keeps opening on
  "Crear cuenta" with no guest action, since that entry point can be showing an already-linked
  account. Anonymous bootstrap is single-flight and retried a bounded number of times on
  failure — a fresh install can legitimately fail it (no network, or an App Check token
  exchange that hasn't settled), and it now degrades instead of crashing. While the gate is
  still resolving the session the app renders a blank surface rather than the backstack's
  initial `PlanesRoute`, so a logged-out cold start no longer flashes `PlanesScreen`. This
  replaces `PlanesViewModel`'s previous auto-anonymous-login retry loop, which used to fire on
  every collector restart regardless of an explicit logout. `CuentaScreen` gains a "Crear
  cuenta"/"Iniciar sesión" toggle (`AuthTab`, which clears the shared form state on switch so a
  failed sign-in's error doesn't leak into the other tab) and a password-recovery dialog; all
  new strings ship in both `values/` and `values-en/` per AGENTS.md's i18n rule.
- Usuario entity + nombreUsuario, slice 1/10 (foundation only, no user-visible behavior yet): the
  `Usuario` domain model and Room entity gain a `nombreUsuario` field (the unique public handle
  that will replace raw UID display in Miembros), and `nombre`/`email` become nullable to
  correctly represent an anonymous session, which has neither. Room migration 2→3 backfills
  existing rows. Nothing writes or reads `nombreUsuario` yet — that lands in the following slices.
- Usuario entity + nombreUsuario, slice 2/10 (foundation only, still nothing wired to real
  Firestore): adds `GenerarNombreUsuarioUnicoUseCase`, which produces a random
  adjective+noun+number candidate (e.g. `RapidoZorro42`) and retries against a bounded budget of
  8 attempts (widening the numeric range after the first 5 collisions) until the repository
  confirms a transactional reservation, failing loudly if none succeeds. Also adds the
  `UsuarioRepository` domain interface (`obtener`, `obtenerVarios`, `intentarReservarNombreUsuario`,
  `crear`, `actualizar`, `renombrar`, `registrarIndiceEmail`, `resolverPorNombreUsuario`,
  `resolverPorEmail`) with no implementation yet, and `normalizarNombreUsuario`/`normalizarEmail`
  helpers for case/whitespace-insensitive lookups.
- Usuario entity + nombreUsuario, slice 3/10 (still no caller wired in): implements
  `UsuarioRepository` against real Firestore (`FirebaseUsuarioRepository`, `usuarios`/
  `nombres_usuario`/`emails_usuario` collections) plus the `UsuarioDto` mapper (`uid` field name,
  matching the existing `usuarios/{usuarioId}` security rule's `request.resource.data.uid ==
  usuarioId` check). `intentarReservarNombreUsuario` and `renombrar` use a Firestore transaction to
  keep the `nombres_usuario` reservation index and the user document consistent. Wired via
  `UsuarioModule`/`UsuarioUseCaseModule` (Hilt). No unit test for the repository itself — same
  pattern as `FirebasePlanFinancieroRepository`/`FirebaseAccesoPlanRepository`, which talk to real
  Firestore and are only covered by DTO tests plus the emulator-based `rules-tests`.
- Usuario entity + nombreUsuario, slice 4/10 (still no UI showing it, but the first slice where
  `nombreUsuario` actually gets created/persisted through real app flows): adds
  `AsegurarUsuarioUseCase`, wired into both `PlanesViewModel`'s anonymous-session bootstrap and
  `CuentaViewModel.registrar()`. `paraSesionAnonima` is a best-effort call made once the anonymous
  session resolves a real uid — creates the `Usuario` doc with a freshly generated `nombreUsuario`
  if one doesn't exist yet, no-ops otherwise. `paraVincularEmail` runs right after linking an
  email/password credential — updates `nombre`/`email` on the existing doc (preserving its
  `nombreUsuario`) and registers the `emails_usuario` index entry; it also has a defensive fallback
  that creates the doc if the anonymous bootstrap was somehow skipped.
- Usuario entity + nombreUsuario, slice 5/10 (the first slice with real, user-visible UI): a
  registered user can now see and edit their `nombreUsuario` from the account profile screen.
  Adds `RenombrarUsuarioUseCase`, which passes the raw display-form handles straight through to
  `UsuarioRepository.renombrar`'s transactional release-and-reserve; `FirebaseUsuarioRepository`
  normalizes locally only for the `nombres_usuario` index doc IDs, keeping the
  `usuarios/{uid}.nombreUsuario` display field in its original casing (e.g. `GatoAzul42`, never
  `gatoazul42`), per the design doc. Wired via `UsuarioUseCaseModule`. `CuentaViewModel` now
  injects `UsuarioRepository` directly (consistent with `AuthRepository` already being a direct
  constructor param there) and loads the signed-in user's `nombreUsuario` once the session
  resolves to a real, non-anonymous uid, resetting it when the session goes back to anonymous
  (e.g. after logout); a failed load no longer kills the collector, so a later session emission
  gets another chance. `PerfilEstado` gains `nombreUsuario`/`nombreUsuarioError`, and
  `editarNombreUsuario(nuevo)` rejects blank/whitespace-only input without calling the use case,
  catches any use-case failure instead of crashing, and otherwise calls the use case and surfaces
  an "already taken" error without touching the previous value on failure. `CuentaScreen`'s
  profile view gets a new username field + Guardar button (same `OutlinedTextField` +
  inline-error pattern as `RegistroForm`, Guardar also disabled while the trimmed input is blank),
  extracted into its own `CampoNombreUsuario` composable to keep `PerfilContenido` under detekt's
  `LongMethod` threshold. The profile `Column` is now vertically scrollable to accommodate the
  extra field on small screens.
- Usuario entity + nombreUsuario, slice 6/10: Miembros now shows each member's `nombreUsuario`
  instead of the raw UID. `MiembrosViewModel` gains `miembrosResueltos(planId)`, which combines
  `AccesoPlanRepository.observarAccesosDelPlan` with a batched `UsuarioRepository.obtenerVarios`
  lookup into the new `MiembroResuelto(acceso, usuario)` domain model (`:core:domain`); the lookup
  is wrapped in `runCatching` so a Firestore failure degrades to an unresolved (`usuario = null`)
  member instead of crashing the collector — `obtenerVarios` already degrades per-item internally,
  this only guards the call itself. `MiembrosScreen` switches from `observarMiembros` to
  `miembrosResueltos`, and `MiembroCard` shows `usuario?.nombreUsuario`, falling back to the new
  `members_cargando` string ("Cargando…"/"Loading…") for a member whose `Usuario` doc hasn't
  resolved yet, rather than ever showing the raw UID.
- Usuario entity + nombreUsuario, slice 7/10 (anti-enumeration invite): Miembros' invite dialog now
  accepts a `nombreUsuario` or an email instead of a raw UID (`members_invite_identificador`,
  replacing `members_invite_uid`). `InvitarMiembroUseCase` resolves the identifier itself (email via
  `emails_usuario`, otherwise via `nombres_usuario`) and creates the `AccesoPlan` directly when it
  resolves to a real account; an unresolved email becomes a pending invite in the new
  `invitaciones_pendientes_por_email` collection (`InvitacionPendienteRepository`/
  `FirebaseInvitacionPendienteRepository`), later auto-resolved into a real `AccesoPlan` once that
  email registers (`AsegurarUsuarioUseCase.paraVincularEmail` now looks up and consumes any pending
  invites for the newly-linked email); an unresolved `nombreUsuario` is silently discarded, since
  unlike an email it can't identify a "future" account. `MiembrosViewModel.invitar()` always reports
  the same generic success regardless of resolution outcome — the use case never surfaces "not
  found" as an error, only a genuine Firestore failure does — so the UI never confirms or denies
  whether a given identifier belongs to a real account.
- Usuario entity + nombreUsuario, slice 8/10 (anonymous analytics signal, no user-visible change):
  `InvitarMiembroUseCase` now fires a `"invitacion_no_resuelta"` event through the new
  `AnalyticsRepository` (`:core:domain`) on every "not resolved" outcome — the email-pending-invite
  branch and the nombreUsuario-discarded branch — and stays silent on the resolved branch that
  creates a real `AccesoPlan`. The event carries no email, nombreUsuario, or other identifying
  payload, only a bare event name: it exists purely for dev-visibility into resolution-failure
  volume, never to expose what was searched, per AGENTS.md's "never log emails" rule and the
  anti-enumeration guarantee slice 7/10 established. `FirebaseAnalyticsRepository` (`:core:data`)
  implements it via `FirebaseAnalytics.logEvent`, wired through `UsuarioModule`/
  `UsuarioUseCaseModule` (Hilt); `firebase-analytics` (BOM-managed, no `-ktx` suffix — the BOM at
  this project's pinned version already merges KTX into the base artifact, matching
  `firebase-auth`/`firebase-firestore`/`firebase-storage`'s existing non-`-ktx` naming) is a new
  `:core:data` dependency. `:core:data` also gains its first `AndroidManifest.xml`, declaring
  `INTERNET`/`ACCESS_NETWORK_STATE`/`WAKE_LOCK` (required by `FirebaseAnalytics.getInstance`,
  flagged by lint's `MissingPermission` check when analyzed at the library-module level).
- Usuario entity + nombreUsuario, slice 9/10 (Firestore rules deployment — the anti-enumeration
  design from slices 1-8 is now actually enforced, not just built): `firestore.rules` splits `get`
  from `list` on `usuarios` (any authenticated caller can `get` a known uid, `list` is denied
  outright), and adds the same get-only, list-denied shape for the new `nombres_usuario` and
  `emails_usuario` lookup indexes (owner-only `create`/`delete`, no `update`, and a `create` on an
  already-reserved id correctly falls through to the disabled `update` rule instead of silently
  overwriting someone's reservation). `invitaciones_pendientes_por_email` gets its own rules: `get`
  is denied (the composite `{email}_{planId}` id isn't something the invitee knows in advance),
  `create` is open to any authenticated caller, `delete` is restricted to the matching-email caller,
  and `list` only succeeds for a query whose result set is provably scoped to the caller's own
  verified `request.auth.token.email` (`resource.data.email == request.auth.token.email`, the
  documented Firestore pattern for "list only your own records" — `request.query.where` accessors,
  flagged as unconfirmed in the plan, turned out not to exist in the rules language at all). Also
  fixes a rule gap found in slice 7's review: `acceso_plan_financiero`'s `create` rule previously
  only allowed a plan's owner (on plan creation) or an existing admin+ to create an access doc,
  which meant `AsegurarUsuarioUseCase.paraVincularEmail`'s pending-invite auto-resolution — writing
  a brand-new invitee's own `AccesoPlan` from their own just-registered session — failed with
  `PERMISSION_DENIED` on every single pending invite once these rules were enforced. A third
  disjunct now allows a caller to self-grant access only for the exact role recorded on a matching
  `invitaciones_pendientes_por_email` doc keyed off their own verified auth-token email, so
  pending-invite resolution works end-to-end for the first time. `rules-tests/rules.test.mjs` covers
  the full matrix (get/list split, create/delete ownership, no-overwrite, scoped-list, and the three
  self-grant cases: correct role succeeds, wrong role fails, no matching pending invite fails) with
  no regressions in the pre-existing `acceso_plan_financiero` suite.
- Usuario entity + nombreUsuario, slice 10/10 (pre-merge security fix round, from the branch's
  final holistic review): closes a **privilege-escalation chain** and a **handle-impersonation**
  hole, both of which were reachable straight from the Firestore SDK with no app involvement.
  (1) `invitaciones_pendientes_por_email.create` was `if isSignedIn()` with no constraint on the
  document or on the caller's relationship to the plan. Combined with slice 9's self-grant
  disjunct on `acceso_plan_financiero.create` — which trusts any pending-invite doc matching the
  caller's own verified email — any authenticated user could write
  `invitaciones_pendientes_por_email/{ownEmail}_{anyPlanId}` with `rol: 'owner'` of their own
  choosing and then self-grant themselves owner access to a plan they had nothing to do with
  (plan IDs are enumerable, since `acceso_plan_financiero` allows an open `read`). `create` now
  requires admin(2)+ on the target plan (mirroring the check `acceso_plan_financiero.create`
  already used for a legitimate inviter), pins `invitadoPor` to the caller's uid, and pins the
  doc id to its own `email`/`planId` fields so id and data can't be desynchronized. Note that the
  rule is the *only* barrier here: `MiembrosScreen` offers "Invitar" to any plan member without
  checking their role, so a viewer/editor's invite of an unregistered email now fails at the rule
  — consistent with a viewer's invite of an *already-registered* user, which slice 9's rules
  already rejected, so the anti-enumeration property (identical outcome regardless of whether the
  identifier resolved) still holds for every role. Gating the invite UI by role is a follow-up.
  (2) `invitaciones_pendientes_por_email`'s `list`/`delete` compared `resource.data.email` against
  the raw `request.auth.token.email`, while the self-grant disjunct compares against
  `.lower()` and the client always stores the email lowercased. A token carrying any uppercase
  character could therefore resolve its invitation but never list or delete it, orphaning the
  pending doc permanently; all three sites now lowercase consistently.
  (3) `usuarios.create`/`update` only checked "this is my own document", but `nombreUsuario` is
  rendered publicly (`MiembroCard`), so anyone could bypass `RenombrarUsuarioUseCase`'s
  reservation flow and write another member's handle directly, impersonating them in the Miembros
  list. Both now require that the `nombres_usuario/{normalizado}` reservation for the written
  handle already exists and already points at the caller's uid; an update that leaves
  `nombreUsuario` unchanged (avatar/nombre/email writes) short-circuits without the extra `get()`.
  This forced `FirebaseUsuarioRepository.renombrar` to split into **two** sequential,
  separately-committed transactions (reserve; then move the display field and drop the old
  reservation) — verified empirically against the emulator, a rules `get()` reads the
  pre-transaction snapshot, so a reservation written earlier in the *same* transaction isn't yet
  visible when the `usuarios` write is validated, and the previous single-transaction shape is
  denied outright. The accepted tradeoff (a rename is no longer atomic: if the reservation
  commits and the second transaction fails, the user holds both reservations and keeps their old
  display name — nothing lost, retry completes it) is documented in the design doc rather than
  engineered around. The display value is now trimmed before being written, since the rule
  re-derives the reservation id with `.lower()` and the rules language has no `.trim()`.
  `rules-tests/rules.test.mjs` covers all of it, including the exact original exploit, the
  legitimate admin-invites path, the full end-to-end invite→register→self-grant chain, a
  mixed-case token resolving its lowercase-stored invitation, and the paired
  one-transaction-denied / two-transactions-allowed rename cases. Also: the three
  `nombreUsuario` error messages in `CuentaViewModel` and the invite-validation message in
  `MiembrosViewModel` were hardcoded Spanish strings in Kotlin, which lint's
  `HardcodedText`/`MissingTranslation` checks never see (they only scan XML) — they now travel as
  `@StringRes` ids resolved by the Composable, with entries in both `values/` and `values-en/`.
  Docs corrected to match reality: `schema.mdd` gains its missing v1.3 change-control rows and a
  "Migration v2 → v3" section, and the design doc no longer promises an "Invitación enviada"
  confirmation the invite dialog never actually showed.
- Home screen: opening a plan now lands on a per-plan dashboard (`HomeScreen`/`HomeViewModel`,
  `:feature:movimientos`) instead of going straight to the Movimientos list — cumulative balance
  across all of the plan's movimientos (new `ObservarBalanceAcumuladoPlanUseCase`), this month's
  income/expense split, and quick "Nuevo Gasto"/"Nuevo Ingreso" actions opening the existing
  `MovimientoFormSheet`. `ResumenMensualPlan` (via `ObservarResumenMensualPlanUseCase`) now exposes
  the month's income and expense totals separately — `netoDelMes` is derived from them rather than
  stored redundantly.
- `:app` gains a bottom-navigation shell (`PlanScaffold`) hosting Home/Movimientos/Categorías/
  Miembros as tabs of a single plan, replacing the previous separate `MovimientosRoute`/
  `MiembrosRoute`/`CategoriasRoute` nav-3 entries with one `PlanRoute(planId)` entry. Home and
  Movimientos share the existing `MovimientosViewModel` instance (hoisted once per `PlanRoute`
  entry via `hiltViewModel()`), matching the sync/error-consumption coupling already documented
  between those two screens. Each tab's own inner `Scaffold` now has its window insets consumed
  via `Modifier.consumeWindowInsets(innerPadding)` on `PlanScaffold`'s content `Box`, avoiding
  double-counted status-bar padding from the outer and inner `Scaffold`s both applying
  `WindowInsets.systemBars`. `:app` gained its first Compose UI test infrastructure (Robolectric +
  `ui-test-junit4`, `libs.androidx.compose.material.icons.{core,extended}` for the tab icons) and
  its first test, `PlanScaffoldTest`. New `nav_home`/`nav_movements`/`nav_categories`/
  `nav_members` strings; `:app` also gains its first `values-en/strings.xml` (only `app_name`
  stays untranslated, marked `tools:ignore="MissingTranslation"` as a proper noun).
- `:core:designsystem` gains a shared `ConfirmarEliminarDialog`, de-duplicating two nearly
  identical private delete-confirmation `AlertDialog`s that `:feature:categorias` and
  `:feature:movimientos` each had implemented on their own (behavior unchanged — same dialog, same
  strings per feature, just one shared component instead of two copies). `:feature:categorias` also
  gained Compose UI test infrastructure (Robolectric + `ui-test-junit4`, mirroring the other
  feature modules) and its first screen-level Compose test.
- M7 Slice A2 (Profile screen — first user-visible piece of the avatar
  feature): `:feature:cuenta`'s `CuentaScreen` now branches on
  `Sesion.esAnonima` — an anonymous session still sees the unchanged
  registration form, while a linked account sees a new profile view with a
  circular avatar (new `AvatarConBadge` in `:core:designsystem`, the first
  real Coil usage in this codebase, with a small edit-icon badge overlay),
  display name, email, an "Account info" card, and a Logout button. Tapping
  the avatar badge opens `ActivityResultContracts.PickVisualMedia`; the
  screen reads the picked image's bytes/content-type via `ContentResolver`
  and calls the new `CuentaViewModel.subirAvatar(bytes, contentType)`, which
  uploads through the existing `SubirAvatarUseCase` (M7 Slice A1) and persists
  the resulting URL via `AuthRepository.actualizarPerfil(photoUrl = ...)`.
  Logout calls `AuthRepository.cerrarSesion()`, which already re-establishes
  an anonymous session (guest-first re-entry, decided in Slice A1).
  `:feature:cuenta` gained its first unit/Compose test setup (JUnit,
  coroutines-test, Robolectric, `ui-test-junit4`).
- Sample plan seeding: the first time a user has zero plans, `SembrarPlanEjemploUseCase`
  creates one demo plan ("Gastos del hogar", EUR) with realistic Spain-locale
  sample expenses/income (Mercadona, gasolina, factura de la luz, nómina,
  etc.) so a fresh install shows a populated demo instead of an empty state.
  Idempotent — does nothing once the user has any plan, mirroring
  `SembrarCategoriasPorDefectoUseCase`'s existing guard.
- M7 Slice A1 (Storage foundation, backend-only — foundation for the upcoming
  Profile avatar feature, no user-visible UI yet): Firebase Storage is wired
  into the app for the first time, with a new `storage.rules` restricting
  `avatars/{uid}/avatar.jpg` to its owner (authenticated, path-scoped,
  `image/*` content-type allowlist, 5MB size limit), covered by a new
  `rules-tests/storage-rules.test.mjs` emulator suite (owner read/write, cross-uid
  read/write denied, oversized/non-image denied, unauthenticated denied) alongside
  the existing `firestore.rules` matrix. New `StorageRepository`/`SubirAvatarUseCase`
  (`:core:domain`) and `FirebaseStorageRepository` (`:core:data`, bridging the
  Task-based Storage SDK via `suspendCancellableCoroutine`, mirroring
  `FirebaseAuthRepository`'s existing pattern) upload to a fixed
  `avatars/{uid}/avatar.jpg` path (overwrite on re-upload) and return the
  download URL. `AuthRepository` gains `actualizarPerfil(nombre, photoUrl)`
  (persisted via `UserProfileChangeRequest`, mirroring `vincularEmail()`) and
  `cerrarSesion()` (signs out, then immediately re-establishes an anonymous
  session — guest-first re-entry). `Sesion` gains `photoUrl`, mapped from
  `FirebaseUser.photoUrl`. Nothing in the UI calls these yet — that lands in a
  later Profile screen slice.
- Perfil accesible desde todas las pantallas (front 3/3): the account/profile entry point, previously
  reachable only from the Planes list, now appears in every tab's `TopAppBar` inside an open plan
  (Home, Movimientos, Categorías, Miembros) via a new `AvatarTopBarAction` (`:core:designsystem`),
  showing the user's real avatar photo instead of a generic icon. `SesionGateViewModel.avatarUrl`
  reads `Sesion.photoUrl` (already kept live by `CuentaViewModel.subirAvatar`) once at the app root
  and passes it down alongside a shared `onAbrirCuenta` callback — no new data plumbing, no feature
  ViewModel changes beyond the screens' own composable parameters. Navigation-only: `CuentaScreen`'s
  profile UI itself is unchanged.

## [0.7.0] - 2026-08-23

### Added (M6 — Home dashboard + Adaptive List-Detail)

- Added branded adaptive and legacy launcher icons and an AndroidX SplashScreen-compatible launch screen.
- M6 Slice A (Home dashboard summary): each plan card on `PlanesScreen` now shows
  a reactive current-month net balance (income minus expenses), computed by the
  new `ObservarResumenMensualPlanUseCase` (`:core:domain`) and combined per-plan
  in `PlanesViewModel.resumenesPorPlan`. Replaces the dead, unwired
  `ListarMovimientosPorMesUseCase` (deleted along with its test). `:feature:planes`
  gained its first unit/Compose test setup (JUnit, coroutines-test, Robolectric,
  `ui-test-junit4`). Extracted a `PlanSincronizacion` interface for
  `PlanSincronizador` (mirroring the existing `MovimientoSincronizacion`/
  `CategoriaSincronizacion` pattern) so `PlanesViewModel` is unit-testable
  without a real `FirebaseFirestore` instance — a small, behavior-preserving
  addition beyond the original slice design, needed to write the required
  `PlanesViewModelTest`.
- M6 Slice B (Adaptive List-Detail in Movimientos): `MovimientosScreen` adopts
  Adaptive List-Detail — the first real usage in this codebase. On expanded/wide
  layouts (Material's Expanded width breakpoint, `maxHorizontalPartitions > 1`),
  the transaction list stays the list pane and `MovimientoFormSheet` (edit/delete)
  is promoted into an inline detail pane via `ListDetailPaneScaffold`
  (`androidx.compose.material3.adaptive.layout`) instead of a full-screen bottom
  sheet. Compact/medium layouts are byte-identical to before M6: the same
  `MovimientosScaffold` + `MovimientoFormularioSheet` as a `ModalBottomSheet`.
  `ConflictoMovimientoDialogHost` (M5 Slice 5b) stays a single top-level call,
  layout-independent, per design Decision 4. `rememberListDetailSceneStrategy<NavKey>()`
  is wired into `MainActivity`'s `NavDisplay` as forward-compatible, currently
  inert plumbing for future cross-route panes (design Decision 3 — see
  `doc/architecture.md`). `MovimientoFormSheet.kt` now exposes
  `MovimientoFormEstadoYContenido` (the pure form state + content, no
  `ModalBottomSheet` chrome) so both the compact sheet and the expanded pane
  reuse the identical fields/logic. `:feature:movimientos` gained its first
  Compose UI test setup (Robolectric + `ui-test-junit4`), asserting the compact
  vs. expanded rendering split and that the conflict dialog fires identically in
  both. This closes the M6 milestone (Slices A + B).

## [0.6.0] - 2026-08-23

### Added (M5 — Movimientos completo: edit/delete, attribution, conflict resolution)

- Gasto/Ingreso update and soft-delete: `Actualizar/Eliminar{Gasto,Ingreso}UseCase`,
  new `MovimientoRepository` update/delete methods, `FirebaseMovimientoRepository`
  get-before-upsert rollback on permanent Firestore failure (mirrors categories).
  Bugfix: `GastoDao`/`IngresoDao`'s `observeByPlan`/`observeByPlanAndRange` now
  exclude soft-deleted rows (`deletedAt IS NULL`), previously unfiltered.
- `editedBy`/`editedAt` attribution stamped client-side (`Instant.now()`) on
  every Gasto/Ingreso/Categoria/PlanFinanciero mutation, enforced server-side
  by updated `firestore.rules` across all four entity families: a spoofed
  `editedBy`, a mutated ownership/creation field, or any direct Firestore
  `delete` are all denied. `rules-tests/rules.test.mjs` grew from 14 to 32
  emulator tests covering the full allow/deny matrix.
- Conflict detection: in-memory `EdicionesPendientes`/`ConflictosPendientes`
  registries (`:core:domain/sync`) track unconfirmed local edits and flag a
  genuine concurrent-edit conflict — an incoming snapshot with a newer
  `editedAt` from a different editor while a local edit is still pending,
  including delete-vs-edit — without special-casing delete. Wired into all
  three sincronizadores (Movimiento/Categoria/Plan). Accepted debt (documented
  in `doc/architecture.md`): the registries are in-memory/process-lifetime
  only, not a persisted outbox; a process death between an optimistic write
  and its Firestore echo loses the pending marker and the remote version
  silently wins on the next sync.
- Conflict resolution UI: a row-level badge on conflicted Gasto/Ingreso
  entries; tapping one opens a blocking `ConflictoDialog` (`:core:designsystem`,
  new `conflict` package, entity-agnostic with no `:core:domain`/`res/`
  dependency) showing both versions side by side, differing fields
  emphasised. Resolution: "usar la mía"/"restaurar mi edición" re-issues the
  local edit; "usar la suya"/"mantener borrado" persists the remote document
  straight into Room via the new `Aplicar{Gasto,Ingreso}RemotoUseCase`,
  bypassing the edit-attribution use case since that write is already
  correctly attributed. `:core:designsystem` gained its first Compose UI test
  setup (Robolectric + `ui-test-junit4`).
- Edit/delete entry points in `:feature:movimientos` (tap a row to edit, a
  confirm-delete dialog), mirroring `:feature:categorias`' existing pattern.

## [0.5.0] - 2026-08-22

### Added (M4 — Categories)

- Categories domain and Firestore data layer in `:core:domain`/`:core:data`:
  `CategoriaRepository`, use cases (`CrearCategoria`, `ActualizarCategoria`,
  `EliminarCategoria`, `ObservarCategorias`, `ObservarCategoriasPorTipo`,
  `SembrarCategoriasPorDefecto`), `FirebaseCategoriaRepository` and a
  `CategoriaSincronizador` (snapshot listener on the plan's categories → Room,
  active-scope only, AGENTS rule 3).
- Optimistic Room-first writes with rollback on permanent Firestore failure for
  both categories and plans (`FirebaseCategoriaRepository`,
  `FirebasePlanFinancieroRepository`): Room updates immediately, and any remote
  failure restores the prior local snapshot before rethrowing. `PlanSincronizador`
  now also attaches a live per-plan snapshot listener instead of a one-shot read.
- Default category seeding is idempotent: deterministic `planId:clave` ids and a
  single batched `crearCategorias` write (`SembrarCategoriasPorDefectoUseCase`).
- `:feature:categorias` (new): category list (grid, filtered by Ingreso/Gasto),
  create/edit in a bottom sheet (name, type, icon picker), delete with a
  confirmation dialog. Reachable from `MovimientosScreen`'s top bar
  (`CategoriasRoute(planId)`). UX referenced from the legacy
  `act02-app_gastos` category screens.
- New dependency added to the approved baseline:
  `androidx.compose.material:material-icons-extended` (Compose BOM-managed),
  needed for a per-category icon set richer than `material-icons-core`'s ~50
  icons. Approved by the user and added to `AGENTS.md`'s "Stack (approved
  baseline)" table.

## [0.4.2] - 2026-08-21

### Changed (M3 close)

- Verified all M3 gates: `:app:assembleDebug`, `testDebugUnitTest`, `lintDebug`
  and `detekt` pass; Firestore rules validated against the Emulator (`spenvo-dev`,
  14/14 tests).
- Deployed the finalized `firestore.rules` and `firestore.indexes.json` to the
  production project (`spenvo-6d10a`), making the deny-by-default role-based rules
  live. `README.md` and `doc/security/owasp.md` updated.
- Remaining M3 manual step: confirm the Anonymous + Email/Password sign-in
  providers and App Check enforcement in the Firebase console.

## [0.4.1] - 2026-08-20

### Fixed (i18n)

- Spanish is now the default UI language (`values/`), matching the project
  convention (`ui-strings-i18n`); the previous build had Spanish in `values-es/`
  and English as the default, so devices on an English locale showed English.
- String keys renamed to English (snake_case): `plans_*`, `account_*`,
  `members_*`, `movements_*` (was `planes_*`, `cuenta_*`, `miembros_*`,
  `movimientos_*`). All `R.string.*` references updated in the four composables.
- English moved to `values-en/` (additive translation); `values-es/` removed.
- Note: `0.3.0`'s "Spanish strings (`values-es`)" entry documented the earlier,
  incorrect location.

## [0.4.0] - 2026-08-20

### Added (M3 — Plans, access, final rules + usable MVP)

- Account creation via email/password linking the anonymous UID
  (`linkWithCredential`), preserving local data without a merge:
  `AuthRepository.vincularEmail` + `VincularCredencialUseCase` in `:core:domain`
  (TDD), `FirebaseAuthRepository` update in `:core:data`, and the new
  `:feature:cuenta` screen (name, email, password). The account menu now shows the
  linked email instead of "Guest".
- Plans and shared access domain (TDD) in `:core:domain`: `PlanFinancieroRepository`,
  `AccesoPlanRepository` and use cases (`CrearPlan`, `ObservarPlanesDelUsuario`,
  `ObservarPlan`, `ActualizarPlan`, `InvitarMiembro`, `AceptarInvitacion`).
- Firestore remote layer in `:core:data`: `FirebasePlanFinancieroRepository`,
  `FirebaseAccesoPlanRepository`, DTO mappers, a `Task.await()` coroutine helper,
  and a `PlanSincronizador` (snapshot listener on the user's accesses → Room) that
  lives only while its Flow is collected (AGENTS rule 3). New DI wiring for the
  Room database (previously not in Hilt), DAOs, `PassphraseProvider`,
  `FirebaseFirestore`, and the plan use cases.
- `:feature:planes` (new): plan list (from Room), create-plan dialog (name +
  ISO 4217 currency), pending invitations with "Accept", and the account menu.
  The navigation root is now the plan list; `MovimientosRoute(planId)` is
  plan-scoped with a members action.
- `:feature:planes`/`:feature:cuenta` member screens: list members, invite by UID
  with a role selector, accept pending invitations.
- Final `firestore.rules` (M3): deny-by-default with server-side roles
  (owner/admin/editor/viewer); `editedBy`/`editedAt` deferred to M5. An owner
  creates their plan + OWNER access; admins invite; a user accepts their own
  pending invitation. `firestore.indexes.json` finalized (categories by
  planId+tipo; expenses/income by planId+fecha; access is single-field).
- `rules-tests/` (Node subproject): `firebase-tools` + `@firebase/rules-unit-testing`
  validate the rules matrix (14 tests) against the Firestore Emulator; `firebase.json`
  added at the repo root (`projectId: spenvo-dev`, emulator on port 8081).
  `node_modules/` and `firestore-debug.log` are gitignored.

### Changed

- Navigation root moved from `MovimientosRoute` to `PlanesRoute`; `MovimientosRoute`
  now carries a `planId` and its account menu moved to the plan list.
- Anonymous sign-in and sync now start from `PlanesViewModel` (was `MovimientosViewModel`).
- `:core:data` loads the SQLCipher native library in `SpenvoDatabase.build`
  (runtime, in addition to the migration-test fix), fixing a startup crash
  (`UnsatisfiedLinkError` on `SQLiteConnection.nativeOpen`).
- Added `libs.firebase.firestore` and regenerated dependency locks for
  `:core:data`, `:app`, `:feature:movimientos`, `:feature:cuenta`, `:feature:planes`.

### Docs

- `doc/architecture.md`: plans/access data flow, new modules, decisions.
- `doc/security/owasp.md`: Firestore rules + App Check status.

## [0.3.0] - 2026-08-20

### Added (M2 — Guest-first identity: anonymous auth + App Check)

- Firebase wired into `:app` (google-services plugin) and `:core:data`
  (`firebase-bom`, `firebase-auth`); App Check provider
  (`firebase-appcheck` + play-integrity in release, debug provider in debug).
- Guest-first anonymous session: `Sesion` model + `AuthRepository` contract +
  `IniciarSesionAnonimaUseCase` in `:core:domain` (TDD); `FirebaseAuthRepository`
  in `:core:data` signs in anonymously in the background and exposes the session
  as a `Flow`. Offline-first: the app opens directly to Movimientos; a failed
  sign-in is silent and retried every 30s.
- Account entry point in the `Movimientos` top app bar: shows the session state
  ("Guest"/"Invitado") and a "Create account" item that links to the M3 screen
  (placeholder).
- `android.permission.INTERNET` declared (required for any Firebase traffic).
- `:feature:movimientos` now depends on `:core:data` (Hilt binding for the
  auth repository); dependency locks regenerated.
- `:core:domain` keeps zero DI dependencies: the anonymous sign-in use case is
  provided via `@Provides` in `:core:data`.

### Changed

- `doc/architecture.md`: `:feature:movimientos → :core:data` edge added.
- Spanish strings (`values-es`) added for the account menu.

## [0.2.0] - 2026-08-20

### Added (M1 — Local data layer)

- Room schema v2 (`SpenvoDatabase`): six local cache entities (`usuarios`,
  `planes_financieros`, `acceso_plan_financiero`, `categorias`, `gastos`, `ingresos`)
  plus `sync_state` (v1, unchanged), all encrypted with SQLCipher.
- `MIGRATION_1_2` in SQL with the same DDL as the exported schema (`2.json`),
  validated by an instrumented migration test (`MigrationTestHelper` +
  SQLCipher `SupportOpenHelperFactory`).
- Mappers between domain models and Room entities, covered by unit tests.
- Schema wiring for the androidTest source set (`assets.directories`).
- New module `:core:security`: `PassphraseProvider` + `AndroidKeystorePassphraseProvider`
  (AES-256/GCM key in the Android Keystore; 256-bit passphrase generated on first
  use, stored encrypted). Instrumented lifecycle test
  (create/insert/close/reopen/read with SQLCipher).
- `SpenvoDatabase.build` now takes a `PassphraseProvider` (wired to
  `:core:security`) instead of a raw passphrase.
- Living docs updated: `doc/database/schema.mdd` v1.1 (Room v2),
  `doc/security/owasp.md` (Keystore control now active).

### Changed

- SQLCipher native library now loaded explicitly with `System.loadLibrary("sqlcipher")`
  (the 4.x `SQLiteDatabase.loadLibs` API was removed).
- `core:data` androidTest dependency lock updated (Room testing + androidx.test).
- New module `:core:security` added to the build (`settings.gradle.kts`) with its
  dependency lock; `androidx.sqlite:sqlite` catalog accessor added.

### Fixed

- Root cause of the earlier `MissingType` KSP error: the Room database file was
  corrupted; rewritten clean with all entities and converters.
- Instrumented migration test failed with `UnsatisfiedLinkError` on
  `SQLiteConnection.nativeOpen`; fixed by loading the native library in `@Before`.

## [0.1.0] - 2026-08-19

### Added (M0 — Bootstrap)

- Multi-module project: `:app`, `:core:domain`, `:core:data`, `:core:designsystem`,
  `:feature:movimientos`.
- Approved baseline stack: AGP 9.3.1 (built-in Kotlin 2.2.10), Compose BOM 2026.02.01,
  Material 3, Navigation 3 (1.1.4), Hilt 2.60.1, Room 2.8.4, SQLCipher 4.18.0,
  DataStore 1.2.1, Coil 3.4.0, kotlinx-serialization 1.11.0, coroutines 1.11.0.
- Green smoke build: compiles, unit tests, lint and detekt with the base architecture.
- Encrypted Room database with SQLCipher (`SpenvoDatabase` + `sync_state`), exported
  versioned schema.
- Security baseline: `allowBackup=false`, R8 enabled, `networkSecurityConfig`
  deny-by-default, versioned dependency locking, secrets out of the repo.
- Agent configuration: `AGENTS.md` + `.agents/` (12 rules, 7 skills, 3 commands).
- Blocking lint: `HardcodedText` and `MissingTranslation` as errors.
- Living docs: `doc/architecture.md`, `doc/database/schema.mdd` v1.0,
  `doc/security/owasp.md`.
- Drafts of `firestore.rules` and `firestore.indexes.json` (deny-by-default, roles).

### Changed

- Kotlin: pinned `kotlin-stdlib` to 2.2.10 for metadata compatibility (compiler 2.2.10).
- Coil pinned to 3.4.0 (3.5.0 requires Kotlin 2.4, incompatible with the 2.2.10 baseline).
- Project daemon JVM pinned to toolchain 21 (local JDK; avoids downloading JDK 25).

### Fixed

- Catalog version accessor `hilt-navigation-compose` (conflict with the Hilt extension).
- SQLCipher 4.18 API: `SQLiteDatabase.getBytes` is private; manual UTF-8 conversion.
- `dependencyLocking` moved to root `build.gradle.kts` + `subprojects` (Gradle 9).
- `android.disallowKotlinSourceSets=false` so KSP can register sources with
  built-in Kotlin.

### Technical notes (M0)

- `gradle-daemon-jvm.properties` points to toolchain 21 (JDK 21.0.3 installed locally).
- The app compiles and packages; on-device validation is pending
  (wireless ADB hung the install) — validated in M3 with the e2e smoke.
- Firebase (google-services plugin) is activated in M2, once the project exists.
