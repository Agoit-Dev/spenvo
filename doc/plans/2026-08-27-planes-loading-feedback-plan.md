# Startup Loading Feedback (Planes) Implementation Plan

> **For agentic workers:** Use `mobiai-mobile-executing-plans-with-subagents` (recommended) or
> `mobiai-mobile-executing-plans` to implement this plan task-by-task. Steps use checkbox syntax
> for tracking.

**Goal:** Replace the "flash of empty state" on Planes' cold start with an explicit loading
indicator, per `doc/designs/2026-08-27-planes-loading-feedback-design.md`.

**Architecture:** Nullable-sentinel `StateFlow`s in `PlanesViewModel` to distinguish "not loaded
yet" from "genuinely empty", combined into one `cargando: StateFlow<Boolean>`; `PlanesLista` shows
a centered `CircularProgressIndicator` in the list area only while `cargando`, leaving the TopBar
and FAB untouched.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), JUnit4 + kotlinx-coroutines-test (ViewModel
tests use `StandardTestDispatcher`, matching this file's existing convention) + Robolectric +
`ui-test-junit4` for the Compose test (using `UnconfinedTestDispatcher`, matching the
`:feature:movimientos` Compose test convention).

**Platform:** Android.

---

## Task 1: `PlanesViewModel` — nullable-sentinel flows and `cargando`

**Files:**
- Modify: `feature/planes/src/main/java/com/agoitdev/spenvo/planes/PlanesViewModel.kt:54-78`
- Modify: `feature/planes/src/test/java/com/agoitdev/spenvo/planes/PlanesViewModelTest.kt`

- [ ] **Step 1: Make the existing fakes controllable**

Read the current `PlanesViewModelTest.kt` first (some line numbers may have drifted). Change the
class-level fields:

```kotlin
    private val sesionFlow = MutableStateFlow(Sesion(uid = "user-1", esAnonima = true))
    private val accesosFlow = MutableStateFlow<List<AccesoPlan>>(emptyList())
    private val planesFlow = MutableStateFlow<List<PlanFinanciero>>(emptyList())
    private val planFinancieroRepo = FakePlanFinancieroRepository(planesFlow)
    private val accesoPlanRepo = FakeAccesoPlanRepository(accesosFlow)
    private val categoriaRepo = FakeCategoriaRepository()
    private val movimientoRepo = FakeMovimientoRepository()
    private val sincronizador = FakePlanSincronizacion()
    private val authRepository = FakeAuthRepository(sesionFlow)
```

Change `FakeAccesoPlanRepository` and `FakeAuthRepository` (near the bottom of the file) to accept
a controllable flow, keeping the same default values so nothing else in the file changes behavior:

```kotlin
private class FakeAccesoPlanRepository(
    private val accesosFlow: MutableStateFlow<List<AccesoPlan>> = MutableStateFlow(emptyList()),
) : AccesoPlanRepository {
    override fun observarAccesosDelUsuario(usuarioId: String): Flow<List<AccesoPlan>> = accesosFlow
    override fun observarAccesosDelPlan(planId: String): Flow<List<AccesoPlan>> = flowOf(emptyList())
    override suspend fun invitarMiembro(acceso: AccesoPlan) = Unit
    override suspend fun aceptarInvitacion(usuarioId: String, planId: String) = Unit
}
```

```kotlin
private class FakeAuthRepository(
    private val sesionFlow: MutableStateFlow<Sesion> = MutableStateFlow(Sesion(uid = "user-1", esAnonima = true)),
) : AuthRepository {
    override fun observeSesion(): Flow<Sesion> = sesionFlow
    override suspend fun iniciarSesionAnonima() = Unit
    override suspend fun vincularEmail(email: String, password: String, nombre: String) = Unit
    override suspend fun actualizarPerfil(nombre: String?, photoUrl: String?) = Unit
    override suspend fun cerrarSesion() = Unit
}
```

`FakeAccesoPlanRepository()`'s other call site (inside `crearViewModel()`'s isolated
`sembrarPlanEjemplo` guard construction) keeps working unchanged, since the default value is
preserved.

Run: `./gradlew :feature:planes:testDebugUnitTest --rerun-tasks`
Expected: BUILD SUCCESSFUL, all existing tests still pass (behavior unchanged, only the fakes
became controllable).

- [ ] **Step 2: Write the failing tests for `cargando`**

Add three new `@Test` functions to `PlanesViewModelTest`:

```kotlin
    @Test
    fun `cargando arranca en true y pasa a false una vez que planes e invitaciones resuelven`() = runTest {
        val viewModel = crearViewModel()

        val job = launch { viewModel.cargando.collect {} }
        assertTrue(viewModel.cargando.value)
        advanceUntilIdle()
        assertFalse(viewModel.cargando.value)
        job.cancel()
    }

    @Test
    fun `cargando permanece en true mientras la sesion no tenga uid`() = runTest {
        sesionFlow.value = Sesion.Anonima
        val viewModel = crearViewModel()

        val job = launch { viewModel.cargando.collect {} }
        advanceUntilIdle()

        assertTrue(viewModel.cargando.value)
        job.cancel()
    }

    @Test
    fun `planes conserva la lista real y no queda pegado al valor inicial del StateFlow`() = runTest {
        planesFlow.value = listOf(plan("p1"))
        val viewModel = crearViewModel()

        val job = launch { viewModel.planes.collect {} }
        advanceUntilIdle()

        assertEquals(listOf(plan("p1")), viewModel.planes.value)
        assertFalse(viewModel.cargando.value)
        job.cancel()
    }
```

Add the import `org.junit.Assert.assertFalse` if not already present (the file already imports
`assertTrue`/`assertEquals`).

Run: `./gradlew :feature:planes:testDebugUnitTest --tests "*.PlanesViewModelTest"`
Expected: **compile failure** — `viewModel.cargando` doesn't exist yet. This is expected; it
confirms the test is exercising a symbol that doesn't exist until Step 3.

- [ ] **Step 3: Implement the nullable-sentinel flows and `cargando`**

In `PlanesViewModel.kt`, replace the `planes` and `invitacionesPendientes` declarations (currently
around lines 54-78) with:

```kotlin
    private val planesRaw: StateFlow<List<PlanFinanciero>?> = sesion.flatMapLatest { s ->
        val uid = s.uid
        if (uid == null) flowOf<List<PlanFinanciero>?>(null) else observarPlanes(uid)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val planes: StateFlow<List<PlanFinanciero>> = planesRaw
        .map { it.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val resumenesPorPlan: StateFlow<Map<String, ResumenMensualPlan>> = planes.flatMapLatest { planesActuales ->
        if (planesActuales.isEmpty()) {
            flowOf(emptyMap())
        } else {
            combine(planesActuales.map { plan -> observarResumenMensualPlan(plan.id) }) { resumenes ->
                resumenes.associateBy { it.planId }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val invitacionesRaw: StateFlow<List<AccesoPlan>?> = sesion.flatMapLatest { s ->
        val uid = s.uid
        if (uid == null) {
            flowOf<List<AccesoPlan>?>(null)
        } else {
            accesosRepository.observarAccesosDelUsuario(uid).map { accesos ->
                accesos.filter { it.invitacionEstado == InvitacionEstado.PENDIENTE }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val invitacionesPendientes: StateFlow<List<AccesoPlan>> = invitacionesRaw
        .map { it.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val cargando: StateFlow<Boolean> = combine(planesRaw, invitacionesRaw) { planes, invitaciones ->
        planes == null || invitaciones == null
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
```

Note on the `flowOf<List<X>?>(null)` branches: `Flow` is declared `Flow<out T>` (covariant), so
`Flow<List<X>>` (the `else` branch, e.g. `observarPlanes(uid)`) is already a subtype of
`Flow<List<X>?>` — the explicit type argument on the `null` branch is enough to make the `if`
expression's inferred type `Flow<List<X>?>` on both sides; no cast is needed on the `else` branch.
If the compiler disagrees (type mismatch on the `else` branch), the fallback is
`observarPlanes(uid).map<List<X>, List<X>?> { it }` with an explicit type argument on `map` instead
— try the simpler form first.

Add the import `kotlinx.coroutines.flow.map` if not already present (the file already imports
`combine`, `filter`, `flatMapLatest`, `flowOf`, `stateIn` from `kotlinx.coroutines.flow`).

`resumenesPorPlan` is unchanged (still derives from the public `planes`, not `planesRaw` — it
doesn't need the loading distinction, per the design's explicit exclusion).

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :feature:planes:testDebugUnitTest --rerun-tasks`
Expected: BUILD SUCCESSFUL, all tests pass (the 3 new ones plus every existing test in the file
unchanged).

- [ ] **Step 5: Mutation check**

Temporarily revert the `cargando` declaration's condition from `planes == null || invitaciones == null`
to a hardcoded `false`. Re-run `./gradlew :feature:planes:testDebugUnitTest --tests "*.PlanesViewModelTest"`
and confirm `cargando arranca en true...` and `cargando permanece en true...` both fail. Restore the
real condition and confirm all tests pass again.

- [ ] **Step 6: detekt**

Run: `./gradlew :feature:planes:detekt`
Expected: BUILD SUCCESSFUL, no findings.

- [ ] **Step 7: Commit**

```bash
git add feature/planes/src/main/java/com/agoitdev/spenvo/planes/PlanesViewModel.kt \
        feature/planes/src/test/java/com/agoitdev/spenvo/planes/PlanesViewModelTest.kt
git commit -m "fix(planes): distinguish loading from genuinely-empty in planes/invitaciones"
```

---

## Task 2: `PlanesScreen`/`PlanesLista` — show the spinner

**Files:**
- Modify: `feature/planes/src/main/java/com/agoitdev/spenvo/planes/PlanesScreen.kt`
- Modify: `feature/planes/src/test/java/com/agoitdev/spenvo/planes/PlanesScreenTest.kt`

- [ ] **Step 1: Write the failing tests**

Read the current `PlanesScreenTest.kt` first (it currently only tests `PlanCard` directly, with no
`@Before`/`@After` or ViewModel fakes). `PlanesLista` is `private` to `PlanesScreen.kt`, so this
must drive the public `PlanesScreen(onCrearCuenta, onAbrirPlan, viewModel, modifier)`, passing a
real (non-Hilt) `viewModel` — same technique used throughout this codebase (movimientos,
categorias) for screens with a `= hiltViewModel()` default.

Rewrite `PlanesScreenTest.kt` to:

```kotlin
package com.agoitdev.spenvo.planes

import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.agoitdev.spenvo.data.remote.sync.PlanSincronizacion
import com.agoitdev.spenvo.domain.model.AccesoPlan
import com.agoitdev.spenvo.domain.model.Categoria
import com.agoitdev.spenvo.domain.model.Gasto
import com.agoitdev.spenvo.domain.model.Ingreso
import com.agoitdev.spenvo.domain.model.Monto
import com.agoitdev.spenvo.domain.model.PlanFinanciero
import com.agoitdev.spenvo.domain.model.ResumenMensualPlan
import com.agoitdev.spenvo.domain.model.Sesion
import com.agoitdev.spenvo.domain.model.TipoCategoria
import com.agoitdev.spenvo.domain.repository.AccesoPlanRepository
import com.agoitdev.spenvo.domain.repository.AuthRepository
import com.agoitdev.spenvo.domain.repository.CategoriaRepository
import com.agoitdev.spenvo.domain.repository.MovimientoRepository
import com.agoitdev.spenvo.domain.repository.PlanFinancieroRepository
import com.agoitdev.spenvo.domain.usecase.AceptarInvitacionUseCase
import com.agoitdev.spenvo.domain.usecase.CrearGastoUseCase
import com.agoitdev.spenvo.domain.usecase.CrearIngresoUseCase
import com.agoitdev.spenvo.domain.usecase.CrearPlanUseCase
import com.agoitdev.spenvo.domain.usecase.ObservarPlanesDelUsuarioUseCase
import com.agoitdev.spenvo.domain.usecase.ObservarResumenMensualPlanUseCase
import com.agoitdev.spenvo.domain.usecase.SembrarCategoriasPorDefectoUseCase
import com.agoitdev.spenvo.domain.usecase.SembrarPlanEjemploUseCase
import com.agoitdev.spenvo.domain.usecase.ValidarMontoUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "es")
class PlanesScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val plan = PlanFinanciero(
        id = "p1",
        nombre = "Casa",
        moneda = "EUR",
        createdBy = "user-1",
    )

    private val sesionFlow = MutableStateFlow(Sesion(uid = "user-1", esAnonima = true))
    private val planesFlow = MutableStateFlow<List<PlanFinanciero>>(emptyList())
    private val planFinancieroRepo = FakePlanFinancieroRepositorioScreen(planesFlow)
    private val accesoPlanRepo = FakeAccesoPlanRepositorioScreen()
    private val categoriaRepo = FakeCategoriaRepositorioScreen()
    private val movimientoRepo = FakeMovimientoRepositorioScreen()
    private val sincronizador = FakePlanSincronizacionScreen()
    private val authRepository = FakeAuthRepositorioScreen(sesionFlow)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun crearViewModel() = PlanesViewModel(
        crearPlan = CrearPlanUseCase(
            planFinancieroRepo,
            accesoPlanRepo,
            SembrarCategoriasPorDefectoUseCase(categoriaRepo),
        ),
        observarPlanes = ObservarPlanesDelUsuarioUseCase(planFinancieroRepo),
        aceptarInvitacion = AceptarInvitacionUseCase(accesoPlanRepo),
        sincronizador = sincronizador,
        accesosRepository = accesoPlanRepo,
        observarResumenMensualPlan = ObservarResumenMensualPlanUseCase(movimientoRepo),
        sembrarPlanEjemplo = SembrarPlanEjemploUseCase(
            observarPlanes = ObservarPlanesDelUsuarioUseCase(
                FakePlanFinancieroRepositorioScreen(MutableStateFlow(listOf(plan))),
            ),
            crearPlan = CrearPlanUseCase(
                FakePlanFinancieroRepositorioScreen(MutableStateFlow(emptyList())),
                FakeAccesoPlanRepositorioScreen(),
                SembrarCategoriasPorDefectoUseCase(FakeCategoriaRepositorioScreen()),
            ),
            crearGasto = CrearGastoUseCase(FakeMovimientoRepositorioScreen(), ValidarMontoUseCase()),
            crearIngreso = CrearIngresoUseCase(FakeMovimientoRepositorioScreen(), ValidarMontoUseCase()),
        ),
        authRepository = authRepository,
    )

    @Test
    fun `muestra un balance positivo`() {
        composeTestRule.setContent {
            PlanCard(
                plan = plan,
                resumen = ResumenMensualPlan(planId = "p1", netoDelMes = Monto(2000)),
                onClick = {},
            )
        }

        composeTestRule.onNodeWithText("+20,00 EUR", substring = true).assertIsDisplayed()
    }

    @Test
    fun `muestra un balance negativo`() {
        composeTestRule.setContent {
            PlanCard(
                plan = plan,
                resumen = ResumenMensualPlan(planId = "p1", netoDelMes = Monto(-4000)),
                onClick = {},
            )
        }

        composeTestRule.onNodeWithText("-40,00 EUR", substring = true).assertIsDisplayed()
    }

    @Test
    fun `muestra el spinner en el area de lista mientras carga, sin ocultar la topbar ni el FAB`() {
        sesionFlow.value = Sesion.Anonima // never resolves -> cargando stays true
        val viewModel = crearViewModel()

        composeTestRule.setContent {
            PlanesScreen(onCrearCuenta = {}, onAbrirPlan = {}, viewModel = viewModel)
        }

        composeTestRule.onNodeWithTag("planes_cargando").assertIsDisplayed()
        composeTestRule.onNodeWithText("Planes").assertIsDisplayed()
        composeTestRule.onNodeWithText("Nuevo plan").assertIsDisplayed()
    }

    @Test
    fun `muestra la lista real una vez que la carga termina`() {
        planesFlow.value = listOf(plan)
        val viewModel = crearViewModel()

        composeTestRule.setContent {
            PlanesScreen(onCrearCuenta = {}, onAbrirPlan = {}, viewModel = viewModel)
        }

        composeTestRule.onNodeWithTag("planes_cargando").assertDoesNotExist()
        composeTestRule.onNodeWithText("Casa").assertIsDisplayed()
    }

    @Test
    fun `muestra el estado vacio real cuando la carga termino sin planes`() {
        val viewModel = crearViewModel()

        composeTestRule.setContent {
            PlanesScreen(onCrearCuenta = {}, onAbrirPlan = {}, viewModel = viewModel)
        }

        composeTestRule.onNodeWithTag("planes_cargando").assertDoesNotExist()
        composeTestRule.onNodeWithText(
            "Todavía no hay planes. Toca + para crear tu primer plan.",
        ).assertIsDisplayed()
    }
}

private class FakePlanFinancieroRepositorioScreen(
    private val planesFlow: MutableStateFlow<List<PlanFinanciero>>,
) : PlanFinancieroRepository {
    override fun observarPlanesDelUsuario(usuarioId: String): Flow<List<PlanFinanciero>> = planesFlow
    override fun observarPlan(planId: String): Flow<PlanFinanciero?> = flowOf(planesFlow.value.find { it.id == planId })
    override suspend fun crearPlan(plan: PlanFinanciero) {
        planesFlow.value = planesFlow.value + plan
    }
    override suspend fun actualizarPlan(plan: PlanFinanciero) = Unit
}

private class FakeAccesoPlanRepositorioScreen(
    private val accesosFlow: MutableStateFlow<List<AccesoPlan>> = MutableStateFlow(emptyList()),
) : AccesoPlanRepository {
    override fun observarAccesosDelUsuario(usuarioId: String): Flow<List<AccesoPlan>> = accesosFlow
    override fun observarAccesosDelPlan(planId: String): Flow<List<AccesoPlan>> = flowOf(emptyList())
    override suspend fun invitarMiembro(acceso: AccesoPlan) = Unit
    override suspend fun aceptarInvitacion(usuarioId: String, planId: String) = Unit
}

private class FakeCategoriaRepositorioScreen : CategoriaRepository {
    override fun observarCategorias(planId: String): Flow<List<Categoria>> = flowOf(emptyList())
    override fun observarCategoriasPorTipo(planId: String, tipo: TipoCategoria): Flow<List<Categoria>> =
        flowOf(emptyList())
    override suspend fun crearCategoria(categoria: Categoria) = Unit
    override suspend fun crearCategorias(categorias: List<Categoria>) = Unit
    override suspend fun actualizarCategoria(categoria: Categoria) = Unit
    override suspend fun eliminarCategoria(categoria: Categoria) = Unit
}

private class FakeMovimientoRepositorioScreen : MovimientoRepository {
    override suspend fun addGasto(gasto: Gasto) = Unit
    override suspend fun addIngreso(ingreso: Ingreso) = Unit
    override suspend fun actualizarGasto(gasto: Gasto) = Unit
    override suspend fun eliminarGasto(gasto: Gasto) = Unit
    override suspend fun actualizarIngreso(ingreso: Ingreso) = Unit
    override suspend fun eliminarIngreso(ingreso: Ingreso) = Unit
    override suspend fun aplicarGastoRemoto(id: String) = Unit
    override suspend fun aplicarIngresoRemoto(id: String) = Unit
    override fun observeGastos(planId: String): Flow<List<Gasto>> = flowOf(emptyList())
    override fun observeIngresos(planId: String): Flow<List<Ingreso>> = flowOf(emptyList())
}

private class FakePlanSincronizacionScreen : PlanSincronizacion {
    override fun sincronizar(usuarioId: String): Flow<Unit> = flowOf(Unit)
}

private class FakeAuthRepositorioScreen(
    private val sesionFlow: MutableStateFlow<Sesion> = MutableStateFlow(Sesion(uid = "user-1", esAnonima = true)),
) : AuthRepository {
    override fun observeSesion(): Flow<Sesion> = sesionFlow
    override suspend fun iniciarSesionAnonima() = Unit
    override suspend fun vincularEmail(email: String, password: String, nombre: String) = Unit
    override suspend fun actualizarPerfil(nombre: String?, photoUrl: String?) = Unit
    override suspend fun cerrarSesion() = Unit
}
```

Run: `./gradlew :feature:planes:testDebugUnitTest --tests "*.PlanesScreenTest" --rerun-tasks`
Expected: the two existing `PlanCard` tests pass unchanged; the three new tests fail — either a
compile error (`onNodeWithTag`/`assertDoesNotExist` used against a tag that doesn't exist yet
because `PlanesLista` has no loading branch) or a runtime failure once it compiles against a
`PlanesScreen`/`PlanesLista` that doesn't accept/use `cargando` yet.

- [ ] **Step 2: Implement the spinner in `PlanesLista`**

In `PlanesScreen.kt`, add these imports:
```kotlin
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
```
(`CircularProgressIndicator` is already imported, used by `CrearPlanDialog`.)

Change the `PlanesScreen` composable's state reads and `PlanesLista` call
(currently around lines 58-104):
```kotlin
    val cargando by viewModel.cargando.collectAsStateWithLifecycle()
    val planes by viewModel.planes.collectAsStateWithLifecycle()
    val resumenesPorPlan by viewModel.resumenesPorPlan.collectAsStateWithLifecycle()
    val invitaciones by viewModel.invitacionesPendientes.collectAsStateWithLifecycle()
    val estadoCrear by viewModel.estadoCrear.collectAsStateWithLifecycle()
    val sesion by viewModel.sesion.collectAsStateWithLifecycle()
```
and:
```kotlin
        PlanesLista(
            cargando = cargando,
            planes = planes,
            resumenesPorPlan = resumenesPorPlan,
            invitaciones = invitaciones,
            onAceptarInvitacion = viewModel::aceptarInvitacion,
            onAbrirPlan = onAbrirPlan,
            modifier = Modifier.padding(innerPadding),
        )
```

Change `PlanesLista`'s signature and body (currently around lines 139-185):
```kotlin
@Suppress("LongParameterList")
@Composable
private fun PlanesLista(
    cargando: Boolean,
    planes: List<PlanFinanciero>,
    resumenesPorPlan: Map<String, ResumenMensualPlan>,
    invitaciones: List<AccesoPlan>,
    onAceptarInvitacion: (String) -> Unit,
    onAbrirPlan: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (cargando) {
        Box(
            modifier = modifier.fillMaxSize().testTag("planes_cargando"),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (invitaciones.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.plans_invitations_title),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            items(invitaciones, key = { it.planId }) { acceso ->
                InvitacionCard(
                    acceso = acceso,
                    onAceptar = { onAceptarInvitacion(acceso.planId) },
                )
            }
        }
        if (planes.isEmpty() && invitaciones.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.plans_empty),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        } else {
            items(planes, key = { it.id }) { plan ->
                PlanCard(
                    plan = plan,
                    resumen = resumenesPorPlan[plan.id],
                    onClick = { onAbrirPlan(plan.id) },
                )
            }
        }
    }
}
```
(The `LazyColumn` body is unchanged from today — only the new early-return `if (cargando)` block
and the new `cargando: Boolean` parameter are added.)

- [ ] **Step 3: Run tests to verify they pass**

Run: `./gradlew :feature:planes:testDebugUnitTest --tests "*.PlanesScreenTest" --rerun-tasks`
Expected: all 5 tests pass (2 existing `PlanCard` tests + 3 new).

- [ ] **Step 4: Mutation check**

Temporarily hardcode `PlanesLista`'s `if (cargando)` to `if (false)`. Re-run the same test command,
confirm `muestra el spinner en el area de lista...` fails. Restore, confirm all 5 pass again.

- [ ] **Step 5: detekt**

Run: `./gradlew :feature:planes:detekt`
Expected: BUILD SUCCESSFUL, no findings.

- [ ] **Step 6: Build verification**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add feature/planes/src/main/java/com/agoitdev/spenvo/planes/PlanesScreen.kt \
        feature/planes/src/test/java/com/agoitdev/spenvo/planes/PlanesScreenTest.kt
git commit -m "feat(planes): show a loading spinner in the list area instead of flashing empty state"
```

---

## Task 3: Full verification and CHANGELOG

**Files:**
- Modify: `CHANGELOG.md`

- [ ] **Step 1: Full build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: All unit tests**

Run: `./gradlew testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests green project-wide.

- [ ] **Step 3: Lint**

Run: `./gradlew lintDebug`
Expected: BUILD SUCCESSFUL, no `HardcodedText`/`MissingTranslation` errors (this change adds no
new string resources, only a `testTag`, so this should be a no-op check).

- [ ] **Step 4: detekt**

Run: `./gradlew detekt`
Expected: BUILD SUCCESSFUL, no findings.

- [ ] **Step 5: Update CHANGELOG.md**

Add under `## [Unreleased]` → `### Fixed` (the section already exists from the prior merged
sub-project):

```markdown
- Planes screen briefly showed "no plans yet" on cold start before the real plan list (or a
  pending invitation) loaded in, because `planes`/`invitacionesPendientes` started at a synthetic
  empty-list `StateFlow` value indistinguishable from "genuinely has none". Both flows now
  distinguish "not loaded yet" from "loaded and empty" internally, and the list area shows a
  centered spinner while loading — covering both the anonymous-session-establishment window and
  the initial Room query — instead of flashing the empty state. The top bar and "create plan" FAB
  are unaffected.
```

- [ ] **Step 6: Commit**

```bash
git add CHANGELOG.md
git commit -m "docs: changelog entry for planes loading feedback"
```

---

## Self-review notes (for whoever executes this plan)

- Every step's code is complete and copy-pasteable from files actually read during planning
  (`PlanesViewModel.kt`, `PlanesScreen.kt`, `PlanesViewModelTest.kt`, `PlanesScreenTest.kt`,
  `Sesion.kt`, `strings.xml`) — no placeholder fakes or guessed constructors.
- Task 1's `flowOf<List<X>?>(null)` covariance note is the one place where the exact Kotlin
  compiler behavior wasn't verified by actually compiling it during planning — the fallback is
  given inline; whoever executes Step 3 should just try the simpler form first and fall back if
  the compiler disagrees.
- Task 2's `PlanesScreenTest.kt` rewrite duplicates fakes already present in `PlanesViewModelTest.kt`
  under a `...Screen` suffix, matching the established convention elsewhere in this codebase
  (`:feature:movimientos`'s `...Form`/`...Panel` suffixed fakes across different test files for the
  same module) rather than introducing a new shared test-fixtures module.
