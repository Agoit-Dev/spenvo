# Login real + logout sin recreación anónima automática — design

Status: approved, pending implementation.
Front 2 of 3 in the auth/identity feature series. Front 1 ("Usuario entity + nombreUsuario") is
merged to `main` (`3687011`). Front 3 ("Perfil accesible desde todas las pantallas") is unstarted.
Google Sign-In stays out of scope here — `doc/security/owasp.md` already tracks it as deferred with
no milestone; this front only closes the email/password gap.

## Problem

Verified against code (`core/data/src/main/java/com/agoitdev/spenvo/data/auth/FirebaseAuthRepository.kt:78-81`):
`cerrarSesion()` does `auth.signOut()` immediately followed by `iniciarSesionAnonima()`. There is no
sign-in flow anywhere in the app — `signInWithEmailAndPassword`/`signInWithCredential` have zero
matches in the repo. The only auth-linking code is `vincularEmail()` (`AuthRepository.vincularEmail`),
which calls `linkWithCredential` on the *current* (anonymous) user — that's account creation, not
sign-in. Once a user logs out, their linked account's data still exists in Firestore but is
unreachable from the app: there is no way back in short of re-registering, which fails
("email already in use").

**The recreation problem is bigger than `cerrarSesion()` alone.** `PlanesViewModel.init`
(`feature/planes/src/main/java/com/agoitdev/spenvo/planes/PlanesViewModel.kt:100-107`) runs a retry
loop that calls `authRepository.iniciarSesionAnonima()` until it succeeds, every time
`PlanesViewModel` is instantiated. `PlanesRoute` is the app's root route
(`app/src/main/java/com/agoitdev/spenvo/MainActivity.kt:58`), so any path that recreates
`PlanesViewModel` — process death, a naive "clear backstack back to Planes" — silently re-creates an
anonymous session regardless of what `cerrarSesion()` itself does. Fixing only `cerrarSesion()` would
not satisfy "sin recreación automática" across a process restart.

## Scope decision

Front 2 covers: real email/password sign-in, password recovery, and making logout never
auto-recreate an anonymous session (survives process death). Explicitly out: Google Sign-In (separate,
unscheduled front), MFA (M8), front 3's profile-access work.

## Auth-bootstrap ownership: moved out of `PlanesViewModel`

`PlanesViewModel`'s auth retry loop is a pre-existing architecture smell — session bootstrap has
nothing to do with "planes" as a feature. This design removes it and centralizes session-gating at
the app root, which is also the only place that can legitimately decide "should the user even see
`PlanesRoute` right now."

## Domain + data layer

`AuthRepository` (`core/domain/.../repository/AuthRepository.kt`) gains two methods:

```kotlin
suspend fun iniciarSesionConEmail(email: String, password: String)
suspend fun enviarRecuperacionPassword(email: String)
```

`FirebaseAuthRepository` implements them with `auth.signInWithEmailAndPassword(...)` and
`auth.sendPasswordResetEmail(...)`, following the existing `suspendCancellableCoroutine` pattern used
by every other method in the class. `cerrarSesion()` drops its call to `iniciarSesionAnonima()` and
instead: mark the logout flag (see below), then `auth.signOut()`.

Two new use cases in `core/domain/.../usecase/`, mirroring `IniciarSesionAnonimaUseCase`'s thin
wrapper shape: `IniciarSesionConEmailUseCase`, `EnviarRecuperacionPasswordUseCase`. Registered in
`FirebaseModule` (`AuthModule.kt`) alongside the existing two.

## Persisted logout flag + root session gate

New `SesionPreferences` (`core/data/.../auth/`) using `PreferenceDataStoreFactory` — DataStore
Preferences is already an approved dependency (`gradle/libs.versions.toml`) but this is its first
use in the codebase, worth flagging during review. Exposes:

```kotlin
val sesionCerradaExplicitamente: Flow<Boolean> // default false
suspend fun marcarLogout()
suspend fun limpiarLogout()
```

`cerrarSesion()` calls `marcarLogout()` before `signOut()`.

New `SesionGateViewModel` in `:app` (root-level, replaces `PlanesViewModel`'s removed retry loop).
Combines `observeSesion()` and the flag into one sealed state:

- `Cargando` — initial, waiting for the first `Sesion` emission.
- `MostrarApp` — `uid != null` (any authenticated or already-anonymous session), **or**
  `uid == null && flag == false` (fresh install, never explicitly logged out: preserves the M7
  guest-first zero-friction entry — triggers `iniciarSesionAnonima()` once, then reports `MostrarApp`).
- `MostrarGate` — `uid == null && flag == true` (explicit logout, awaiting the user's choice).

`SpenvoApp` (`MainActivity.kt`) reads this state to pick the current backstack: `MostrarApp` →
`PlanesRoute` as usual; `MostrarGate` → `CuentaRoute` as the only reachable entry, no back path to
`PlanesRoute`. Picking "iniciar sesión" (successful) or "continuar como invitado" from that state
calls `limpiarLogout()`, which flips the gate back to `MostrarApp`.

This makes "no automatic anonymous recreation" a structural consequence of removing the retry loop,
not a special case bolted onto `cerrarSesion()` — and it survives process death, since Firebase Auth
persists the signed-out state locally and the flag lives in DataStore.

## UI: unified `CuentaScreen`

`CuentaScreen` keeps showing `PerfilContenido` when `sesion.estaAutenticada`. When not authenticated
(covers both a fresh anonymous session and the gate's transient `uid == null` state), it shows a new
`AuthForm` with two modes instead of the fixed `RegistroForm`:

- **"Crear cuenta"** — today's `RegistroForm`, unchanged, calls `viewModel::registrar`.
- **"Iniciar sesión"** — new: email + password fields, calls `viewModel::iniciarSesion`; a
  "¿Olvidaste tu contraseña?" `TextButton` opens a single-field dialog calling
  `viewModel::recuperarPassword`.

`CuentaScreen` gains a `tabInicial` parameter so the caller controls which mode is selected by
default: the gate opens it on "Iniciar sesión" (more likely the user already has an account); the
account-menu entry point from `PlanesScreen` (fresh anonymous session) opens it on "Crear cuenta",
matching current behavior. "Continuar como invitado" lives in the gate wrapper itself, not inside
`CuentaScreen`, so an already-authenticated user never sees it.

## Error handling

`CuentaViewModel` gains `iniciarSesion(email, password)` and `recuperarPassword(email)`, following
`registrar()`'s existing `runCatching` + `RegistroEstado` pattern. New `mapearErrorAuth(Throwable):
String` in `:feature:cuenta` (backed by `values/`+`values-en/` strings):

- `FirebaseAuthInvalidUserException` / `FirebaseAuthInvalidCredentialsException` → the same
  "email o contraseña incorrectos" message for both — never reveal which part was wrong, avoids
  account enumeration (`doc/security/owasp.md`).
- `FirebaseAuthUserCollisionException` — unchanged, already handled in `registrar`.
- `FirebaseNetworkException` → connectivity message.
- Anything else → generic message; log without PII (never log email/password, per `AGENTS.md`).

`enviarRecuperacionPassword` always shows the same success message regardless of whether the email
exists — Firebase's `sendPasswordResetEmail` already behaves this way by default (no error on an
unknown email), same anti-enumeration reasoning.

## Modules touched

- `:core:domain` — `AuthRepository` interface, 2 new use cases.
- `:core:data` — `FirebaseAuthRepository` (2 new methods, `cerrarSesion()` changed), new
  `SesionPreferences`, `AuthModule.kt` DI registrations.
- `:app` — `MainActivity.kt`/`SpenvoApp` (new `SesionGateViewModel`, backstack driven by gate state).
- `:feature:planes` — `PlanesViewModel` loses its `init` auth-bootstrap block.
- `:feature:cuenta` — `CuentaScreen` (`AuthForm` with 2 modes, `tabInicial` param, recovery dialog),
  `CuentaViewModel` (2 new actions, error mapping).

## Testing

Per `AGENTS.md`'s strict TDD:

- `FirebaseAuthRepository` fake (same one used by `VincularCredencialUseCaseTest`/
  `IniciarSesionAnonimaUseCaseTest`): new success/failure cases for `iniciarSesionConEmail` and
  `enviarRecuperacionPassword`.
- `IniciarSesionConEmailUseCaseTest` / `EnviarRecuperacionPasswordUseCaseTest` — thin delegation tests.
- `SesionPreferencesTest` — against a real temp-file/in-memory DataStore, same standard already
  applied to Room DAOs (no fake).
- `SesionGateViewModelTest` (new) — all 3 state transitions across `uid`/flag combinations, with
  `AuthRepository`/`SesionPreferences` fakes.
- `CuentaViewModelTest` — new cases for `iniciarSesion` (success, invalid credentials, no network)
  and `recuperarPassword` (always-success UX).
- `CuentaScreenTest` (Compose, existing Robolectric infra in `:feature:cuenta`) — the crear/iniciar
  sesión toggle, the recovery dialog, `tabInicial` picking the right default tab.
- **Explicit regression test for the core requirement**: no automatic flow (cold start with
  flag == true, or a `PlanesViewModel` remount) ever calls `iniciarSesionAnonima()` without an
  explicit user action. This is the central guarantee of this front, not an incidental case.

## Out of scope (explicitly)

- Google Sign-In — deferred, no milestone (`doc/security/owasp.md`).
- MFA, osv-scanner in CI — M8, unrelated.
- Front 3 ("Perfil accesible desde todas las pantallas") — separate front, unstarted.
- `CuentaViewModel.registrar()`'s partial-failure UX gap and
  `AsegurarUsuarioUseCase.paraVincularEmail`'s orphaned-pending-invite gap — pre-existing, documented
  in `doc/designs/2026-08-30-usuario-nombreusuario-design.md`'s "Known gap" sections, unrelated to
  this front's scope.
