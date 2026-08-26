# Movimientos Edit Modal — Implementation Plan

> **For agentic workers:** Use `mobiai-mobile-executing-plans-with-subagents` (recommended) or
> `mobiai-mobile-executing-plans` to implement this plan task-by-task. Steps use checkbox syntax
> for tracking.

**Goal:** Fix the type-chip and category-icon bugs in the movimiento edit modal, and add a
view-before-edit interaction model (Editar/Cancelar, Eliminar gated behind edit mode), per
`doc/designs/2026-08-26-movimientos-edit-modal-design.md`.

**Architecture:** All changes are local Compose state + one shared `:core:designsystem` dialog
promotion. No ViewModel contract changes, no navigation changes, no DI changes.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), JUnit4 + Robolectric + `ui-test-junit4`
(hand-written fakes, no MockK), detekt.

**Platform:** Android.

---

## Task 1: Fix the category-loading race condition

**Files:**
- Modify: `feature/movimientos/src/main/java/com/agoitdev/spenvo/movimientos/MovimientoFormSheet.kt:120-124`
- Test: `feature/movimientos/src/test/java/com/agoitdev/spenvo/movimientos/MovimientoFormSheetTest.kt` (new file)

- [ ] **Step 1: Write the failing test**

Create `feature/movimientos/src/test/java/com/agoitdev/spenvo/movimientos/MovimientoFormSheetTest.kt`:

```kotlin
package com.agoitdev.spenvo.movimientos

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.agoitdev.spenvo.data.remote.sync.MovimientoSincronizacion
import com.agoitdev.spenvo.domain.model.Categoria
import com.agoitdev.spenvo.domain.model.Gasto
import com.agoitdev.spenvo.domain.model.Ingreso
import com.agoitdev.spenvo.domain.model.Monto
import com.agoitdev.spenvo.domain.model.Sesion
import com.agoitdev.spenvo.domain.model.TipoCategoria
import com.agoitdev.spenvo.domain.repository.AuthRepository
import com.agoitdev.spenvo.domain.repository.CategoriaRepository
import com.agoitdev.spenvo.domain.repository.MovimientoRepository
import com.agoitdev.spenvo.domain.sync.ConflictosPendientes
import com.agoitdev.spenvo.domain.usecase.ActualizarGastoUseCase
import com.agoitdev.spenvo.domain.usecase.ActualizarIngresoUseCase
import com.agoitdev.spenvo.domain.usecase.AplicarGastoRemotoUseCase
import com.agoitdev.spenvo.domain.usecase.AplicarIngresoRemotoUseCase
import com.agoitdev.spenvo.domain.usecase.CrearGastoUseCase
import com.agoitdev.spenvo.domain.usecase.CrearIngresoUseCase
import com.agoitdev.spenvo.domain.usecase.EliminarGastoUseCase
import com.agoitdev.spenvo.domain.usecase.EliminarIngresoUseCase
import com.agoitdev.spenvo.domain.usecase.ObservarCategoriasPorTipoUseCase
import com.agoitdev.spenvo.domain.usecase.ObservarCategoriasUseCase
import com.agoitdev.spenvo.domain.usecase.ObservarMovimientosUseCase
import com.agoitdev.spenvo.domain.usecase.ValidarMontoUseCase
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
class MovimientoFormSheetTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val movimientoRepo = FakeMovimientoRepositorioForm()
    private val categoriaRepo = FakeCategoriaRepositorioForm()
    private val sincronizador = FakeMovimientoSincronizacionForm()
    private val authRepository = FakeAuthRepositorioForm()

    private val gasto = Gasto(
        id = "g1",
        planId = "p1",
        categoriaId = "cat-transporte",
        monto = Monto(1500),
        fecha = LocalDate.of(2026, 8, 22),
        creadoPor = "user-1",
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun crearViewModel() = MovimientosViewModel(
        observarMovimientos = ObservarMovimientosUseCase(movimientoRepo),
        observarCategoriasPorTipo = ObservarCategoriasPorTipoUseCase(categoriaRepo),
        observarCategorias = ObservarCategoriasUseCase(categoriaRepo),
        crearGasto = CrearGastoUseCase(movimientoRepo, ValidarMontoUseCase()),
        crearIngreso = CrearIngresoUseCase(movimientoRepo, ValidarMontoUseCase()),
        actualizarGasto = ActualizarGastoUseCase(movimientoRepo, ValidarMontoUseCase()),
        eliminarGasto = EliminarGastoUseCase(movimientoRepo),
        actualizarIngreso = ActualizarIngresoUseCase(movimientoRepo, ValidarMontoUseCase()),
        eliminarIngreso = EliminarIngresoUseCase(movimientoRepo),
        aplicarGastoRemoto = AplicarGastoRemotoUseCase(movimientoRepo),
        aplicarIngresoRemoto = AplicarIngresoRemotoUseCase(movimientoRepo),
        sincronizador = sincronizador,
        authRepository = authRepository,
        conflictosPendientes = ConflictosPendientes(),
    )

    @Test
    fun `categoria guardada se mantiene aunque la lista de categorias llegue vacia primero`() {
        var guardado: MovimientoFormDatos? = null
        val viewModel = crearViewModel()

        composeTestRule.setContent {
            MovimientoFormEstadoYContenido(
                planId = "p1",
                tipoInicial = TipoCategoria.GASTO,
                cargando = false,
                viewModel = viewModel,
                movimientoExistente = gasto,
                acciones = MovimientoFormAcciones(
                    onGuardar = { guardado = it },
                    onDismiss = {},
                    onEliminar = {},
                ),
            )
        }

        // Real data arrives after the initial empty StateFlow value -- the exact race the bug depends on.
        categoriaRepo.categorias.value = listOf(
            Categoria(id = "cat-comida", planId = "p1", nombre = "Comida", tipo = TipoCategoria.GASTO),
            Categoria(id = "cat-transporte", planId = "p1", nombre = "Transporte", tipo = TipoCategoria.GASTO),
        )
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Editar").performClick()
        composeTestRule.onNodeWithText("Guardar").performClick()

        assertEquals("cat-transporte", guardado?.categoriaId)
    }
}

private class FakeMovimientoRepositorioForm : MovimientoRepository {
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

private class FakeCategoriaRepositorioForm : CategoriaRepository {
    val categorias = MutableStateFlow<List<Categoria>>(emptyList())
    override fun observarCategorias(planId: String): Flow<List<Categoria>> = categorias
    override fun observarCategoriasPorTipo(planId: String, tipo: TipoCategoria): Flow<List<Categoria>> = categorias
    override suspend fun crearCategoria(categoria: Categoria) = Unit
    override suspend fun crearCategorias(categorias: List<Categoria>) = Unit
    override suspend fun actualizarCategoria(categoria: Categoria) = Unit
    override suspend fun eliminarCategoria(categoria: Categoria) = Unit
}

private class FakeMovimientoSincronizacionForm : MovimientoSincronizacion {
    override fun sincronizar(planId: String): Flow<Unit> = flowOf(Unit)
}

private class FakeAuthRepositorioForm : AuthRepository {
    override fun observeSesion(): Flow<Sesion> = flowOf(Sesion(uid = "user-1", esAnonima = true))
    override suspend fun iniciarSesionAnonima() = Unit
    override suspend fun vincularEmail(email: String, password: String, nombre: String) = Unit
    override suspend fun actualizarPerfil(nombre: String?, photoUrl: String?) = Unit
    override suspend fun cerrarSesion() = Unit
}
```

Note: this test references an "Editar" button (`onNodeWithText("Editar")`) that doesn't exist yet
— it will exist after Task 4. For Task 1 alone, temporarily replace that click + the assertion
with a direct check that doesn't depend on Task 4's UI: **skip Steps 1-2 of this exact form and
instead follow Task 1a below**, then extend this same test file in Task 4 once Editar exists.

**Revised Step 1 for Task 1 (no dependency on Task 4):** write the test above but replace the
body from `composeTestRule.onNodeWithText("Editar")...` down to the `assertEquals` with:

```kotlin
        composeTestRule.onNodeWithText("Guardar").performClick()

        assertEquals("cat-transporte", guardado?.categoriaId)
```

(Fields are enabled by default until Task 4 lands, so Guardar is reachable directly.)

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :feature:movimientos:testDebugUnitTest --tests "*.MovimientoFormSheetTest"`
Expected: FAIL — `guardado?.categoriaId` is `"cat-comida"` (the first category in the list) instead
of `"cat-transporte"` (the gasto's real, original category).

- [ ] **Step 3: Fix the guard**

Modify `MovimientoFormSheet.kt:120-124`:

```kotlin
    LaunchedEffect(categoriasDisponibles) {
        if (categoriasDisponibles.isNotEmpty() && categoriasDisponibles.none { it.id == categoriaId }) {
            categoriaId = categoriasDisponibles.first().id
        }
    }
```

(Replaces the old `if (categoriaId.isBlank() || categoriasDisponibles.none { it.id == categoriaId })`
guard, which fired — and wiped `categoriaId` — while the list was still the transient empty
`StateFlow` initial value.)

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :feature:movimientos:testDebugUnitTest --tests "*.MovimientoFormSheetTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add feature/movimientos/src/main/java/com/agoitdev/spenvo/movimientos/MovimientoFormSheet.kt \
        feature/movimientos/src/test/java/com/agoitdev/spenvo/movimientos/MovimientoFormSheetTest.kt
git commit -m "fix(movimientos): stop category resetting to first while the list is still loading"
```

---

## Task 2: Disable type chips for an existing movimiento

**Files:**
- Modify: `feature/movimientos/src/main/java/com/agoitdev/spenvo/movimientos/MovimientosScaffoldPartes.kt:74-91`
- Modify: `feature/movimientos/src/main/java/com/agoitdev/spenvo/movimientos/MovimientoFormSheet.kt:185` (the `FiltroTipoMovimiento` call site)
- Test: `feature/movimientos/src/test/java/com/agoitdev/spenvo/movimientos/MovimientoFormSheetTest.kt` (extend)

- [ ] **Step 1: Write the failing test**

Add to `MovimientoFormSheetTest`:

```kotlin
    @Test
    fun `chips de tipo estan deshabilitados al editar y muestran el tipo real marcado`() {
        val viewModel = crearViewModel()

        composeTestRule.setContent {
            MovimientoFormEstadoYContenido(
                planId = "p1",
                tipoInicial = TipoCategoria.GASTO,
                cargando = false,
                viewModel = viewModel,
                movimientoExistente = gasto,
                acciones = MovimientoFormAcciones(onGuardar = {}, onDismiss = {}, onEliminar = {}),
            )
        }

        composeTestRule.onNodeWithText("Gasto").assertIsDisplayed().assertIsNotEnabled()
        composeTestRule.onNodeWithText("Ingreso").assertIsDisplayed().assertIsNotEnabled()
    }
```

Add the two new imports to the test file: `androidx.compose.ui.test.assertIsDisplayed` and
`androidx.compose.ui.test.assertIsNotEnabled`.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :feature:movimientos:testDebugUnitTest --tests "*.MovimientoFormSheetTest"`
Expected: FAIL — both chips are enabled (clickable) today regardless of `movimientoExistente`.

- [ ] **Step 3: Add the `habilitado` parameter**

Modify `MovimientosScaffoldPartes.kt:74-91`:

```kotlin
@Composable
internal fun FiltroTipoMovimiento(
    tipoSeleccionado: TipoCategoria,
    onTipoChange: (TipoCategoria) -> Unit,
    modifier: Modifier = Modifier,
    habilitado: Boolean = true,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = tipoSeleccionado == TipoCategoria.GASTO,
            onClick = { onTipoChange(TipoCategoria.GASTO) },
            enabled = habilitado,
            label = { Text(stringResource(R.string.movements_filter_expense)) },
        )
        FilterChip(
            selected = tipoSeleccionado == TipoCategoria.INGRESO,
            onClick = { onTipoChange(TipoCategoria.INGRESO) },
            enabled = habilitado,
            label = { Text(stringResource(R.string.movements_filter_income)) },
        )
    }
}
```

Modify the call site in `MovimientoFormSheet.kt:185` (inside `MovimientoFormContenido`):

```kotlin
        FiltroTipoMovimiento(tipoSeleccionado = tipo, onTipoChange = onTipoChange, habilitado = !editando)
```

(`editando` is the existing `movimientoExistente != null` boolean already threaded into
`MovimientoFormContenido` — reused here, no new parameter needed for this step.)

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :feature:movimientos:testDebugUnitTest --tests "*.MovimientoFormSheetTest"`
Expected: PASS — both tests in the file pass.

- [ ] **Step 5: Commit**

```bash
git add feature/movimientos/src/main/java/com/agoitdev/spenvo/movimientos/MovimientosScaffoldPartes.kt \
        feature/movimientos/src/main/java/com/agoitdev/spenvo/movimientos/MovimientoFormSheet.kt \
        feature/movimientos/src/test/java/com/agoitdev/spenvo/movimientos/MovimientoFormSheetTest.kt
git commit -m "fix(movimientos): lock the type chips when editing an existing movimiento"
```

---

## Task 3: Add the "Editar" action string

**Files:**
- Modify: `feature/movimientos/src/main/res/values/strings.xml`
- Modify: `feature/movimientos/src/main/res/values-en/strings.xml`

- [ ] **Step 1: Add the Spanish string**

In `feature/movimientos/src/main/res/values/strings.xml`, next to the existing `movements_edit` key:

```xml
    <string name="movements_edit_action">Editar</string>
```

- [ ] **Step 2: Add the English string**

In `feature/movimientos/src/main/res/values-en/strings.xml`, next to `movements_edit`:

```xml
    <string name="movements_edit_action">Edit</string>
```

(`movements_edit` stays as-is — it's the sheet *title* "Editar movimiento"/"Edit transaction".
`movements_edit_action` is the standalone button label "Editar"/"Edit", kept distinct so
`onNodeWithText("Editar")` in tests unambiguously targets the button, not the title.)

- [ ] **Step 3: Verify lint passes (both files must have matching key sets)**

Run: `./gradlew :feature:movimientos:lintDebug`
Expected: PASS — no `MissingTranslation` error.

- [ ] **Step 4: Commit**

```bash
git add feature/movimientos/src/main/res/values/strings.xml feature/movimientos/src/main/res/values-en/strings.xml
git commit -m "feat(movimientos): add the Editar action string"
```

---

## Task 4: View-before-edit gating (Editar / Cancelar / Eliminar-after-Editar)

**Files:**
- Modify: `feature/movimientos/src/main/java/com/agoitdev/spenvo/movimientos/MovimientoFormSheet.kt`
  (`MovimientoFormEstadoYContenido`, `MovimientoFormContenido`, `SelectorCategoria`)
- Test: `feature/movimientos/src/test/java/com/agoitdev/spenvo/movimientos/MovimientoFormSheetTest.kt` (extend)

- [ ] **Step 1: Write the failing tests**

Add to `MovimientoFormSheetTest` (and add import `androidx.compose.ui.test.assertIsEnabled`,
`androidx.compose.ui.test.onNodeWithText` already present):

```kotlin
    @Test
    fun `editar movimiento existente abre en modo vista con campos deshabilitados`() {
        val viewModel = crearViewModel()

        composeTestRule.setContent {
            MovimientoFormEstadoYContenido(
                planId = "p1",
                tipoInicial = TipoCategoria.GASTO,
                cargando = false,
                viewModel = viewModel,
                movimientoExistente = gasto,
                acciones = MovimientoFormAcciones(onGuardar = {}, onDismiss = {}, onEliminar = {}),
            )
        }

        composeTestRule.onNodeWithText("Monto").assertIsNotEnabled()
        composeTestRule.onNodeWithText("Editar").assertIsDisplayed()
        composeTestRule.onNodeWithText("Guardar").assertDoesNotExist()
        composeTestRule.onNodeWithText("Cancelar").assertDoesNotExist()
        composeTestRule.onNodeWithText("Eliminar").assertDoesNotExist()
    }

    @Test
    fun `pulsar editar habilita campos y muestra guardar cancelar y eliminar`() {
        val viewModel = crearViewModel()

        composeTestRule.setContent {
            MovimientoFormEstadoYContenido(
                planId = "p1",
                tipoInicial = TipoCategoria.GASTO,
                cargando = false,
                viewModel = viewModel,
                movimientoExistente = gasto,
                acciones = MovimientoFormAcciones(onGuardar = {}, onDismiss = {}, onEliminar = {}),
            )
        }

        composeTestRule.onNodeWithText("Editar").performClick()

        composeTestRule.onNodeWithText("Monto").assertIsEnabled()
        composeTestRule.onNodeWithText("Guardar").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancelar").assertIsDisplayed()
        composeTestRule.onNodeWithText("Eliminar").assertIsDisplayed()
    }

    @Test
    fun `cancelar revierte los cambios y vuelve a modo vista sin cerrar el sheet`() {
        val viewModel = crearViewModel()

        composeTestRule.setContent {
            MovimientoFormEstadoYContenido(
                planId = "p1",
                tipoInicial = TipoCategoria.GASTO,
                cargando = false,
                viewModel = viewModel,
                movimientoExistente = gasto,
                acciones = MovimientoFormAcciones(onGuardar = {}, onDismiss = {}, onEliminar = {}),
            )
        }

        composeTestRule.onNodeWithText("Editar").performClick()
        composeTestRule.onNodeWithText("Monto").performTextReplacement("999")
        composeTestRule.onNodeWithText("Cancelar").performClick()

        composeTestRule.onNodeWithText("15").assertIsDisplayed() // Monto(1500) -> "15"
        composeTestRule.onNodeWithText("Editar").assertIsDisplayed()
        composeTestRule.onNodeWithText("Guardar").assertDoesNotExist()
    }

    @Test
    fun `crear movimiento nuevo no muestra el boton editar`() {
        val viewModel = crearViewModel()

        composeTestRule.setContent {
            MovimientoFormEstadoYContenido(
                planId = "p1",
                tipoInicial = TipoCategoria.GASTO,
                cargando = false,
                viewModel = viewModel,
                movimientoExistente = null,
                acciones = MovimientoFormAcciones(onGuardar = {}, onDismiss = {}, onEliminar = null),
            )
        }

        composeTestRule.onNodeWithText("Editar").assertDoesNotExist()
        composeTestRule.onNodeWithText("Monto").assertIsEnabled()
        composeTestRule.onNodeWithText("Guardar").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancelar").assertDoesNotExist()
        composeTestRule.onNodeWithText("Eliminar").assertDoesNotExist()
    }
```

Add import `androidx.compose.ui.test.performTextReplacement` to the test file.

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :feature:movimientos:testDebugUnitTest --tests "*.MovimientoFormSheetTest"`
Expected: FAIL on all four new tests — today the sheet always opens fully editable, there is no
"Editar" button, and Cancelar doesn't exist.

- [ ] **Step 3: Implement the view/edit state machine**

Replace `MovimientoFormEstadoYContenido` in `MovimientoFormSheet.kt` with:

```kotlin
@Suppress("LongParameterList")
@Composable
internal fun MovimientoFormEstadoYContenido(
    planId: String,
    tipoInicial: TipoCategoria,
    cargando: Boolean,
    viewModel: MovimientosViewModel,
    acciones: MovimientoFormAcciones,
    movimientoExistente: Movimiento? = null,
) {
    var tipo by rememberSaveable { mutableStateOf(movimientoExistente?.let { tipoDeMovimiento(it) } ?: tipoInicial) }
    var categoriaId by rememberSaveable { mutableStateOf(movimientoExistente?.categoriaId.orEmpty()) }
    var montoTexto by rememberSaveable {
        mutableStateOf(movimientoExistente?.monto?.let { montoInicialTexto(it) }.orEmpty())
    }
    var descripcion by rememberSaveable { mutableStateOf(movimientoExistente?.descripcion.orEmpty()) }
    var errorLocal by remember { mutableStateOf<Int?>(null) }
    var modoEdicion by rememberSaveable { mutableStateOf(movimientoExistente == null) }

    val categoriasFlow = remember(planId, tipo) { viewModel.categorias(planId, tipo) }
    val categoriasDisponibles by categoriasFlow.collectAsStateWithLifecycle()

    LaunchedEffect(categoriasDisponibles) {
        if (categoriasDisponibles.isNotEmpty() && categoriasDisponibles.none { it.id == categoriaId }) {
            categoriaId = categoriasDisponibles.first().id
        }
    }

    MovimientoFormContenido(
        editando = movimientoExistente != null,
        modoEdicion = modoEdicion,
        tipo = tipo,
        onTipoChange = { tipo = it },
        montoTexto = montoTexto,
        onMontoChange = { montoTexto = it },
        categoriasDisponibles = categoriasDisponibles,
        categoriaId = categoriaId,
        onCategoriaChange = { categoriaId = it },
        descripcion = descripcion,
        onDescripcionChange = { descripcion = it },
        errorLocal = errorLocal,
        cargando = cargando,
        onEditarClick = { modoEdicion = true },
        onCancelarClick = {
            categoriaId = movimientoExistente?.categoriaId.orEmpty()
            montoTexto = movimientoExistente?.monto?.let { montoInicialTexto(it) }.orEmpty()
            descripcion = movimientoExistente?.descripcion.orEmpty()
            errorLocal = null
            modoEdicion = false
        },
        onGuardarClick = {
            val entrada = MovimientoFormEntrada(
                planId = planId,
                tipo = tipo,
                categoriaId = categoriaId,
                montoTexto = montoTexto,
                descripcion = descripcion,
                fecha = movimientoExistente?.fecha ?: LocalDate.now(),
            )
            errorLocal = validarYGuardar(entrada, acciones.onGuardar)
        },
        onEliminar = acciones.onEliminar,
    )
}
```

Replace `MovimientoFormContenido` with:

```kotlin
@Suppress("LongParameterList")
@Composable
private fun MovimientoFormContenido(
    editando: Boolean,
    modoEdicion: Boolean,
    tipo: TipoCategoria,
    onTipoChange: (TipoCategoria) -> Unit,
    montoTexto: String,
    onMontoChange: (String) -> Unit,
    categoriasDisponibles: List<Categoria>,
    categoriaId: String,
    onCategoriaChange: (String) -> Unit,
    descripcion: String,
    onDescripcionChange: (String) -> Unit,
    errorLocal: Int?,
    cargando: Boolean,
    onEditarClick: () -> Unit,
    onCancelarClick: () -> Unit,
    onGuardarClick: () -> Unit,
    onEliminar: (() -> Unit)?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .imePadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(if (editando) R.string.movements_edit else R.string.movements_add),
            style = MaterialTheme.typography.titleMedium,
        )
        FiltroTipoMovimiento(tipoSeleccionado = tipo, onTipoChange = onTipoChange, habilitado = !editando)
        OutlinedTextField(
            value = montoTexto,
            onValueChange = onMontoChange,
            label = { Text(stringResource(R.string.movements_amount)) },
            singleLine = true,
            enabled = modoEdicion,
            modifier = Modifier.fillMaxWidth(),
        )
        SelectorCategoria(
            categorias = categoriasDisponibles,
            categoriaSeleccionada = categoriaId,
            onCategoriaChange = onCategoriaChange,
            habilitado = modoEdicion,
        )
        OutlinedTextField(
            value = descripcion,
            onValueChange = onDescripcionChange,
            label = { Text(stringResource(R.string.movements_description)) },
            singleLine = true,
            enabled = modoEdicion,
            modifier = Modifier.fillMaxWidth(),
        )
        errorLocal?.let {
            Text(text = stringResource(it), color = MaterialTheme.colorScheme.error)
        }
        if (editando && !modoEdicion) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onEditarClick) {
                    Text(stringResource(R.string.movements_edit_action))
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (onEliminar != null && editando) Arrangement.SpaceBetween else Arrangement.End,
            ) {
                if (onEliminar != null && editando) {
                    TextButton(onClick = onEliminar, enabled = !cargando) {
                        Text(stringResource(R.string.movements_delete))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (editando) {
                        TextButton(onClick = onCancelarClick, enabled = !cargando) {
                            Text(stringResource(R.string.movements_cancel))
                        }
                    }
                    TextButton(enabled = !cargando, onClick = onGuardarClick) {
                        if (cargando) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        } else {
                            Text(stringResource(R.string.movements_save))
                        }
                    }
                }
            }
        }
    }
}
```

Replace `SelectorCategoria` with:

```kotlin
@Composable
private fun SelectorCategoria(
    categorias: List<Categoria>,
    categoriaSeleccionada: String,
    onCategoriaChange: (String) -> Unit,
    habilitado: Boolean,
    modifier: Modifier = Modifier,
) {
    LazyRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(categorias, key = { it.id }) { categoria ->
            val seleccionada = categoria.id == categoriaSeleccionada
            Surface(
                onClick = { onCategoriaChange(categoria.id) },
                enabled = habilitado,
                shape = CircleShape,
                color = if (seleccionada) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                modifier = Modifier.size(48.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(imageVector = iconoParaClave(categoria.icono), contentDescription = categoria.nombre)
                }
            }
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :feature:movimientos:testDebugUnitTest --tests "*.MovimientoFormSheetTest"`
Expected: PASS — all 7 tests in the file (Tasks 1, 2, and 4's tests).

- [ ] **Step 5: Commit**

```bash
git add feature/movimientos/src/main/java/com/agoitdev/spenvo/movimientos/MovimientoFormSheet.kt \
        feature/movimientos/src/test/java/com/agoitdev/spenvo/movimientos/MovimientoFormSheetTest.kt
git commit -m "feat(movimientos): view-before-edit gating with Editar/Cancelar, Eliminar after Editar"
```

- [ ] **Step 6: Build verification**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

---

## Task 5: Promote the delete-confirmation dialog to `:core:designsystem`

**Files:**
- Create: `core/designsystem/src/main/java/com/agoitdev/spenvo/designsystem/components/ConfirmarEliminarDialog.kt`
- Modify: `feature/categorias/src/main/java/com/agoitdev/spenvo/categorias/CategoriasScreen.kt:125-163,346-364`
- Modify: `feature/movimientos/src/main/java/com/agoitdev/spenvo/movimientos/MovimientoFormularioHost.kt:96-165`
- Test: `feature/movimientos/src/test/java/com/agoitdev/spenvo/movimientos/MovimientoFormSheetTest.kt` (extend)

- [ ] **Step 1: Write the failing test (movimientos side)**

Add to `MovimientoFormSheetTest`. This test drives the whole host (`MovimientoFormularioSheet`,
not the bare `MovimientoFormEstadoYContenido`) since the confirmation dialog lives in the host,
one level above what the earlier tests target:

```kotlin
    @Test
    fun `eliminar abre el dialogo de confirmacion compartido y elimina al confirmar`() {
        val viewModel = crearViewModel()
        val parametros = MovimientoFormularioParametros(
            planId = "p1",
            tipoPorDefecto = TipoCategoria.GASTO,
            formulario = FormularioMovimiento.Editar(gasto),
            viewModel = viewModel,
            cargando = false,
            onCerrar = {},
        )

        composeTestRule.setContent { MovimientoFormularioSheet(parametros) }

        composeTestRule.onNodeWithText("Editar").performClick()
        composeTestRule.onNodeWithText("Eliminar").performClick()

        composeTestRule.onNodeWithText("Eliminar movimiento").assertIsDisplayed()
        composeTestRule.onNodeWithText("Esta acción no se puede deshacer.").assertIsDisplayed()

        composeTestRule.onAllNodesWithText("Eliminar")[1].performClick()

        composeTestRule.onNodeWithText("Eliminar movimiento").assertDoesNotExist()
    }
```

Add import `androidx.compose.ui.test.onAllNodesWithText` to the test file.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :feature:movimientos:testDebugUnitTest --tests "*.MovimientoFormSheetTest"`
Expected: currently PASSES against the *old*, still-private `ConfirmarEliminarMovimientoDialog` —
confirms today's behavior already matches (movimientos already had this dialog, per the design
doc's correction). This step is a **characterization test**: it must stay green through the
refactor below; if it goes red at Step 4, the promotion broke behavior, not fixed a bug.

- [ ] **Step 3: Create the shared dialog and switch both features to it**

Create `core/designsystem/src/main/java/com/agoitdev/spenvo/designsystem/components/ConfirmarEliminarDialog.kt`:

```kotlin
package com.agoitdev.spenvo.designsystem.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

data class ConfirmarEliminarTextos(
    val titulo: String,
    val mensaje: String,
    val confirmar: String,
    val cancelar: String,
)

@Composable
fun ConfirmarEliminarDialog(
    textos: ConfirmarEliminarTextos,
    onConfirmar: () -> Unit,
    onCancelar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onCancelar,
        modifier = modifier,
        icon = { Icon(imageVector = Icons.Filled.Delete, contentDescription = null) },
        title = { Text(textos.titulo) },
        text = { Text(textos.mensaje) },
        confirmButton = {
            TextButton(onClick = onConfirmar) {
                Text(textos.confirmar)
            }
        },
        dismissButton = {
            TextButton(onClick = onCancelar) {
                Text(textos.cancelar)
            }
        },
    )
}
```

In `feature/categorias/.../CategoriasScreen.kt`:
- Delete the private `ConfirmarEliminarDialog` composable (lines 346-364).
- Add import: `com.agoitdev.spenvo.designsystem.components.ConfirmarEliminarDialog` and
  `com.agoitdev.spenvo.designsystem.components.ConfirmarEliminarTextos`.
- In `CategoriaFormularioSheet` (lines 155-163), replace the call:

```kotlin
    if (mostrarConfirmarEliminar && categoriaExistente != null) {
        ConfirmarEliminarDialog(
            textos = ConfirmarEliminarTextos(
                titulo = stringResource(R.string.categories_delete_confirm_title),
                mensaje = stringResource(R.string.categories_delete_confirm_message),
                confirmar = stringResource(R.string.categories_delete),
                cancelar = stringResource(R.string.categories_cancel),
            ),
            onConfirmar = {
                mostrarConfirmarEliminar = false
                viewModel.eliminar(categoriaExistente)
            },
            onCancelar = { mostrarConfirmarEliminar = false },
        )
    }
```

In `feature/movimientos/.../MovimientoFormularioHost.kt`:
- Delete the private `ConfirmarEliminarMovimientoDialog` composable (lines 147-165).
- Add the same two imports as above.
- In `MovimientoFormularioEstado`, replace the call:

```kotlin
    if (mostrarConfirmarEliminar && movimientoExistente != null) {
        ConfirmarEliminarDialog(
            textos = ConfirmarEliminarTextos(
                titulo = stringResource(R.string.movements_delete_confirm_title),
                mensaje = stringResource(R.string.movements_delete_confirm_message),
                confirmar = stringResource(R.string.movements_delete),
                cancelar = stringResource(R.string.movements_cancel),
            ),
            onConfirmar = {
                mostrarConfirmarEliminar = false
                viewModel.eliminar(movimientoExistente)
            },
            onCancelar = { mostrarConfirmarEliminar = false },
        )
    }
```

Add the `:core:designsystem` composable import (`androidx.compose.ui.res.stringResource`) to
`MovimientoFormularioHost.kt` if not already present.

- [ ] **Step 4: Run test to verify it still passes**

Run: `./gradlew :feature:movimientos:testDebugUnitTest --tests "*.MovimientoFormSheetTest"`
Expected: PASS — same behavior, now backed by the shared component.

- [ ] **Step 5: Commit**

```bash
git add core/designsystem/src/main/java/com/agoitdev/spenvo/designsystem/components/ConfirmarEliminarDialog.kt \
        feature/categorias/src/main/java/com/agoitdev/spenvo/categorias/CategoriasScreen.kt \
        feature/movimientos/src/main/java/com/agoitdev/spenvo/movimientos/MovimientoFormularioHost.kt \
        feature/movimientos/src/test/java/com/agoitdev/spenvo/movimientos/MovimientoFormSheetTest.kt
git commit -m "refactor(designsystem): de-duplicate the confirm-delete dialog from categorias/movimientos"
```

---

## Task 6: Add Compose test infra to `:feature:categorias` and its first Compose test

**Files:**
- Modify: `feature/categorias/build.gradle.kts`
- Test: `feature/categorias/src/test/java/com/agoitdev/spenvo/categorias/CategoriaFormularioSheetTest.kt` (new file)

- [ ] **Step 1: Add the test dependencies**

Modify `feature/categorias/build.gradle.kts` — inside the `android {}` block, add (mirroring
`:feature:movimientos`'s `build.gradle.kts:26-30`):

```kotlin
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
```

In the `dependencies {}` block, replace:

```kotlin
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
```

with:

```kotlin
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.compose.ui.test.junit4)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
```

(The two `debugImplementation` lines are new to this module too — `:feature:movimientos`'s
`build.gradle.kts:65-66` has them outside the `testImplementation` group; Compose UI tests need
them for the manifest-based test activity.)

- [ ] **Step 2: Verify the module still builds with the new deps (no test yet, nothing to fail red)**

Run: `./gradlew :feature:categorias:testDebugUnitTest`
Expected: BUILD SUCCESSFUL (existing `CategoriasViewModelTest` still passes; no Compose test exists yet).

- [ ] **Step 3: Write the failing Compose test**

`CategoriaFormularioSheet` (the private composable hosting the confirm dialog) is file-private to
`CategoriasScreen.kt`, unlike movimientos' equivalent (`internal`) — so this test drives it through
the public `CategoriasScreen(planId, viewModel, modifier)`, passing a real (non-Hilt) `viewModel`
argument directly, same technique `MovimientosScreenListDetailTest` already uses for
`MovimientosPantallaCompacta`.

Create `feature/categorias/src/test/java/com/agoitdev/spenvo/categorias/CategoriaFormularioSheetTest.kt`:

```kotlin
package com.agoitdev.spenvo.categorias

import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.agoitdev.spenvo.data.remote.sync.CategoriaSincronizacion
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
class CategoriaFormularioSheetTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val categoria = Categoria(
        id = "cat-comida",
        planId = "p1",
        nombre = "Comida",
        tipo = TipoCategoria.GASTO,
    )
    private val repo = FakeCategoriaRepositorioSheet(listOf(categoria))
    private val sincronizador = FakeCategoriaSincronizacionSheet()
    private val authRepository = FakeAuthRepositorioSheet()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun crearViewModel() = CategoriasViewModel(
        observarCategoriasPorTipo = ObservarCategoriasPorTipoUseCase(repo),
        crearCategoria = CrearCategoriaUseCase(repo),
        actualizarCategoria = ActualizarCategoriaUseCase(repo),
        eliminarCategoria = EliminarCategoriaUseCase(repo),
        sincronizador = sincronizador,
        authRepository = authRepository,
    )

    @Test
    fun `eliminar categoria abre el dialogo compartido y confirma el borrado`() {
        val viewModel = crearViewModel()

        composeTestRule.setContent {
            CategoriasScreen(planId = "p1", viewModel = viewModel)
        }

        composeTestRule.onNodeWithText("Comida").performClick()
        composeTestRule.onNodeWithText("Eliminar").performClick()

        composeTestRule.onNodeWithText("Eliminar categoría").assertIsDisplayed()
        composeTestRule.onNodeWithText("Esta acción no se puede deshacer.").assertIsDisplayed()

        composeTestRule.onAllNodesWithText("Eliminar")[1].performClick()

        composeTestRule.onNodeWithText("Eliminar categoría").assertDoesNotExist()
        assertEquals(listOf(categoria), repo.eliminadas)
    }
}

private class FakeCategoriaRepositorioSheet(
    private val categorias: List<Categoria> = emptyList(),
) : CategoriaRepository {
    val eliminadas = mutableListOf<Categoria>()
    override fun observarCategorias(planId: String): Flow<List<Categoria>> =
        flowOf(categorias.filter { it.planId == planId })
    override fun observarCategoriasPorTipo(planId: String, tipo: TipoCategoria): Flow<List<Categoria>> =
        flowOf(categorias.filter { it.planId == planId && it.tipo == tipo })
    override suspend fun crearCategoria(categoria: Categoria) = Unit
    override suspend fun crearCategorias(categorias: List<Categoria>) = Unit
    override suspend fun actualizarCategoria(categoria: Categoria) = Unit
    override suspend fun eliminarCategoria(categoria: Categoria) {
        eliminadas.add(categoria)
    }
}

private class FakeCategoriaSincronizacionSheet : CategoriaSincronizacion {
    override fun sincronizar(planId: String): Flow<Unit> = flowOf(Unit)
}

private class FakeAuthRepositorioSheet : AuthRepository {
    override fun observeSesion(): Flow<Sesion> = flowOf(Sesion(uid = "user-1", esAnonima = false))
    override suspend fun iniciarSesionAnonima() = Unit
    override suspend fun actualizarPerfil(nombre: String?, photoUrl: String?) = Unit
    override suspend fun cerrarSesion() = Unit
    override suspend fun vincularEmail(email: String, password: String, nombre: String) = Unit
}
```

This is a characterization test like Task 5's — the delete flow already works today (via the
now-shared dialog from Task 5), so it's expected to fail only because the module has no Compose
test infra yet (compile failure) until Step 1 lands, then pass once Step 1's dependencies are in
place — there is no production-code bug for this step to catch, only new coverage.

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew :feature:categorias:testDebugUnitTest --tests "*.CategoriaFormularioSheetTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add feature/categorias/build.gradle.kts \
        feature/categorias/src/test/java/com/agoitdev/spenvo/categorias/CategoriaFormularioSheetTest.kt
git commit -m "test(categorias): add Compose UI test infra and first screen-level test"
```

---

## Task 7: Full verification and CHANGELOG

**Files:**
- Modify: `CHANGELOG.md`

- [ ] **Step 1: Full build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: All unit tests**

Run: `./gradlew testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests green (including the new `MovimientoFormSheetTest` and
`CategoriaFormularioSheetTest`).

- [ ] **Step 3: Lint**

Run: `./gradlew lintDebug`
Expected: BUILD SUCCESSFUL, no `HardcodedText`/`MissingTranslation` errors (verifies
`movements_edit_action` landed in both `values/` and `values-en/`).

- [ ] **Step 4: detekt**

Run: `./gradlew detekt`
Expected: BUILD SUCCESSFUL, no findings.

- [ ] **Step 5: Update CHANGELOG.md**

Add under `## [Unreleased]` → `### Fixed` (create the subsection if it doesn't exist yet in the
unreleased block):

```markdown
### Fixed

- Movimientos edit modal: the type (Gasto/Ingreso) could be changed on an existing movimiento via
  the type chips, even though `Gasto`/`Ingreso` are separate domain types with their own storage —
  the chips are now locked (shown, disabled) whenever editing an existing movimiento. The category
  selector also no longer resets to the plan's first category on open: the reset guard was firing
  while the categories `StateFlow` was still at its transient empty initial value, before the real
  list loaded, silently losing the movimiento's actual category if the user saved without touching
  the selector.
- Movimientos edit modal now opens read-only for an existing movimiento, with an explicit "Editar"
  action to enable fields; Cancelar reverts unsaved changes and returns to the read-only view
  instead of dismissing the sheet; Eliminar is now only reachable after Editar.
- De-duplicated the delete-confirmation dialog that `:feature:categorias` and `:feature:movimientos`
  each had privately implemented — both now use a shared `ConfirmarEliminarDialog` in
  `:core:designsystem`. `:feature:categorias` also gained Compose UI test infrastructure
  (Robolectric + `ui-test-junit4`) and its first screen-level test.
```

- [ ] **Step 6: Commit**

```bash
git add CHANGELOG.md
git commit -m "docs: changelog entry for movimientos edit modal fix + redesign"
```

---

## Self-review notes (for whoever executes this plan)

- Every step's code is complete and copy-pasteable from files actually read during planning
  (`MovimientoFormSheet.kt`, `MovimientosScaffoldPartes.kt`, `MovimientoFormularioHost.kt`,
  `CategoriasScreen.kt`, `CategoriasViewModelTest.kt`, `ConflictoDialog.kt`, both modules'
  `build.gradle.kts` and `strings.xml`, `MovimientosScreenListDetailTest.kt`) — no placeholder
  fakes or guessed constructors.
- Tasks 1-4 modify the same file (`MovimientoFormSheet.kt`) sequentially and are designed to apply
  cleanly in order (each step's "before" state matches the previous task's "after" state) — do not
  reorder them.
- Task 5's Step 2 is unusual (expected to already pass) — it's a characterization test guarding
  against the refactor changing behavior, not a red-first bugfix test. Flag clearly to whoever
  executes so it isn't mistaken for a broken plan step.
