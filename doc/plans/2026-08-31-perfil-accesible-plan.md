# Perfil accesible desde todas las pantallas Implementation Plan

> **For agentic workers:** Use `mobiai-mobile-executing-plans-with-subagents` (recommended) or `mobiai-mobile-executing-plans` to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Make the profile/account screen reachable from every screen of an open plan (Home,
Movimientos, Categorías, Miembros), not just from the Planes list, showing the user's real avatar
photo instead of a generic icon.

**Architecture:** A new badge-less `AvatarTopBarAction` composable in `:core:designsystem` gets
added to each of the four tab screens' existing `TopAppBar`. The avatar URL and a single
`onAbrirCuenta` navigation callback are read once in `:app`'s `SesionGateViewModel` (new `avatarUrl`
`StateFlow`, derived from `Sesion.photoUrl`, which already exists and is already kept live by
`CuentaViewModel.subirAvatar`) and passed down as plain composable parameters — mirroring the
`onCrearCuenta`/`onAbrirPlan` pattern `PlanesScreen` already uses. No feature ViewModel other than
`SesionGateViewModel` changes; no Firestore/Room work.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, Coroutines/Flow, Navigation 3, JUnit4 + Robolectric
Compose UI tests.

**Platform:** Android.

**Design doc:** `doc/designs/2026-08-31-perfil-accesible-design.md`

---

### Task 1: `AvatarTopBarAction` composable (`:core:designsystem`)

**Files:**
- Modify: `core/designsystem/src/main/java/com/agoitdev/spenvo/designsystem/components/Avatar.kt`
- Test: `core/designsystem/src/test/java/com/agoitdev/spenvo/designsystem/components/AvatarTest.kt`

- [ ] **Step 1: Write the failing tests**

Append to `AvatarTest.kt` (same file, same `@RunWith(RobolectricTestRunner::class) @Config(sdk = [34])`
class already there — these are new `@Test` methods inside the existing `class AvatarTest {`):

```kotlin
    @Test
    fun `boton de topbar muestra la imagen cuando hay foto`() {
        composeTestRule.setContent {
            AvatarTopBarAction(
                photoUrl = "https://example.com/avatar.jpg",
                contentDescription = "Cuenta",
                onClick = {},
            )
        }

        composeTestRule.onNodeWithTag(TAG_AVATAR_TOPBAR_IMAGEN).assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(TAG_AVATAR_TOPBAR_PLACEHOLDER).assertCountEquals(0)
    }

    @Test
    fun `boton de topbar muestra un placeholder cuando no hay foto`() {
        composeTestRule.setContent {
            AvatarTopBarAction(
                photoUrl = null,
                contentDescription = "Cuenta",
                onClick = {},
            )
        }

        composeTestRule.onNodeWithTag(TAG_AVATAR_TOPBAR_PLACEHOLDER).assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(TAG_AVATAR_TOPBAR_IMAGEN).assertCountEquals(0)
    }

    @Test
    fun `boton de topbar invoca onClick al tocarlo`() {
        var clics = 0

        composeTestRule.setContent {
            AvatarTopBarAction(
                photoUrl = null,
                contentDescription = "Cuenta",
                onClick = { clics++ },
            )
        }

        composeTestRule.onNodeWithTag(TAG_AVATAR_TOPBAR_PLACEHOLDER).performClick()

        assertEquals(1, clics)
    }
```

Add the missing import at the top of `AvatarTest.kt` (alongside the existing `assertCountEquals`/etc.
imports already there):

```kotlin
import org.junit.Assert.assertEquals
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :core:designsystem:testDebugUnitTest --tests "com.agoitdev.spenvo.designsystem.components.AvatarTest"`
Expected: FAIL — `AvatarTopBarAction`, `TAG_AVATAR_TOPBAR_IMAGEN`, `TAG_AVATAR_TOPBAR_PLACEHOLDER`
are unresolved references (compile failure).

- [ ] **Step 3: Write the implementation**

Add to `Avatar.kt`, after the existing `TAG_AVATAR_BADGE` constant (line 25) and before
`TamanoAvatarPorDefecto` (line 27):

```kotlin
const val TAG_AVATAR_TOPBAR_IMAGEN = "avatar_topbar_imagen"
const val TAG_AVATAR_TOPBAR_PLACEHOLDER = "avatar_topbar_placeholder"

private val TamanoAvatarTopBar = 32.dp
```

Append this new composable at the end of `Avatar.kt` (after the closing `}` of `AvatarConBadge`):

```kotlin

/**
 * Compact, badge-less avatar for a [androidx.compose.material3.TopAppBar]'s `actions` slot — the
 * "open my account" entry point reachable from every screen (front 3 of the auth/identity series).
 * Same photoUrl-or-placeholder shape as [AvatarConBadge], without the edit badge: this button only
 * navigates, it never edits the photo directly.
 */
@Composable
fun AvatarTopBarAction(
    photoUrl: String?,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(onClick = onClick, modifier = modifier) {
        Box(modifier = Modifier.size(TamanoAvatarTopBar)) {
            if (photoUrl != null) {
                AsyncImage(
                    model = photoUrl,
                    contentDescription = contentDescription,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .testTag(TAG_AVATAR_TOPBAR_IMAGEN),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .testTag(TAG_AVATAR_TOPBAR_PLACEHOLDER),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = contentDescription,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}
```

Add the new import at the top of `Avatar.kt` (alongside the existing `androidx.compose.material3.Icon`
import):

```kotlin
import androidx.compose.material3.IconButton
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :core:designsystem:testDebugUnitTest --tests "com.agoitdev.spenvo.designsystem.components.AvatarTest"`
Expected: PASS (6 tests total: 3 existing `AvatarConBadge` + 3 new).

- [ ] **Step 5: Commit**

```bash
git add core/designsystem/src/main/java/com/agoitdev/spenvo/designsystem/components/Avatar.kt \
        core/designsystem/src/test/java/com/agoitdev/spenvo/designsystem/components/AvatarTest.kt
git commit -m "feat(designsystem): add AvatarTopBarAction for top-bar profile entry points"
```

---

### Task 2: `SesionGateViewModel.avatarUrl` (`:app`)

**Files:**
- Modify: `app/src/main/java/com/agoitdev/spenvo/SesionGateViewModel.kt`
- Test: `app/src/test/java/com/agoitdev/spenvo/SesionGateViewModelTest.kt`

- [ ] **Step 1: Write the failing test**

Add inside `class SesionGateViewModelTest` (after the existing test at line ~100, before the closing
`}` of the test class — check the file for its exact end, insert alongside the other `@Test` methods):

```kotlin
    @Test
    fun `avatarUrl refleja el photoUrl de la sesion actual`() = runTest {
        authRepository.sesionFlow.value =
            Sesion(uid = "user-1", esAnonima = false, photoUrl = "https://example.com/avatar.jpg")
        val viewModel = crearViewModel()
        val job = launch { viewModel.avatarUrl.collect {} }
        advanceUntilIdle()

        assertEquals("https://example.com/avatar.jpg", viewModel.avatarUrl.value)
        job.cancel()
    }

    @Test
    fun `avatarUrl es null para una sesion sin foto`() = runTest {
        authRepository.sesionFlow.value = Sesion(uid = "user-1", esAnonima = true)
        val viewModel = crearViewModel()
        val job = launch { viewModel.avatarUrl.collect {} }
        advanceUntilIdle()

        assertEquals(null, viewModel.avatarUrl.value)
        job.cancel()
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.agoitdev.spenvo.SesionGateViewModelTest"`
Expected: FAIL — `avatarUrl` is an unresolved reference on `SesionGateViewModel` (compile failure).

- [ ] **Step 3: Write the implementation**

In `SesionGateViewModel.kt`, add the import (alongside the existing `kotlinx.coroutines.flow.*`
imports, line ~12-19):

```kotlin
import kotlinx.coroutines.flow.map
```

Add this property right after `val estado: StateFlow<EstadoGate> = ...` (after line 51, the closing
`.stateIn(...)` of `estado`):

```kotlin

    /**
     * The current session's avatar photo, for the "open my account" action every screen exposes
     * (front 3 of the auth/identity series). `null` covers both an anonymous session and a
     * registered user who never uploaded a photo — [AvatarTopBarAction] falls back to a generic
     * icon either way.
     */
    val avatarUrl: StateFlow<String?> = authRepository.observeSesion()
        .map { it.photoUrl }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.agoitdev.spenvo.SesionGateViewModelTest"`
Expected: PASS (all existing tests plus the 2 new ones).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/agoitdev/spenvo/SesionGateViewModel.kt \
        app/src/test/java/com/agoitdev/spenvo/SesionGateViewModelTest.kt
git commit -m "feat(app): expose SesionGateViewModel.avatarUrl"
```

---

### Task 3: Wire Home (`:feature:movimientos`)

**Files:**
- Modify: `feature/movimientos/src/main/java/com/agoitdev/spenvo/movimientos/HomeScreen.kt`
- Modify: `feature/movimientos/src/main/res/values/strings.xml`
- Modify: `feature/movimientos/src/main/res/values-en/strings.xml`
- Test: `feature/movimientos/src/test/java/com/agoitdev/spenvo/movimientos/HomeScreenTest.kt`

`HomeScreen` currently has no `TopAppBar` at all (its `Scaffold` only sets `snackbarHost`) — this
task adds one.

- [ ] **Step 1: Add the string resources**

In `feature/movimientos/src/main/res/values/strings.xml`, add after the existing
`movements_detail_placeholder` line (line 10):

```xml
    <string name="account_menu_description">Cuenta</string>
```

In `feature/movimientos/src/main/res/values-en/strings.xml`, add the matching English entry at the
same position:

```xml
    <string name="account_menu_description">Account</string>
```

- [ ] **Step 2: Write the failing test**

In `HomeScreenTest.kt`, change the `montarHome()` helper (lines 116-125) to accept the two new
parameters with test-friendly defaults, and pass them through:

```kotlin
    private fun montarHome(avatarUrl: String? = null, onAbrirCuenta: () -> Unit = {}) {
        movimientosViewModel = crearMovimientosViewModel()
        composeTestRule.setContent {
            HomeScreen(
                planId = "p1",
                movimientosViewModel = movimientosViewModel,
                viewModel = crearHomeViewModel(),
                avatarUrl = avatarUrl,
                onAbrirCuenta = onAbrirCuenta,
            )
        }
    }
```

Add this new test, alongside the other `@Test` methods in the same class:

```kotlin
    @Test
    fun `tocar el avatar de la topbar invoca onAbrirCuenta`() {
        var invocado = false
        montarHome(onAbrirCuenta = { invocado = true })

        composeTestRule.onNodeWithTag(TAG_AVATAR_TOPBAR_PLACEHOLDER).performClick()

        assertEquals(true, invocado)
    }
```

Add the missing imports at the top of `HomeScreenTest.kt` (alongside the other
`com.agoitdev.spenvo.designsystem...`-style and `androidx.compose.ui.test...` imports already there):

```kotlin
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.agoitdev.spenvo.designsystem.components.TAG_AVATAR_TOPBAR_PLACEHOLDER
```

(`onNodeWithTag`/`performClick` may already be imported for other tests in the file — if so, do not
duplicate the import, only add `TAG_AVATAR_TOPBAR_PLACEHOLDER`.)

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew :feature:movimientos:testDebugUnitTest --tests "com.agoitdev.spenvo.movimientos.HomeScreenTest"`
Expected: FAIL — `HomeScreen` has no `avatarUrl`/`onAbrirCuenta` parameters (compile failure).

- [ ] **Step 4: Write the implementation**

In `HomeScreen.kt`, change the `HomeScreen` function signature (lines 50-55) to:

```kotlin
@Composable
fun HomeScreen(
    planId: String,
    movimientosViewModel: MovimientosViewModel,
    avatarUrl: String?,
    onAbrirCuenta: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
```

Change the `Scaffold` call (lines 80-93) to add a `topBar`:

```kotlin
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(text = plan?.nombre.orEmpty()) },
                actions = {
                    AvatarTopBarAction(
                        photoUrl = avatarUrl,
                        contentDescription = stringResource(R.string.account_menu_description),
                        onClick = onAbrirCuenta,
                    )
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        HomeContenido(
            nombrePlan = plan?.nombre.orEmpty(),
            moneda = plan?.moneda.orEmpty(),
            resumen = resumen,
            balanceAcumulado = balanceAcumulado,
            onNuevoGasto = { tipoFormularioAbierto = TipoCategoria.GASTO },
            onNuevoIngreso = { tipoFormularioAbierto = TipoCategoria.INGRESO },
            modifier = Modifier.padding(innerPadding),
        )
    }
```

`HomeContenido` already shows the plan name as its first `Text` (line 127) — leaving it there too is
intentional, not a duplication bug: the top bar's title is the standard app-chrome location users
expect a screen title, while `HomeContenido`'s heading is part of the dashboard's own content layout.
Do not remove either.

Add the new imports at the top of `HomeScreen.kt` (alongside the existing `androidx.compose.material3.*`
imports):

```kotlin
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBar
import com.agoitdev.spenvo.designsystem.components.AvatarTopBarAction
```

Add `@OptIn(ExperimentalMaterial3Api::class)` above `@Composable fun HomeScreen(` (`TopAppBar` needs
it — check whether `HomeScreen.kt` already has this opt-in elsewhere in the file before adding a
duplicate).

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :feature:movimientos:testDebugUnitTest --tests "com.agoitdev.spenvo.movimientos.HomeScreenTest"`
Expected: PASS (all existing `HomeScreenTest` tests plus the new one).

- [ ] **Step 6: Commit**

```bash
git add feature/movimientos/src/main/java/com/agoitdev/spenvo/movimientos/HomeScreen.kt \
        feature/movimientos/src/main/res/values/strings.xml \
        feature/movimientos/src/main/res/values-en/strings.xml \
        feature/movimientos/src/test/java/com/agoitdev/spenvo/movimientos/HomeScreenTest.kt
git commit -m "feat(movimientos): add account entry point to Home's new TopAppBar"
```

---

### Task 4: Wire Movimientos (`:feature:movimientos`)

**Files:**
- Modify: `feature/movimientos/src/main/java/com/agoitdev/spenvo/movimientos/MovimientosScreen.kt`
- Modify: `feature/movimientos/src/main/java/com/agoitdev/spenvo/movimientos/MovimientosScaffoldPartes.kt`
- Test: `feature/movimientos/src/test/java/com/agoitdev/spenvo/movimientos/MovimientosScreenListDetailTest.kt`

`MovimientosAcciones` (`MovimientosScreen.kt:242-244`) already threads through every layout variant
(`MovimientosPantallas` → `MovimientosPantallaExpandida`/`MovimientosPantallaCompacta` →
`MovimientosScaffold`) down to where `MovimientosTopBar()` is called — extending this one data class
avoids adding two new parameters to five function signatures.

- [ ] **Step 1: Write the failing test**

In `MovimientosScreenListDetailTest.kt`, find the `acciones` property (used by every test in the
file, defined once near the top alongside `filtro`/similar test fixtures — search for
`private val acciones` or the equivalent `MovimientosAcciones(...)` construction) and add the two
new fields with test-friendly defaults:

```kotlin
    private val acciones = MovimientosAcciones(
        onNuevoMovimiento = {},
        avatarUrl = null,
        onAbrirCuenta = {},
    )
```

(If the test file constructs `MovimientosAcciones` inline in multiple places instead of one shared
`private val`, add `avatarUrl = null, onAbrirCuenta = {}` to each call site instead.)

Add this new test alongside the existing ones (e.g. after `` `la topbar ya no muestra los accesos a
categorias ni miembros` ``):

```kotlin
    @Test
    fun `la topbar del layout compacto muestra el avatar y navega al tocarlo`() {
        val viewModel = crearViewModel()
        var invocado = false

        composeTestRule.setContent {
            MovimientosPantallaCompacta(
                modifier = Modifier,
                acciones = MovimientosAcciones(
                    onNuevoMovimiento = {},
                    avatarUrl = null,
                    onAbrirCuenta = { invocado = true },
                ),
                filtro = filtro,
                snackbarHostState = remember { SnackbarHostState() },
                lista = listaEstado(),
                formularioParametros = formularioParametros(FormularioMovimiento.Cerrado, viewModel),
            )
        }

        composeTestRule.onNodeWithTag(TAG_AVATAR_TOPBAR_PLACEHOLDER).performClick()

        assertEquals(true, invocado)
    }
```

Add the missing imports at the top of `MovimientosScreenListDetailTest.kt` (check first whether
`onNodeWithTag`/`performClick`/`assertEquals` are already imported for other tests in the file — add
only what's missing):

```kotlin
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.agoitdev.spenvo.designsystem.components.TAG_AVATAR_TOPBAR_PLACEHOLDER
import org.junit.Assert.assertEquals
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :feature:movimientos:testDebugUnitTest --tests "com.agoitdev.spenvo.movimientos.MovimientosScreenListDetailTest"`
Expected: FAIL — `MovimientosAcciones` has no `avatarUrl`/`onAbrirCuenta` parameters (compile
failure).

- [ ] **Step 3: Write the implementation**

In `MovimientosScreen.kt`, change `MovimientosAcciones` (lines 242-244) to:

```kotlin
internal data class MovimientosAcciones(
    val onNuevoMovimiento: () -> Unit,
    val avatarUrl: String?,
    val onAbrirCuenta: () -> Unit,
)
```

Update the construction site in `MovimientosScreen` (lines 76-78) — add the two params to its
top-level public signature and thread them into `MovimientosAcciones`:

```kotlin
@Composable
fun MovimientosScreen(
    planId: String,
    avatarUrl: String?,
    onAbrirCuenta: () -> Unit,
    viewModel: MovimientosViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
```

```kotlin
        acciones = MovimientosAcciones(
            onNuevoMovimiento = { formulario = FormularioMovimiento.Nuevo },
            avatarUrl = avatarUrl,
            onAbrirCuenta = onAbrirCuenta,
        ),
```

In `MovimientosScaffoldPartes.kt`, change `MovimientosTopBar` (lines 18-22) to accept and use the new
values:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MovimientosTopBar(avatarUrl: String?, onAbrirCuenta: () -> Unit) {
    TopAppBar(
        title = { Text(stringResource(R.string.movements_title)) },
        actions = {
            AvatarTopBarAction(
                photoUrl = avatarUrl,
                contentDescription = stringResource(R.string.account_menu_description),
                onClick = onAbrirCuenta,
            )
        },
    )
}
```

Add the new import at the top of `MovimientosScaffoldPartes.kt`:

```kotlin
import com.agoitdev.spenvo.designsystem.components.AvatarTopBarAction
```

In `MovimientosScreen.kt`, change the `MovimientosTopBar()` call site inside `MovimientosScaffold`
(line 271) to:

```kotlin
        topBar = { MovimientosTopBar(avatarUrl = acciones.avatarUrl, onAbrirCuenta = acciones.onAbrirCuenta) },
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :feature:movimientos:testDebugUnitTest --tests "com.agoitdev.spenvo.movimientos.MovimientosScreenListDetailTest"`
Expected: PASS (all existing tests in the file plus the new one).

- [ ] **Step 5: Build verification**

Run: `./gradlew :feature:movimientos:testDebugUnitTest`
Expected: BUILD SUCCESSFUL — this module's other test files (`MovimientoFormSheetTest`,
`MovimientosViewModelTest`, etc.) also construct `MovimientosAcciones`/call `MovimientosScreen`; if
any fail to compile because they build `MovimientosAcciones` inline without the two new fields, add
`avatarUrl = null, onAbrirCuenta = {}` to those call sites too before proceeding.

- [ ] **Step 6: Commit**

```bash
git add feature/movimientos/src/main/java/com/agoitdev/spenvo/movimientos/MovimientosScreen.kt \
        feature/movimientos/src/main/java/com/agoitdev/spenvo/movimientos/MovimientosScaffoldPartes.kt \
        feature/movimientos/src/test/java/com/agoitdev/spenvo/movimientos/MovimientosScreenListDetailTest.kt
git commit -m "feat(movimientos): add account entry point to Movimientos' TopAppBar"
```

---

### Task 5: Wire Categorías (`:feature:categorias`)

**Files:**
- Modify: `feature/categorias/src/main/java/com/agoitdev/spenvo/categorias/CategoriasScreen.kt`
- Modify: `feature/categorias/src/main/res/values/strings.xml`
- Modify: `feature/categorias/src/main/res/values-en/strings.xml`
- Create: `feature/categorias/src/test/java/com/agoitdev/spenvo/categorias/CategoriasScreenTest.kt`

This module has no existing `CategoriasScreenTest.kt` — this task creates it.
`CategoriasViewModelTest.kt`'s fakes (`FakeCategoriaRepository`, `FakeCategoriaSincronizacion`,
`FakeAuthRepository`) are `private` to that file (Kotlin top-level `private` is file-scoped, not
package-scoped), so they cannot be imported here — this new file defines its own minimal fakes.

Verified `CategoriasViewModel`'s real constructor (`CategoriasViewModel.kt:32-39`):
`observarCategoriasPorTipo: ObservarCategoriasPorTipoUseCase, crearCategoria: CrearCategoriaUseCase,
actualizarCategoria: ActualizarCategoriaUseCase, eliminarCategoria: EliminarCategoriaUseCase,
sincronizador: CategoriaSincronizacion, authRepository: AuthRepository` — matched exactly below.

- [ ] **Step 1: Add the string resources**

In `feature/categorias/src/main/res/values/strings.xml`, add after the existing `categories_title`
line (line 3):

```xml
    <string name="account_menu_description">Cuenta</string>
```

In `feature/categorias/src/main/res/values-en/strings.xml`, add the matching English entry at the
same position.

- [ ] **Step 2: Write the failing test**

Create `feature/categorias/src/test/java/com/agoitdev/spenvo/categorias/CategoriasScreenTest.kt`:

```kotlin
package com.agoitdev.spenvo.categorias

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.agoitdev.spenvo.data.remote.sync.CategoriaSincronizacion
import com.agoitdev.spenvo.designsystem.components.TAG_AVATAR_TOPBAR_PLACEHOLDER
import com.agoitdev.spenvo.domain.model.Categoria
import com.agoitdev.spenvo.domain.model.Sesion
import com.agoitdev.spenvo.domain.model.TipoCategoria
import com.agoitdev.spenvo.domain.repository.AuthRepository
import com.agoitdev.spenvo.domain.repository.CategoriaRepository
import com.agoitdev.spenvo.domain.usecase.ActualizarCategoriaUseCase
import com.agoitdev.spenvo.domain.usecase.CrearCategoriaUseCase
import com.agoitdev.spenvo.domain.usecase.EliminarCategoriaUseCase
import com.agoitdev.spenvo.domain.usecase.ObservarCategoriasPorTipoUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "es")
class CategoriasScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `tocar el avatar de la topbar invoca onAbrirCuenta`() {
        var invocado = false
        val categoriaRepo = FakeCategoriaRepositorioCategoriasScreen()
        val viewModel = CategoriasViewModel(
            observarCategoriasPorTipo = ObservarCategoriasPorTipoUseCase(categoriaRepo),
            crearCategoria = CrearCategoriaUseCase(categoriaRepo),
            actualizarCategoria = ActualizarCategoriaUseCase(categoriaRepo),
            eliminarCategoria = EliminarCategoriaUseCase(categoriaRepo),
            sincronizador = FakeCategoriaSincronizacionCategoriasScreen(),
            authRepository = FakeAuthRepositorioCategoriasScreen(),
        )

        composeTestRule.setContent {
            CategoriasScreen(
                planId = "p1",
                avatarUrl = null,
                onAbrirCuenta = { invocado = true },
                viewModel = viewModel,
            )
        }

        composeTestRule.onNodeWithTag(TAG_AVATAR_TOPBAR_PLACEHOLDER).performClick()

        assertEquals(true, invocado)
    }
}

private class FakeCategoriaRepositorioCategoriasScreen : CategoriaRepository {
    override fun observarCategorias(planId: String): Flow<List<Categoria>> = flowOf(emptyList())
    override fun observarCategoriasPorTipo(planId: String, tipo: TipoCategoria): Flow<List<Categoria>> =
        flowOf(emptyList())
    override suspend fun crearCategoria(categoria: Categoria) = Unit
    override suspend fun crearCategorias(categorias: List<Categoria>) = Unit
    override suspend fun actualizarCategoria(categoria: Categoria) = Unit
    override suspend fun eliminarCategoria(categoria: Categoria) = Unit
}

private class FakeCategoriaSincronizacionCategoriasScreen : CategoriaSincronizacion {
    override fun sincronizar(planId: String): Flow<Unit> = flowOf(Unit)
}

private class FakeAuthRepositorioCategoriasScreen : AuthRepository {
    override fun observeSesion(): Flow<Sesion> = flowOf(Sesion(uid = "user-1", esAnonima = true))
    override suspend fun iniciarSesionAnonima() = Unit
    override suspend fun iniciarSesionConEmail(email: String, password: String) = Unit
    override suspend fun enviarRecuperacionPassword(email: String) = Unit
    override suspend fun vincularEmail(email: String, password: String, nombre: String) = Unit
    override suspend fun actualizarPerfil(nombre: String?, photoUrl: String?) = Unit
    override suspend fun cerrarSesion() = Unit
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew :feature:categorias:testDebugUnitTest --tests "com.agoitdev.spenvo.categorias.CategoriasScreenTest"`
Expected: FAIL — `CategoriasScreen` has no `avatarUrl`/`onAbrirCuenta` parameters (compile failure).

- [ ] **Step 4: Write the implementation**

In `CategoriasScreen.kt`, change the `CategoriasScreen` function signature (lines 61-65) to:

```kotlin
fun CategoriasScreen(
    planId: String,
    avatarUrl: String?,
    onAbrirCuenta: () -> Unit,
    viewModel: CategoriasViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
```

Change the `topBar` inside its `Scaffold` (line 91) to:

```kotlin
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.categories_title)) },
                actions = {
                    AvatarTopBarAction(
                        photoUrl = avatarUrl,
                        contentDescription = stringResource(R.string.account_menu_description),
                        onClick = onAbrirCuenta,
                    )
                },
            )
        },
```

Add the new import at the top of `CategoriasScreen.kt`:

```kotlin
import com.agoitdev.spenvo.designsystem.components.AvatarTopBarAction
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :feature:categorias:testDebugUnitTest --tests "com.agoitdev.spenvo.categorias.CategoriasScreenTest"`
Expected: PASS.

- [ ] **Step 6: Build verification**

Run: `./gradlew :feature:categorias:testDebugUnitTest`
Expected: BUILD SUCCESSFUL — fix any other test file in this module that calls `CategoriasScreen`
directly and doesn't yet pass the two new parameters.

- [ ] **Step 7: Commit**

```bash
git add feature/categorias/src/main/java/com/agoitdev/spenvo/categorias/CategoriasScreen.kt \
        feature/categorias/src/main/res/values/strings.xml \
        feature/categorias/src/main/res/values-en/strings.xml \
        feature/categorias/src/test/java/com/agoitdev/spenvo/categorias/CategoriasScreenTest.kt
git commit -m "feat(categorias): add account entry point to Categorías' TopAppBar"
```

---

### Task 6: Wire Miembros (`:feature:planes`)

**Files:**
- Modify: `feature/planes/src/main/java/com/agoitdev/spenvo/planes/MiembrosScreen.kt`
- Create: `feature/planes/src/test/java/com/agoitdev/spenvo/planes/MiembrosScreenTest.kt`

This module already has `R.string.account_menu_description` (`PlanesScreen`'s existing entry point,
same module) — no new string resources needed. There is no existing `MiembrosScreenTest.kt`.
`MiembrosViewModelTest.kt`'s fakes are `private` to that file (file-scoped, not importable), so this
new file defines its own.

Verified `MiembrosViewModel`'s real constructor (`MiembrosViewModel.kt:26-31`):
`accesosRepository: AccesoPlanRepository, invitarMiembro: InvitarMiembroUseCase,
usuarioRepository: UsuarioRepository, authRepository: AuthRepository`. `InvitarMiembroUseCase`'s own
constructor (`InvitarMiembroUseCase.kt:22-27`): `accesosRepository: AccesoPlanRepository,
usuarioRepository: UsuarioRepository, pendientesRepository: InvitacionPendienteRepository,
analyticsRepository: AnalyticsRepository`.

- [ ] **Step 1: Write the failing test**

Create `feature/planes/src/test/java/com/agoitdev/spenvo/planes/MiembrosScreenTest.kt`:

```kotlin
package com.agoitdev.spenvo.planes

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.agoitdev.spenvo.designsystem.components.TAG_AVATAR_TOPBAR_PLACEHOLDER
import com.agoitdev.spenvo.domain.model.AccesoPlan
import com.agoitdev.spenvo.domain.model.InvitacionPendiente
import com.agoitdev.spenvo.domain.model.Sesion
import com.agoitdev.spenvo.domain.model.Usuario
import com.agoitdev.spenvo.domain.repository.AccesoPlanRepository
import com.agoitdev.spenvo.domain.repository.AnalyticsRepository
import com.agoitdev.spenvo.domain.repository.AuthRepository
import com.agoitdev.spenvo.domain.repository.InvitacionPendienteRepository
import com.agoitdev.spenvo.domain.repository.UsuarioRepository
import com.agoitdev.spenvo.domain.usecase.InvitarMiembroUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "es")
class MiembrosScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `tocar el avatar de la topbar invoca onAbrirCuenta`() {
        var invocado = false
        val accesosRepo = FakeAccesoPlanRepositorioMiembrosScreen()
        val usuarioRepo = FakeUsuarioRepositorioMiembrosScreen()
        val viewModel = MiembrosViewModel(
            accesosRepository = accesosRepo,
            invitarMiembro = InvitarMiembroUseCase(
                accesosRepository = accesosRepo,
                usuarioRepository = usuarioRepo,
                pendientesRepository = FakePendientesRepositorioMiembrosScreen(),
                analyticsRepository = FakeAnalyticsRepositorioMiembrosScreen(),
            ),
            usuarioRepository = usuarioRepo,
            authRepository = FakeAuthRepositorioMiembrosScreen(),
        )

        composeTestRule.setContent {
            MiembrosScreen(
                planId = "p1",
                avatarUrl = null,
                onAbrirCuenta = { invocado = true },
                viewModel = viewModel,
            )
        }

        composeTestRule.onNodeWithTag(TAG_AVATAR_TOPBAR_PLACEHOLDER).performClick()

        assertEquals(true, invocado)
    }
}

private class FakeAccesoPlanRepositorioMiembrosScreen : AccesoPlanRepository {
    override fun observarAccesosDelUsuario(usuarioId: String): Flow<List<AccesoPlan>> = flowOf(emptyList())
    override fun observarAccesosDelPlan(planId: String): Flow<List<AccesoPlan>> = flowOf(emptyList())
    override suspend fun invitarMiembro(acceso: AccesoPlan) = Unit
    override suspend fun aceptarInvitacion(usuarioId: String, planId: String) = Unit
}

private class FakeUsuarioRepositorioMiembrosScreen : UsuarioRepository {
    override suspend fun obtener(usuarioId: String): Usuario? = null
    override suspend fun obtenerVarios(usuarioIds: List<String>): List<Usuario> = emptyList()
    override suspend fun intentarReservarNombreUsuario(
        nombreUsuarioNormalizado: String,
        usuarioId: String,
    ): Boolean = true
    override suspend fun crear(usuario: Usuario) = Unit
    override suspend fun actualizar(usuario: Usuario) = Unit
    override suspend fun renombrar(
        usuarioId: String,
        nombreUsuarioAnterior: String,
        nombreUsuarioNuevo: String,
    ): Boolean = true
    override suspend fun registrarIndiceEmail(usuarioId: String, emailNormalizado: String) = Unit
    override suspend fun resolverPorNombreUsuario(nombreUsuarioNormalizado: String): String? = null
    override suspend fun resolverPorEmail(emailNormalizado: String): String? = null
}

private class FakePendientesRepositorioMiembrosScreen : InvitacionPendienteRepository {
    override suspend fun crear(invitacion: InvitacionPendiente) = Unit
    override suspend fun obtenerPorEmail(emailNormalizado: String): List<InvitacionPendiente> = emptyList()
    override suspend fun eliminar(emailNormalizado: String, planId: String) = Unit
}

private class FakeAnalyticsRepositorioMiembrosScreen : AnalyticsRepository {
    override fun registrarEvento(nombre: String) = Unit
}

private class FakeAuthRepositorioMiembrosScreen : AuthRepository {
    override fun observeSesion(): Flow<Sesion> = flowOf(Sesion(uid = "user-1", esAnonima = true))
    override suspend fun iniciarSesionAnonima() = Unit
    override suspend fun iniciarSesionConEmail(email: String, password: String) = Unit
    override suspend fun enviarRecuperacionPassword(email: String) = Unit
    override suspend fun vincularEmail(email: String, password: String, nombre: String) = Unit
    override suspend fun actualizarPerfil(nombre: String?, photoUrl: String?) = Unit
    override suspend fun cerrarSesion() = Unit
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :feature:planes:testDebugUnitTest --tests "com.agoitdev.spenvo.planes.MiembrosScreenTest"`
Expected: FAIL — `MiembrosScreen` has no `avatarUrl`/`onAbrirCuenta` parameters (compile failure).

- [ ] **Step 3: Write the implementation**

In `MiembrosScreen.kt`, change the `MiembrosScreen` function signature (lines 50-54) to:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MiembrosScreen(
    planId: String,
    avatarUrl: String?,
    onAbrirCuenta: () -> Unit,
    viewModel: MiembrosViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
```

Change the `TopAppBar`'s `actions` block (lines 82-89) to add the avatar alongside the existing
invite icon:

```kotlin
                actions = {
                    AvatarTopBarAction(
                        photoUrl = avatarUrl,
                        contentDescription = stringResource(R.string.account_menu_description),
                        onClick = onAbrirCuenta,
                    )
                    IconButton(onClick = { mostrarDialogoInvitar = true }) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = stringResource(R.string.members_invite),
                        )
                    }
                },
```

Add the new import at the top of `MiembrosScreen.kt`:

```kotlin
import com.agoitdev.spenvo.designsystem.components.AvatarTopBarAction
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :feature:planes:testDebugUnitTest --tests "com.agoitdev.spenvo.planes.MiembrosScreenTest"`
Expected: PASS.

- [ ] **Step 5: Build verification**

Run: `./gradlew :feature:planes:testDebugUnitTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add feature/planes/src/main/java/com/agoitdev/spenvo/planes/MiembrosScreen.kt \
        feature/planes/src/test/java/com/agoitdev/spenvo/planes/MiembrosScreenTest.kt
git commit -m "feat(planes): add account entry point to Miembros' TopAppBar"
```

---

### Task 7: Wire `SpenvoApp` (`:app`)

**Files:**
- Modify: `app/src/main/java/com/agoitdev/spenvo/MainActivity.kt`

No new test for this task, deliberately: verified `SpenvoAppTest.kt`'s existing tests use
`@Config(sdk = [34], qualifiers = "es", application = Application::class)` specifically so nothing in
that file needs Hilt — its own top comment says so ("Nothing here needs Hilt — the gate ViewModel is
constructed directly with fakes"). `PlanRoute`'s tab screens (`HomeScreen`, `MovimientosScreen`, etc.)
default their `viewModel` parameter to `hiltViewModel()`, which crashes without a Hilt test rule this
module doesn't have — the same constraint that already kept front 2 from adding a `PlanRoute`-level
`SpenvoApp` test. Real coverage for this task's wiring comes from Tasks 3-6's per-screen tests (each
already asserts `avatarUrl`/`onAbrirCuenta` reach the right composable and fire on click) plus
Task 8's manual device verification.

- [ ] **Step 1: Write the implementation**

In `MainActivity.kt`, inside `SpenvoApp` (after line 171, `val backStack = rememberNavBackStack(PlanesRoute)`),
add:

```kotlin
    val avatarUrl by gateViewModel.avatarUrl.collectAsStateWithLifecycle()
```

Change the `entry<PlanRoute>` block (lines 199-211) to pass `avatarUrl` and a shared
`onAbrirCuenta` lambda into each of the four tab screens:

```kotlin
                entry<PlanRoute> { route ->
                    val movimientosViewModel: MovimientosViewModel = hiltViewModel()
                    // Explicit () -> Unit: without it, this infers NavBackStack.add's own return
                    // type (Boolean), which HomeScreen/MovimientosScreen/etc.'s onAbrirCuenta: ()
                    // -> Unit parameter would then reject — a lambda literal passed directly as an
                    // argument gets Unit-coerced by its expected type, but a bare val assignment
                    // has no such expected type to coerce against.
                    val onAbrirCuenta: () -> Unit = { backStack.add(CuentaRoute) }
                    PlanScaffold(
                        contenidoHome = {
                            HomeScreen(
                                planId = route.planId,
                                movimientosViewModel = movimientosViewModel,
                                avatarUrl = avatarUrl,
                                onAbrirCuenta = onAbrirCuenta,
                            )
                        },
                        contenidoMovimientos = {
                            MovimientosScreen(
                                planId = route.planId,
                                avatarUrl = avatarUrl,
                                onAbrirCuenta = onAbrirCuenta,
                                viewModel = movimientosViewModel,
                            )
                        },
                        contenidoCategorias = {
                            CategoriasScreen(
                                planId = route.planId,
                                avatarUrl = avatarUrl,
                                onAbrirCuenta = onAbrirCuenta,
                            )
                        },
                        contenidoMiembros = {
                            MiembrosScreen(
                                planId = route.planId,
                                avatarUrl = avatarUrl,
                                onAbrirCuenta = onAbrirCuenta,
                            )
                        },
                    )
                }
```

- [ ] **Step 2: Verify the existing app-level tests still pass**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL — `SesionGateViewModelTest`, `SpenvoAppTest`, and `MainActivityTest` are
all unaffected by this change (none of them render `PlanRoute`'s tab screens), so this step is a
regression check, not new coverage.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/agoitdev/spenvo/MainActivity.kt
git commit -m "feat(app): wire the profile entry point into every plan tab"
```

---

### Task 8: Full verification + docs

**Files:**
- Modify: `CHANGELOG.md`

- [ ] **Step 1: Full build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Full test suite**

Run: `./gradlew testDebugUnitTest`
Expected: BUILD SUCCESSFUL, 0 failures.

- [ ] **Step 3: Lint + detekt**

Run: `./gradlew lintDebug detekt`
Expected: BUILD SUCCESSFUL — `HardcodedText`/`MissingTranslation` clean (every new string is in both
`values/` and `values-en/` for its module, per Tasks 3 and 5).

- [ ] **Step 4: Manual verification on device**

Install the debug build on a device/emulator, sign in (or continue as guest), open a plan, and
confirm the avatar action appears and navigates to the profile screen from all four tabs (Home,
Movimientos, Categorías, Miembros), not only from the Planes list. Confirm back navigation from the
profile screen returns to the exact tab that was open.

- [ ] **Step 5: Update CHANGELOG.md**

Add a new bullet under `## [Unreleased]`'s `### Added` header (the same header the front 1/front 2
entries already live under — do not create a new one):

```markdown
- Perfil accesible desde todas las pantallas (front 3/3): the account/profile entry point, previously
  reachable only from the Planes list, now appears in every tab's `TopAppBar` inside an open plan
  (Home, Movimientos, Categorías, Miembros) via a new `AvatarTopBarAction` (`:core:designsystem`),
  showing the user's real avatar photo instead of a generic icon. `SesionGateViewModel.avatarUrl`
  reads `Sesion.photoUrl` (already kept live by `CuentaViewModel.subirAvatar`) once at the app root
  and passes it down alongside a shared `onAbrirCuenta` callback — no new data plumbing, no feature
  ViewModel changes beyond the screens' own composable parameters. Navigation-only: `CuentaScreen`'s
  profile UI itself is unchanged.
```

- [ ] **Step 6: Commit**

```bash
git add CHANGELOG.md
git commit -m "docs(changelog): perfil accesible desde todas las pantallas"
```
