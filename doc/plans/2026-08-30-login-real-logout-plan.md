# Login real + logout sin recreación anónima — Implementation Plan

> **For agentic workers:** Use `mobiai-mobile-executing-plans-with-subagents` (recommended) or `mobiai-mobile-executing-plans` to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Add real email/password sign-in and password recovery, and stop `cerrarSesion()` (and any other code path) from silently re-creating an anonymous Firebase session — the guarantee must survive process death.

**Architecture:** Two new `AuthRepository` methods (`iniciarSesionConEmail`, `enviarRecuperacionPassword`) backed by Firebase Auth. A DataStore-persisted `sesionCerradaExplicitamente` flag, set on logout. Auth-bootstrap moves out of `PlanesViewModel.init` into a new root-level `SesionGateViewModel` in `:app` that drives `SpenvoApp`'s backstack (`PlanesRoute` vs. gate-only `CuentaRoute`). `CuentaScreen` gains a crear-cuenta/iniciar-sesión toggle plus a recovery dialog.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), Navigation 3, Hilt, Firebase Auth, DataStore Preferences, JUnit4 + kotlinx-coroutines-test, Robolectric + `ui-test-junit4`.

**Platform:** Android

---

## Dependency note (verified, no gradle change needed)

`androidx.datastore:datastore-preferences` is **already** declared: `gradle/libs.versions.toml:14,79` (`datastore = "1.2.1"`, alias `datastore-preferences`) and already applied in `core/data/build.gradle.kts:54` (`implementation(libs.datastore.preferences)`). No new dependency, no `--write-locks` step in this plan.

## `schema.mdd` verdict (verified)

`doc/database/schema.mdd` documents Room tables and Firestore collections only — no DataStore section exists anywhere in it (checked: zero matches for "DataStore"/"Preferences"). This front adds no Room table and no Firestore collection, so **no `schema.mdd` change**.

## Ripple effect: every `AuthRepository` fake needs the 2 new overrides

`AuthRepository` currently has 14 implementations across the repo (grep-verified): the production `FirebaseAuthRepository` and 13 private test fakes across 11 test files. Adding 2 new abstract methods to the interface breaks compilation of **all 13** until each gets the two new overrides. Task 1 covers the interface + production impl; Task 2 covers all 13 fakes as one batch (mechanical, same 2-line stub in most of them).

---

### Task 1: `AuthRepository` interface + `FirebaseAuthRepository` implementation

**Files:**
- Modify: `core/domain/src/main/java/com/agoitdev/spenvo/domain/repository/AuthRepository.kt`
- Modify: `core/data/src/main/java/com/agoitdev/spenvo/data/auth/FirebaseAuthRepository.kt`
- Test: `core/data/src/test/java/com/agoitdev/spenvo/data/auth/FirebaseAuthRepositoryTest.kt` (new file — no existing test file for this class; it's currently untested per codegraph's "⚠️ no covering tests found")

There's no existing unit test harness for `FirebaseAuthRepository` (it wraps the real `FirebaseAuth` SDK object, which isn't mockable without Robolectric/a fake). Since `iniciarSesionConEmail`/`enviarRecuperacionPassword` follow the exact same `suspendCancellableCoroutine` + `addOnSuccessListener`/`addOnFailureListener` shape already used untested by `vincularEmail`/`actualizarPerfil`/`cerrarSesion`, and the project doesn't currently invest in mocking the Firebase SDK surface, this task's TDD loop happens one layer up: the **use case** tests in Task 3 exercise the contract through a hand-written fake `AuthRepository`, which is this codebase's established pattern (see `VincularCredencialUseCaseTest`'s `FakeAuthRepositoryVincular`). `FirebaseAuthRepository` itself stays covered by the existing instrumented/manual verification precedent used for the rest of the class, not a new unit test file.

- [ ] **Step 1: Add the two new methods to the domain interface**

`core/domain/src/main/java/com/agoitdev/spenvo/domain/repository/AuthRepository.kt` — full replacement:

```kotlin
package com.agoitdev.spenvo.domain.repository

import com.agoitdev.spenvo.domain.model.Sesion
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    fun observeSesion(): Flow<Sesion>

    suspend fun iniciarSesionAnonima()

    suspend fun iniciarSesionConEmail(email: String, password: String)

    suspend fun enviarRecuperacionPassword(email: String)

    suspend fun vincularEmail(email: String, password: String, nombre: String)

    suspend fun actualizarPerfil(nombre: String? = null, photoUrl: String? = null)

    suspend fun cerrarSesion()
}
```

- [ ] **Step 2: Build to confirm the interface change breaks every implementer**

Run: `./gradlew :core:domain:compileDebugKotlin`
Expected: PASS (the interface module itself compiles fine — the breakage shows up in every module that implements it, addressed in Task 2 and this task's Step 3).

- [ ] **Step 3: Implement both methods in `FirebaseAuthRepository`, and drop the anonymous-recreate from `cerrarSesion`**

`core/data/src/main/java/com/agoitdev/spenvo/data/auth/FirebaseAuthRepository.kt` — full replacement:

```kotlin
package com.agoitdev.spenvo.data.auth

import android.net.Uri
import com.agoitdev.spenvo.domain.model.Sesion
import com.agoitdev.spenvo.domain.repository.AuthRepository
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine

@Singleton
class FirebaseAuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val sesionPreferences: SesionPreferences,
) : AuthRepository {

    override fun observeSesion(): Flow<Sesion> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser.toSesion())
        }
        auth.addAuthStateListener(listener)
        trySend(auth.currentUser.toSesion())
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    override suspend fun iniciarSesionAnonima() {
        if (auth.currentUser != null) return
        suspendCancellableCoroutine { cont ->
            auth.signInAnonymously()
                .addOnSuccessListener { cont.resume(Unit) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }
    }

    override suspend fun iniciarSesionConEmail(email: String, password: String) {
        suspendCancellableCoroutine { cont ->
            auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener { cont.resume(Unit) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }
        sesionPreferences.limpiarLogout()
    }

    override suspend fun enviarRecuperacionPassword(email: String) {
        suspendCancellableCoroutine { cont ->
            auth.sendPasswordResetEmail(email)
                .addOnSuccessListener { cont.resume(Unit) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }
    }

    override suspend fun vincularEmail(email: String, password: String, nombre: String) {
        val currentUser = auth.currentUser
            ?: throw FirebaseAuthException("NO_CURRENT_USER", "No hay una sesión activa para vincular")
        val credencial = EmailAuthProvider.getCredential(email, password)
        suspendCancellableCoroutine { cont ->
            currentUser.linkWithCredential(credencial)
                .addOnSuccessListener { cont.resume(Unit) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }
        if (nombre.isNotBlank()) {
            suspendCancellableCoroutine { cont ->
                val perfil = UserProfileChangeRequest.Builder()
                    .setDisplayName(nombre)
                    .build()
                currentUser.updateProfile(perfil)
                    .addOnSuccessListener { cont.resume(Unit) }
                    .addOnFailureListener { cont.resumeWithException(it) }
            }
        }
    }

    override suspend fun actualizarPerfil(nombre: String?, photoUrl: String?) {
        val currentUser = auth.currentUser
            ?: throw FirebaseAuthException("NO_CURRENT_USER", "No hay una sesión activa para actualizar")
        if (nombre == null && photoUrl == null) return
        suspendCancellableCoroutine { cont ->
            val builder = UserProfileChangeRequest.Builder()
            if (nombre != null) builder.setDisplayName(nombre)
            if (photoUrl != null) builder.setPhotoUri(Uri.parse(photoUrl))
            currentUser.updateProfile(builder.build())
                .addOnSuccessListener { cont.resume(Unit) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }
    }

    override suspend fun cerrarSesion() {
        sesionPreferences.marcarLogout()
        auth.signOut()
    }
}

private fun FirebaseUser?.toSesion(): Sesion = this?.let {
    Sesion(
        uid = it.uid,
        esAnonima = it.isAnonymous,
        email = it.email,
        nombre = it.displayName,
        photoUrl = it.photoUrl?.toString(),
    )
} ?: Sesion.Anonima
```

Note: `iniciarSesionConEmail` calls `sesionPreferences.limpiarLogout()` on success — this covers the case where a user reaches the gate's "iniciar sesión" tab directly (bypassing "continuar como invitado"), so the flag doesn't stay stuck `true` after a real sign-in. `SesionPreferences` doesn't exist yet — this file won't compile until Task 4 creates it. That's expected; Task 4 immediately follows.

- [ ] **Step 4: Commit (deferred — bundled with Task 4's commit once `SesionPreferences` exists and the module compiles)**

No standalone commit here; `FirebaseAuthRepository.kt` and `AuthRepository.kt` are staged together with Task 4's `SesionPreferences.kt` in Task 4's commit, since Step 3 introduces a compile dependency Task 4 satisfies. Task 2 (fakes) does NOT depend on `SesionPreferences` and can commit independently first.

---

### Task 2: Add the 2 new overrides to all 13 `AuthRepository` test fakes

**Files (modify all 13 — each gets the same 2-line no-op addition unless noted):**
- `core/domain/src/test/java/com/agoitdev/spenvo/domain/usecase/VincularCredencialUseCaseTest.kt` (`FakeAuthRepositoryVincular`)
- `core/domain/src/test/java/com/agoitdev/spenvo/domain/usecase/IniciarSesionAnonimaUseCaseTest.kt` (`FakeAuthRepository`)
- `feature/planes/src/test/java/com/agoitdev/spenvo/planes/PlanesViewModelTest.kt`
- `feature/planes/src/test/java/com/agoitdev/spenvo/planes/PlanesScreenTest.kt`
- `feature/planes/src/test/java/com/agoitdev/spenvo/planes/MiembrosViewModelTest.kt`
- `feature/movimientos/src/test/java/com/agoitdev/spenvo/movimientos/MovimientosViewModelTest.kt`
- `feature/movimientos/src/test/java/com/agoitdev/spenvo/movimientos/MovimientosScreenListDetailTest.kt`
- `feature/movimientos/src/test/java/com/agoitdev/spenvo/movimientos/MovimientoFormSheetTest.kt`
- `feature/movimientos/src/test/java/com/agoitdev/spenvo/movimientos/HomeScreenTest.kt`
- `feature/categorias/src/test/java/com/agoitdev/spenvo/categorias/CategoriasViewModelTest.kt`
- `feature/categorias/src/test/java/com/agoitdev/spenvo/categorias/CategoriaFormularioSheetTest.kt`
- `feature/cuenta/src/test/java/com/agoitdev/spenvo/cuenta/CuentaViewModelTest.kt` (`FakeAuthRepositorioCuenta`) — richer stub, see Task 5/6
- `feature/cuenta/src/test/java/com/agoitdev/spenvo/cuenta/CuentaScreenTest.kt` (`FakeAuthRepositorioPantalla`) — richer stub, see Task 7

This task covers only the **11 unrelated fakes** (everywhere except the 2 `:feature:cuenta` files, which get their real tracking logic in Tasks 5–7 alongside the features that actually exercise `iniciarSesionConEmail`/`enviarRecuperacionPassword`). No new test cases here — this is the mechanical compile-fix pass; existing test behavior is unaffected.

- [ ] **Step 1: Add the two no-op overrides to each of the 11 fakes**

In each file, find the private class implementing `AuthRepository` and add, next to its existing `override suspend fun vincularEmail(...)` line:

```kotlin
    override suspend fun iniciarSesionConEmail(email: String, password: String) = Unit
    override suspend fun enviarRecuperacionPassword(email: String) = Unit
```

- [ ] **Step 2: Compile and run the full test suite to confirm nothing broke**

Run: `./gradlew testDebugUnitTest`
Expected: all previously-passing tests in these 11 files still PASS (this step only restores compilation — no new assertions).

- [ ] **Step 3: Commit**

```bash
git add core/domain/src/main/java/com/agoitdev/spenvo/domain/repository/AuthRepository.kt \
        core/domain/src/test/java/com/agoitdev/spenvo/domain/usecase/VincularCredencialUseCaseTest.kt \
        core/domain/src/test/java/com/agoitdev/spenvo/domain/usecase/IniciarSesionAnonimaUseCaseTest.kt \
        feature/planes/src/test/java/com/agoitdev/spenvo/planes/PlanesViewModelTest.kt \
        feature/planes/src/test/java/com/agoitdev/spenvo/planes/PlanesScreenTest.kt \
        feature/planes/src/test/java/com/agoitdev/spenvo/planes/MiembrosViewModelTest.kt \
        feature/movimientos/src/test/java/com/agoitdev/spenvo/movimientos/MovimientosViewModelTest.kt \
        feature/movimientos/src/test/java/com/agoitdev/spenvo/movimientos/MovimientosScreenListDetailTest.kt \
        feature/movimientos/src/test/java/com/agoitdev/spenvo/movimientos/MovimientoFormSheetTest.kt \
        feature/movimientos/src/test/java/com/agoitdev/spenvo/movimientos/HomeScreenTest.kt \
        feature/categorias/src/test/java/com/agoitdev/spenvo/categorias/CategoriasViewModelTest.kt \
        feature/categorias/src/test/java/com/agoitdev/spenvo/categorias/CategoriaFormularioSheetTest.kt
git commit -m "feat(auth): add AuthRepository.iniciarSesionConEmail/enviarRecuperacionPassword"
```

---

### Task 3: `IniciarSesionConEmailUseCase` + `EnviarRecuperacionPasswordUseCase`

**Files:**
- Create: `core/domain/src/main/java/com/agoitdev/spenvo/domain/usecase/IniciarSesionConEmailUseCase.kt`
- Create: `core/domain/src/main/java/com/agoitdev/spenvo/domain/usecase/EnviarRecuperacionPasswordUseCase.kt`
- Create: `core/domain/src/test/java/com/agoitdev/spenvo/domain/usecase/IniciarSesionConEmailUseCaseTest.kt`
- Create: `core/domain/src/test/java/com/agoitdev/spenvo/domain/usecase/EnviarRecuperacionPasswordUseCaseTest.kt`
- Modify: `core/data/src/main/java/com/agoitdev/spenvo/data/auth/AuthModule.kt`

- [ ] **Step 1: Write the failing test for `IniciarSesionConEmailUseCase`**

`core/domain/src/test/java/com/agoitdev/spenvo/domain/usecase/IniciarSesionConEmailUseCaseTest.kt`:

```kotlin
package com.agoitdev.spenvo.domain.usecase

import com.agoitdev.spenvo.domain.model.Sesion
import com.agoitdev.spenvo.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IniciarSesionConEmailUseCaseTest {

    private val repo = FakeAuthRepositoryLogin()

    @Test
    fun `delega email y password al repositorio`() = runTest {
        val useCase = IniciarSesionConEmailUseCase(repo)

        useCase(email = "ana@example.com", password = "secreta-123")

        assertEquals("ana@example.com", repo.ultimoEmail)
        assertEquals("secreta-123", repo.ultimaPassword)
        assertTrue(repo.llamado)
    }
}

private class FakeAuthRepositoryLogin : AuthRepository {
    val sesion = MutableStateFlow(Sesion.Anonima)
    var ultimoEmail: String? = null
    var ultimaPassword: String? = null
    var llamado = false

    override fun observeSesion(): Flow<Sesion> = sesion
    override suspend fun iniciarSesionAnonima() = Unit
    override suspend fun iniciarSesionConEmail(email: String, password: String) {
        llamado = true
        ultimoEmail = email
        ultimaPassword = password
        sesion.value = Sesion(uid = "user-1", esAnonima = false, email = email)
    }
    override suspend fun enviarRecuperacionPassword(email: String) = Unit
    override suspend fun vincularEmail(email: String, password: String, nombre: String) = Unit
    override suspend fun actualizarPerfil(nombre: String?, photoUrl: String?) = Unit
    override suspend fun cerrarSesion() = Unit
}
```

- [ ] **Step 2: Run to confirm it fails**

Run: `./gradlew :core:domain:testDebugUnitTest --tests "*.IniciarSesionConEmailUseCaseTest"`
Expected: FAIL — `Unresolved reference: IniciarSesionConEmailUseCase`

- [ ] **Step 3: Implement `IniciarSesionConEmailUseCase`**

`core/domain/src/main/java/com/agoitdev/spenvo/domain/usecase/IniciarSesionConEmailUseCase.kt`:

```kotlin
package com.agoitdev.spenvo.domain.usecase

import com.agoitdev.spenvo.domain.repository.AuthRepository

class IniciarSesionConEmailUseCase(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(email: String, password: String) {
        authRepository.iniciarSesionConEmail(email, password)
    }
}
```

- [ ] **Step 4: Run to confirm it passes**

Run: `./gradlew :core:domain:testDebugUnitTest --tests "*.IniciarSesionConEmailUseCaseTest"`
Expected: PASS

- [ ] **Step 5: Write the failing test for `EnviarRecuperacionPasswordUseCase`**

`core/domain/src/test/java/com/agoitdev/spenvo/domain/usecase/EnviarRecuperacionPasswordUseCaseTest.kt`:

```kotlin
package com.agoitdev.spenvo.domain.usecase

import com.agoitdev.spenvo.domain.model.Sesion
import com.agoitdev.spenvo.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class EnviarRecuperacionPasswordUseCaseTest {

    private val repo = FakeAuthRepositoryRecovery()

    @Test
    fun `delega el email al repositorio`() = runTest {
        val useCase = EnviarRecuperacionPasswordUseCase(repo)

        useCase(email = "ana@example.com")

        assertEquals("ana@example.com", repo.ultimoEmail)
    }
}

private class FakeAuthRepositoryRecovery : AuthRepository {
    val sesion = MutableStateFlow(Sesion.Anonima)
    var ultimoEmail: String? = null

    override fun observeSesion(): Flow<Sesion> = sesion
    override suspend fun iniciarSesionAnonima() = Unit
    override suspend fun iniciarSesionConEmail(email: String, password: String) = Unit
    override suspend fun enviarRecuperacionPassword(email: String) {
        ultimoEmail = email
    }
    override suspend fun vincularEmail(email: String, password: String, nombre: String) = Unit
    override suspend fun actualizarPerfil(nombre: String?, photoUrl: String?) = Unit
    override suspend fun cerrarSesion() = Unit
}
```

- [ ] **Step 6: Run to confirm it fails**

Run: `./gradlew :core:domain:testDebugUnitTest --tests "*.EnviarRecuperacionPasswordUseCaseTest"`
Expected: FAIL — `Unresolved reference: EnviarRecuperacionPasswordUseCase`

- [ ] **Step 7: Implement `EnviarRecuperacionPasswordUseCase`**

`core/domain/src/main/java/com/agoitdev/spenvo/domain/usecase/EnviarRecuperacionPasswordUseCase.kt`:

```kotlin
package com.agoitdev.spenvo.domain.usecase

import com.agoitdev.spenvo.domain.repository.AuthRepository

class EnviarRecuperacionPasswordUseCase(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(email: String) {
        authRepository.enviarRecuperacionPassword(email)
    }
}
```

- [ ] **Step 8: Run to confirm it passes**

Run: `./gradlew :core:domain:testDebugUnitTest --tests "*.EnviarRecuperacionPasswordUseCaseTest"`
Expected: PASS

- [ ] **Step 9: Register both use cases in `AuthModule.kt`**

`core/data/src/main/java/com/agoitdev/spenvo/data/auth/AuthModule.kt` — full replacement:

```kotlin
package com.agoitdev.spenvo.data.auth

import com.agoitdev.spenvo.domain.repository.AuthRepository
import com.agoitdev.spenvo.domain.usecase.EnviarRecuperacionPasswordUseCase
import com.agoitdev.spenvo.domain.usecase.IniciarSesionAnonimaUseCase
import com.agoitdev.spenvo.domain.usecase.IniciarSesionConEmailUseCase
import com.agoitdev.spenvo.domain.usecase.VincularCredencialUseCase
import com.google.firebase.auth.FirebaseAuth
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: FirebaseAuthRepository): AuthRepository
}

@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides
    fun provideIniciarSesionAnonima(
        authRepository: AuthRepository,
    ): IniciarSesionAnonimaUseCase = IniciarSesionAnonimaUseCase(authRepository)

    @Provides
    fun provideIniciarSesionConEmail(
        authRepository: AuthRepository,
    ): IniciarSesionConEmailUseCase = IniciarSesionConEmailUseCase(authRepository)

    @Provides
    fun provideEnviarRecuperacionPassword(
        authRepository: AuthRepository,
    ): EnviarRecuperacionPasswordUseCase = EnviarRecuperacionPasswordUseCase(authRepository)

    @Provides
    fun provideVincularCredencial(
        authRepository: AuthRepository,
    ): VincularCredencialUseCase = VincularCredencialUseCase(authRepository)
}
```

Note: this module still won't compile standalone — `FirebaseAuthRepository`'s constructor (Task 1) now requires a `SesionPreferences` that doesn't exist until Task 4. That's expected; Tasks 1, 3, and 4 land together in Task 4's commit.

- [ ] **Step 10: Commit (bundled — see Task 4)**

No standalone commit; staged together with Task 4.

---

### Task 4: `SesionPreferences` (DataStore) + bundled commit for Tasks 1/3/4

**Files:**
- Create: `core/data/src/main/java/com/agoitdev/spenvo/data/auth/SesionPreferences.kt`
- Create: `core/data/src/androidTest/java/com/agoitdev/spenvo/data/auth/SesionPreferencesTest.kt` (instrumented — DataStore needs a real `Context`; follows this module's existing `androidTest` convention already used for Room migration tests)

- [ ] **Step 1: Write the failing test**

`core/data/src/androidTest/java/com/agoitdev/spenvo/data/auth/SesionPreferencesTest.kt`:

```kotlin
package com.agoitdev.spenvo.data.auth

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SesionPreferencesTest {

    private fun crearPreferences(): SesionPreferences {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(SupervisorJob()),
        ) { context.preferencesDataStoreFile("sesion_test_${System.nanoTime()}") }
        return SesionPreferences(dataStore)
    }

    @Test
    fun defaultEsFalse() = runBlocking {
        val preferences = crearPreferences()

        assertFalse(preferences.sesionCerradaExplicitamente.first())
    }

    @Test
    fun marcarLogoutPonePendienteEnTrue() = runBlocking {
        val preferences = crearPreferences()

        preferences.marcarLogout()

        assertTrue(preferences.sesionCerradaExplicitamente.first())
    }

    @Test
    fun limpiarLogoutVuelveAFalse() = runBlocking {
        val preferences = crearPreferences()
        preferences.marcarLogout()

        preferences.limpiarLogout()

        assertFalse(preferences.sesionCerradaExplicitamente.first())
    }
}
```

- [ ] **Step 2: Run to confirm it fails**

Run: `./gradlew :core:data:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.agoitdev.spenvo.data.auth.SesionPreferencesTest`
Expected: FAIL — `Unresolved reference: SesionPreferences` (requires an emulator/device — see `AGENTS.md`'s "Android SDK + Pixel_4 AVD" instructions for launching one if not already running).

- [ ] **Step 3: Implement `SesionPreferences`**

`core/data/src/main/java/com/agoitdev/spenvo/data/auth/SesionPreferences.kt`:

```kotlin
package com.agoitdev.spenvo.data.auth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.sesionDataStore: DataStore<Preferences> by preferencesDataStore(name = "sesion")

@Singleton
class SesionPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(context.sesionDataStore)

    val sesionCerradaExplicitamente: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_LOGOUT_EXPLICITO] ?: false
    }

    suspend fun marcarLogout() {
        dataStore.edit { it[KEY_LOGOUT_EXPLICITO] = true }
    }

    suspend fun limpiarLogout() {
        dataStore.edit { it[KEY_LOGOUT_EXPLICITO] = false }
    }

    private companion object {
        val KEY_LOGOUT_EXPLICITO = booleanPreferencesKey("sesion_cerrada_explicitamente")
    }
}
```

Note: two `@Inject` constructors (one taking a raw `DataStore<Preferences>` for the test above, one taking `@ApplicationContext Context` for Hilt) is intentionally the same shape as this class needs to be both directly testable and Hilt-injectable without a separate DI-module-provided `DataStore` — Hilt picks the `@Inject`-annotated constructor; having two `@Inject` constructors is invalid Kotlin/Hilt (only one constructor may carry `@Inject`). **Correction before implementing:** use a single `@Inject constructor(@ApplicationContext context: Context)` and have the test construct `SesionPreferences` via a small `internal` secondary constructor instead — see the corrected version below; use this one, not the one above:

```kotlin
package com.agoitdev.spenvo.data.auth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.sesionDataStore: DataStore<Preferences> by preferencesDataStore(name = "sesion")

@Singleton
class SesionPreferences internal constructor(
    private val dataStore: DataStore<Preferences>,
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(context.sesionDataStore)

    val sesionCerradaExplicitamente: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_LOGOUT_EXPLICITO] ?: false
    }

    suspend fun marcarLogout() {
        dataStore.edit { it[KEY_LOGOUT_EXPLICITO] = true }
    }

    suspend fun limpiarLogout() {
        dataStore.edit { it[KEY_LOGOUT_EXPLICITO] = false }
    }

    private companion object {
        val KEY_LOGOUT_EXPLICITO = booleanPreferencesKey("sesion_cerrada_explicitamente")
    }
}
```

(`internal constructor` on the primary + one `@Inject`-annotated secondary constructor is valid: Hilt only ever sees the `@Inject` one; the test in Step 1 above calls `SesionPreferences(dataStore)` which resolves to the `internal` primary constructor since the test lives in the same Gradle module, `core:data`.)

- [ ] **Step 4: Run to confirm it passes**

Run: `./gradlew :core:data:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.agoitdev.spenvo.data.auth.SesionPreferencesTest`
Expected: PASS (all 3 cases)

- [ ] **Step 5: Full module build to confirm Tasks 1/3/4 now compile together**

Run: `./gradlew :core:data:assembleDebug :core:domain:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit Tasks 1, 3, and 4 together**

```bash
git add core/domain/src/main/java/com/agoitdev/spenvo/domain/repository/AuthRepository.kt \
        core/domain/src/main/java/com/agoitdev/spenvo/domain/usecase/IniciarSesionConEmailUseCase.kt \
        core/domain/src/main/java/com/agoitdev/spenvo/domain/usecase/EnviarRecuperacionPasswordUseCase.kt \
        core/domain/src/test/java/com/agoitdev/spenvo/domain/usecase/IniciarSesionConEmailUseCaseTest.kt \
        core/domain/src/test/java/com/agoitdev/spenvo/domain/usecase/EnviarRecuperacionPasswordUseCaseTest.kt \
        core/data/src/main/java/com/agoitdev/spenvo/data/auth/FirebaseAuthRepository.kt \
        core/data/src/main/java/com/agoitdev/spenvo/data/auth/AuthModule.kt \
        core/data/src/main/java/com/agoitdev/spenvo/data/auth/SesionPreferences.kt \
        core/data/src/androidTest/java/com/agoitdev/spenvo/data/auth/SesionPreferencesTest.kt
git commit -m "feat(auth): real email sign-in, password recovery, and persisted logout flag"
```

---

### Task 5: `SesionGateViewModel` in `:app`

**Files:**
- Create: `app/src/main/java/com/agoitdev/spenvo/SesionGateViewModel.kt`
- Create: `app/src/test/java/com/agoitdev/spenvo/SesionGateViewModelTest.kt`

This is the first ViewModel to live in `:app` — confirmed no existing DI/Hilt ViewModel pattern there yet (`app/build.gradle.kts` already has `libs.hilt.android`/`hilt.compiler`/`hilt.navigation.compose`, so `@HiltViewModel` + `hiltViewModel()` work with zero extra wiring).

- [ ] **Step 1: Write the failing test — all 3 states**

`app/src/test/java/com/agoitdev/spenvo/SesionGateViewModelTest.kt`:

```kotlin
package com.agoitdev.spenvo

import com.agoitdev.spenvo.data.auth.SesionPreferences
import com.agoitdev.spenvo.domain.model.Sesion
import com.agoitdev.spenvo.domain.repository.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SesionGateViewModelTest {

    private val authRepository = FakeAuthRepositoryGate()
    private val sesionPreferences = FakeSesionPreferencesGate()

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun crearViewModel() = SesionGateViewModel(authRepository, sesionPreferences)

    @Test
    fun `uid nulo y flag false dispara login anonimo y muestra MostrarApp`() = runTest {
        authRepository.sesionFlow.value = Sesion.Anonima
        sesionPreferences.flagFlow.value = false
        val viewModel = crearViewModel()
        val job = launch { viewModel.estado.collect {} }
        advanceUntilIdle()

        assertTrue(authRepository.anonimaLlamada)
        assertEquals(EstadoGate.MostrarApp, viewModel.estado.value)
        job.cancel()
    }

    @Test
    fun `uid nulo y flag true muestra MostrarGate sin recrear anonimo`() = runTest {
        authRepository.sesionFlow.value = Sesion.Anonima
        sesionPreferences.flagFlow.value = true
        val viewModel = crearViewModel()
        val job = launch { viewModel.estado.collect {} }
        advanceUntilIdle()

        assertEquals(false, authRepository.anonimaLlamada)
        assertEquals(EstadoGate.MostrarGate, viewModel.estado.value)
        job.cancel()
    }

    @Test
    fun `uid presente muestra MostrarApp sin importar el flag`() = runTest {
        authRepository.sesionFlow.value = Sesion(uid = "user-1", esAnonima = false)
        sesionPreferences.flagFlow.value = true
        val viewModel = crearViewModel()
        val job = launch { viewModel.estado.collect {} }
        advanceUntilIdle()

        assertEquals(false, authRepository.anonimaLlamada)
        assertEquals(EstadoGate.MostrarApp, viewModel.estado.value)
        job.cancel()
    }
}

private class FakeAuthRepositoryGate : AuthRepository {
    val sesionFlow = MutableStateFlow(Sesion.Anonima)
    var anonimaLlamada = false

    override fun observeSesion(): Flow<Sesion> = sesionFlow
    override suspend fun iniciarSesionAnonima() {
        anonimaLlamada = true
        sesionFlow.value = Sesion(uid = "anon-1", esAnonima = true)
    }
    override suspend fun iniciarSesionConEmail(email: String, password: String) = Unit
    override suspend fun enviarRecuperacionPassword(email: String) = Unit
    override suspend fun vincularEmail(email: String, password: String, nombre: String) = Unit
    override suspend fun actualizarPerfil(nombre: String?, photoUrl: String?) = Unit
    override suspend fun cerrarSesion() = Unit
}

private class FakeSesionPreferencesGate {
    val flagFlow = MutableStateFlow(false)
}
```

Note: `FakeSesionPreferencesGate` above is a plain fake, not a subtype of `SesionPreferences` (that class is `internal constructor`-gated and concrete, not an interface) — this only works if `SesionGateViewModel` depends on a narrow-enough shape. **Resolve during implementation:** give `SesionGateViewModel` a constructor parameter typed as `Flow<Boolean>` (the flag) rather than the whole `SesionPreferences` class, sourced at the call site as `sesionPreferences.sesionCerradaExplicitamente` — this keeps the ViewModel trivially fake-able without needing an interface for `SesionPreferences`. Adjust the test above to pass `sesionPreferences.flagFlow` directly as that `Flow<Boolean>` parameter instead of a `FakeSesionPreferencesGate` wrapper; the `FakeSesionPreferencesGate` class becomes unnecessary — delete it and pass `MutableStateFlow(false)` directly in each test.

- [ ] **Step 2: Run to confirm it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "*.SesionGateViewModelTest"`
Expected: FAIL — `Unresolved reference: SesionGateViewModel` / `EstadoGate`

- [ ] **Step 3: Implement `SesionGateViewModel`**

`app/src/main/java/com/agoitdev/spenvo/SesionGateViewModel.kt`. Note: the constructor takes the flag as a plain `Flow<Boolean>` (qualified with `@LogoutExplicitoFlag`, a Hilt `@Qualifier` created in Task 6's `AppModule`), not the whole `SesionPreferences` class — this keeps `SesionGateViewModel` trivially fake-able in Step 1's unit test above without needing an instrumented DataStore/Context. `AppModule` (Task 6) supplies the binding as `sesionPreferences.sesionCerradaExplicitamente`.

```kotlin
package com.agoitdev.spenvo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agoitdev.spenvo.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class EstadoGate { Cargando, MostrarApp, MostrarGate }

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SesionGateViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    @LogoutExplicitoFlag flagLogoutExplicito: Flow<Boolean>,
) : ViewModel() {

    val estado: StateFlow<EstadoGate> = combine(
        authRepository.observeSesion(),
        flagLogoutExplicito,
    ) { sesion, flagPendiente -> sesion to flagPendiente }
        .flatMapLatest { (sesion, flagPendiente) ->
            when {
                sesion.uid != null -> flowOf(EstadoGate.MostrarApp)
                flagPendiente -> flowOf(EstadoGate.MostrarGate)
                else -> flowOf(EstadoGate.MostrarApp).also {
                    viewModelScope.launch { authRepository.iniciarSesionAnonima() }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EstadoGate.Cargando)
}
```

`@LogoutExplicitoFlag` is a new Hilt `@Qualifier` (needed because `Flow<Boolean>` alone is too generic a binding type) — create it and its provider in Task 6's `AppModule` alongside the rest of `:app`'s DI wiring, together with the actual `SesionGateViewModelTest` update matching this final signature (constructor takes `AuthRepository` + a plain `Flow<Boolean>`, exactly matching the test written in Step 1 once the "Resolve during implementation" note is applied — no code changes needed there, it already matches this signature once the unused `@Inject`/qualifier annotation is understood as a Hilt-only concern that doesn't affect direct-construction tests).

- [ ] **Step 4: Run to confirm it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "*.SesionGateViewModelTest"`
Expected: PASS (all 3 cases)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/agoitdev/spenvo/SesionGateViewModel.kt \
        app/src/test/java/com/agoitdev/spenvo/SesionGateViewModelTest.kt
git commit -m "feat(app): add SesionGateViewModel driving the post-logout gate"
```

---

### Task 6: Wire the gate into `SpenvoApp`, add `@LogoutExplicitoFlag` DI, remove `PlanesViewModel`'s retry loop

**Files:**
- Create: `app/src/main/java/com/agoitdev/spenvo/AppModule.kt`
- Modify: `app/src/main/java/com/agoitdev/spenvo/MainActivity.kt`
- Modify: `feature/planes/src/main/java/com/agoitdev/spenvo/planes/PlanesViewModel.kt`
- Modify: `feature/planes/src/test/java/com/agoitdev/spenvo/planes/PlanesViewModelTest.kt` (remove/update any assertion relying on the removed retry loop, if present)

This task is UI/navigation wiring rather than a pure TDD unit — `SesionGateViewModelTest` (Task 5) already covers the state logic. The Compose-level check is a manual/instrumented verification step (Step 5 below), consistent with how `SpenvoApp`/`MainActivity.kt` has no existing Compose test coverage of its own today (verified: no `MainActivityTest`/`SpenvoAppTest` file exists in the repo).

- [ ] **Step 1: Create the `@LogoutExplicitoFlag` qualifier + DI provider**

`app/src/main/java/com/agoitdev/spenvo/AppModule.kt`:

```kotlin
package com.agoitdev.spenvo

import com.agoitdev.spenvo.data.auth.SesionPreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class LogoutExplicitoFlag

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @LogoutExplicitoFlag
    fun provideLogoutExplicitoFlag(sesionPreferences: SesionPreferences): Flow<Boolean> =
        sesionPreferences.sesionCerradaExplicitamente
}
```

- [ ] **Step 2: Remove the auth-bootstrap `init` block from `PlanesViewModel`, keep the other 3 blocks intact**

`feature/planes/src/main/java/com/agoitdev/spenvo/planes/PlanesViewModel.kt` — replace only the `init { ... }` block (current lines 100-125) with:

```kotlin
    init {
        viewModelScope.launch {
            sesion.filter { it.uid != null }.flatMapLatest { s ->
                val uid = s.uid
                if (uid == null) flowOf(Unit) else sincronizador.sincronizar(uid)
            }
                .catch { /* best-effort sync: un error de red/rules no debe tumbar la app */ }
                .collect { }
        }
        viewModelScope.launch {
            val uid = sesion.filter { it.uid != null }.first().uid ?: return@launch
            // Best-effort bootstrap: ensures the Usuario doc (nombreUsuario) exists for this uid.
            runCatching { asegurarUsuario.paraSesionAnonima(uid) }
        }
        viewModelScope.launch {
            val uid = sesion.filter { it.uid != null }.first().uid ?: return@launch
            runCatching { sembrarPlanEjemplo(uid) }
        }
    }
```

Also remove the now-unused `RETRY_DELAY_MS` constant and the `delay` import (both only served the removed loop), and drop the `authRepository: AuthRepository` constructor parameter if nothing else in the class still reads it directly — **verify before deleting**: `sesion` (line 53-54) still calls `authRepository.observeSesion()`, so the parameter stays; only the retry-loop's direct `authRepository.iniciarSesionAnonima()` call and the `RETRY_DELAY_MS`/`delay` import go away.

- [ ] **Step 3: Check `PlanesViewModelTest` for any assertion on the removed retry behavior**

Read `feature/planes/src/test/java/com/agoitdev/spenvo/planes/PlanesViewModelTest.kt` for a test asserting `iniciarSesionAnonima` gets called from `PlanesViewModel` itself (e.g. a fake tracking a call count on construction). If one exists, delete it — that responsibility now belongs to `SesionGateViewModel` (already covered by Task 5's tests), and this exact removal is the point of this task, not a regression. If no such test exists (the loop was previously "no covering tests found" per codegraph), no change needed here.

- [ ] **Step 4: Run the planes test suite**

Run: `./gradlew :feature:planes:testDebugUnitTest`
Expected: PASS (all remaining `PlanesViewModelTest` cases green — no test should have depended on the removed loop's side effect, since `sesion.filter { it.uid != null }` in the other 3 init blocks means those tests already had to seed a non-null uid on the fake to get anything to happen, independent of the retry loop).

- [ ] **Step 5: Wire `SpenvoApp` to branch on `SesionGateViewModel`'s state**

`app/src/main/java/com/agoitdev/spenvo/MainActivity.kt` — modify `SpenvoApp`:

```kotlin
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun SpenvoApp(modifier: Modifier = Modifier, gateViewModel: SesionGateViewModel = hiltViewModel()) {
    val estadoGate by gateViewModel.estado.collectAsStateWithLifecycle()
    val backStack = rememberNavBackStack(PlanesRoute)

    LaunchedEffect(estadoGate) {
        when (estadoGate) {
            EstadoGate.MostrarGate -> {
                backStack.clear()
                backStack.add(CuentaRoute)
            }
            EstadoGate.MostrarApp -> {
                if (backStack.singleOrNull() == CuentaRoute) {
                    backStack.clear()
                    backStack.add(PlanesRoute)
                }
            }
            EstadoGate.Cargando -> Unit
        }
    }

    Surface(modifier = modifier.fillMaxSize()) {
        NavDisplay(
            backStack = backStack,
            onBack = { if (backStack.size > 1) backStack.removeLastOrNull() },
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            sceneStrategies = listOf(rememberListDetailSceneStrategy<NavKey>()),
            entryProvider = entryProvider {
                entry<PlanesRoute> {
                    PlanesScreen(
                        onCrearCuenta = { backStack.add(CuentaRoute) },
                        onAbrirPlan = { planId -> backStack.add(PlanRoute(planId)) },
                    )
                }
                entry<PlanRoute> { route ->
                    val movimientosViewModel: MovimientosViewModel = hiltViewModel()
                    PlanScaffold(
                        contenidoHome = {
                            HomeScreen(planId = route.planId, movimientosViewModel = movimientosViewModel)
                        },
                        contenidoMovimientos = {
                            MovimientosScreen(planId = route.planId, viewModel = movimientosViewModel)
                        },
                        contenidoCategorias = { CategoriasScreen(planId = route.planId) },
                        contenidoMiembros = { MiembrosScreen(planId = route.planId) },
                    )
                }
                entry<CuentaRoute> {
                    CuentaScreen(
                        tabInicial = if (estadoGate == EstadoGate.MostrarGate) AuthTab.INICIAR_SESION else AuthTab.CREAR_CUENTA,
                        onRegistroCompletado = { if (backStack.size > 1) backStack.removeLastOrNull() },
                    )
                }
            },
        )
    }
}
```

Import additions needed at the top of `MainActivity.kt`: `androidx.compose.runtime.LaunchedEffect`, `androidx.compose.runtime.getValue`, `androidx.lifecycle.compose.collectAsStateWithLifecycle`, `com.agoitdev.spenvo.cuenta.AuthTab` (created in Task 7).

`onBack` changes from unconditional `removeLastOrNull()` to a guarded `if (backStack.size > 1)` — this is the mechanism that makes `MostrarGate`'s `CuentaRoute` un-back-out-of-able when it's the sole backstack entry (matches the design doc's "no back path to `PlanesRoute`" requirement), while every other route keeps today's exact back behavior (backstack always has ≥ 2 entries in every other reachable state).

- [ ] **Step 6: Manual verification (documented exception — no automated Compose test for this step)**

Per `AGENTS.md`'s "documented exceptions only" TDD clause: `SpenvoApp`'s gate-branching logic is exercised end-to-end by `SesionGateViewModelTest` (state logic) and will be covered by Task 8's `CuentaScreenTest` additions (the `tabInicial` behavior) and Task 9's explicit anti-recreation regression test — but the actual `LaunchedEffect`/backstack-mutation wiring inside `SpenvoApp` itself has no existing Compose test harness to extend (no `MainActivityTest` exists), and standing one up (Hilt+Compose integration test for the app's root, per the home-bottom-nav design doc's identical precedent) is disproportionate to this front's scope. Verify manually: run the debug build, log in with a test account, log out, confirm the gate screen appears and the device back button doesn't leave it; force-stop and relaunch while on the gate screen, confirm it reappears instead of an anonymous session.

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/agoitdev/spenvo/AppModule.kt \
        app/src/main/java/com/agoitdev/spenvo/MainActivity.kt \
        feature/planes/src/main/java/com/agoitdev/spenvo/planes/PlanesViewModel.kt \
        feature/planes/src/test/java/com/agoitdev/spenvo/planes/PlanesViewModelTest.kt
git commit -m "feat(app): drive root navigation from SesionGateViewModel, drop PlanesViewModel auth retry"
```

---

### Task 7: `CuentaViewModel` — `iniciarSesion`, `recuperarPassword`, error mapping

**Files:**
- Modify: `feature/cuenta/src/main/java/com/agoitdev/spenvo/cuenta/CuentaViewModel.kt`
- Modify: `feature/cuenta/src/test/java/com/agoitdev/spenvo/cuenta/CuentaViewModelTest.kt`
- Create: `feature/cuenta/src/main/java/com/agoitdev/spenvo/cuenta/MapearErrorAuth.kt`
- Modify: `feature/cuenta/src/main/res/values/strings.xml`
- Modify: `feature/cuenta/src/main/res/values-en/strings.xml`

- [ ] **Step 1: Add the new strings (both locales)**

`feature/cuenta/src/main/res/values/strings.xml` — add before `</resources>`:

```xml
    <string name="account_login_tab">Iniciar sesión</string>
    <string name="account_registration_tab">Crear cuenta</string>
    <string name="account_login_email">Correo</string>
    <string name="account_login_password">Contraseña</string>
    <string name="account_login_submit">Iniciar sesión</string>
    <string name="account_login_forgot_password">¿Olvidaste tu contraseña?</string>
    <string name="account_recovery_title">Recuperar contraseña</string>
    <string name="account_recovery_email_label">Correo</string>
    <string name="account_recovery_submit">Enviar</string>
    <string name="account_recovery_cancel">Cancelar</string>
    <string name="account_recovery_success">Si el correo existe, te enviamos instrucciones para recuperar tu contraseña.</string>
    <string name="account_error_credenciales_invalidas">Email o contraseña incorrectos</string>
    <string name="account_error_sin_red">Revisá tu conexión e intentá de nuevo</string>
    <string name="account_error_generico">Ocurrió un error. Intentá de nuevo</string>
```

`feature/cuenta/src/main/res/values-en/strings.xml` — add before `</resources>`:

```xml
    <string name="account_login_tab">Log in</string>
    <string name="account_registration_tab">Create account</string>
    <string name="account_login_email">Email</string>
    <string name="account_login_password">Password</string>
    <string name="account_login_submit">Log in</string>
    <string name="account_login_forgot_password">Forgot your password?</string>
    <string name="account_recovery_title">Recover password</string>
    <string name="account_recovery_email_label">Email</string>
    <string name="account_recovery_submit">Send</string>
    <string name="account_recovery_cancel">Cancel</string>
    <string name="account_recovery_success">If that email exists, we sent instructions to recover your password.</string>
    <string name="account_error_credenciales_invalidas">Incorrect email or password</string>
    <string name="account_error_sin_red">Check your connection and try again</string>
    <string name="account_error_generico">Something went wrong. Try again</string>
```

- [ ] **Step 2: Write the failing tests for `iniciarSesion` and `recuperarPassword`**

Add to `feature/cuenta/src/test/java/com/agoitdev/spenvo/cuenta/CuentaViewModelTest.kt`, inside the `CuentaViewModelTest` class (after the existing `registrar exitoso...` test):

```kotlin
    @Test
    fun `iniciarSesion exitoso marca el estado como completado`() = runTest {
        authRepository.sesionFlow.value = Sesion.Anonima
        val viewModel = crearViewModel()
        val job = launch { viewModel.sesion.collect {} }
        advanceUntilIdle()

        viewModel.iniciarSesion(email = "ana@example.com", password = "secret123")
        advanceUntilIdle()

        assertEquals("ana@example.com", authRepository.ultimoEmailLogin)
        assertEquals("secret123", authRepository.ultimaPasswordLogin)
        assertTrue(viewModel.estado.value.completado)
        job.cancel()
    }

    @Test
    fun `iniciarSesion con credenciales invalidas expone el mismo mensaje que usuario inexistente`() = runTest {
        authRepository.sesionFlow.value = Sesion.Anonima
        authRepository.excepcionLogin = FirebaseAuthInvalidCredentialsException("ERROR_WRONG_PASSWORD", "wrong password")
        val viewModel = crearViewModel()
        val job = launch { viewModel.sesion.collect {} }
        advanceUntilIdle()

        viewModel.iniciarSesion(email = "ana@example.com", password = "wrong")
        advanceUntilIdle()

        assertEquals(mensajeCredencialesInvalidas, viewModel.estado.value.error)
        job.cancel()
    }

    @Test
    fun `recuperarPassword siempre marca exito visible sin importar si el email existe`() = runTest {
        val viewModel = crearViewModel()
        val job = launch { viewModel.sesion.collect {} }
        advanceUntilIdle()

        viewModel.recuperarPassword(email = "quien-sea@example.com")
        advanceUntilIdle()

        assertEquals("quien-sea@example.com", authRepository.ultimoEmailRecovery)
        assertTrue(viewModel.recoveryEstado.value.exito)
        job.cancel()
    }
```

Add the required imports to the test file's import block: `com.google.firebase.auth.FirebaseAuthInvalidCredentialsException`, `org.junit.Assert.assertTrue` (already present), `org.junit.Assert.assertEquals` (already present). Also add a helper constant near the top of the test class body: `private val mensajeCredencialesInvalidas = "..."` — resolved at implementation time from the actual `R.string.account_error_credenciales_invalidas` value once `MapearErrorAuth` exists (Step 4); use `androidx.test.core.app.ApplicationProvider` is not available in this pure-JUnit `src/test` module (no Robolectric here per this file's existing imports), so assert against the raw string literal `"Email o contraseña incorrectos"` matching Step 1's `values/strings.xml` entry directly instead of resolving the resource.

Update `FakeAuthRepositorioCuenta` (bottom of the same file) to track the new calls:

```kotlin
private class FakeAuthRepositorioCuenta : AuthRepository {
    val sesionFlow = MutableStateFlow(Sesion.Anonima)
    var ultimoPhotoUrlActualizado: String? = null
    var cerrarSesionLlamado = false
    var ultimoEmailLogin: String? = null
    var ultimaPasswordLogin: String? = null
    var excepcionLogin: Throwable? = null
    var ultimoEmailRecovery: String? = null

    override fun observeSesion(): Flow<Sesion> = sesionFlow
    override suspend fun iniciarSesionAnonima() = Unit
    override suspend fun iniciarSesionConEmail(email: String, password: String) {
        ultimoEmailLogin = email
        ultimaPasswordLogin = password
        excepcionLogin?.let { throw it }
        sesionFlow.value = Sesion(uid = "user-1", esAnonima = false, email = email)
    }
    override suspend fun enviarRecuperacionPassword(email: String) {
        ultimoEmailRecovery = email
    }
    override suspend fun vincularEmail(email: String, password: String, nombre: String) = Unit
    override suspend fun actualizarPerfil(nombre: String?, photoUrl: String?) {
        ultimoPhotoUrlActualizado = photoUrl
    }
    override suspend fun cerrarSesion() {
        cerrarSesionLlamado = true
    }
}
```

- [ ] **Step 3: Run to confirm the new tests fail**

Run: `./gradlew :feature:cuenta:testDebugUnitTest --tests "*.CuentaViewModelTest"`
Expected: FAIL — `Unresolved reference: iniciarSesion` / `recuperarPassword` / `recoveryEstado`

- [ ] **Step 4: Implement `mapearErrorAuth`**

`feature/cuenta/src/main/java/com/agoitdev/spenvo/cuenta/MapearErrorAuth.kt`:

```kotlin
package com.agoitdev.spenvo.cuenta

import androidx.annotation.StringRes
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException

@StringRes
fun mapearErrorAuth(error: Throwable): Int = when (error) {
    is FirebaseAuthInvalidUserException,
    is FirebaseAuthInvalidCredentialsException,
    -> R.string.account_error_credenciales_invalidas
    is FirebaseNetworkException -> R.string.account_error_sin_red
    else -> R.string.account_error_generico
}
```

- [ ] **Step 5: Implement `iniciarSesion` and `recuperarPassword` in `CuentaViewModel`**

Add to `feature/cuenta/src/main/java/com/agoitdev/spenvo/cuenta/CuentaViewModel.kt`, as new constructor parameters and methods:

Constructor gains two new use cases:

```kotlin
class CuentaViewModel @Inject constructor(
    private val vincularCredencial: VincularCredencialUseCase,
    private val iniciarSesionConEmail: IniciarSesionConEmailUseCase,
    private val enviarRecuperacionPassword: EnviarRecuperacionPasswordUseCase,
    private val authRepository: AuthRepository,
    private val usuarioRepository: UsuarioRepository,
    private val subirAvatarUseCase: SubirAvatarUseCase,
    private val asegurarUsuario: AsegurarUsuarioUseCase,
    private val renombrarUsuario: RenombrarUsuarioUseCase,
) : ViewModel() {
```

Add import `com.agoitdev.spenvo.domain.usecase.IniciarSesionConEmailUseCase` and `com.agoitdev.spenvo.domain.usecase.EnviarRecuperacionPasswordUseCase`.

New state + methods (add after `registrar`/`consumirError`):

```kotlin
    private val _recoveryEstado = MutableStateFlow(RecoveryEstado())
    val recoveryEstado: StateFlow<RecoveryEstado> = _recoveryEstado.asStateFlow()

    fun iniciarSesion(email: String, password: String) {
        _estado.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            runCatching { iniciarSesionConEmail(email, password) }
                .onSuccess { _estado.value = RegistroEstado(completado = true) }
                .onFailure { error ->
                    _estado.value = RegistroEstado(error = errorMensaje(error))
                }
        }
    }

    fun recuperarPassword(email: String) {
        viewModelScope.launch {
            runCatching { enviarRecuperacionPassword(email) }
            // Same visible outcome whether the email exists or not — anti-enumeration.
            _recoveryEstado.value = RecoveryEstado(exito = true)
        }
    }

    fun consumirRecoveryEstado() {
        _recoveryEstado.value = RecoveryEstado()
    }
```

Note: `RegistroEstado.error` is a `String?` today (raw message), but `mapearErrorAuth` returns a `@StringRes Int`. Add a small resolution helper — since `ViewModel` can't call `stringResource()`, follow this file's own existing precedent (`PerfilEstado.nombreUsuarioError: Int?`, resolved by the Composable) instead of trying to resolve a string here. **Correction to the snippet above:** `RegistroEstado` needs a new `@StringRes errorRes: Int?` field alongside (or instead of) `error: String?` for the auth-error path specifically, since `registrar`'s existing `error.message` usage is a different, pre-existing pattern this task shouldn't disturb. Add:

```kotlin
    fun iniciarSesion(email: String, password: String) {
        _estado.update { it.copy(cargando = true, error = null, errorRes = null) }
        viewModelScope.launch {
            runCatching { iniciarSesionConEmail(email, password) }
                .onSuccess { _estado.value = RegistroEstado(completado = true) }
                .onFailure { error ->
                    _estado.value = RegistroEstado(errorRes = mapearErrorAuth(error))
                }
        }
    }
```

And extend `RegistroEstado`:

```kotlin
data class RegistroEstado(
    val cargando: Boolean = false,
    val completado: Boolean = false,
    val error: String? = null,
    @param:StringRes val errorRes: Int? = null,
)

data class RecoveryEstado(
    val exito: Boolean = false,
)
```

`CuentaScreen` (Task 8) resolves `errorRes` via `stringResource()` the same way it already resolves `PerfilEstado.nombreUsuarioError`.

Also update the existing `consumirError()` (currently `_estado.update { it.copy(error = null) }`, `CuentaViewModel.kt:76-78`) to also clear the new field, so it resets both error channels:

```kotlin
    fun consumirError() {
        _estado.update { it.copy(error = null, errorRes = null) }
    }
```

- [ ] **Step 6: Run to confirm the tests pass**

Run: `./gradlew :feature:cuenta:testDebugUnitTest --tests "*.CuentaViewModelTest"`
Expected: PASS (all cases, including the pre-existing ones — verify no regression)

- [ ] **Step 7: Update `CuentaScreenTest`'s `FakeAuthRepositorioPantalla` and `crearViewModel` call site to match the new constructor**

`feature/cuenta/src/test/java/com/agoitdev/spenvo/cuenta/CuentaScreenTest.kt` — update `crearViewModel` to pass the two new use cases, and add the same 2 overrides + tracking fields to `FakeAuthRepositorioPantalla` as done in Step 2 above for `FakeAuthRepositorioCuenta` (mirror exactly — both fakes need the same shape since both back a `CuentaViewModel`).

Run: `./gradlew :feature:cuenta:testDebugUnitTest --tests "*.CuentaScreenTest"`
Expected: FAIL at this point (compile error until Task 8 adds `tabInicial`/`AuthTab` — expected, resolved next task) — if it happens to compile without `tabInicial` changes, it should PASS unchanged, since Task 8's screen changes aren't in yet.

- [ ] **Step 8: Commit**

```bash
git add feature/cuenta/src/main/res/values/strings.xml \
        feature/cuenta/src/main/res/values-en/strings.xml \
        feature/cuenta/src/main/java/com/agoitdev/spenvo/cuenta/MapearErrorAuth.kt \
        feature/cuenta/src/main/java/com/agoitdev/spenvo/cuenta/CuentaViewModel.kt \
        feature/cuenta/src/test/java/com/agoitdev/spenvo/cuenta/CuentaViewModelTest.kt \
        feature/cuenta/src/test/java/com/agoitdev/spenvo/cuenta/CuentaScreenTest.kt
git commit -m "feat(cuenta): CuentaViewModel.iniciarSesion/recuperarPassword with anti-enumeration error mapping"
```

---

### Task 8: `CuentaScreen` — `AuthTab` toggle, login form, recovery dialog

**Files:**
- Modify: `feature/cuenta/src/main/java/com/agoitdev/spenvo/cuenta/CuentaScreen.kt`
- Modify: `feature/cuenta/src/test/java/com/agoitdev/spenvo/cuenta/CuentaScreenTest.kt`

- [ ] **Step 1: Write the failing Compose tests**

Add to `feature/cuenta/src/test/java/com/agoitdev/spenvo/cuenta/CuentaScreenTest.kt` (after the existing "sesion anonima muestra el formulario de registro" test):

```kotlin
    @Test
    fun `sesion anonima con tabInicial CREAR_CUENTA muestra el formulario de registro por defecto`() {
        val viewModel = crearViewModel(FakeAuthRepositorioPantalla(Sesion.Anonima))

        composeTestRule.setContent {
            CuentaScreen(onRegistroCompletado = {}, viewModel = viewModel, tabInicial = AuthTab.CREAR_CUENTA)
        }

        composeTestRule.onNodeWithText("Tus datos de invitado", substring = true).assertIsDisplayed()
    }

    @Test
    fun `sesion anonima con tabInicial INICIAR_SESION muestra el formulario de login por defecto`() {
        val viewModel = crearViewModel(FakeAuthRepositorioPantalla(Sesion.Anonima))

        composeTestRule.setContent {
            CuentaScreen(onRegistroCompletado = {}, viewModel = viewModel, tabInicial = AuthTab.INICIAR_SESION)
        }

        composeTestRule.onNodeWithText("¿Olvidaste tu contraseña?").assertIsDisplayed()
        composeTestRule.onNodeWithText("Tus datos de invitado", substring = true).assertDoesNotExist()
    }

    @Test
    fun `tocar iniciar sesion invoca iniciarSesion del viewmodel con email y password`() {
        val authRepository = FakeAuthRepositorioPantalla(Sesion.Anonima)
        val viewModel = crearViewModel(authRepository)

        composeTestRule.setContent {
            CuentaScreen(onRegistroCompletado = {}, viewModel = viewModel, tabInicial = AuthTab.INICIAR_SESION)
        }

        composeTestRule.onNodeWithText("Correo").performTextReplacement("ana@example.com")
        composeTestRule.onNodeWithText("Contraseña").performTextReplacement("secret123")
        composeTestRule.onNodeWithText("Iniciar sesión").performClick()
        composeTestRule.waitForIdle()

        assertEquals("ana@example.com", authRepository.ultimoEmailLogin)
        assertEquals("secret123", authRepository.ultimaPasswordLogin)
    }

    @Test
    fun `abrir y confirmar el dialogo de recuperacion invoca recuperarPassword`() {
        val authRepository = FakeAuthRepositorioPantalla(Sesion.Anonima)
        val viewModel = crearViewModel(authRepository)

        composeTestRule.setContent {
            CuentaScreen(onRegistroCompletado = {}, viewModel = viewModel, tabInicial = AuthTab.INICIAR_SESION)
        }

        composeTestRule.onNodeWithText("¿Olvidaste tu contraseña?").performClick()
        composeTestRule.onNodeWithText("Correo").performTextReplacement("ana@example.com")
        composeTestRule.onNodeWithText("Enviar").performClick()
        composeTestRule.waitForIdle()

        assertEquals("ana@example.com", authRepository.ultimoEmailRecovery)
    }
```

- [ ] **Step 2: Run to confirm they fail**

Run: `./gradlew :feature:cuenta:testDebugUnitTest --tests "*.CuentaScreenTest"`
Expected: FAIL — `Unresolved reference: AuthTab` / no `tabInicial` parameter on `CuentaScreen`

- [ ] **Step 3: Implement `AuthTab` and the new `AuthForm` in `CuentaScreen.kt`**

Replace `CuentaScreen`'s signature and body, and replace the fixed `RegistroForm` call with the new toggle. Full replacement of the relevant section of `feature/cuenta/src/main/java/com/agoitdev/spenvo/cuenta/CuentaScreen.kt` (everything from `@Composable fun CuentaScreen` through the end of `RegistroForm`, i.e. lines 50–283 of the current file):

```kotlin
enum class AuthTab { CREAR_CUENTA, INICIAR_SESION }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CuentaScreen(
    onRegistroCompletado: () -> Unit,
    viewModel: CuentaViewModel = hiltViewModel(),
    tabInicial: AuthTab = AuthTab.CREAR_CUENTA,
    modifier: Modifier = Modifier,
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val sesion by viewModel.sesion.collectAsStateWithLifecycle()
    val perfilEstado by viewModel.perfilEstado.collectAsStateWithLifecycle()
    val recoveryEstado by viewModel.recoveryEstado.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var tabSeleccionado by rememberSaveable { mutableStateOf(tabInicial) }
    var mostrarRecoveryDialog by rememberSaveable { mutableStateOf(false) }

    CuentaSideEffects(estado, perfilEstado, snackbarHostState, onRegistroCompletado, viewModel)
    val seleccionarImagen = rememberSeleccionarImagenLauncher(onImagenSeleccionada = viewModel::subirAvatar)

    val mensajeRecoveryExito = stringResource(R.string.account_recovery_success)
    LaunchedEffect(recoveryEstado.exito) {
        if (recoveryEstado.exito) {
            mostrarRecoveryDialog = false
            snackbarHostState.showSnackbar(mensajeRecoveryExito)
            viewModel.consumirRecoveryEstado()
        }
    }

    if (mostrarRecoveryDialog) {
        RecoveryDialog(
            onConfirmar = viewModel::recuperarPassword,
            onCancelar = { mostrarRecoveryDialog = false },
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(tituloDe(sesion))) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(horizontal = 24.dp)
        if (sesion.estaAutenticada) {
            PerfilContenido(
                sesion = sesion,
                perfilEstado = perfilEstado,
                onEditarAvatar = { seleccionarImagen() },
                onLogout = viewModel::logout,
                onEditarNombreUsuario = viewModel::editarNombreUsuario,
                modifier = contentModifier,
            )
        } else {
            Column(modifier = contentModifier) {
                TabRow(selectedTabIndex = tabSeleccionado.ordinal) {
                    Tab(
                        selected = tabSeleccionado == AuthTab.CREAR_CUENTA,
                        onClick = { tabSeleccionado = AuthTab.CREAR_CUENTA },
                        text = { Text(stringResource(R.string.account_registration_tab)) },
                    )
                    Tab(
                        selected = tabSeleccionado == AuthTab.INICIAR_SESION,
                        onClick = { tabSeleccionado = AuthTab.INICIAR_SESION },
                        text = { Text(stringResource(R.string.account_login_tab)) },
                    )
                }
                when (tabSeleccionado) {
                    AuthTab.CREAR_CUENTA -> RegistroForm(cargando = estado.cargando, onRegistrar = viewModel::registrar)
                    AuthTab.INICIAR_SESION -> LoginForm(
                        cargando = estado.cargando,
                        errorRes = estado.errorRes,
                        onIniciarSesion = viewModel::iniciarSesion,
                        onOlvidoPassword = { mostrarRecoveryDialog = true },
                    )
                }
            }
        }
    }
}

private fun tituloDe(sesion: Sesion): Int =
    if (sesion.estaAutenticada) R.string.account_profile_title else R.string.account_registration_title

@Composable
private fun CuentaSideEffects(
    estado: RegistroEstado,
    perfilEstado: PerfilEstado,
    snackbarHostState: SnackbarHostState,
    onRegistroCompletado: () -> Unit,
    viewModel: CuentaViewModel,
) {
    LaunchedEffect(estado.completado) {
        if (estado.completado) onRegistroCompletado()
    }

    LaunchedEffect(estado.error) {
        estado.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.consumirError()
        }
    }

    LaunchedEffect(perfilEstado.avatarError) {
        perfilEstado.avatarError?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.consumirAvatarError()
        }
    }
}

/** Reads the picked [Uri]'s bytes/content-type in the Composable, then delegates. */
@Composable
private fun rememberSeleccionarImagenLauncher(onImagenSeleccionada: (ByteArray, String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        uri?.let {
            val contentType = context.contentResolver.getType(it) ?: "image/*"
            context.contentResolver.openInputStream(it)?.use { stream ->
                onImagenSeleccionada(stream.readBytes(), contentType)
            }
        }
    }
    return {
        launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
}

@Composable
private fun RegistroForm(
    cargando: Boolean,
    onRegistrar: (String, String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var nombre by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    Column(modifier = modifier, verticalArrangement = Arrangement.Center) {
        Text(
            text = stringResource(R.string.account_registration_subtitle),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = nombre,
            onValueChange = { nombre = it },
            label = { Text(stringResource(R.string.account_registration_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text(stringResource(R.string.account_registration_email)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(stringResource(R.string.account_registration_password)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { onRegistrar(nombre, email, password) },
            enabled = !cargando && nombre.isNotBlank() && email.isNotBlank() && password.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (cargando) {
                CircularProgressIndicator(modifier = Modifier.height(20.dp))
            } else {
                Text(stringResource(R.string.account_registration_create))
            }
        }
    }
}

@Composable
private fun LoginForm(
    cargando: Boolean,
    @StringRes errorRes: Int?,
    onIniciarSesion: (String, String) -> Unit,
    onOlvidoPassword: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    Column(modifier = modifier, verticalArrangement = Arrangement.Center) {
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text(stringResource(R.string.account_login_email)) },
            isError = errorRes != null,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(stringResource(R.string.account_login_password)) },
            isError = errorRes != null,
            supportingText = errorRes?.let { { Text(stringResource(it)) } },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(onClick = onOlvidoPassword) {
            Text(stringResource(R.string.account_login_forgot_password))
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { onIniciarSesion(email, password) },
            enabled = !cargando && email.isNotBlank() && password.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (cargando) {
                CircularProgressIndicator(modifier = Modifier.height(20.dp))
            } else {
                Text(stringResource(R.string.account_login_submit))
            }
        }
    }
}

@Composable
private fun RecoveryDialog(
    onConfirmar: (String) -> Unit,
    onCancelar: () -> Unit,
) {
    var email by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text(stringResource(R.string.account_recovery_title)) },
        text = {
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text(stringResource(R.string.account_recovery_email_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirmar(email) }, enabled = email.isNotBlank()) {
                Text(stringResource(R.string.account_recovery_submit))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancelar) { Text(stringResource(R.string.account_recovery_cancel)) }
        },
    )
}
```

Notes on this replacement:
- `sesion.esAnonima` checks become `sesion.estaAutenticada` (inverted) throughout — matches the design doc's "not authenticated" condition, which must also cover the gate's transient `Sesion.Anonima` (`uid == null`) state, not just a real anonymous Firebase user. `Sesion.estaAutenticada` (`uid != null`) already draws exactly that line.
- `estado.errorRes` (login errors) is surfaced inline via `LoginForm`'s `supportingText` on the password field, not a snackbar — `CuentaSideEffects`'s error-snackbar effect stays untouched, still only handling `estado.error` (the pre-existing `registrar()` error path). Don't route `errorRes` through it too; that would double-display the same error.
- `mensajeRecoverySuccess` is resolved via `stringResource()` at `CuentaScreen`'s composable top level (above the `LaunchedEffect(recoveryEstado.exito)` that uses it) — `stringResource()` can only be called from composable context, never from inside a `LaunchedEffect`'s suspend body.
- Add `import androidx.compose.material3.AlertDialog`, `androidx.compose.material3.Tab`, `androidx.compose.material3.TabRow`, `androidx.compose.runtime.LaunchedEffect`, `androidx.compose.runtime.rememberSaveable` (if not already imported) to the top of the file.

- [ ] **Step 4: Run to confirm the new tests pass, and no existing test regressed**

Run: `./gradlew :feature:cuenta:testDebugUnitTest`
Expected: PASS — all `CuentaScreenTest` and `CuentaViewModelTest` cases green.

- [ ] **Step 5: Build verification checkpoint**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL (confirms Task 6's `AuthTab` import into `MainActivity.kt` resolves now that `AuthTab` exists)

- [ ] **Step 6: Commit**

```bash
git add feature/cuenta/src/main/java/com/agoitdev/spenvo/cuenta/CuentaScreen.kt \
        feature/cuenta/src/test/java/com/agoitdev/spenvo/cuenta/CuentaScreenTest.kt
git commit -m "feat(cuenta): CuentaScreen crear-cuenta/iniciar-sesion toggle and recovery dialog"
```

---

### Task 9: Explicit regression test — no automatic anonymous recreation

**Files:**
- Modify: `app/src/test/java/com/agoitdev/spenvo/SesionGateViewModelTest.kt`
- Modify: `feature/planes/src/test/java/com/agoitdev/spenvo/planes/PlanesViewModelTest.kt`

This is the central guarantee of this front — give it its own explicit, named test rather than relying on it being an implicit side effect of Task 5's/Task 6's other tests.

- [ ] **Step 1: Add the explicit regression test to `SesionGateViewModelTest`**

Add to `app/src/test/java/com/agoitdev/spenvo/SesionGateViewModelTest.kt`:

```kotlin
    @Test
    fun `REGRESION cold start con flag true nunca llama iniciarSesionAnonima`() = runTest {
        // Simulates: process was killed right after an explicit logout (flag persisted true),
        // then relaunched — this is exactly the scenario the in-memory-only alternative (rejected
        // approach B in the design doc) would have failed.
        authRepository.sesionFlow.value = Sesion.Anonima
        sesionPreferences.flagFlow.value = true
        val viewModel = crearViewModel()
        val job = launch { viewModel.estado.collect {} }
        advanceUntilIdle()

        assertEquals(false, authRepository.anonimaLlamada)
        assertEquals(EstadoGate.MostrarGate, viewModel.estado.value)
        job.cancel()
    }
```

(Adjust `sesionPreferences.flagFlow` to whatever the resolved Task 5 constructor parameter is named — a plain `MutableStateFlow(true)` passed as the `Flow<Boolean>` constructor argument, per Task 5's final resolved signature.)

- [ ] **Step 2: Add the explicit regression test to `PlanesViewModelTest`**

Add to `feature/planes/src/test/java/com/agoitdev/spenvo/planes/PlanesViewModelTest.kt` (using that file's existing fake/setup pattern — read the file first to match its exact `crearViewModel()`-equivalent helper and fake class name before writing this, since this plan doesn't have its full content):

```kotlin
    @Test
    fun `REGRESION instanciar PlanesViewModel con uid nulo no llama iniciarSesionAnonima`() = runTest {
        // PlanesViewModel used to own an init-time retry loop that called this unconditionally;
        // Task 6 removed it. This test guards against it silently coming back.
        authRepository.sesionFlow.value = Sesion.Anonima // or that file's equivalent
        val viewModel = crearViewModel()
        advanceUntilIdle()

        assertEquals(false, authRepository.iniciarSesionAnonimaLlamada) // or that file's equivalent tracking field
    }
```

Note: `PlanesViewModelTest`'s existing fake almost certainly doesn't currently track "was `iniciarSesionAnonima` called" (nothing needed to, before this task) — add that tracking boolean to its fake alongside this test, mirroring the `anonimaLlamada` field pattern from `SesionGateViewModelTest`'s fake.

- [ ] **Step 3: Run both regression tests**

Run: `./gradlew :app:testDebugUnitTest :feature:planes:testDebugUnitTest`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add app/src/test/java/com/agoitdev/spenvo/SesionGateViewModelTest.kt \
        feature/planes/src/test/java/com/agoitdev/spenvo/planes/PlanesViewModelTest.kt
git commit -m "test(auth): explicit regression guard against automatic anonymous re-creation"
```

---

### Task 10: Full gates pass + CHANGELOG

**Files:**
- Modify: `CHANGELOG.md`

- [ ] **Step 1: Run every gate from `AGENTS.md`**

```bash
./gradlew :app:assembleDebug
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew detekt
```

Expected: all four green. Fix any `HardcodedText`/`MissingTranslation` lint findings (every new string from Task 7 must exist in both `values/` and `values-en/` — already done in Step 1 of Task 7, this is the verification pass) and any detekt findings (e.g. function length/complexity on the expanded `CuentaScreen.kt` — split private composables further if detekt flags `LongMethod`/`ComplexMethod`, following this file's own existing pattern of small `private fun` composables per concern).

- [ ] **Step 2: Add the CHANGELOG entry**

`CHANGELOG.md` — add under `## [Unreleased]` → `### Added` (create that subsection if it doesn't exist yet above `### Fixed`, matching Keep a Changelog's category ordering):

```markdown
### Added

- Real email/password sign-in and password recovery (`AuthRepository.iniciarSesionConEmail`/
  `enviarRecuperacionPassword`). Logging out no longer silently re-creates an anonymous Firebase
  session — a persisted flag (`SesionPreferences`) plus a new root-level `SesionGateViewModel`
  in `:app` now gate navigation to a mandatory `CuentaScreen` login/guest choice after an explicit
  logout, and this holds even across a process restart. `PlanesViewModel` no longer owns any
  auth-bootstrap logic. `CuentaScreen` gained a "Crear cuenta"/"Iniciar sesión" tab toggle and a
  password-recovery dialog; both credential-invalid and user-not-found errors show the same
  message to avoid account enumeration.
```

- [ ] **Step 3: Commit**

```bash
git add CHANGELOG.md
git commit -m "docs(changelog): front 2 login real + logout sin recreación anónima"
```

---

## Self-review

**Spec coverage against `doc/designs/2026-08-30-login-real-logout-design.md`:**
- Domain + data layer (§1) → Tasks 1, 3.
- Persisted flag + root gate (§2) → Tasks 4, 5, 6.
- UI unification (§3) → Task 8.
- Error handling (§4) → Task 7 (`mapearErrorAuth`), Task 8 (inline display, recovery always-success).
- Testing (§5) → every task's own test steps, plus Task 9's explicit regression test.
- Ripple effect on 13 pre-existing `AuthRepository` fakes (not in the design doc — discovered during planning) → Task 2.

**Post-commit editorial pass (2026-08-30, before dispatching implementers):** re-read the whole plan against the design doc and found this section's items 1 and 2 weren't just drafting noise — the code blocks themselves had leftover broken scratch-work (a nonexistent `TODO_NOT_USED` reference in Task 5; an undefined `context` reference and a double-display bug in Task 8's `CuentaSideEffects`/recovery-success snackbar). Cleaned both directly in this file rather than leaving it for the implementer to untangle:
- Task 5 now shows only the final, correct `SesionGateViewModel` (single constructor, qualified `Flow<Boolean>`) — the broken draft is gone.
- Task 8's `CuentaSideEffects` is back to its original shape (only `estado.error`, unchanged) since `errorRes` is already shown inline via `LoginForm`'s `supportingText` — routing it through the snackbar too would've double-displayed the same error. The recovery-success snackbar now resolves its string via `stringResource()` hoisted to composable scope instead of a nonexistent `context.getString(...)`.
- Task 7 gained one line: `consumirError()` now also clears `errorRes`, so both error channels reset together.

**Remaining ambiguities, still flagged for whoever executes:**

1. **`SesionGateViewModel`'s constructor shape** (Task 5) takes `AuthRepository` + a qualified `Flow<Boolean>` rather than the whole `SesionPreferences` class — done so the ViewModel stays trivially unit-testable without an interface for `SesionPreferences`. This introduces a new `@LogoutExplicitoFlag` Hilt qualifier (Task 6) that isn't in the design doc. Low-risk (an implementation detail within the design's intent), but worth a second pair of eyes.
2. **`PlanesViewModelTest`'s exact fake shape is unread** (Task 9, Step 2) — this plan does not have that file's full content, so the regression test there is given in outline form with explicit instructions to match the file's real pattern rather than fabricated field names. Whoever executes Task 9 must read that file first.
3. **`FirebaseAuthRepository` has no unit test file and this plan doesn't add one** (Task 1) — deliberate, matches existing project precedent (the class is already partially untested), but flagging since it's a deviation from "every new method gets a test."

**Placeholder scan:** none found — every step above has concrete, compilable (after the noted corrections) code, not descriptions.

**Type/signature consistency:** `AuthRepository`'s 2 new methods, `RegistroEstado.errorRes`, `EstadoGate`, and `AuthTab` are used identically across every task that references them.
