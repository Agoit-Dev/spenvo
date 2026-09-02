package com.agoitdev.spenvo.movimientos

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.agoitdev.spenvo.data.remote.sync.MovimientoSincronizacion
import com.agoitdev.spenvo.domain.model.Categoria
import com.agoitdev.spenvo.domain.model.Gasto
import com.agoitdev.spenvo.domain.model.Ingreso
import com.agoitdev.spenvo.domain.model.Monto
import com.agoitdev.spenvo.domain.model.Movimiento
import com.agoitdev.spenvo.domain.model.Sesion
import com.agoitdev.spenvo.domain.model.TipoCategoria
import com.agoitdev.spenvo.domain.repository.AuthRepository
import com.agoitdev.spenvo.domain.repository.CategoriaRepository
import com.agoitdev.spenvo.domain.repository.MovimientoRepository
import com.agoitdev.spenvo.domain.sync.ConflictoEdicion
import com.agoitdev.spenvo.domain.sync.RegistroConflictosPendientes
import com.agoitdev.spenvo.domain.usecase.ActualizarGastoUseCase
import com.agoitdev.spenvo.domain.usecase.ActualizarIngresoUseCase
import com.agoitdev.spenvo.domain.usecase.CrearGastoUseCase
import com.agoitdev.spenvo.domain.usecase.CrearIngresoUseCase
import com.agoitdev.spenvo.domain.usecase.EliminarGastoUseCase
import com.agoitdev.spenvo.domain.usecase.EliminarIngresoUseCase
import com.agoitdev.spenvo.domain.usecase.ObservarCategoriasPorTipoUseCase
import com.agoitdev.spenvo.domain.usecase.ObservarCategoriasUseCase
import com.agoitdev.spenvo.domain.usecase.ObservarMovimientosUseCase
import com.agoitdev.spenvo.domain.usecase.ResolverConflictoGastoUsandoLocalUseCase
import com.agoitdev.spenvo.domain.usecase.ResolverConflictoGastoUsandoRemotoUseCase
import com.agoitdev.spenvo.domain.usecase.ResolverConflictoIngresoUsandoLocalUseCase
import com.agoitdev.spenvo.domain.usecase.ResolverConflictoIngresoUsandoRemotoUseCase
import com.agoitdev.spenvo.domain.usecase.ValidarMontoUseCase
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
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

    private val gasto2 = Gasto(
        id = "g2",
        planId = "p1",
        categoriaId = "cat-comida",
        monto = Monto(2500),
        fecha = LocalDate.of(2026, 8, 23),
        creadoPor = "user-1",
    )

    private val categoriasGasto = listOf(
        Categoria(id = "cat-comida", planId = "p1", nombre = "Comida", tipo = TipoCategoria.GASTO),
        Categoria(id = "cat-transporte", planId = "p1", nombre = "Transporte", tipo = TipoCategoria.GASTO),
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
        resolverConflictoGastoUsandoLocal = ResolverConflictoGastoUsandoLocalUseCase(
            movimientoRepo,
            ValidarMontoUseCase(),
        ),
        resolverConflictoIngresoUsandoLocal = ResolverConflictoIngresoUsandoLocalUseCase(
            movimientoRepo,
            ValidarMontoUseCase(),
        ),
        resolverConflictoGastoUsandoRemoto = ResolverConflictoGastoUsandoRemotoUseCase(movimientoRepo),
        resolverConflictoIngresoUsandoRemoto = ResolverConflictoIngresoUsandoRemotoUseCase(movimientoRepo),
        sincronizador = sincronizador,
        authRepository = authRepository,
        registroConflictosPendientes = FakeRegistroConflictosPendientesForm(),
    )

    private fun montarFormulario(
        movimientoExistente: Movimiento? = null,
        onEliminar: (() -> Unit)? = null,
        onGuardar: (MovimientoFormDatos) -> Unit = {},
    ) {
        val viewModel = crearViewModel()
        composeTestRule.setContent {
            MovimientoFormEstadoYContenido(
                planId = "p1",
                tipoInicial = TipoCategoria.GASTO,
                cargando = false,
                viewModel = viewModel,
                movimientoExistente = movimientoExistente,
                acciones = MovimientoFormAcciones(
                    onGuardar = onGuardar,
                    onDismiss = {},
                    onEliminar = onEliminar,
                ),
            )
        }
    }

    @Test
    fun `categoria guardada se mantiene aunque la lista de categorias llegue vacia primero`() {
        var guardado: MovimientoFormDatos? = null
        montarFormulario(
            movimientoExistente = gasto,
            onEliminar = {},
            onGuardar = { guardado = it },
        )

        // Real data arrives after the initial empty StateFlow value -- the exact race the bug depends on.
        categoriaRepo.categorias.value = categoriasGasto
        composeTestRule.waitForIdle()

        // Existing movimiento opens read-only -- enter edit mode before Guardar is available.
        composeTestRule.onNodeWithText("Editar").performClick()
        composeTestRule.onNodeWithText("Guardar").performClick()

        assertEquals("cat-transporte", guardado?.categoriaId)
    }

    @Test
    fun `cambiar de tipo al crear limpia la categoria del tipo anterior`() {
        var guardado: MovimientoFormDatos? = null
        montarFormulario(onGuardar = { guardado = it })

        categoriaRepo.categorias.value = listOf(
            Categoria(id = "cat-comida", planId = "p1", nombre = "Comida", tipo = TipoCategoria.GASTO),
        )
        composeTestRule.waitForIdle()
        // categoriaId is now auto-selected to "cat-comida" (GASTO).

        composeTestRule.onNodeWithText("Ingresos").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Monto").performTextInput("10")
        // Switching tipo must clear the GASTO category -- there is no INGRESO category yet, so
        // Guardar must be blocked, not silently succeed with a GASTO category id under an Ingreso.
        composeTestRule.onNodeWithText("Guardar").performClick()

        assertEquals(null, guardado)
    }

    // Checks that an existing movimiento's stored category id, when it's no longer present in the
    // loaded category list, falls back to the first available category instead of staying invalid.
    @Test
    fun `categoria guardada que ya no existe en la lista cae en la primera`() {
        var guardado: MovimientoFormDatos? = null
        montarFormulario(
            movimientoExistente = Gasto(
                id = "cat-borrada",
                planId = "p1",
                categoriaId = "cat-borrada",
                monto = Monto(1000),
                fecha = LocalDate.of(2026, 8, 22),
                creadoPor = "user-1",
            ),
            onEliminar = {},
            onGuardar = { guardado = it },
        )

        categoriaRepo.categorias.value = categoriasGasto
        composeTestRule.waitForIdle()

        // Existing movimiento opens read-only -- enter edit mode before Guardar is available.
        composeTestRule.onNodeWithText("Editar").performClick()
        composeTestRule.onNodeWithText("Guardar").performClick()

        assertEquals("cat-comida", guardado?.categoriaId)
    }

    // Complementary to the previous test: here categoriaId starts blank (a genuinely new
    // movimiento), not pointing at a since-deleted category.
    @Test
    fun `movimiento nuevo selecciona la primera categoria cuando la lista llega`() {
        var guardado: MovimientoFormDatos? = null
        montarFormulario(onGuardar = { guardado = it })

        categoriaRepo.categorias.value = categoriasGasto
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Monto").performTextInput("10")
        composeTestRule.onNodeWithText("Guardar").performClick()

        assertEquals("cat-comida", guardado?.categoriaId)
    }

    @Test
    fun `re-tocar el chip ya seleccionado no borra la categoria elegida`() {
        var guardado: MovimientoFormDatos? = null
        montarFormulario(onGuardar = { guardado = it })

        categoriaRepo.categorias.value = listOf(
            Categoria(id = "cat-comida", planId = "p1", nombre = "Comida", tipo = TipoCategoria.GASTO),
        )
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Gastos").performClick() // already selected -- must be a no-op
        composeTestRule.onNodeWithText("Monto").performTextInput("10")
        composeTestRule.onNodeWithText("Guardar").performClick()

        assertEquals("cat-comida", guardado?.categoriaId)
    }

    @Test
    fun `chips de tipo estan deshabilitados al editar un movimiento existente`() {
        montarFormulario(movimientoExistente = gasto, onEliminar = {})

        // Disabled AND still showing the real type as selected -- selected/enabled are independent
        // semantics, so both must be asserted to actually pin "which type is this" while locked.
        composeTestRule.onNodeWithText("Gastos").assertIsDisplayed().assertIsNotEnabled().assertIsSelected()
        composeTestRule.onNodeWithText("Ingresos").assertIsDisplayed().assertIsNotEnabled().assertIsNotSelected()
    }

    @Test
    fun `chips de tipo estan habilitados al crear un movimiento nuevo`() {
        montarFormulario()

        composeTestRule.onNodeWithText("Gastos").assertIsDisplayed().assertIsEnabled()
        composeTestRule.onNodeWithText("Ingresos").assertIsDisplayed().assertIsEnabled()
    }

    @Test
    fun `editar movimiento existente abre en modo vista con campos deshabilitados`() {
        montarFormulario(movimientoExistente = gasto, onEliminar = {})

        composeTestRule.onNodeWithText("Monto").assertIsNotEnabled()
        composeTestRule.onNodeWithText("Editar").assertIsDisplayed()
        composeTestRule.onNodeWithText("Guardar").assertDoesNotExist()
        composeTestRule.onNodeWithText("Cancelar").assertDoesNotExist()
        composeTestRule.onNodeWithText("Eliminar").assertDoesNotExist()
    }

    @Test
    fun `pulsar editar habilita campos y muestra guardar cancelar y eliminar`() {
        montarFormulario(movimientoExistente = gasto, onEliminar = {})

        composeTestRule.onNodeWithText("Editar").performClick()

        composeTestRule.onNodeWithText("Monto").assertIsEnabled()
        composeTestRule.onNodeWithText("Descripción (opcional)").assertIsEnabled()
        composeTestRule.onNodeWithText("Guardar").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancelar").assertIsDisplayed()
        composeTestRule.onNodeWithText("Eliminar").assertIsDisplayed()
        composeTestRule.onNodeWithText("Gastos").assertIsNotEnabled()
        composeTestRule.onNodeWithText("Ingresos").assertIsNotEnabled()
    }

    @Test
    fun `cancelar revierte los cambios y vuelve a modo vista sin cerrar el sheet`() {
        montarFormulario(movimientoExistente = gasto, onEliminar = {})

        composeTestRule.onNodeWithText("Editar").performClick()
        composeTestRule.onNodeWithText("Monto").performTextReplacement("999")
        composeTestRule.onNodeWithText("Cancelar").performClick()

        composeTestRule.onNodeWithText("15").assertIsDisplayed()
        composeTestRule.onNodeWithText("Editar").assertIsDisplayed()
        composeTestRule.onNodeWithText("Guardar").assertDoesNotExist()
    }

    @Test
    fun `crear movimiento nuevo no muestra el boton editar`() {
        montarFormulario()

        composeTestRule.onNodeWithText("Editar").assertDoesNotExist()
        composeTestRule.onNodeWithText("Monto").assertIsEnabled()
        composeTestRule.onNodeWithText("Guardar").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancelar").assertDoesNotExist()
        composeTestRule.onNodeWithText("Eliminar").assertDoesNotExist()
    }

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
        // EliminarGastoUseCase stamps editedBy/editedAt/deletedAt before calling the repository,
        // so the recorded gasto isn't structurally equal to the original fixture -- only its
        // identity and the fact that a delete actually reached the repository are asserted here.
        assertEquals(listOf(gasto.id), movimientoRepo.eliminados.map { it.id })
        assertEquals(1, movimientoRepo.eliminados.size)
    }

    // Regression for the expanded/tablet layout: MovimientoFormularioPanel keeps its form
    // composable mounted in the same slot as the user taps between movimientos, and
    // MovimientosScreen sets FormularioMovimiento.Editar(movimiento) directly without ever
    // passing through Cerrado in between -- so the form state must be keyed to the movimiento's
    // identity or it carries over stale modoEdicion/tipo/categoriaId/montoTexto/descripcion.
    @Test
    fun `cambiar de movimiento en el panel expandido reinicia el modo vista`() {
        val viewModel = crearViewModel()
        categoriaRepo.categorias.value = categoriasGasto

        var formulario: FormularioMovimiento by mutableStateOf(FormularioMovimiento.Editar(gasto))

        composeTestRule.setContent {
            MovimientoFormularioPanel(
                MovimientoFormularioParametros(
                    planId = "p1",
                    tipoPorDefecto = TipoCategoria.GASTO,
                    formulario = formulario,
                    viewModel = viewModel,
                    cargando = false,
                    onCerrar = {},
                ),
            )
        }

        // Enter edit mode on movimiento A (gasto).
        composeTestRule.onNodeWithText("Editar").performClick()
        composeTestRule.onNodeWithText("Guardar").assertIsDisplayed()

        // Switch to a different movimiento (B) in the same detail-pane slot.
        formulario = FormularioMovimiento.Editar(gasto2)
        composeTestRule.waitForIdle()

        // B must open fresh in view mode, not inherit A's edit mode.
        composeTestRule.onNodeWithText("Editar").assertIsDisplayed()
        composeTestRule.onNodeWithText("Guardar").assertDoesNotExist()
        composeTestRule.onNodeWithText("Monto").assertIsNotEnabled()
    }

    // Regression: Cancelar must reapply the same "fallback to first available category" logic
    // as the on-open LaunchedEffect, not blindly restore movimientoExistente.categoriaId -- that
    // stored id can point at a category that no longer exists, and the LaunchedEffect's key
    // (categoriasDisponibles) won't re-run to fix it a second time.
    @Test
    fun `cancelar sin cambios no reinstala una categoria eliminada`() {
        var guardado: MovimientoFormDatos? = null
        montarFormulario(
            movimientoExistente = Gasto(
                id = "cat-borrada",
                planId = "p1",
                categoriaId = "cat-borrada",
                monto = Monto(1000),
                fecha = LocalDate.of(2026, 8, 22),
                creadoPor = "user-1",
            ),
            onEliminar = {},
            onGuardar = { guardado = it },
        )

        categoriaRepo.categorias.value = categoriasGasto
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Editar").performClick()
        composeTestRule.onNodeWithText("Cancelar").performClick()
        composeTestRule.onNodeWithText("Editar").performClick()
        composeTestRule.onNodeWithText("Guardar").performClick()

        assertEquals("cat-comida", guardado?.categoriaId)
    }
}

private class FakeMovimientoRepositorioForm : MovimientoRepository {
    val eliminados = mutableListOf<Gasto>()
    override suspend fun addGasto(gasto: Gasto) = Unit
    override suspend fun addIngreso(ingreso: Ingreso) = Unit
    override suspend fun actualizarGasto(gasto: Gasto) = Unit
    override suspend fun eliminarGasto(gasto: Gasto) { eliminados.add(gasto) }
    override suspend fun actualizarIngreso(ingreso: Ingreso) = Unit
    override suspend fun eliminarIngreso(ingreso: Ingreso) = Unit
    override fun observeGastos(planId: String): Flow<List<Gasto>> = flowOf(emptyList())
    override fun observeIngresos(planId: String): Flow<List<Ingreso>> = flowOf(emptyList())
    override suspend fun resolverConflictoGastoUsandoLocal(gasto: Gasto, clave: String) = Unit
    override suspend fun resolverConflictoIngresoUsandoLocal(ingreso: Ingreso, clave: String) = Unit
    override suspend fun resolverConflictoGastoUsandoRemoto(id: String, clave: String) = Unit
    override suspend fun resolverConflictoIngresoUsandoRemoto(id: String, clave: String) = Unit
}

private class FakeRegistroConflictosPendientesForm : RegistroConflictosPendientes {
    private val _conflictos = MutableStateFlow<Map<String, ConflictoEdicion>>(emptyMap())
    override val conflictos: Flow<Map<String, ConflictoEdicion>> = _conflictos.asStateFlow()
    override suspend fun conflictoPara(clave: String): ConflictoEdicion? = _conflictos.value[clave]
    override suspend fun registrar(clave: String, conflicto: ConflictoEdicion) {
        _conflictos.value = _conflictos.value + (clave to conflicto)
    }
    override suspend fun resolver(clave: String) {
        _conflictos.value = _conflictos.value - clave
    }
}

private class FakeCategoriaRepositorioForm : CategoriaRepository {
    val categorias = MutableStateFlow<List<Categoria>>(emptyList())
    override fun observarCategorias(planId: String): Flow<List<Categoria>> = categorias
    override fun observarCategoriasPorTipo(planId: String, tipo: TipoCategoria): Flow<List<Categoria>> =
        categorias.map { cats -> cats.filter { it.tipo == tipo } }
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
    override suspend fun iniciarSesionConEmail(email: String, password: String) = Unit
    override suspend fun enviarRecuperacionPassword(email: String) = Unit
    override suspend fun actualizarPerfil(nombre: String?, photoUrl: String?) = Unit
    override suspend fun cerrarSesion() = Unit
}
